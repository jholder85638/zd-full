/*
 * 
 */

package com.zimbra.cs.mailbox;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.MessagingException;
import javax.mail.internet.ContentType;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import com.zimbra.common.mime.MimeConstants;
import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.ByteUtil;
import com.zimbra.common.util.Log;
import com.zimbra.common.util.LogFactory;
import com.zimbra.common.util.Pair;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.common.zmime.ZMimeBodyPart;
import com.zimbra.common.zmime.ZMimeMessage;
import com.zimbra.common.zmime.ZMimeMultipart;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.CalendarResource;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.account.Provisioning.AccountBy;
import com.zimbra.cs.db.DbMailItem;
import com.zimbra.cs.index.IndexDocument;
import com.zimbra.cs.index.LuceneFields;
import com.zimbra.cs.index.analysis.FieldTokenStream;
import com.zimbra.cs.index.analysis.RFC822AddressTokenStream;
import com.zimbra.cs.localconfig.DebugConfig;
import com.zimbra.cs.mailbox.MailItem.CustomMetadata.CustomMetadataList;
import com.zimbra.cs.mailbox.calendar.Alarm;
import com.zimbra.cs.mailbox.calendar.Alarm.Action;
import com.zimbra.cs.mailbox.calendar.CalendarMailSender;
import com.zimbra.cs.mailbox.calendar.ICalTimeZone;
import com.zimbra.cs.mailbox.calendar.IcalXmlStrMap;
import com.zimbra.cs.mailbox.calendar.Invite;
import com.zimbra.cs.mailbox.calendar.InviteChanges;
import com.zimbra.cs.mailbox.calendar.InviteInfo;
import com.zimbra.cs.mailbox.calendar.ParsedDateTime;
import com.zimbra.cs.mailbox.calendar.ParsedDuration;
import com.zimbra.cs.mailbox.calendar.RecurId;
import com.zimbra.cs.mailbox.calendar.Recurrence;
import com.zimbra.cs.mailbox.calendar.Recurrence.IRecurrence;
import com.zimbra.cs.mailbox.calendar.Recurrence.RecurrenceRule;
import com.zimbra.cs.mailbox.calendar.Recurrence.SimpleRepeatingRule;
import com.zimbra.cs.mailbox.calendar.TimeZoneMap;
import com.zimbra.cs.mailbox.calendar.ZAttendee;
import com.zimbra.cs.mailbox.calendar.ZCalendar.ICalTok;
import com.zimbra.cs.mailbox.calendar.ZCalendar.ZProperty;
import com.zimbra.cs.mailbox.calendar.ZCalendar.ZVCalendar;
import com.zimbra.cs.mailbox.calendar.ZOrganizer;
import com.zimbra.cs.mailbox.calendar.ZRecur;
import com.zimbra.cs.mailbox.calendar.ZRecur.Frequency;
import com.zimbra.cs.mime.Mime;
import com.zimbra.cs.mime.Mime.FixedMimeMessage;
import com.zimbra.cs.mime.MimeVisitor;
import com.zimbra.cs.mime.ParsedMessage;
import com.zimbra.cs.mime.ParsedMessage.CalendarPartInfo;
import com.zimbra.cs.session.PendingModifications.Change;
import com.zimbra.cs.store.MailboxBlob;
import com.zimbra.cs.store.StagedBlob;
import com.zimbra.cs.store.StoreManager;
import com.zimbra.cs.util.AccountUtil.AccountAddressMatcher;
import com.zimbra.cs.util.JMSession;

/**
 * An APPOINTMENT consists of one or more INVITES in the same series -- ie that
 * have the same UID. From the appointment you can get the INSTANCES which are
 * the start/end times of each occurrence.
 *
 * Sample Appointment: APPOINTMENT UID=1234 (two INVITES above) ...Instances on
 * every monday with name "Gorilla Discussion" EXCEPT for the 21st, where we
 * talk about lefties instead. CANCELED for the 28th
 */
public abstract class CalendarItem extends MailItem implements ScheduledTaskResult {

    // these are special values indexed in the L_FIELD structured index field, they allow us to
    // restrict lucene searches keyed off of public/private settings
    //
    // the constants should be all-lowercase if possible, saves us doing a toLower if we need to
    // manually build a query to search for them w/o using our custom FieldTokenStream
    public static final String INDEX_FIELD_ITEM_CLASS = "_calendaritemclass";

    // special values for next alarm trigger time
    public static final long NEXT_ALARM_KEEP_CURRENT  = 0;   // keep current value
    public static final long NEXT_ALARM_ALL_DISMISSED = -1;  // all alarms have been shown and dismissed
    public static final long NEXT_ALARM_FROM_NOW = -2;       // compute next trigger time from current time

    static Log sLog = LogFactory.getLog(CalendarItem.class);

    private String mUid;

    /** the time IN MSEC UTC that this appointment/task "starts" */
    private long mStartTime;
    /** the time IN MSEC UTC that this appointment/task "ends" */
    private long mEndTime;

    private AlarmData mAlarmData;  // next/last DISPLAY alarm info

    private Recurrence.IRecurrence mRecurrence;
    private TimeZoneMap mTzMap;

    private List<Invite> mInvites;

    private ReplyList mReplyList;
    protected ReplyList getReplyList() { return mReplyList; }

    public TimeZoneMap getTimeZoneMap() { return mTzMap; }

    public static class CalendarMetadata {
        public int mailboxId;
        public int itemId;
        public String uid;
        public int mod_metadata;
        public int mod_content;
        public long start_time;
        public long end_time;

        public CalendarMetadata(int mailboxId, int itemId, String uid, int mod_metadata, int mod_content, long start_time, long end_time) {
            this.mailboxId = mailboxId;
            this.itemId = itemId;
            this.uid = uid;
            this.mod_metadata = mod_metadata;
            this.mod_content = mod_content;
            this.start_time = start_time;
            this.end_time = end_time;
        }
    }

    protected CalendarItem(Mailbox mbox, UnderlyingData data) throws ServiceException {
        super(mbox, data);
        if (mData.type != TYPE_APPOINTMENT && mData.type != TYPE_TASK)
            throw new IllegalArgumentException();
    }

    public Recurrence.IRecurrence getRecurrence() {
        return mRecurrence;
    }

    public boolean isRecurring() {
        return (mRecurrence != null);
    }

    public boolean hasExceptions() {
        if (isRecurring()) {
            for (Invite inv : mInvites) {
                if (inv.hasRecurId()) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public String getSender() {
        String sender = null;
        Invite firstInvite = getDefaultInviteOrNull();
        if (firstInvite != null) {
            ZOrganizer org = firstInvite.getOrganizer();
            if (org != null)
                sender = org.getIndexString();
        }
        if (sender == null)
            sender = "";
        return sender;
    }

    public long getStartTime() {
        return mStartTime;
    }

    public long getEndTime() {
        return mEndTime;
    }

    public AlarmData getAlarmData() {
        return mAlarmData;
    }

    @Override
    public void saveMetadata() throws ServiceException {
//        super.saveMetadata();
        reanalyze(null, getSize());
    }

    @Override
    public void markItemModified(int reason) {
        mMailbox.markItemModified(this, reason);
    }


    @Override
    boolean isTaggable() {
        return true;
    }

    @Override
    boolean isCopyable() {
        return inDumpster();
    }

    @Override
    boolean isMovable() {
        return true;
    }

    @Override
    public boolean isMutable() {
        return true;
    }

    @Override
    boolean isIndexed() {
        return true;
    }

    @Override
    boolean canHaveChildren() {
        return false;
    }

    @Override
    public List<IndexDocument> generateIndexData(boolean doConsistencyCheck) throws MailItem.TemporaryIndexingException {
        List<IndexDocument> docs = null;
        synchronized(getMailbox()) {
            docs = getIndexDocuments();
        }
        return docs;
    }

    protected List<IndexDocument> getIndexDocuments() throws TemporaryIndexingException{
        List<IndexDocument> toRet = new ArrayList<IndexDocument>();

        // Special case to prevent getDefaultInviteOrNull() from logging an error
        // when this method is called during commit of cancel operation.
        if (numInvites() < 1)
            return toRet;

        Invite defaultInvite = getDefaultInviteOrNull();

        String defaultLocation = "";
        if (defaultInvite != null && defaultInvite.getLocation() != null)
            defaultLocation = defaultInvite.getLocation();

        String defaultName= "";
        if (defaultInvite != null && defaultInvite.getName() != null)
            defaultName = defaultInvite.getName();

        String defaultOrganizer = "";
        if (defaultInvite != null && defaultInvite.getOrganizer() != null)
            defaultOrganizer = defaultInvite.getOrganizer().getIndexString();

        for (Invite inv : getInvites()) {
            StringBuilder s = new StringBuilder();
            List<String> toAddrs = new ArrayList<String>();

            // NAME (subject)
            String nameToUse = "";
            if (inv.getName() != null) {
                s.append(inv.getName()).append(' ');
                nameToUse = inv.getName();
            } else {
                s.append(defaultName).append(' ');
                nameToUse = defaultName;
            }

            // ORGANIZER (from)
            String orgToUse = null;
            if (inv.getOrganizer() != null) {
                String thisInvOrg = inv.getOrganizer().getIndexString();
                if (thisInvOrg != null && thisInvOrg.length() > 0)
                    orgToUse = thisInvOrg;
            }
            if (orgToUse == null)
                orgToUse = defaultOrganizer;

            // ATTENDIES (TO)
            for (ZAttendee at : inv.getAttendees()) {
                try {
                    toAddrs.add(at.getFriendlyAddress().toString());
                    s.append(at.getIndexString()).append(' ');
                } catch (ServiceException e) {}
            }
            s.append(' ');

            // LOCATION
            if (inv.getLocation() != null) {
                s.append(inv.getLocation()).append(' ');
            }  else {
                s.append(defaultLocation).append(' ');
            }

            // DESCRIPTION
            try {
                s.append(inv.getDescription()).append(' ');
            } catch (ServiceException ex) {
                if (ZimbraLog.index.isDebugEnabled()) {
                    ZimbraLog.index.debug("Caught exception fetching description while indexing CalendarItem "+this.getId()+" skipping", ex);
                }
            }

            // COMMENTS
            List<String> comments = inv.getComments();
            if (comments != null && !comments.isEmpty()) {
                for (String comm : comments) {
                    s.append(comm).append(' ');
                }
            }

            // CONTACTS
            List<String> contacts = inv.getContacts();
            if (contacts != null && !contacts.isEmpty()) {
                for (String contact : contacts) {
                    s.append(contact).append(' ');
                }
            }

            // CATEGORIES
            List<String> categories = inv.getCategories();
            if (categories != null && !categories.isEmpty()) {
                for (String cat : categories) {
                    s.append(cat).append(' ');
                }
            }

            MimeMessage mm = null;

            if (!inv.getDontIndexMimeMessage()) {
                try {
                    mm = inv.getMimeMessage();
                } catch(ServiceException e) {
                    if (ZimbraLog.index.isDebugEnabled()) {
                        ZimbraLog.index.debug("Caught MessagingException for Invite "+inv.toString()+" while fetching MM during indexing of CalendarItem "+this.getId()+" skipping Invite", e);
                    }
                }
            }

            List<IndexDocument> docList = new ArrayList<IndexDocument>();

            if (mm == null) { // no blob!
                IndexDocument doc = new IndexDocument();
                // need to properly emulate an indexed Invite message here -- set the TOP partname
                doc.addPartName(LuceneFields.L_PARTNAME_TOP);
                docList.add(doc);
            } else {
                try {
                    ParsedMessage pm = new ParsedMessage(mm, mMailbox.attachmentsIndexingEnabled());
                    pm.analyzeFully();

                    if (pm.hasTemporaryAnalysisFailure())
                        throw new MailItem.TemporaryIndexingException();

                    docList = pm.getLuceneDocuments();
                } catch(ServiceException e) {
                    if (ZimbraLog.index.isDebugEnabled()) {
                        ZimbraLog.index.debug("Caught MessagingException for Invite "+inv.toString()+" while indexing CalendarItem "+this.getId()+" skipping Invite", e);
                    }
                }
            }

            for (IndexDocument doc : docList) {
                // update the doc, overriding many of the fields with data from the appointment
                doc.addContent(s.toString());

                doc.removeTo();
                doc.removeFrom();
                doc.removeSubject();

                for (String to : toAddrs) {
                    doc.addTo(new RFC822AddressTokenStream(to));
                }
                doc.addFrom(new RFC822AddressTokenStream(orgToUse));
                doc.addSubject(nameToUse);
                toRet.add(doc);
            }
        }

        // set the "public"/"private" flag in the index for this appointment
        FieldTokenStream fields = new FieldTokenStream(INDEX_FIELD_ITEM_CLASS, isPublic() ? "public" : "private");
        for (IndexDocument doc : toRet) {
            doc.addField(fields);
        }

        return toRet;
    }

    static CalendarItem create(int id, Folder folder, int flags, long tags, String uid,
                               ParsedMessage pm, Invite firstInvite, long nextAlarm, CustomMetadata custom)
    throws ServiceException {
        firstInvite.sanitize(false);

        if (!folder.canAccess(ACL.RIGHT_INSERT))
            throw ServiceException.PERM_DENIED("you do not have the required rights on the folder");
        if (!firstInvite.isPublic() && !folder.canAccess(ACL.RIGHT_PRIVATE))
            throw ServiceException.PERM_DENIED("you do not have permission to create private calendar item in this folder");

        Mailbox mbox = folder.getMailbox();

        if (pm != null && pm.hasAttachments()) {
            firstInvite.setHasAttachment(true);
            flags |= Flag.BITMASK_ATTACHED;
        } else {
            firstInvite.setHasAttachment(false);
            flags &= ~Flag.BITMASK_ATTACHED;
        }
        if (firstInvite.isDraft())
            flags |= Flag.BITMASK_DRAFT;
        else
            flags &= ~Flag.BITMASK_DRAFT;
        if (firstInvite.isHighPriority())
            flags |= Flag.BITMASK_HIGH_PRIORITY;
        else
            flags &= ~Flag.BITMASK_HIGH_PRIORITY;
        if (firstInvite.isLowPriority())
            flags |= Flag.BITMASK_LOW_PRIORITY;
        else
            flags &= ~Flag.BITMASK_LOW_PRIORITY;

        byte type = firstInvite.isEvent() ? TYPE_APPOINTMENT : TYPE_TASK;

        String sender = null;
        ZOrganizer org = firstInvite.getOrganizer();
        if (org != null)
            sender = org.getIndexString();
        if (sender == null)
            sender = "";

        String subject = firstInvite.getName();
        if (subject == null)
            subject= "";

        List<Invite> invites = new ArrayList<Invite>();
        invites.add(firstInvite);

        Recurrence.IRecurrence recur = firstInvite.getRecurrence();
        long startTime, endTime;
        if (recur != null) {
            ParsedDateTime dtStart = recur.getStartTime();
            startTime = dtStart != null ? dtStart.getUtcTime() : 0;
            ParsedDateTime dtEnd = recur.getEndTime();
            endTime = dtEnd != null ? dtEnd.getUtcTime() : 0;
        } else {
            ParsedDateTime dtStart = firstInvite.getStartTime();
            startTime = dtStart != null ? dtStart.getUtcTime() : 0;
            ParsedDateTime dtEnd = firstInvite.getEffectiveEndTime();
            endTime = dtEnd != null ? dtEnd.getUtcTime() : startTime;
        }

        Account account = mbox.getAccount();
        firstInvite.updateMyPartStat(account, firstInvite.getPartStat());

        UnderlyingData data = new UnderlyingData();
        data.id       = id;
        data.type     = type;
        data.folderId = folder.getId();
        if (!folder.inSpam() || mbox.getAccount().getBooleanAttr(Provisioning.A_zimbraJunkMessagesIndexingEnabled, false))
            data.indexId  = id;
        data.imapId   = id;
        data.date     = mbox.getOperationTimestamp();
        data.flags    = flags & (Flag.FLAGS_CALITEM | Flag.FLAGS_GENERIC);
        data.tags     = tags;
        data.subject  = DbMailItem.truncateSubjectToMaxAllowedLength(subject);
        data.metadata = encodeMetadata(DEFAULT_COLOR_RGB, 1, custom, uid, startTime, endTime, recur,
                                       invites, firstInvite.getTimeZoneMap(), new ReplyList(), null);
        data.contentChanged(mbox);
        if (!firstInvite.hasRecurId())
            ZimbraLog.calendar.info(
                    "Adding CalendarItem: id=%d, Message-ID=\"%s\", folderId=%d, subject=\"%s\", UID=%s",
                    data.id, pm != null ? pm.getMessageID() : "(none)", folder.getId(),
                    firstInvite.isPublic() ? firstInvite.getName() : "(private)",
                    firstInvite.getUid());
        else
            ZimbraLog.calendar.info(
                    "Adding CalendarItem: id=%d, Message-ID=\"%s\", folderId=%d, subject=\"%s\", UID=%s, recurId=%s",
                    data.id, pm != null ? pm.getMessageID() : "(none)", folder.getId(),
                    firstInvite.isPublic() ? firstInvite.getName() : "(private)",
                    firstInvite.getUid(), firstInvite.getRecurId().getDtZ());
        DbMailItem.create(mbox, data, sender);

        CalendarItem item = type == TYPE_APPOINTMENT ? new Appointment(mbox, data) : new Task(mbox, data);

        // If we're creating an invite during email delivery, always default to NEEDS_ACTION state.
        // If not email delivery, we assume the requesting client knows what it's doing and has set the
        // correct partstat in the invite.
        String defaultPartStat;
        if (mbox.getOperationContext() == null) {
            // octxt == null implies we're in email delivery.  (There needs to be better way to determine this...)
            defaultPartStat = IcalXmlStrMap.PARTSTAT_NEEDS_ACTION;
        } else {
            defaultPartStat = firstInvite.getPartStat();
        }
        item.processPartStat(firstInvite, pm != null ? pm.getMimeMessage() : null, true, defaultPartStat);
        item.finishCreation(null);

        if (pm != null)
            item.createBlob(pm, firstInvite);

        item.mEndTime = item.recomputeRecurrenceEndTime(item.mEndTime);

        if (firstInvite.hasAlarm()) {
            item.recomputeNextAlarm(nextAlarm, false);
            item.saveMetadata();
            AlarmData alarmData = item.getAlarmData();
            if (alarmData != null) {
                long newNextAlarm = alarmData.getNextAt();
                if (newNextAlarm > 0 && newNextAlarm < item.mStartTime)
                    item.mStartTime = newNextAlarm;
            }
        }

        DbMailItem.addToCalendarItemTable(item);

        Callback cb = getCallback();
        if (cb != null)
            cb.created(item);

        return item;
    }

    private long recomputeRecurrenceEndTime(long defaultVal)
    throws ServiceException {
        long endTime = defaultVal;
        if (mRecurrence != null) {
            ParsedDateTime e = mRecurrence.getEndTime();
            if (e != null)
                endTime = e.getUtcTime();
        }
        return endTime;
    }

    // for migration of old data
    public int fixRecurrenceEndTime() throws ServiceException {
        long endTime = recomputeRecurrenceEndTime(mEndTime);
        if (endTime != mEndTime) {
            mEndTime = endTime;
            DbMailItem.updateInCalendarItemTable(this);
            return 1;
        }
        return 0;
    }

    private boolean updateRecurrence(long nextAlarm) throws ServiceException {
        long startTime, endTime;

        // update our recurrence rule, start with the initial rule
        Invite firstInv = getDefaultInviteOrNull();
        if (firstInv == null) {
            return false;
        }

        IRecurrence recur = firstInv.getRecurrence();
        if (recur instanceof Recurrence.RecurrenceRule) {
            mRecurrence = (IRecurrence) recur.clone();

            // now, go through the list of invites and find all the exceptions
            for (Invite cur : mInvites) {
                if (cur != firstInv) {
                    String method = cur.getMethod();
                    if (cur.isCancel()) {
                        assert(cur.hasRecurId());
                        if (cur.hasRecurId()) {
                            Recurrence.CancellationRule cancelRule =
                                new Recurrence.CancellationRule(cur.getRecurId());

                            ((Recurrence.RecurrenceRule) mRecurrence).addException(cancelRule);
                        }
                    } else if (method.equals(ICalTok.REQUEST.toString()) ||
                        method.equals(ICalTok.PUBLISH.toString())) {
                        assert (cur.hasRecurId());
                        if (cur.hasRecurId() && cur.getStartTime() != null) {
                            Recurrence.ExceptionRule exceptRule = null;
                            IRecurrence curRule = cur.getRecurrence();
                            if (curRule != null && curRule instanceof Recurrence.ExceptionRule) {
                                exceptRule = (Recurrence.ExceptionRule) curRule.clone();
                            } else {
                                // create a fake ExceptionRule wrapper around the single-instance
                                exceptRule = new Recurrence.ExceptionRule(
                                        cur.getRecurId(),
                                        cur.getStartTime(),
                                        cur.getEffectiveDuration(),
                                        new InviteInfo(cur)
                                        );
                            }
                            ((Recurrence.RecurrenceRule) mRecurrence).addException(exceptRule);
                        } else {
                            sLog.debug("Got second invite with no RecurID: " + cur.toString());
                        }
                    }
                }
            }
            // Find the earliest DTSTART and latest DTEND.  We're just looking for the bounds, so we won't worry
            // about cancelled instances.
            ParsedDateTime earliestStart = null;
            ParsedDateTime latestEnd = null;
            for (Invite cur : mInvites) {
                if (!cur.isCancel()) {
                    ParsedDateTime start = cur.getStartTime();
                    if (earliestStart == null)
                        earliestStart = start;
                    else if (start != null && start.compareTo(earliestStart) < 0)
                        earliestStart = start;
                    ParsedDateTime end = cur.getEffectiveEndTime();
                    if (latestEnd == null)
                        latestEnd = end;
                    else if (end != null && end.compareTo(latestEnd) > 0)
                        latestEnd = end;
                }
            }
            // Take the later of latestEnd and recurrence's end time.
            ParsedDateTime recurEnd = mRecurrence.getEndTime();
            if (latestEnd == null)
                latestEnd = recurEnd;
            else if (recurEnd != null && recurEnd.compareTo(latestEnd) > 0)
                latestEnd = recurEnd;

            // update the start and end time in the CalendarItem table if
            // necessary
            startTime = earliestStart != null ? earliestStart.getUtcTime() : 0;
            endTime = latestEnd != null ? latestEnd.getUtcTime() : 0;
        } else {
            mRecurrence = null;
            startTime = 0; endTime = 0;
            for (Invite inv : mInvites) {
                if (!inv.isCancel()) {
                    ParsedDateTime dtStart = inv.getStartTime();
                    long st = dtStart != null ? dtStart.getUtcTime() : 0;
                    if (st != 0 && (st < startTime || startTime == 0))
                        startTime = st;
                    ParsedDateTime dtEnd = inv.getEffectiveEndTime();
                    long et = dtEnd != null ? dtEnd.getUtcTime() : 0;
                    if (et != 0 && et > endTime)
                        endTime = et;
                }
            }
        }

        // Adjust start/end times before recomputing alarm because alarm computation depends on those times.
        boolean timesChanged = false;
        if (mStartTime != startTime || mEndTime != endTime) {
            timesChanged = true;
            mStartTime = startTime;
            mEndTime = endTime;
        }
        // Recompute next alarm.  Bring appointment start time forward to the alarm time,
        // if the next alarm is before the first instance.
        recomputeNextAlarm(nextAlarm, false);
        if (mAlarmData != null) {
            long newNextAlarm = mAlarmData.getNextAt();
            if (newNextAlarm > 0 && newNextAlarm < startTime && mStartTime != startTime) {
                timesChanged = true;
                mStartTime = newNextAlarm;
            }
        }

        if (timesChanged) {
            if (ZimbraLog.calendar.isDebugEnabled()) {
                ZimbraLog.calendar.debug("Updating recurrence for %s.  nextAlarm=%d.",
                    getMailopContext(this), nextAlarm);
            }
            DbMailItem.updateInCalendarItemTable(this);
        }
        return true;
    }

    public static final String FN_CALITEM_RECURRENCE = "apptRecur";

    @Override
    void decodeMetadata(Metadata meta) throws ServiceException {
        super.decodeMetadata(meta);

        int mdVersion = meta.getVersion();

        mUid = Invite.fixupIfOutlookUid(meta.get(Metadata.FN_UID, null));
        mInvites = new ArrayList<Invite>();

        ICalTimeZone accountTZ = ICalTimeZone.getAccountTimeZone(getMailbox().getAccount());
        if (mdVersion < 6) {
            mStartTime = 0;
            mEndTime = 0;
        } else {
            Set<String> tzids = new HashSet<String>();
            mTzMap = TimeZoneMap.decodeFromMetadata(meta.getMap(Metadata.FN_TZMAP), accountTZ);

            // appointment/task start and end
            mStartTime = meta.getLong(Metadata.FN_CALITEM_START, 0);
            mEndTime = meta.getLong(Metadata.FN_CALITEM_END, 0);

            // invite ID's
            long numComp = meta.getLong(Metadata.FN_NUM_COMPONENTS);
            for (int i = 0; i < numComp; i++) {
                Metadata md = meta.getMap(Metadata.FN_INV + i);
                Invite inv = Invite.decodeMetadata(getMailboxId(), md, this, accountTZ);
                mInvites.add(inv);
                tzids.addAll(inv.getReferencedTZIDs());
                mTzMap.add(inv.getTimeZoneMap());
            }

            Metadata metaRecur = meta.getMap(FN_CALITEM_RECURRENCE, true);
            if (metaRecur != null) {
                mRecurrence = Recurrence.decodeMetadata(metaRecur, mTzMap);
                if (mRecurrence != null) {
                    tzids.addAll(Recurrence.getReferencedTZIDs(mRecurrence));
                }
            }

            if (meta.containsKey(Metadata.FN_REPLY_LIST)) {
                mReplyList = ReplyList.decodeFromMetadata(meta.getMap(Metadata.FN_REPLY_LIST), mTzMap);
                // Get all TZIDs referenced by replies.
                for (ReplyInfo ri : mReplyList.mReplies) {
                    if (ri.mRecurId != null) {
                        ParsedDateTime dt = ri.mRecurId.getDt();
                        if (dt != null && dt.hasTime()) {
                            ICalTimeZone tz = dt.getTimeZone();
                            if (tz != null)
                                tzids.add(tz.getID());
                        }
                    }
                }
            } else {
                mReplyList = new ReplyList();
            }

            Metadata metaAlarmData = meta.getMap(Metadata.FN_ALARM_DATA, true);
            if (metaAlarmData != null)
                mAlarmData = AlarmData.decodeMetadata(metaAlarmData);

            // Reduce tzmap to minimal set of TZIDs referenced by invites, recurrence, and replies.
            mTzMap.reduceTo(tzids);
        }
    }

    @Override Metadata encodeMetadata(Metadata meta) {
        return encodeMetadata(meta, mRGBColor, mVersion, mExtendedData, mUid, mStartTime, mEndTime,
                              mRecurrence, mInvites, mTzMap, mReplyList, mAlarmData);
    }

    private static String encodeMetadata(Color color, int version, CustomMetadata custom, String uid, long startTime, long endTime,
                                         Recurrence.IRecurrence recur, List<Invite> invs, TimeZoneMap tzmap,
                                         ReplyList replyList, AlarmData alarmData) {
        CustomMetadataList extended = (custom == null ? null : custom.asList());
        return encodeMetadata(new Metadata(), color, version, extended, uid, startTime, endTime, recur,
                              invs, tzmap, replyList, alarmData).toString();
    }

    static Metadata encodeMetadata(Metadata meta, Color color, int version, CustomMetadataList extended,
                                   String uid, long startTime, long endTime, Recurrence.IRecurrence recur,
                                   List<Invite> invs, TimeZoneMap tzmap, ReplyList replyList, AlarmData alarmData) {
        meta.put(Metadata.FN_TZMAP, tzmap.encodeAsMetadata());
        meta.put(Metadata.FN_UID, uid);
        meta.put(Metadata.FN_CALITEM_START, startTime);
        meta.put(Metadata.FN_CALITEM_END, endTime);
        meta.put(Metadata.FN_NUM_COMPONENTS, invs.size());

        meta.put(Metadata.FN_REPLY_LIST, replyList.encodeAsMetadata());

        int num = 0;
        for (Invite comp : invs)
            meta.put(Metadata.FN_INV + num++, Invite.encodeMetadata(comp));

        if (recur != null)
            meta.put(FN_CALITEM_RECURRENCE, recur.encodeMetadata());

        if (alarmData != null)
            meta.put(Metadata.FN_ALARM_DATA, alarmData.encodeMetadata());

        return MailItem.encodeMetadata(meta, color, version, extended);
    }

    /**
     * Expand all the instances for the time period from start to end
     *
     * @param start
     * @param end
     * @param includeAlarmOnlyInstances
     * @return list of Instances for the specified time period
     */
    public Collection<Instance> expandInstances(long start, long end, boolean includeAlarmOnlyInstances)
    throws ServiceException {
        long endAdjusted = end;
        long alarmInstStart = 0;
        if (includeAlarmOnlyInstances) {
            // Adjust end of the range for alarm case.  If the alarm time of an appointment falls
            // within the time range but the instance start time is after the range, we need to
            // extend the end time long enough include the instance start time.  This will allow
            // the rest of the recurrence expansion logic to do the right thing based on the new
            // range.
            if (mAlarmData != null) {
                alarmInstStart = mAlarmData.getNextInstanceStart();
                long nextAlarm = mAlarmData.getNextAt();
                if (nextAlarm >= start && nextAlarm < end) {
                    if (alarmInstStart >= end)
                        endAdjusted = alarmInstStart + 1;
                }
            }
        }

        List<Instance> instances = new ArrayList<Instance>();
        if (mRecurrence != null) {
            long startTime = System.currentTimeMillis();
            instances = Recurrence.expandInstances(mRecurrence, getId(), start, endAdjusted);
            if (ZimbraLog.calendar.isDebugEnabled()) {
                long elapsed = System.currentTimeMillis() - startTime;
                ZimbraLog.calendar.debug(
                        "RECURRENCE EXPANSION for appt/task " + getId() +
                        ": start=" + start + ", end=" + end +
                        "; took " + elapsed + "ms");
            }
        } else {
            // Calendar item has no recurrence.  The basic case is a simple, non-recurring appointment
            // which has only one invite.  If there are multiple invites, it could be an attendee who
            // was invited to a few instances but not the series of a recurring appointment by the
            // organizer.
            if (mInvites != null) {
                for (Invite inv : mInvites) {
                    if (inv.isCancel())  // Skip canceled instances.
                        continue;
                    ParsedDateTime dtStart = inv.getStartTime();
                    long invStart = dtStart != null ? dtStart.getUtcTime() : 0;
                    ParsedDateTime dtEnd = inv.getEffectiveEndTime();
                    long invEnd = dtEnd != null ? dtEnd.getUtcTime() : 0;
                    if ((invStart < endAdjusted && invEnd > start) || (dtStart == null)) {
                        Instance inst = new Instance(getId(), new InviteInfo(inv),
                                                     dtStart != null, dtEnd != null,
                                                     invStart, invEnd,
                                                     inv.isAllDayEvent(),
                                                     dtStart != null ? dtStart.getOffset() : 0,
                                                     dtEnd != null ? dtEnd.getOffset() : 0,
                                                     inv.hasRecurId(), false);
                        instances.add(inst);
                    }
                }
            }
        }

        // Remove instances that aren't in the actual range.
        for (Iterator<Instance> iter = instances.iterator(); iter.hasNext(); ) {
            Instance inst = iter.next();
            if (inst.hasStart() && inst.hasEnd()) {
                long instStart = inst.getStart();
                long instEnd = inst.getEnd();
                // Remove if instance is not the alarm instance and instance ends before range start
                // or instance starts after range end. (i.e. instance does not overlap range)
                if (instStart != alarmInstStart && (instEnd <= start || instStart >= end))
                    iter.remove();
            }
        }

        return instances;
    }

    public static class Instance implements Comparable<Instance> {
        private final boolean mHasStart;
        private final boolean mHasEnd;
        private long mStart;        // calculated start time of this instance
        private long mEnd;          // calculated end time of this instance

        private boolean mIsException; // TRUE if this instance is an exception
                                      // to a recurrence
        private final boolean mFromRdate;  // true if this instance was generated from RDATE
                                     // rather than RRULE or a stand-alone exception VEVENT/VTODO

        private final InviteInfo mInvId;

        private final int mCalItemId;
        private final boolean mAllDay;
        private int mStartTzOffset;    // used when mAllDay == true; timezone offset in millis of mStart
        private int mEndTzOffset;      // used when mAllDay == true; timezone offset in millis of mEnd

        /**
         * Create an Instance object using data in an Invite that points to
         * a specific instance of an CalendarItem.
         * @param inv
         * @return
         */
        public static Instance fromInvite(int calItemId, Invite inv) {
            ParsedDateTime dtStart = inv.getStartTime();
            long start = dtStart != null ? dtStart.getUtcTime() : 0;
            ParsedDateTime dtEnd = inv.getEffectiveEndTime();
            long end = dtEnd != null ? dtEnd.getUtcTime() : 0;
            int startTzo = 0, endTzo = 0;
            boolean allDay = inv.isAllDayEvent();
            if (allDay) {
                if (dtStart != null)
                    startTzo = dtStart.getOffset();
                if (dtEnd != null)
                    endTzo = dtEnd.getOffset();
            }
            return new Instance(calItemId, new InviteInfo(inv), dtStart != null, dtEnd != null, start, end,
                                allDay, startTzo, endTzo, inv.hasRecurId(), false);
        }

        public Instance(int calItemId, InviteInfo invInfo,
                boolean hasStart, boolean hasEnd,
                long start, long end, boolean allDay, int startTzOffset, int endTzOffset,
                boolean _exception, boolean fromRdate)
        {
            mInvId = invInfo;
            mCalItemId = calItemId;
            mHasStart = hasStart;
            mHasEnd = hasEnd;
            mAllDay = allDay;
            if (mHasStart) {
                mStart = start;
                mStartTzOffset = mAllDay ? startTzOffset : 0;  // don't set TZ offset for non-allday instances
            } else {
                mStart = 0;
                mStartTzOffset = 0;
            }
            if (mHasEnd) {
                mEnd = end;
                mEndTzOffset = mAllDay ? endTzOffset : 0;  // don't set TZ offset for non-allday instances
            } else {
                mEnd = 0;
                mEndTzOffset = 0;
            }
            mIsException = _exception;
            mFromRdate = fromRdate;
        }

        @Override
        public int compareTo(Instance other) {
            long toRet = mCalItemId - other.mCalItemId;
            if (toRet == 0) {
                toRet = compareTimes(mHasStart, mStart, mAllDay, mStartTzOffset,
                                     other.mHasStart, other.mStart, other.mAllDay, other.mStartTzOffset);
                if (toRet == 0) {
                    toRet = compareTimes(mHasEnd, mEnd, mAllDay, mEndTzOffset,
                                         other.mHasEnd, other.mEnd, other.mAllDay, other.mEndTzOffset);
                    if (toRet == 0) {
                        if (mInvId != null)
                            toRet = mInvId.compareTo(other.mInvId);
                        else if (other.mInvId != null)
                            toRet = other.mInvId.compareTo(mInvId) * -1;
                    }
                }
            }
            if (toRet > 0) {
                return 1;
            } else if (toRet < 0) {
                return -1;
            }
            return 0;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Instance)) {
                return false;
            }

            Instance other = (Instance) o;
            boolean sameInvId;
            if (mInvId != null)
                sameInvId = mInvId.equals(other.mInvId);
            else
                sameInvId = other.mInvId == null;
            return sameInvId && sameTime(other);
        }

        public boolean sameTime(Instance other) {
            // Times are same only if they are both all-day or both non-allday.
            if (mAllDay != other.mAllDay)
                return false;

            // If one has start time but the other doesn't, they have different times.
            if (mHasStart != other.mHasStart)
                return false;
            if (mHasStart) {  // implies other.mHasStart is also true
                // Both time and offset must be same to be considered the same time.
                if (mStart != other.mStart || mStartTzOffset != other.mStartTzOffset)
                    return false;
            }
            // Neither has start time, or both have same start time.

            // Likewise for end time.
            if (mHasEnd != other.mHasEnd)
                return false;
            if (mHasEnd) {  // implies other.mHasEnd is also true
                // Both time and offset must be same to be considered the same time.
                if (mEnd != other.mEnd || mEndTzOffset != other.mEndTzOffset)
                    return false;
            }
            // Neither has end time, or both have same start time.

            return true;
        }

        @Override
        public String toString() {
            StringBuilder toRet = new StringBuilder("INST(");
            Date dstart = new Date(mStart);
            Date dend = new Date(mEnd);
            toRet.append(mHasStart).append(",").append(mHasEnd).append(",");
            toRet.append(dstart).append(",").append(dend).append(",").append(mIsException);
            toRet.append(",allDay=").append(mAllDay);
            toRet.append(",startTzo=").append(mStartTzOffset).append(",endTzo=").append(mEndTzOffset);
            if (mInvId != null)
                toRet.append(",ID=").append(mInvId.getMsgId()).append("-").append(mInvId.getComponentId());
            toRet.append(")");
            return toRet.toString();
        }

        public int getCalendarItemId() { return mCalItemId; }
        public CalendarItem getCalendarItem() { return null; }
        public int getMailItemId() { return mInvId != null ? mInvId.getMsgId() : -1; }
        public int getComponentNum() { return mInvId != null ? mInvId.getComponentId() : -1; }
        public long getStart() { return mStart; }
        public long getEnd() { return mEnd; }
        public boolean isAllDay() { return mAllDay; }
        public int getStartTzOffset() { return mStartTzOffset; }
        public int getEndTzOffset() { return mEndTzOffset; }
        public boolean hasStart() { return mHasStart; }
        public boolean hasEnd() { return mHasEnd; }
        public boolean isException() { return mIsException; }
        public void setIsException(boolean isException) { mIsException = isException; }
        public boolean fromRdate() { return mFromRdate; }
        public InviteInfo getInviteInfo() { return mInvId; }

        public static class StartTimeComparator implements Comparator<Instance> {
            @Override
            public int compare(Instance a, Instance b) {
                return compareTimes(a.mHasStart, a.mStart, a.mAllDay, a.mStartTzOffset,
                                    b.mHasStart, b.mStart, b.mAllDay, b.mStartTzOffset);
            }
        }

        // compares two times A and B
        private static int compareTimes(boolean hasTimeA, long timeA, boolean allDayA, int tzoA,
                                        boolean hasTimeB, long timeB, boolean allDayB, int tzoB) {
            if (!hasTimeA) {
                if (hasTimeB) {
                    return -1;  // no time < has time
                } else {
                    return 0;  // neither has time
                }
            } else if (!hasTimeB) {
                return 1;  // has time > no time
            }
            // both have time

            if (timeA < timeB) {
                return -1;
            } else if (timeA > timeB) {
                return 1;
            } else {
                if (allDayA == allDayB) {
                    if (tzoA > tzoB)
                        return -1;
                    else if (tzoA < tzoB)
                        return 1;
                    else
                        return 0;
                } else {
                    if (allDayA)
                        return -1;
                    else
                        return 1;
                }
            }
        }

        /**
         * Returns "YYYYMMDD[ThhmmssZ]" string for this instance.
         * @return
         */
        public String getRecurIdZ() {
            if (!mHasStart)
                return null;
            ParsedDateTime dt;
            if (mAllDay) {
                dt = ParsedDateTime.fromUTCTime(mStart + mStartTzOffset);
                dt.setHasTime(false);
            } else {
                dt = ParsedDateTime.fromUTCTime(mStart);
            }
            return dt.getDateTimePartString(false);
        }

        public RecurId makeRecurId(Invite refInv) {
            // Get it from InviteInfo if we can.
            RecurId rid = mInvId != null ? mInvId.getRecurrenceId() : null;
            if (rid != null)
                return rid;

            // InviteInfo didn't have anything, meaning this instance wasn't an exception instance.
            // Generate a recurrence id using the data from the reference invite.
            if (refInv == null) {
                if (mHasStart) {
                    return new RecurId(ParsedDateTime.fromUTCTime(mStart), RecurId.RANGE_NONE);
                } else {
                    // We can't handle all-day appointments if reference invite wasn't given.
                    return null;
                }
            }
            ParsedDateTime dtStart = refInv.getStartTime();
            if (dtStart == null)
                return null;
            ICalTimeZone tz = dtStart.getTimeZone();
            long startTime = mStart;
            ParsedDateTime dt;
            if (tz != null)
                dt = ParsedDateTime.fromUTCTime(startTime, tz);
            else
                dt = ParsedDateTime.fromUTCTime(startTime);
            if (refInv.isAllDayEvent())
                dt.setHasTime(false);
            return new RecurId(dt, RecurId.RANGE_NONE);
        }
    }

    public String getUid() {
        return mUid;
    }

    public Invite getInvite(int invId, int compNum) {
        for (Invite inv : mInvites) {
            if (inv.getMailItemId() == invId && inv.getComponentNum() == compNum) {
                return inv;
            }
        }
        return null;
    }

    public Invite[] getInvites() {
        int num = mInvites.size();
        if (num == 1) {
            Invite[] ret = new Invite[1];
            ret[0] = mInvites.get(0);
            return ret;
        } else {
            ArrayList<Invite> toRet = new ArrayList<Invite>(mInvites.size());
            // First get the series invite(s), then exceptions.  This will generate a friendlier ics file.
            for (Invite inv : mInvites)
                if (!inv.hasRecurId())
                    toRet.add(inv);
            for (Invite inv : mInvites)
                if (inv.hasRecurId())
                    toRet.add(inv);
            return toRet.toArray(new Invite[0]);
        }
    }

    public Invite[] getInvites(int invId) {
        ArrayList<Invite> toRet = new ArrayList<Invite>();
        for (Invite inv : mInvites)
            if (inv.getMailItemId() == invId)
                toRet.add(inv);
        return toRet.toArray(new Invite[0]);
    }

    public int numInvites() {
        return mInvites.size();
    }

    public Invite getInvite(int index) {
        return mInvites.get(index);
    }

    /**
     * Returns the Invite with matching RecurId, or null.
     * @param rid
     * @return
     */
    public Invite getInvite(RecurId rid) {
        if (rid == null) {
            for (Invite inv : mInvites) {
                if (inv.getRecurId() == null)
                    return inv;
            }
        } else {
            for (Invite inv: mInvites) {
                if (rid.equals(inv.getRecurId()))
                    return inv;
            }
        }
        return null;
    }

    /**
     * Returns the Invite with matching RECURRENCE-ID date/time, expressed as
     * as millis since epoch.  If no matching one is found, the default/series
     * Invite is returned.
     * @param recurIdDtstamp
     * @return
     */
    public Invite getInviteForRecurId(long recurIdDtstamp) {
        Invite defInv = null;
        for (Invite inv : mInvites) {
            RecurId rid = inv.getRecurId();
            if (rid == null) {
                if (defInv == null)
                    defInv = inv;
            } else {
                ParsedDateTime dt = rid.getDt();
                if (dt != null) {
                    if (dt.getUtcTime() == recurIdDtstamp)
                        return inv;
                }
            }
        }
        return defInv;
    }

    /**
     * Returns the Invite with matching RECURRENCE-ID date/time, expressed as
     * "YYYYMMDD[ThhmmssZ]" string.  If time comonent is specified, it must be
     * in UTC timezone ("Z").
     * If no matching one is found, the default/series Invite is returned.
     * @param recurIdZ
     * @return
     */
    public Invite getInviteForRecurIdZ(String recurIdZ) {
        Invite defInv = null;
        for (Invite inv : mInvites) {
            RecurId rid = inv.getRecurId();
            if (recurIdZ != null) {
                if (rid == null) {
                    if (defInv == null)
                        defInv = inv;
                } else {
                    if (recurIdZ.equals(rid.getDtZ()))
                        return inv;
                }
            } else {  // recurIdZ == null
                if (rid == null)
                    return inv;
            }
        }
        return defInv;
    }

    public Invite getDefaultInviteOrNull() {
        Invite first = null;
        for (Invite cur : mInvites) {
            if (!cur.hasRecurId())
                return cur;
            if (first == null)
                first = cur;
        }
        if (first == null)
            ZimbraLog.calendar.error(
                    "Invalid state: appointment/task " + getId() + " in mailbox " + getMailbox().getId() + " has no default invite; " +
                    (mInvites != null ? ("invite count = " + mInvites.size()) : "null invite list"));
        return first;
    }

    /**
     * Returns true if all invites in the calendar item have CLASS:PUBLIC.
     * @return
     */
    public boolean isPublic() {
        boolean result = true;
        Invite[] invs = getInvites();
        if (invs != null && invs.length > 0) {
            for (Invite i : invs) {
                if (!i.isPublic()) {
                    result = false;
                    break;
                }
            }
        }
        return result;
    }

    /**
     * Returns true if authAccount should be allowed access to private data in this appointment.
     * Returns true if authAccount is the account that owns the appointment, or authAccount has
     * admin rights over the owner account.
     * @param authAccount
     * @param asAdmin true if authAccount is authenticated with admin privileges
     * @return
     * @throws ServiceException
     */
    public boolean allowPrivateAccess(Account authAccount, boolean asAdmin) throws ServiceException {
        return canAccess(ACL.RIGHT_PRIVATE, authAccount, asAdmin);
    }

    /**
     * Returns true if authAccount is allowed access to private data in calendar items in folder.
     * @param folder
     * @param authAccount
     * @param asAdmin
     * @return
     * @throws ServiceException
     */
    public static boolean allowPrivateAccess(Folder folder, Account authAccount, boolean asAdmin)
    throws ServiceException {
        return folder.canAccess(ACL.RIGHT_PRIVATE, authAccount, asAdmin);
    }

    /**
     * Returns true if authAccount is allowed to run free/busy search against calendar folder.
     * @param folder
     * @param authAccount
     * @param asAdmin
     * @return
     * @throws ServiceException
     */
    public static boolean allowFreeBusyAccess(Folder folder, Account authAccount, boolean asAdmin)
    throws ServiceException {
        return folder.canAccess(ACL.RIGHT_FREEBUSY, authAccount, asAdmin);
    }

    boolean processNewInvite(ParsedMessage pm, Invite invite,
                             int folderId, boolean replaceExistingInvites)
    throws ServiceException {
        return processNewInvite(pm, invite, folderId, CalendarItem.NEXT_ALARM_KEEP_CURRENT, true, replaceExistingInvites);
    }

    /**
     * A new Invite has come in, take a look at it and see what needs to happen.
     * Maybe we need to send updates out. Maybe we need to modify the
     * CalendarItem table.
     *
     * @param invite
     * @return
     *            TRUE if an update calendar was written, FALSE if the CalendarItem is
     *            unchanged or deleted
     */
    boolean processNewInvite(ParsedMessage pm, Invite invite,
                             int folderId, long nextAlarm,
                             boolean preserveAlarms, boolean replaceExistingInvites)
    throws ServiceException {
        invite.setHasAttachment(pm != null ? pm.hasAttachments() : false);

        String method = invite.getMethod();
        if (method.equals(ICalTok.REQUEST.toString()) ||
            method.equals(ICalTok.CANCEL.toString()) ||
            method.equals(ICalTok.PUBLISH.toString())) {
            return processNewInviteRequestOrCancel(pm, invite, folderId, nextAlarm,
                                                   preserveAlarms, replaceExistingInvites);
        } else if (method.equals(ICalTok.REPLY.toString())) {
            return processNewInviteReply(invite);
        }

        if (!method.equals(ICalTok.COUNTER.toString()) && !method.equals(ICalTok.DECLINECOUNTER.toString()))
            ZimbraLog.calendar.warn("Unsupported METHOD " + method);
        return false;
    }

    /**
     * Returns true if RECURRENCE-ID of two invites are equal or null.
     * @param inv1
     * @param inv2
     * @return
     */
    private static boolean recurrenceIdsMatch(Invite inv1, Invite inv2) {
        RecurId r1 = inv1.getRecurId();
        RecurId r2 = inv2.getRecurId();
        if (r1 != null)
            return r1.equals(r2);
        else
            return r2 == null;
    }

    private boolean processNewInviteRequestOrCancel(ParsedMessage pm,
                                                    Invite newInvite,
                                                    int folderId,
                                                    long nextAlarm,
                                                    boolean preserveAlarms,
                                                    boolean discardExistingInvites)
    throws ServiceException {
        // trace logging
        if (!newInvite.hasRecurId())
            ZimbraLog.calendar.info(
                    "Modifying CalendarItem: id=%d, folderId=%d, method=%s, subject=\"%s\", UID=%s",
                    mId, getFolderId(), newInvite.getMethod(),
                    newInvite.isPublic() ? newInvite.getName() : "(private)", mUid);
        else
            ZimbraLog.calendar.info(
                    "Modifying CalendarItem: id=%d, folderId=%d, method=%s, subject=\"%s\", UID=%s, recurId=%s",
                    mId, getFolderId(), newInvite.getMethod(),
                    newInvite.isPublic() ? newInvite.getName() : "(private)", mUid, newInvite.getRecurId().getDtZ());

        newInvite.sanitize(false);

        OperationContext octxt = getMailbox().getOperationContext();
        Account authAccount = octxt != null ? octxt.getAuthenticatedUser() : null;
        boolean asAdmin = octxt != null ? octxt.isUsingAdminPrivileges() : false;
        boolean isCancel = newInvite.isCancel();

        boolean requirePrivateCheck = requirePrivateCheck(newInvite);
        short rightsNeeded = isCancel ? (short) (ACL.RIGHT_DELETE | ACL.RIGHT_WRITE) : ACL.RIGHT_WRITE;
        if (!canAccess(rightsNeeded, authAccount, asAdmin, requirePrivateCheck))
            throw ServiceException.PERM_DENIED("you do not have sufficient permissions on this calendar item");

        // Don't allow moving a private appointment on behalf of another user,
        // unless that other user is a calendar resource.
        boolean isCalendarResource = getMailbox().getAccount() instanceof CalendarResource;
        boolean denyPrivateAccess = requirePrivateCheck ? !allowPrivateAccess(authAccount, asAdmin) : false;
        if (!newInvite.isPublic() || !isPublic()) {
            if (folderId != getFolderId()) {
                Folder folder = getMailbox().getFolderById(folderId);
                if (!allowPrivateAccess(folder, authAccount, asAdmin)) {
                    denyPrivateAccess = true;
                    if (!isCalendarResource)
                        throw ServiceException.PERM_DENIED("you do not have permission to update/cancel private calendar item in target folder");
                }
            }
        }

        // Do not allow organizer to be changed. (bug 74400)
        boolean organizerChanged = organizerChangeCheck(newInvite, true);
        ZOrganizer newOrganizer = newInvite.getOrganizer();

        // If we got a cancel request, check if this cancel will result in canceling the entire appointment.
        // If so, move the appointment to trash folder.
        // Also at the same time, check if the cancel request is oudated, i.e. there is already a newer version
        // of the invite.
        if (isCancel) {
            boolean cancelAll;
            boolean outdated;
            if (!newInvite.hasRecurId()) {
                cancelAll = true;
                // Canceling series.  Check the sequencing requirement to make sure the invite isn't outdated.
                Invite series = getInvite((RecurId) null);
                // If series invite is not found, assume cancel is not outdated.
                outdated = series != null && !newInvite.isSameOrNewerVersion(series);
            } else {
                // Canceling an instance.  It's a total cancel only if mInvites has one invite and it matches
                // the recurrence id.  (subject to sequencing requirements)
                cancelAll = false;
                outdated = false;
                Invite curr = getInvite(newInvite.getRecurId());
                if (curr != null) {
                    if (newInvite.isSameOrNewerVersion(curr)) {
                        cancelAll = true;
                        // See if there any non-cancel invites besides the one being canceled.
                        for (Invite inv : mInvites) {
                            if (!inv.equals(curr) && !inv.isCancel()) {
                                cancelAll = false;
                                break;
                            }
                        }
                    } else {
                        // There is already a newer invite.  Ignore the cancel.
                        outdated = true;
                    }
                }
            }
            if (outdated) {
                ZimbraLog.calendar.info("Ignoring outdated cancel request");
                return false;
            }
            if (cancelAll) {
                Folder trash = mMailbox.getFolderById(Mailbox.ID_FOLDER_TRASH);
                move(trash);
                // If we have revisions enabled we need to force metadata write to db because version field changed.
                if (getMaxRevisions() != 1)
                    saveMetadata();
                return true;
            }
        }

        // Clear all replies if replacing appointment in trash folder with a new invite.  All existing invites are
        // being discarded, and so all existing replies must be discarded as well.
        Folder folder = getMailbox().getFolderById(folderId);
        if (!isCancel && discardExistingInvites && inTrash() && !folder.inTrash()) {
            mReplyList.mReplies.clear();
        }

        // Handle change to the series that involves time and/or recurrence.  In Exchange compatibility mode,
        // time/recurrence change blows away all exception instances.  In non-compat mode (old ZCS behavior),
        // look for change in the start time and shift the time part of exceptions' RECURRENCE-ID by the same delta.
        boolean needRecurrenceIdUpdate = false;
        ParsedDateTime oldDtStart = null;
        ParsedDuration dtStartMovedBy = null;
        ArrayList<Invite> toUpdate = new ArrayList<Invite>();
        if (!discardExistingInvites && !isCancel && newInvite.isRecurrence()) {
            Invite defInv = getDefaultInviteOrNull();
            if (defInv != null && defInv.isRecurrence()) {
                if (!getAccount().isCalendarKeepExceptionsOnSeriesTimeChange()) {  // Exchange compatibility mode
                    InviteChanges ic = new InviteChanges(defInv, newInvite);
                    if (ic.isExceptionRemovingChange()) {
                        discardExistingInvites = true;
                    }
                } else {  // old ZCS behavior
                    // Be careful.  If invites got delivered out of order, we may have defInv that's not
                    // a series.  Imagine 1st invite received was an exception and 2nd was the series.
                    // In that situation we simply skip the DTSTART shift calculation.
                    oldDtStart = defInv.getStartTime();
                    ParsedDateTime newDtStart = newInvite.getStartTime();
                    //if (newDtStart != null && oldDtStart != null && !newDtStart.sameTime(oldDtStart)) {
                    if (newDtStart != null && oldDtStart != null && !newDtStart.equals(oldDtStart)) {
                        // Find the series frequency.
                        Frequency freq = null;
                        IRecurrence recurrence = newInvite.getRecurrence();
                        if (recurrence != null) {
                            Iterator rulesIter = recurrence.addRulesIterator();
                            if (rulesIter != null) {
                                for (; rulesIter.hasNext(); ) {
                                    Object ruleObj = rulesIter.next();
                                    if (ruleObj instanceof SimpleRepeatingRule) {
                                        SimpleRepeatingRule series = (SimpleRepeatingRule) ruleObj;
                                        ZRecur recur = series.getRule();
                                        freq = recur.getFrequency();
                                        break;
                                     }
                                }
                            }
                        }
                        // Maximum allowed delta depends on the frequency.
                        ParsedDuration deltaLimit = null;
                        if (freq != null) {
                            switch (freq) {
                            case DAILY:
                                deltaLimit = ParsedDuration.ONE_DAY;
                                break;
                            case WEEKLY:
                            case MONTHLY:
                            case YEARLY:
                                // Do the RECURRENCE-ID adjustment only when DTSTART moved by 7 days or less.
                                // If it moved by more, it gets too complicated to figure out what the old RECURRENCE-ID
                                // should be in the new series.  Just blow away all exceptions.
                                deltaLimit = ParsedDuration.ONE_WEEK;
                                break;
                            default:
                                // Secondly/minutely/hourly rules are too frequent to allow recurrence id shifting.
                                break;
                            }
                        }
                        if (deltaLimit != null) {
                            ParsedDuration delta = newDtStart.difference(oldDtStart);
                            if (delta.abs().compareTo(deltaLimit) < 0) {
                                needRecurrenceIdUpdate = true;
                                dtStartMovedBy = delta;
                            }
                        }
                    }
                }
            }
        }

        // Inherit alarms from the invite with matching RECURRENCE-ID.  If no matching invite is
        // found, inherit from the series invite.
        if (!discardExistingInvites && preserveAlarms) {
            Invite localSeries = null;
            Invite alarmSourceInv = null;
            for (Invite inv : mInvites) {
                if (recurrenceIdsMatch(inv, newInvite)) {
                    alarmSourceInv = inv;
                    break;
                }
                if (!inv.hasRecurId())
                    localSeries = inv;
            }
            if (alarmSourceInv == null)
                alarmSourceInv = localSeries;
            if (alarmSourceInv != null) {
                newInvite.clearAlarms();
                for (Iterator<Alarm> alarmIter = alarmSourceInv.alarmsIterator(); alarmIter.hasNext(); ) {
                    newInvite.addAlarm(alarmIter.next());
                }
            }
        }

        // Is this a series update invite from ZCO?  If so, we have to treat all exceptions as local-only
        // and make them snap to series.
        boolean zcoSeriesUpdate = false;
        ZProperty xzDiscardExcepts = newInvite.getXProperty(ICalTok.X_ZIMBRA_DISCARD_EXCEPTIONS.toString());
        if (xzDiscardExcepts != null)
            zcoSeriesUpdate = xzDiscardExcepts.getBoolValue();

        // Is this an update to the series with UNTIL in the rule?  If so, we need to remove exceptions
        // whose RECURRENCE-ID come later than UNTIL. (bug 11870)
        long seriesUntil = Long.MAX_VALUE;
        if (!isCancel && !newInvite.hasRecurId()) {
            ParsedDateTime dtStart = newInvite.getStartTime();
            IRecurrence recur = newInvite.getRecurrence();
            if (recur != null && dtStart != null) {
                ICalTimeZone tz = dtStart.getTimeZone();
                // Find the repeating rule.
                Iterator<?> iter = recur.addRulesIterator();
                if (iter != null) {
                    for (; iter.hasNext();) {
                        IRecurrence cur = (IRecurrence) iter.next();
                        if (cur.getType() == Recurrence.TYPE_REPEATING) {
                            ZRecur rrule = ((Recurrence.SimpleRepeatingRule) cur).getRule();
                            ParsedDateTime until = rrule.getUntil();
                            if (until != null)
                                seriesUntil = Math.min(until.getDateForRecurUntil(tz).getTime(), seriesUntil);
                        }
                    }
                }
            }
        }

        // Check if exception instances are made obsolete by updated recurrence rule.  (bug 47061)
        Set<String> obsoletedRecurIdZs = new HashSet<String>();
        if (!isCancel && newInvite.isRecurrence()) {
            Invite seriesInv = null;
            // Find the range of existing exception instances.
            long rangeStart = Long.MAX_VALUE;
            long rangeEnd = Long.MIN_VALUE;
            for (Invite inv : mInvites) {
                if (inv.hasRecurId()) {
                    RecurId rid = inv.getRecurId();
                    ParsedDateTime ridDt = rid.getDt();
                    if (ridDt != null) {
                        // Turn Outlook-style all-day RecurId to standard-style.
                        if (inv.isAllDayEvent() && ridDt.hasTime() && ridDt.hasZeroTime()) {
                            ParsedDateTime ridDtFixed = (ParsedDateTime) ridDt.clone();
                            ridDtFixed.setHasTime(false);
                            rid = new RecurId(ridDtFixed, rid.getRange());
                            ridDt = rid.getDt();
                        }
                        // Adjust start time if necessary.
                        RecurId adjustedRid;
                        long adjustedT;
                        if (dtStartMovedBy != null) {
                            ParsedDateTime dt = ridDt.add(dtStartMovedBy);
                            adjustedRid = new RecurId(dt, rid.getRange());
                            adjustedT = dt.getUtcTime();
                        } else {
                            adjustedRid = rid;
                            adjustedT = ridDt.getUtcTime();
                        }
                        rangeStart = Math.min(rangeStart, adjustedT);
                        rangeEnd = Math.max(rangeEnd, adjustedT);
                        obsoletedRecurIdZs.add(adjustedRid.getDtZ());  // initially all instances considered obsolete
                    }
                } else {
                    seriesInv = inv;
                }
            }
            // Extend the range by a day on both ends to compensate for all-day appointments.
            long millisIn25Hours = 25 * 60 * 60 * 1000;  // 25 hours to accommodate DST onset dates
            if (rangeStart != Long.MAX_VALUE)
                rangeStart -= millisIn25Hours;
            if (rangeEnd != Long.MIN_VALUE)
                rangeEnd += millisIn25Hours;
            if (rangeStart != Long.MAX_VALUE && rangeEnd != Long.MIN_VALUE && rangeStart <= rangeEnd) {
                ++rangeEnd;  // so the final instance is included in the range
                IRecurrence recur = newInvite.getRecurrence();
                if (recur instanceof RecurrenceRule) {
                    RecurrenceRule rrule = (RecurrenceRule) recur;
                    List<Instance> instances = rrule.expandInstances(getId(), rangeStart, rangeEnd);
                    if (instances != null) {
                        for (Instance inst : instances) {
                            Invite refInv = seriesInv != null ? seriesInv : newInvite;
                            RecurId rid = inst.makeRecurId(refInv);
                            // Turn Outlook-style all-day RecurId to standard-style.
                            if (refInv.isAllDayEvent() && rid.getDt() != null) {
                                ParsedDateTime ridDtFixed = (ParsedDateTime) rid.getDt().clone();
                                ridDtFixed.setHasTime(false);
                                rid = new RecurId(ridDtFixed, rid.getRange());
                            }
                            obsoletedRecurIdZs.remove(rid.getDtZ());  // "Un-obsolete" the surviving recurrence ids.
                        }
                    }
                } else {
                    // This shouldn't happen.
                    ZimbraLog.calendar.warn("Expected RecurrenceRule object, but got " + recur.getClass().getName());
                }
            }
        }

        boolean addNewOne = true;
        boolean replaceExceptionBodyWithSeriesBody = false;
        boolean modifiedCalItem = false;
        Invite prev = null; // the invite which has been made obsolete by the new one coming in
        ArrayList<Invite> toRemove = new ArrayList<Invite>(); // Invites to remove from our blob store
        ArrayList<Integer> idxsToRemove = new ArrayList<Integer>(); // indexes to remove from mInvites
        int numInvitesCurrent = mInvites.size();  // get current size because we may add to the list in the loop
        for (int i = 0; i < numInvitesCurrent; i++) {
            Invite cur = mInvites.get(i);

            // If request is a cancellation of entire appointment, simply add each invite to removal list.
            if (isCancel && !newInvite.hasRecurId()) {
                addNewOne = false;
                modifiedCalItem = true;
                toRemove.add(cur);
                idxsToRemove.add(0, i);
                continue;
            }
            // Remove exceptions beyond the UNTIL date. (bug 11870)
            // Use DTSTART for comparison rather than RECURRENCE-ID.
            if (!isCancel && cur.hasRecurId()) {
                ParsedDateTime instDtStart = cur.getStartTime();
                if (instDtStart != null && instDtStart.getUtcTime() > seriesUntil) {
                    modifiedCalItem = true;
                    toRemove.add(cur);
                    idxsToRemove.add(0, i);
                    continue;
                }
            }
            // Remove exceptions obsoleted by changed RRULE.  (bug 47061)
            if (cur.hasRecurId() && !obsoletedRecurIdZs.isEmpty()) {
                RecurId rid = cur.getRecurId();
                if (rid != null && rid.getDt() != null) {
                    // Turn Outlook-style all-day RecurId to standard-style.
                    ParsedDateTime ridDt = rid.getDt();
                    if (cur.isAllDayEvent() && ridDt.hasTime() && ridDt.hasZeroTime()) {
                        ParsedDateTime ridDtFixed = (ParsedDateTime) ridDt.clone();
                        ridDtFixed.setHasTime(false);
                        rid = new RecurId(ridDtFixed, rid.getRange());
                    }
                    // Adjust start time if necessary.
                    RecurId adjustedRid;
                    if (dtStartMovedBy != null) {
                        ParsedDateTime dt = rid.getDt().add(dtStartMovedBy);
                        adjustedRid = new RecurId(dt, rid.getRange());
                    } else {
                        adjustedRid = rid;
                    }
                    if (obsoletedRecurIdZs.contains(adjustedRid.getDtZ())) {
                        modifiedCalItem = true;
                        toRemove.add(cur);
                        idxsToRemove.add(0, i);
                        continue;
                    }
                }
            }

            boolean matchingRecurId = recurrenceIdsMatch(cur, newInvite);
            if (discardExistingInvites || matchingRecurId) {
                if (discardExistingInvites || newInvite.isSameOrNewerVersion(cur)) {
                    // Invite is local-only only if both old and new are local-only.
                    newInvite.setLocalOnly(cur.isLocalOnly() && newInvite.isLocalOnly());

                    toRemove.add(cur);
                    // add to FRONT of list, so when we iterate for the removals we go from HIGHER TO LOWER
                    // that way the numbers all match up as the list contracts!
                    idxsToRemove.add(0, new Integer(i));

                    boolean invalidateReplies = false;
                    if (!discardExistingInvites) {
                        InviteChanges invChg = new InviteChanges(cur, newInvite);
                        invalidateReplies = invChg.isReplyInvalidatingChange();
                    }
                    if (discardExistingInvites || invalidateReplies) {
                        // clean up any old REPLYs that have been made obsolete by this new invite
                        mReplyList.removeObsoleteEntries(newInvite.getRecurId(), newInvite.getSeqNo(), newInvite.getDTStamp());
                    } else {
                        // If the change is minor, don't discard earlier replies.  Organizer may have incremented the
                        // sequence unnecessarily, and we have to cope with this by bumping up the sequence in the
                        // replies accordingly.
                        mReplyList.upgradeEntriesToNewSeq(newInvite.getRecurId(), newInvite.getSeqNo(), newInvite.getDTStamp());
                    }

                    prev = cur;
                    modifiedCalItem = true;
                    if (isCancel && !newInvite.hasRecurId()) {
                        // can't CANCEL just the recurId=null entry -- we must delete the whole appointment
                        addNewOne = false;
                    }
                } else {
                    // Appointment already has a newer version of the Invite.  The passed-in one is outdated,
                    // perhaps delivered out of order.  Ignore it.
                    return false;
                }
            } else if (!isCancel) {
                modifiedCalItem = true;

                boolean addToUpdateList = false;
                if (organizerChanged) {
                    // If organizer is changing on any invite, change it on all invites.
                    cur.setOrganizer(newOrganizer);
                    addToUpdateList = true;
                }

                if (needRecurrenceIdUpdate) {
                    // Adjust RECURRENCE-ID by the delta in series DTSTART, if recurrence id value has the
                    // same time of day as old DTSTART.
                    RecurId rid = cur.getRecurId();
                    if (rid != null && rid.getDt() != null && oldDtStart != null) {
                        ParsedDateTime ridDt = rid.getDt();
                        if (ridDt.sameTime(oldDtStart)) {
                            ParsedDateTime dt = rid.getDt().add(dtStartMovedBy);
                            RecurId newRid = new RecurId(dt, rid.getRange());
                            cur.setRecurId(newRid);
                            // For CANCELLED instances, set DTSTART to the same time
                            // used in RECURRENCE-ID and adjust DTEND accordingly.
                            if (cur.isCancel()) {
                                cur.setDtStart(dt);
                                ParsedDateTime dtEnd = cur.getEndTime();
                                if (dtEnd != null) {
                                    ParsedDateTime dtEndMoved = dtEnd.add(dtStartMovedBy);
                                    cur.setDtEnd(dtEndMoved);
                                }
                            }
                            addToUpdateList = true;
                        }
                    }
                }

                // If updating series, copy the series data to the exception, preserving only the alarm info.
                // If exception is local-only, snap it to the series start/end times.  Canceled local-only
                // exceptions are un-canceled.  Non-local-only exceptions (those that were published by
                // organizer) are left alone.
                if (!newInvite.hasRecurId() && cur.hasRecurId() && (zcoSeriesUpdate || cur.isLocalOnly())) {
                    if (cur.isCancel()) {
                        // Local-only cancellations are undone by update to the series.
                        toRemove.add(cur);
                        // add to FRONT of list, so when we iterate for the removals we go from HIGHER TO LOWER
                        // that way the numbers all match up as the list contracts!
                        idxsToRemove.add(0, new Integer(i));

                        // clean up any old REPLYs that have been made obsolete by this new invite
                        mReplyList.removeObsoleteEntries(newInvite.getRecurId(), newInvite.getSeqNo(),
                                                         newInvite.getDTStamp());

                        addToUpdateList = false;
                    } else {
                        replaceExceptionBodyWithSeriesBody = true;
                        // Recreate invite with data from newInvite, but preserve alarm info.
                        Invite copy = newInvite.newCopy();
                        copy.setLocalOnly(true);  // It's still local-only.
                        copy.setMailItemId(cur.getMailItemId());
                        copy.setComponentNum(cur.getComponentNum());
                        copy.setSeqNo(cur.getSeqNo());
                        copy.setDtStamp(cur.getDTStamp());
                        copy.setRecurId(cur.getRecurId());
                        copy.setRecurrence(null);  // because we're only dealing with exceptions
                        ParsedDateTime start = cur.getRecurId().getDt();
                        if (start != null) {
                            copy.setDtStart(start);  // snap back to series start time
                            ParsedDuration dur = cur.getDuration();
                            if (dur != null) {
                                copy.setDtEnd(null);
                                copy.setDuration(dur);
                            } else {
                                copy.setDuration(null);
                                dur = cur.getEffectiveDuration();
                                ParsedDateTime end = null;
                                if (dur != null)
                                    end = start.add(dur);
                                copy.setDtEnd(end);
                            }
                        } else {
                            copy.setDtStart(null);
                            copy.setDtEnd(cur.getEndTime());
                            copy.setDuration(null);
                        }
                        copy.clearAlarms();
                        for (Iterator<Alarm> iter = cur.alarmsIterator(); iter.hasNext(); ) {
                            copy.addAlarm(iter.next());
                        }

                        // Series was updated, so change this exception's partstat to NEEDS-ACTION.
                        ZAttendee me = copy.getMatchingAttendee(getAccount());
                        if (me != null)
                            me.setPartStat(IcalXmlStrMap.PARTSTAT_NEEDS_ACTION);

                        mInvites.set(i, copy);
                        addToUpdateList = true;
                    }
                }

                if (addToUpdateList)
                    toUpdate.add(cur);
            }
        }

        boolean callProcessPartStat = false;
        if (addNewOne) {
            newInvite.setCalendarItem(this);

            // Don't allow creating/editing a private appointment on behalf of another user,
            // unless that other user is a calendar resource.
            if (denyPrivateAccess && prev != null && !prev.isPublic() && !isCalendarResource)
                throw ServiceException.PERM_DENIED("you do not have sufficient permissions on this calendar item");

            if (prev!=null && !newInvite.isOrganizer() && newInvite.sentByMe()) {
                // A non-organizer attendee is modifying data on his/her
                // appointment/task.  Any information that is tracked in
                // metadata rather than in the iCal MIME part must be
                // carried over from the last invite to the new one.
                newInvite.setPartStat(prev.getPartStat());
                newInvite.setRsvp(prev.getRsvp());
                newInvite.getCalendarItem().saveMetadata();
                // No need to mark invite as modified item in mailbox as
                // it has already been marked as a created item.
            } else {
                callProcessPartStat = true;
            }

            newInvite.setClassPropSetByMe(newInvite.sentByMe());

            // If updating attendee's appointment/task with a new version published by organizer
            // and attendee's copy was previously set to private/confidential by himself/herself,
            // retain the value and therefore don't allow the organizer to override it.
            if (prev != null && !newInvite.isOrganizer() && !newInvite.sentByMe()) {
                if (!prev.isPublic() && prev.classPropSetByMe()) {
                    newInvite.setClassProp(prev.getClassProp());
                    newInvite.setClassPropSetByMe(true);
                }
            }

            mInvites.add(newInvite);

            // the appointment/task stores an uber-tzmap, for its uber-recurrence
            // this might give us problems if we had two invites with conflicting TZ
            // defs....should be very unlikely
            mTzMap.add(newInvite.getTimeZoneMap());

            // TIM: don't write the blob until the end of the function (so we only do one write for the update)
//            modifyBlob(toRemove, replaceExistingInvites, toUpdate, pm, newInvite, locator, isCancel, !denyPrivateAccess);
            modifiedCalItem = true;
        } else {
            // TIM: don't write the blob until the end of the function (so we only do one write for the update)
//            modifyBlob(toRemove, replaceExistingInvites, toUpdate, null, null, locator, isCancel, !denyPrivateAccess);
        }

        // now remove the inviteid's from our list
        for (Iterator<Integer> iter = idxsToRemove.iterator(); iter.hasNext();) {
            assert(modifiedCalItem);
            Integer i = iter.next();
            mInvites.remove(i.intValue());
        }

        if (getFolderId() != folderId) {
            // Move appointment/task to a different folder.
            move(folder);
        }

        // Check if there are any surviving non-cancel invites after applying the update.
        // Also check for changes in flags.
        int oldFlags = mData.flags;
        int newFlags = mData.flags & ~(Flag.BITMASK_ATTACHED | Flag.BITMASK_DRAFT | Flag.BITMASK_HIGH_PRIORITY | Flag.BITMASK_LOW_PRIORITY);
        boolean hasSurvivingRequests = false;
        for (Invite cur : mInvites) {
            String method = cur.getMethod();
            if (method.equals(ICalTok.REQUEST.toString()) ||
                method.equals(ICalTok.PUBLISH.toString())) {
                hasSurvivingRequests = true;
                if (cur.hasAttachment())
                    newFlags |= Flag.BITMASK_ATTACHED;
                if (cur.isDraft())
                    newFlags |= Flag.BITMASK_DRAFT;
                if (cur.isHighPriority())
                    newFlags |= Flag.BITMASK_HIGH_PRIORITY;
                if (cur.isLowPriority())
                    newFlags |= Flag.BITMASK_LOW_PRIORITY;
            }
        }
        if (newFlags != oldFlags) {
            mData.flags = newFlags;
            modifiedCalItem = true;
        }

        if (!hasSurvivingRequests) {
            if (!isCancel)
                ZimbraLog.calendar.warn(
                        "Invalid state: deleting calendar item " + getId() +
                        " in mailbox " + getMailboxId() + " while processing a non-cancel request");
            else
                ZimbraLog.calendar.warn(
                        "Invalid state: deleting calendar item " + getId() +
                        " in mailbox " + getMailboxId() + " because it has no invite after applying cancel invite");
            delete();  // delete this appointment/task from the table,
                       // it doesn't have anymore REQUESTs!
            return false;
        } else {
            if (nextAlarm > 0 && mAlarmData != null && mAlarmData.getNextAt() != nextAlarm)
                modifiedCalItem = true;

            if (modifiedCalItem) {
                if (!updateRecurrence(nextAlarm)) {
                    // no default invite!  This appointment/task no longer valid
                    ZimbraLog.calendar.warn(
                            "Invalid state: deleting calendar item " + getId() +
                            " in mailbox " + getMailboxId() + " because it has no invite");
                    delete();
                    return false;
                } else {
                    if (callProcessPartStat) {
                        // processPartStat() must be called after
                        // updateRecurrence() has been called.  (bug 8072)
                        processPartStat(newInvite,
                                        pm != null ? pm.getMimeMessage() : null,
                                        false,
                                        newInvite.getPartStat());
                    }

                    // Did the appointment have a blob before the change?
                    boolean hadBlobPart = false;
                    Invite[] oldInvs = getInvites();
                    if (oldInvs != null) {
                        for (Invite oldInv : oldInvs) {
                            if (oldInv.hasBlobPart()) {
                                hadBlobPart = true;
                                break;
                            }
                        }
                    }
                    // Update blob if adding a new ParsedMessage or if there is already a blob, in which
                    // case we may have to delete a section from it.
                    boolean newInvHasBlobPart = newInvite.hasBlobPart();
                    if (hadBlobPart || newInvHasBlobPart) {
                        if (addNewOne) {
                            modifyBlob(toRemove, discardExistingInvites, toUpdate, pm, newInvite,
                                       isCancel, !denyPrivateAccess, true, replaceExceptionBodyWithSeriesBody);
                        } else {
                            if (!newInvHasBlobPart)
                                toRemove.add(newInvite);  // force existing MIME part to be removed
                            modifyBlob(toRemove, discardExistingInvites, toUpdate, null, null,
                                       isCancel, !denyPrivateAccess, true, replaceExceptionBodyWithSeriesBody);
                        }
                        // TIM: modifyBlob will save the metadata for us as a side-effect
//                      saveMetadata();
                    } else {
                        markItemModified(Change.MODIFIED_INVITE);
                        try {
                            // call setContent here so that MOD_CONTENT is updated...this is required
                            // for the index entry to be correctly updated (bug 39463)
                            setContent(null, null);
                        } catch (IOException e) {
                            throw ServiceException.FAILURE("IOException", e);
                        }
                    }

                    Callback cb = getCallback();
                    if (cb != null)
                        cb.modified(this);

                    return true;
                }
            } else {
                return false;
            }
        }
    }

    @Override
    void delete() throws ServiceException {
        super.delete();
        if (!inDumpster()) {
            Callback cb = getCallback();
            if (cb != null)
                cb.deleted(this);
        }
    }

    // YCC special
    public interface Callback {
        public void created(CalendarItem calItem) throws ServiceException;
        public void modified(CalendarItem calItem) throws ServiceException;
        public void deleted(CalendarItem calItem) throws ServiceException;
    }
    private static Callback sCallback = null;
    public static synchronized void registerCallback(Callback cb) {
        sCallback = cb;
    }
    public static synchronized Callback getCallback() {
        return sCallback;
    }

    /**
     * Check to make sure the new invite doesn't change the organizer in a disallowed way.
     * @param newInvite
     * @return true if organizer change was detected, false if no change
     * @throws ServiceException
     */
    private boolean organizerChangeCheck(Invite newInvite, boolean denyChange)
    throws ServiceException {
        Invite originalInvite = null;
        if (!newInvite.hasRecurId()) {
            // New invite is not for an exception.
            originalInvite = getDefaultInviteOrNull();
        } else {
            // New invite is for an exception.
            boolean found = false;
            RecurId newRid = newInvite.getRecurId();
            for (Invite inv : mInvites) {
                if (inv.hasRecurId() && newRid.equals(inv.getRecurId())) {
                    originalInvite = inv;
                    found = true;
                    break;
                }
            }
            if (!found) {
                // If no invite with matching RECURRENCE-ID was found, use the default invite.
                originalInvite = getDefaultInviteOrNull();
            }
        }
        if (originalInvite == null) {
            // If no "default" invite was found, use the first one.
            if (mInvites.size() > 0)
                originalInvite = mInvites.get(0);
            if (originalInvite == null) {
                // Still no invite.  Something is seriously wrong.  Return without any further
                // checks in this method.
                return false;
            }
        }

        boolean changed = false;
        ZOrganizer originalOrganizer = originalInvite.getOrganizer();
        if (!originalInvite.isOrganizer()) {
            // This account WAS NOT the organizer.  Prevent organizer change.
            if (newInvite.hasOrganizer()) {
                String newOrgAddr = newInvite.getOrganizer().getAddress();
                if (originalOrganizer == null) {
                    if (denyChange) {
                        throw ServiceException.INVALID_REQUEST(
                                "Changing organizer of an appointment/task to another user is not allowed: old=(unspecified), new=" + newOrgAddr, null);
                    } else {
                        changed = true;
                    }
                } else {
                    // Both old and new organizers are set.  They must be the
                    // same address.
                    String origOrgAddr = originalOrganizer.getAddress();
                    if (newOrgAddr == null || !newOrgAddr.equalsIgnoreCase(origOrgAddr)) {
                        if (denyChange) {
                            throw ServiceException.INVALID_REQUEST(
                                    "Changing organizer of an appointment/task is not allowed: old=" + origOrgAddr + ", new=" + newOrgAddr, null);
                        } else {
                            changed = true;
                        }
                    }
                }
            } else if (originalOrganizer != null) {
                if (denyChange) {
                    throw ServiceException.INVALID_REQUEST(
                            "Removing organizer of an appointment/task is not allowed", null);
                } else {
                    changed = true;
                }
            }
        } else {
            // Even for the organizer account, don't allow changing the organizer field
            // to an arbitrary address.
            if (newInvite.hasOrganizer()) {
                if (!newInvite.isOrganizer()) {
                    if (denyChange) {
                        String newOrgAddr = newInvite.getOrganizer().getAddress();
                        if (originalOrganizer != null) {
                            String origOrgAddr = originalOrganizer.getAddress();
                            throw ServiceException.INVALID_REQUEST(
                                    "Changing organizer of an appointment/task to another user is not allowed: old=" +
                                    origOrgAddr + ", new=" + newOrgAddr, null);
                        } else {
                            throw ServiceException.INVALID_REQUEST(
                                    "Changing organizer of an appointment/task to another user is not allowed: old=(unspecified), new=" +
                                    newOrgAddr, null);
                        }
                    } else {
                        changed = true;
                    }
                }
            }
        }
        if (changed) {
            String origOrg = originalOrganizer != null ? originalOrganizer.getAddress() : null;
            ZOrganizer newOrganizer = newInvite.getOrganizer();
            String newOrg = newOrganizer != null ? newOrganizer.getAddress() : null;
            boolean wasOrganizer = originalInvite.isOrganizer();
            boolean isOrganizer = newInvite.isOrganizer();
            ZimbraLog.calendar.info("Changed organizer: old=" + origOrg + ", new=" + newOrg +
                                    ", wasOrg=" + wasOrganizer + ", isOrg=" + isOrganizer +
                                    ", UID=\"" + newInvite.getUid() + "\", invId=" + newInvite.getMailItemId());
        }
        return changed;
    }

    /**
     * ParsedMessage DataSource -- for writing a ParsedMessage (new invite)
     * into our combined multipart/alternative Appointment store
     */
    private static class PMDataSource implements DataSource {
        private final ParsedMessage mPm;

        public PMDataSource(ParsedMessage pm) {
            mPm = pm;
        }

        @Override
        public String getName() {
            // TODO should we just return null?
            return mPm != null ? mPm.getMessageID() : null;
        }

        @Override
        public String getContentType() {
            return "message/rfc822";
        }

        /**
         * Returns the InputStream for this blob. Note that this method
         * needs a database connection and will obtain/release one
         * automatically if needed, or use the one passed to it from
         * the constructor.
         * @throws IOException
         */
        @Override
        public InputStream getInputStream() throws IOException {
            InputStream is = null;
            if (mPm != null)
                is = mPm.getRawInputStream();
            if (is == null)
                is = new ByteArrayInputStream(new byte[0]);
            return is;
        }

        @Override
        public OutputStream getOutputStream() {
            throw new UnsupportedOperationException();
        }
    }

    private MailboxBlob storeUpdatedBlob(MimeMessage mm) throws ServiceException, IOException {
        ParsedMessage pm = new ParsedMessage(mm, mMailbox.attachmentsIndexingEnabled());
        StoreManager sm = StoreManager.getInstance();
        InputStream is = null;
        try {
            is = pm.getRawInputStream();
            if (is != null) {
                StagedBlob sblob = sm.stage(is, null, mMailbox);
                return setContent(sblob, pm);
            } else {
                ZimbraLog.calendar.warn(
                        "Invalid state: updating blob with null data for calendar item " + getId() +
                        " in mailbox " + getMailboxId());
                return setContent(null, pm);
            }
        } finally {
            ByteUtil.closeStream(is);
        }
    }

    @Override
    void reanalyze(Object data, long newSize) throws ServiceException {
        String subject = null;
        Invite firstInvite = getDefaultInviteOrNull();
        if (firstInvite != null)
            subject = firstInvite.getName();
        if (subject == null)
            subject= "";

        mData.subject = DbMailItem.truncateSubjectToMaxAllowedLength(subject);
        saveData(getSender());
    }

    /**
     * The Blob for the appointment/task is currently single Mime multipart/digest which has
     * each invite's MimeMessage stored as a part.
     *
     * @param invPm
     * @param firstInvite
     * @throws ServiceException
     */
    private MailboxBlob createBlob(ParsedMessage invPm, Invite firstInvite)
    throws ServiceException {
        // Create blob only if there's an attachment or DESCRIPTION is too big to be stored in metadata.
        if (!firstInvite.hasAttachment() && (invPm == null || firstInvite.descInMeta())) {
            // If we're not going to create a blob after all, we must at least save the metadata to db.
            // It's weird that the db update is implicitly required of this method, but that's the way
            // it is, unfortunately.  If we're creating a blob, the implicit db update is done by
            // storeUpdatedBlob() call.  (see below)
            saveMetadata();
            return null;
        }

        try {
            // create the toplevel multipart/digest...
            MimeMessage mm = new Mime.FixedMimeMessage(JMSession.getSession());
            MimeMultipart mmp = new ZMimeMultipart("digest");
            mm.setContent(mmp);

            // add the invite
            MimeBodyPart mbp = new ZMimeBodyPart();
            mbp.setDataHandler(new DataHandler(new PMDataSource(invPm)));
            mbp.addHeader("invId", Integer.toString(firstInvite.getMailItemId()));
            mmp.addBodyPart(mbp);

            mm.saveChanges();

            return storeUpdatedBlob(mm);
        } catch (MessagingException e) {
            throw ServiceException.FAILURE("MessagingException "+e, e);
        } catch (IOException e) {
            throw ServiceException.FAILURE("IOException "+e, e);
        }
    }

    /**
     * Upate the Blob for this CalendarItem object: possibly remove one or more entries from it,
     * possibly add an entry to it.
     *
     * It IS okay to have newInv != null and invPm==null....this would mean an invite-add where the
     * new invite had no ParsedMessage: IE because it didn't actually come in via an RFC822 msg
     *
     * @param toRemove
     * @param removeAllExistingInvites if true, all existing invites are removed regardless of
     *                                 what's passed in toRemove list
     * @param toUpdate
     * @param invPm
     * @param newInv
     * @param isCancel if the method is being called while processing a cancel request
     * @param allowPrivateAccess
     * @param forceSave if TRUE then this call is guaranteed to save the current metadata state
     * @param replaceExceptionBodyWithSeriesBody if TRUE bodies of exceptions are replaced with series body
     *                                           for invites in toUpdate list
     * @throws ServiceException
     */
    private void modifyBlob(List<Invite> toRemove,
                            boolean removeAllExistingInvites,
                            List<Invite> toUpdate,
                            ParsedMessage invPm,
                            Invite newInv,
                            boolean isCancel,
                            boolean allowPrivateAccess,
                            boolean forceSave,
                            boolean replaceExceptionBodyWithSeriesBody)
    throws ServiceException
    {
        // TODO - as an optimization, should check to see if the invite's MM is already in here! (ie
        //         if a single incoming Message has multiple invites in it, all for this CalendarItem)
        try {
            // now, make sure the message is in our blob already...
            MimeMessage mm = null;
            if (getSize() > 0) {
                try {
                    mm = getMimeMessage();
                } catch (ServiceException e) {
                    ZimbraLog.calendar.warn("Error reading blob for calendar item " + getId() +
                                            " in mailbox " + getMailboxId(), e);
                }
            }
            if (mm == null) {
                if (newInv != null && invPm != null) {
                    // if the blob isn't already there, and we're going to add one, then
                    // just go into create
                    createBlob(invPm, newInv);
                } else {
                    if (forceSave)
                        saveMetadata();
                }
                return;
            }

            // It should be a multipart/digest.
            MimeMultipart mmp;
            Object obj = mm.getContent();
            if (obj instanceof MimeMultipart)
                mmp = (MimeMultipart) obj;
            else
                throw ServiceException.FAILURE(
                        "Expected MimeMultipart, but got " + obj.getClass().getName() +
                        ": id=" + mId + ", content=" + obj.toString(), null);

            boolean updated = false;

            // remove invites
            if (removeAllExistingInvites) {
                int numParts = mmp.getCount();
                if (numParts > 0) {
                    for (int i = numParts - 1; i >= 0; i--) {
                        mmp.removeBodyPart(i);
                    }
                    updated = true;
                }
            } else {
                // Remove specific invites.
                for (Invite inv: toRemove) {
                    int matchedIdx;
                    do {
                        // find the matching parts...
                        int numParts = mmp.getCount();
                        matchedIdx = -1;
                        for (int i = 0; i < numParts; i++) {
                            MimeBodyPart mbp = (MimeBodyPart)mmp.getBodyPart(i);
                            String[] hdrs = mbp.getHeader("invId");
                            if (hdrs == null || hdrs.length == 0) {
                                matchedIdx = i;
                                break;
                            }
                            if (hdrs != null && hdrs.length > 0 && (Integer.parseInt(hdrs[0])==inv.getMailItemId())) {
                                matchedIdx = i;
                                break;
                            }
                        }
                        if (matchedIdx > -1) {
                            mmp.removeBodyPart(matchedIdx);
                            updated = true;
                        }
                    } while(matchedIdx > -1);
                }
            }

            // Update some invites.
            for (Invite inv: toUpdate) {
                MimeBodyPart mbpInv = null;
                int numParts = mmp.getCount();
                for (int i = 0; i < numParts; i++) {
                    MimeBodyPart mbp = (MimeBodyPart) mmp.getBodyPart(i);
                    String[] hdrs = mbp.getHeader("invId");
                    if (hdrs != null && hdrs.length > 0 &&
                        (Integer.parseInt(hdrs[0]) == inv.getMailItemId())) {
                        mbpInv = mbp;
                        break;
                    }
                }
                if (mbpInv == null)
                    continue;

                if (replaceExceptionBodyWithSeriesBody) {
                    // Throw away the existing part.  Replace it with body from new invite.
                    mmp.removeBodyPart(mbpInv);
                    mbpInv = new ZMimeBodyPart();
                    mbpInv.setDataHandler(new DataHandler(new PMDataSource(invPm)));
                    mbpInv.addHeader("invId", Integer.toString(inv.getMailItemId()));
                    mmp.addBodyPart(mbpInv);
                    mm.saveChanges();  // required by JavaMail for some magical reason
                }

                // Find the text/calendar part and replace it.
                String mbpInvCt = mbpInv.getContentType();
                Object objInv = mbpInv.getContent();
                if (!(objInv instanceof MimeMessage)) continue;
                MimeMessage mmInv = (MimeMessage) objInv;
                Object objMulti = mmInv.getContent();
                if (!(objMulti instanceof MimeMultipart)) continue;
                MimeMultipart multi = (MimeMultipart) objMulti;
                int numSubParts = multi.getCount();
                int icalPartNum = -1;
                for (int j = 0; j < numSubParts; j++) {
                    MimeBodyPart part = (MimeBodyPart) multi.getBodyPart(j);
                    ContentType ct = new ContentType(part.getContentType());
                    if (ct.match(MimeConstants.CT_TEXT_CALENDAR)) {
                        icalPartNum = j;
                        break;
                    }
                }
                if (icalPartNum != -1) {
                    updated = true;
                    multi.removeBodyPart(icalPartNum);
                    ZVCalendar cal = inv.newToICalendar(allowPrivateAccess);
                    MimeBodyPart icalPart = CalendarMailSender.makeICalIntoMimePart(cal);
                    multi.addBodyPart(icalPart, icalPartNum);
                    // Courtesy of JavaMail.  All three lines are necessary.
                    // Reasons unclear from JavaMail docs.
                    mmInv.setContent(multi);
                    mmInv.saveChanges();
                    mbpInv.setContent(mmInv, mbpInvCt);
                    // End of courtesy of JavaMail
                }
            }

            if (newInv != null) {
                MimeBodyPart mbp = new ZMimeBodyPart();
                mbp.setDataHandler(new DataHandler(new PMDataSource(invPm)));
                mmp.addBodyPart(mbp);
                mbp.addHeader("invId", Integer.toString(newInv.getMailItemId()));
                updated = true;
            }

            if (!updated) {
                if (forceSave)
                    saveMetadata();
                return;
            }

            if (mmp.getCount() == 0) {
                markBlobForDeletion();
                setContent(null, null);
                if (forceSave)
                    saveMetadata();
            } else {
                // must call this explicitly or else new part won't be added...
                mm.setContent(mmp);
                mm.saveChanges();
                storeUpdatedBlob(mm);
            }
        } catch (MessagingException e) {
            throw ServiceException.FAILURE("MessagingException", e);
        } catch (IOException e) {
            throw ServiceException.FAILURE("IOException", e);
        }
    }

    public static class ReplyInfo {
        private ZAttendee mAttendee; // attendee record w/ PartStat
        private int mSeqNo;
        private long mDtStamp;
        private final RecurId mRecurId;

        private static final String FN_RECURID = "r";
        private static final String FN_SEQNO = "s";
        private static final String FN_DTSTAMP = "d";
        private static final String FN_ATTENDEE = "at";

        public ReplyInfo(ZAttendee at, int seq, long dtstamp, RecurId recurId) {
            mAttendee = at;
            mSeqNo = seq;
            mDtStamp = dtstamp;
            mRecurId = recurId;
        }

        public ZAttendee getAttendee() { return mAttendee; }
        public int getSeq() { return mSeqNo; }
        public long getDtStamp() { return mDtStamp; }
        public RecurId getRecurId() { return mRecurId; }

        public Metadata encodeAsMetadata() {
            Metadata meta = new Metadata();

            if (mRecurId != null) {
                meta.put(FN_RECURID, mRecurId.encodeMetadata());
            }

            meta.put(FN_SEQNO, mSeqNo);
            meta.put(FN_DTSTAMP, mDtStamp);
            meta.put(FN_ATTENDEE, mAttendee.encodeAsMetadata());

            return meta;
        }

        public static ReplyInfo decodeFromMetadata(Metadata md, TimeZoneMap tzMap)
        throws ServiceException {
            RecurId recurId;
            if (md.containsKey(FN_RECURID)) {
                recurId = RecurId.decodeMetadata(md.getMap(FN_RECURID), tzMap);
            } else {
                recurId = null;
            }
            int seq = (int) md.getLong(FN_SEQNO);
            long dtstamp = md.getLong(FN_DTSTAMP);
            Metadata metaAttendee = md.getMap(FN_ATTENDEE);
            ZAttendee at = metaAttendee != null ? new ZAttendee(metaAttendee) : null;
            ReplyInfo ri = new ReplyInfo(at, seq, dtstamp, recurId);
            return ri;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("seq=").append(mSeqNo);
            sb.append(", dtstamp=").append(mDtStamp);
            if (mRecurId != null)
                sb.append(", recurId=\"").append(mRecurId).append("\"");
            sb.append(", attendee=").append(mAttendee);
            return sb.toString();
        }
    }

    /**
     * @author tim
     *
     * Internal class for storing replies
     *
     */
    protected static class ReplyList {
        List<ReplyInfo> mReplies;

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("[ReplyList]\n");
            if (mReplies != null) {
                for (ReplyInfo ri : mReplies) {
                    sb.append(ri.toString()).append("\n");
                }
            }
            sb.append("[end]\n");
            return sb.toString();
        }

        public ReplyList() {
            mReplies = new ArrayList<ReplyInfo>();
        }

        public ReplyList(List<ReplyInfo> list) {
            mReplies = list;
        }

        private static final String FN_NUM_REPLY_INFO = "n";
        private static final String FN_REPLY_INFO = "i";

        Metadata encodeAsMetadata() {
            Metadata meta = new Metadata();

            meta.put(FN_NUM_REPLY_INFO, mReplies.size());
            for (int i = 0; i < mReplies.size(); i++) {
                String fn = FN_REPLY_INFO + i;

                meta.put(fn, mReplies.get(i).encodeAsMetadata());
            }
            return meta;
        }

        static ReplyList decodeFromMetadata(Metadata md, TimeZoneMap tzMap) throws ServiceException {
            ReplyList toRet = new ReplyList();
            int numReplies = (int) md.getLong(FN_NUM_REPLY_INFO);
            toRet.mReplies = new ArrayList<ReplyInfo>(numReplies);
            for (int i = 0; i < numReplies; i++) {
                ReplyInfo inf = ReplyInfo.decodeFromMetadata(md.getMap(FN_REPLY_INFO+i), tzMap);
                toRet.mReplies.add(i, inf);
            }
            return toRet;
        }

        void removeObsoleteEntries(RecurId recurId, int seqNo, long dtStamp) {
            for (Iterator<ReplyInfo> iter = mReplies.iterator(); iter.hasNext();) {
                ReplyInfo cur = iter.next();

                if (recurMatches(cur.mRecurId, recurId)) {
//                    if (cur.mSeqNo < seqNo || (cur.mSeqNo == seqNo && cur.mDtStamp < dtStamp)) {
                    if (cur.mSeqNo < seqNo) {
                        iter.remove();
                    }
                } else if (recurId == null) {
                    // We're updating the series and the current reply is for an instance.
                    // Toss the instance reply because the series update can impact the exceptions.
                    iter.remove();
                }
            }
        }

        void upgradeEntriesToNewSeq(RecurId recurId, int seqNo, long dtStamp) {
            List<ReplyInfo> upgraded = new ArrayList<ReplyInfo>();
            for (Iterator<ReplyInfo> iter = mReplies.iterator(); iter.hasNext();) {
                ReplyInfo cur = iter.next();
                if (recurMatches(cur.mRecurId, recurId)) {
                    if (cur.mSeqNo < seqNo || (cur.mSeqNo == seqNo && cur.mDtStamp < dtStamp)) {
                        // Upgrade the reply to the new sequence and dtstamp by removing the old and adding a new one.
                        iter.remove();
                        ReplyInfo reply = new ReplyInfo(cur.getAttendee(), seqNo, dtStamp, recurId);
                        upgraded.add(reply);
                    }
                }
            }
            mReplies.addAll(upgraded);
        }

        /**
         * If <code>inv</code> is the most up to date response we've received, add this reply to
         * <code>mReplies</code> and discard any outdated response found.
         * @return true if decide to store <code>inv</code>
         */
        boolean maybeStoreNewReply(Invite inv, ZAttendee at) throws ServiceException {
            // Look up internal account for the attendee.  For internal users we want to match
            // on all email addresses of the account.
            AccountAddressMatcher acctMatcher = null;
            String address = at.getAddress();
            if (address != null) {
                Account acct = Provisioning.getInstance().get(AccountBy.name, address);
                if (acct != null) {
                    acctMatcher = new AccountAddressMatcher(acct);
                }
            }

            for (Iterator<ReplyInfo> iter = mReplies.iterator(); iter.hasNext();) {
                ReplyInfo cur = iter.next();
                if (at.addressesMatch(cur.mAttendee) ||
                    (acctMatcher != null && acctMatcher.matches(cur.mAttendee.getAddress()))) {
                    if (recurMatches(inv.getRecurId(), cur.mRecurId)) {
                        if (inv.getSeqNo() < cur.getSeq())
                            return false; // previously received reply has later sequence than new reply
                        if ((inv.getSeqNo() == cur.getSeq()) && (inv.getDTStamp() < cur.getDtStamp()))
                            return false; // sequence is same but previously received reply has later DTSTAMP
                        // Good.  This new reply is more up to date than the previous one
                        iter.remove();
                        ReplyInfo toAdd = new ReplyInfo(at, inv.getSeqNo(), inv.getDTStamp(), inv.getRecurId());
                        mReplies.add(toAdd);
                        return true;
                    }
                } // attendee check
            }

            // if we get here, we didn't find one at all.  add a new one...
            ReplyInfo toAdd = new ReplyInfo(at, inv.getSeqNo(), inv.getDTStamp(), inv.getRecurId());
            mReplies.add(toAdd);
            return true;
        }

        void modifyPartStat(Account acctOrNull, RecurId recurId, String cnStr, String addressStr, String cutypeStr, String roleStr,
                String partStatStr, Boolean needsReply, int seqNo, long dtStamp)  throws ServiceException {
            AccountAddressMatcher acctMatcher = acctOrNull != null ? new AccountAddressMatcher(acctOrNull) : null;
            for (ReplyInfo cur : mReplies) {
                if ((cur.mRecurId == null && recurId == null) || (cur.mRecurId != null && cur.mRecurId.withinRange(recurId))) {
                    if ((acctMatcher != null && (acctMatcher.matches(cur.mAttendee.getAddress()))) ||
                        (acctMatcher == null && cur.mAttendee.addressMatches(addressStr))) {
                        if (cur.mAttendee.hasCn()) {
                            cnStr = cur.mAttendee.getCn();
                        }

                        if (cur.mAttendee.hasCUType()) {
                            cutypeStr = cur.mAttendee.getCUType();
                        }

                        if (cur.mAttendee.hasRole()) {
                            roleStr = cur.mAttendee.getRole();
                        }

                        ZAttendee newAt = new ZAttendee(cur.mAttendee);
                        newAt.setCn(cnStr);
                        newAt.setCUType(cutypeStr);
                        newAt.setRole(roleStr);
                        newAt.setPartStat(partStatStr);
                        newAt.setRsvp(needsReply);

                        cur.mAttendee = newAt;
                        cur.mSeqNo = seqNo;
                        cur.mDtStamp = dtStamp;
                        return;
                    }
                }
            }

            // no existing partstat for this instance...add a new one
            ZAttendee at =
                new ZAttendee(addressStr, cnStr, null, null, null,
                              cutypeStr, roleStr, partStatStr, needsReply,
                              null, null, null, null);
            ReplyInfo ri = new ReplyInfo(at, seqNo, dtStamp, recurId);
            mReplies.add(ri);
        }

        boolean recurMatches(RecurId lhs, RecurId rhs) {
            if (lhs == null) {
                return (rhs==null);
            }
            return lhs.equals(rhs);
        }

        /**
         * Find the "Effective" attendee for a particular Instance
         *
         * Three possible returns
         *    1) an exact match (reply is to default and asking for default OR reply is to specific instance and asking for it)
         *    2) the default, if one is set
         *    3) null
         *
         * @param acct
         * @param inv
         * @param inst Instance to look up, or "null" for the default instance
         * @return
         * @throws ServiceException
         */
        ZAttendee getEffectiveAttendee(Account acct, Invite inv, Instance inst) throws ServiceException {
            // Are we dealing with a simple, non-recurring meeting?
            boolean isSimple = inv.getRecurrence() == null && !inv.hasRecurId();
            ZAttendee defaultAt = null;

            AccountAddressMatcher acctMatcher = new AccountAddressMatcher(acct);
            for (ReplyInfo cur : mReplies) {
                if (acctMatcher.matches(cur.mAttendee.getAddress())) {
                    // We have a match if reply isn't for a specific instance and either we're asking about
                    // the default instance of a recurring appointment or we're not dealing with a recurring
                    // appointment.
                    boolean match = cur.mRecurId == null && (inst == null || isSimple);
                    if (!match) {
                        if (inst != null && cur.mRecurId != null) {  // matches specific requested instance
                            long instStart = inst.getStart();
                            if (inst.mInvId != null) {
                                RecurId instRecurId = inst.mInvId.getRecurrenceId();
                                if (instRecurId != null) {
                                    // For an exception instance, use the time in RECURRENCE-ID rather than DTSTART. (bug 33181)
                                    instStart = instRecurId.getDt().getUtcTime();
                                }
                            }
                            match = cur.mRecurId.withinRange(instStart);
                        }
                    }
                    if (match) {
                        //
                        // we found exactly what they're looking for!  Either the default (and they asked for it) or the
                        // specific one they asked for
                        //
                        if (inv.getSeqNo() <= cur.mSeqNo) {
                            if (inv.getDTStamp() <= cur.mDtStamp) {
                                return cur.mAttendee;
                            }
                        }
                    }

                    if (cur.mRecurId == null) {
                        // save the default reply if it is valid: we will return it if we don't find an exact match
                        if ((inv.getSeqNo() <= cur.mSeqNo) && (inv.getDTStamp() <= cur.mDtStamp))
                            defaultAt = cur.mAttendee;
                    }
                }
            }

            // We didn't find an exact match.

            if (isSimple || inst == null) {
                // simple appointment or the series of a recurring appointment
                return defaultAt != null ? defaultAt : inv.getMatchingAttendee(acct);
            } else {
                // instance of a recurring appointment

                // Special handling for local-only invites.  (Local-only means the data exists on attendee
                // side only and wasn't published by the organizer.)

                // For local-only invite, inherit the reply status from the series, if one was found.
                // It's okay to inherit from the series because we know there was no instance-specific
                // reply.  If there was one, we would have found ti and returned from the method already
                // and would not be here.
                //
                // If the invite is not local-only and is for an exception instance (meaning it was published
                // by organizer), do not use the default series reply.  Get the partstat from the attendee
                // info in the invite.  The default series reply only applies to instances expanded from
                // the recurrence rule.
                if (defaultAt != null && (!inv.hasRecurId() || inv.isLocalOnly()))
                    return defaultAt;
                else
                    return inv.getMatchingAttendee(acct);
            }
        }

        List<ReplyInfo> getAllReplies() {
            List<ReplyInfo> toRet = new ArrayList<ReplyInfo>();
            toRet.addAll(mReplies);
            return toRet;
        }

        List<ReplyInfo> getReplyInfo(Invite inv, String recurIdZ) {
            assert(inv != null);

            Map<String /* attendee address */, ReplyInfo> repliesByAddr = new HashMap<String, ReplyInfo>();

            if (recurIdZ == null && inv.hasRecurId())
                recurIdZ = inv.getRecurId().getDtZ();

            for (ReplyInfo reply : mReplies) {
                ReplyInfo toAdd = null;
                String replyRidZ = null;
                if (reply.getRecurId() != null) {
                    // RECURRENCE-ID in the reply may have the Outlook-hack style value
                    // (i.e. RECURRENCE-ID;TZID=blah:YYYYMMDDT000000) for an all-day appointment.
                    // Find the YYYYMMDD-only value so we can compare it against recurIdZ passed in.
                    ParsedDateTime dt = reply.getRecurId().getDt();
                    ParsedDateTime dtTmp = (ParsedDateTime) dt.clone();
                    if (inv.isAllDayEvent()) {
                        dtTmp.setHasTime(false);
                    } else {
                        dtTmp.setHasTime(true);
                        dtTmp.toUTC();
                    }
                    replyRidZ = dtTmp.getDateTimePartString(false);
                }
                if (inv.hasRecurId()) {
                    // Looking for replies for an exception instance.
                    if (reply.mRecurId != null && recurIdZ.equals(replyRidZ) &&
                        inv.getSeqNo() <= reply.mSeqNo) {  // Ignore outdated replies.
                        toAdd = reply;
                    }
                } else {
                    // Looking for replies to series (if recurIdZ == null)
                    // or a non-exception instance (if recurIdZ != null).
                    if (recurIdZ == null) {
                        if (reply.mRecurId == null &&
                            inv.getSeqNo() <= reply.mSeqNo)  // Ignore outdated replies.
                            toAdd = reply;
                    } else {
                        // Get all replies to series AND the instance with matching recurrence id.
                        if ((reply.mRecurId == null || recurIdZ.equals(replyRidZ)) &&
                            inv.getSeqNo() <= reply.mSeqNo)  // Ignore outdated replies.
                            toAdd = reply;
                    }
                }

                // Add to map if this is the first reply from the attendee, or this reply
                // is more specific than previous reply.  (i.e. prev reply is to series, this one
                // is to an instance)  If both are for series or both are for same instance,
                // pick the more recent reply.
                if (toAdd != null && toAdd.getAttendee() != null) {
                    String addr = toAdd.getAttendee().getAddress();
                    if (addr != null) {
                        ReplyInfo existing = repliesByAddr.get(addr);
                        if (existing == null) {
                            repliesByAddr.put(addr, toAdd);
                        } else {
                            if (existing.getRecurId() == null) {  // Existing reply is for series.
                                if (replyRidZ != null) {
                                    // This reply is for an instance and is therefore more specific.
                                    repliesByAddr.put(addr, toAdd);
                                } else if (existing.getDtStamp() <= toAdd.getDtStamp()) {
                                    // Both are for series and this one is more recent.
                                    repliesByAddr.put(addr, toAdd);
                                }
                            } else {  // Existing reply is for an instance.
                                if (replyRidZ == null) {
                                    // This one is for series and is therefore less specific.  Ignore it.
                                } else if (existing.getDtStamp() <= toAdd.getDtStamp()) {
                                    // Both are for an instance and this one is more recent.
                                    repliesByAddr.put(addr, toAdd);
                                }
                            }
                        }
                    }
                }
            }

            return new ArrayList<ReplyInfo>(repliesByAddr.values());
        }
    } // class ReplyList

    /**
     * Get all Reply data.
     *
     * @param inv
     * @return
     */
    public List<ReplyInfo> getAllReplies() {
        return mReplyList.getAllReplies();
    }

    /**
     * Get all of the Reply data corresponding to an Invite and a RECURRENCE-ID string.
     * Pass null recurIdZ to get replies to the series.  Recurrence ID string has the
     * format of "YYYYMMDD[ThhmmssZ]".
     *
     * @param inv      cannot be null
     * @param recurIdZ
     * @return
     */
    public List<ReplyInfo> getReplyInfo(Invite inv, String recurIdZ) {
        return mReplyList.getReplyInfo(inv, recurIdZ);
    }

    /**
     * Set the replies list to the given list.  Existing list is replaced.
     * @param replies
     */
    public void setReplies(List<ReplyInfo> replies) throws ServiceException {
        mReplyList = new ReplyList(replies);
        saveMetadata();
    }

    /**
     * Returns the effective PartStat given the Invite and Instance.
     *
     * @param inv
     * @param inst
     * @return
     * @throws ServiceException
     */
    public String getEffectivePartStat(Invite inv, Instance inst) throws ServiceException {
        if (!inv.isOrganizer()) {
            ZAttendee at = mReplyList.getEffectiveAttendee(getMailbox().getAccount(), inv, inst);
            if (at != null) {
                if (at.hasPartStat())
                    return at.getPartStat();
                else
                    return IcalXmlStrMap.PARTSTAT_NEEDS_ACTION;
            }
        }
        return inv.getPartStat();
    }

    /**
     *
     * Used when we're sending out a reply -- we add a "reply" record to this appointment/task...this
     * ends up affecting our "Effective PartStat" (ie if we ACCEPT a meeting, then our effective partstat
     * changes)
     *
     * @param acctOrNull
     * @param recurId
     * @param cnStr
     * @param addressStr
     * @param cutypeStr
     * @param roleStr
     * @param partStatStr
     * @param rsvp
     * @param seqNo
     * @param dtStamp
     * @throws ServiceException
     */
    void modifyPartStat(Account acctOrNull, RecurId recurId, String cnStr, String addressStr, String cutypeStr, String roleStr,
            String partStatStr, Boolean needsReply, int seqNo, long dtStamp) throws ServiceException {
        mReplyList.modifyPartStat(acctOrNull, recurId, cnStr, addressStr, cutypeStr, roleStr, partStatStr, needsReply, seqNo, dtStamp);
        if (addressStr != null) {
            Invite inv = getInvite(recurId);
            if (inv != null) {
                ZAttendee at;
                if (acctOrNull != null)
                    at = inv.getMatchingAttendee(acctOrNull);
                else
                    at = inv.getMatchingAttendee(addressStr);
                if (at != null)
                    at.setPartStat(partStatStr);
            }
        }
        saveMetadata();
    }

    public boolean processNewInviteReply(Invite reply)
    throws ServiceException {
        List<ZAttendee> attendees = reply.getAttendees();

        // trace logging
        ZAttendee att1 = !attendees.isEmpty() ? attendees.get(0) : null;
        if (att1 != null) {
            String ptst = IcalXmlStrMap.sPartStatMap.toIcal(att1.getPartStat());
            if (!reply.hasRecurId())
                ZimbraLog.calendar.info(
                        "Processing CalendarItem reply: attendee=%s, partstat=%s, id=%d, folderId=%d, subject=\"%s\", UID=%s",
                        att1.getAddress(), ptst, mId, getFolderId(),
                        reply.isPublic() ? reply.getName() : "(private)", mUid);
            else
                ZimbraLog.calendar.info(
                        "Processing CalendarItem reply: attendee=%s, partstat=%s, id=%d, folderId=%d, subject=\"%s\", UID=%s, recurId=%s",
                        att1.getAddress(), ptst, mId, getFolderId(),
                        reply.isPublic() ? reply.getName() : "(private)", mUid, reply.getRecurId().getDtZ());
        }

        // Require private access permission only when we're replying to a private series/instance.
        boolean requirePrivateCheck = requirePrivateCheck(reply);
        OperationContext octxt = getMailbox().getOperationContext();
        Account authAccount = octxt != null ? octxt.getAuthenticatedUser() : null;
        boolean asAdmin = octxt != null ? octxt.isUsingAdminPrivileges() : false;
        if (!canAccess(ACL.RIGHT_ACTION, authAccount, asAdmin, requirePrivateCheck))
            throw ServiceException.PERM_DENIED("you do not have sufficient permissions to change this appointment/task's state");

        boolean dirty = false;
        Invite invMatchingRecurId = null;
        // unique ID: UID+RECURRENCE_ID

        for (int i = 0; i < numInvites(); i++) {
            Invite cur = getInvite(i);

            // UID already matches...next check if RecurId matches
            // if so, then seqNo is next
            // finally use DTStamp
            //
            // See RFC2446: 2.1.5 Message Sequencing
            //


            // If any of the existing Invites have recurIDs which exactly match the reply,
            // then we should do a sequence-check against them before deciding to accept
            // this reply
            // FIXME should check for cur.recurID WITHIN_RANGE (THISANDFUTURE-type support)
            if ((cur.getRecurId() != null && cur.getRecurId().equals(reply.getRecurId())) ||
                    (cur.getRecurId() == null && reply.getRecurId() == null)) {

                // they're replying to this invite!

                // Bug 32573 Meeting replies can be dropped if client PC time is behind server time
                // Removed check that (cur.getDTStamp() > reply.getDTStamp()) as the following
                // implies that REPLY DTSTAMP is related to client submission time rather than
                // the DTSTAMP in the original invite & clock skew can result in it being earlier.
                // From http://tools.ietf.org/html/rfc5546 (iTIP) 2.1.5. Message Sequencing :
                //     for each "ATTENDEE" property of a component,
                //     "Organizer" CUAs will need to persist the "SEQUENCE"
                //     and "DTSTAMP" property values associated with the "Attendee's" last
                //     response, so that any earlier responses from an "Attendee" that are
                //     received out of order (e.g., due to a delay in the transport) can be
                //     correctly discarded.
                if (cur.getSeqNo() > reply.getSeqNo()) {
                    sLog.info("Invite-Reply "+reply.toString()+" is outdated (Calendar entry has higher SEQUENCE), ignoring!");
                    return false;
                }

                // maybeStoreNewReply does some further checks which might invalidate this reply
                // so, postpone updating attendee information until after that.
                invMatchingRecurId = cur;
                break; // found a match, fall through to below and process it!
            }
        }

        // if we got here then we have validated the sequencing against all of our Invites,
        // OR alternatively we looked and couldn't find one with a matching RecurID (therefore
        // they must be replying to a arbitrary instance)
        for (ZAttendee at : attendees) {
            if (mReplyList.maybeStoreNewReply(reply, at))
                dirty = true;
        }

        if (!dirty) {
            sLog.info("Invite-Reply "+reply.toString()+" is outdated ignoring!");
            return false;
        }
        if (invMatchingRecurId != null)
            invMatchingRecurId.updateMatchingAttendeesFromReply(reply);
        saveMetadata();
        getMailbox().markItemModified(this, Change.MODIFIED_INVITE);
        return true;
    }

    public InputStream getRawMessage() throws ServiceException {
        return getContentStream();
    }

    void appendRawCalendarData(ZVCalendar cal,
                               boolean useOutlookCompatMode,
                               boolean ignoreErrors,
                               boolean allowPrivateAccess)
    throws ServiceException {
        Invite[] invs = getInvites();
        for (Invite inv : invs) {
            try {
                cal.addComponent(inv.newToVComponent(useOutlookCompatMode, allowPrivateAccess));
            } catch (ServiceException e) {
                if (ignoreErrors) {
                    ZimbraLog.calendar.warn(
                            "Error retrieving iCalendar data for item " +
                            inv.getMailItemId() + ": " + e.getMessage(), e);
                } else
                    throw e;
            }
        }
    }

    public MimeMessage getMimeMessage() throws ServiceException {
        InputStream is = null;
        MimeMessage mm = null;
        try {
            is = getRawMessage();
            if (is == null)
                return null;
            mm = new ZMimeMessage(JMSession.getSession(), is);
            ByteUtil.closeStream(is);

            try {
                for (Class<? extends MimeVisitor> visitor : MimeVisitor.getConverters())
                    visitor.newInstance().accept(mm);
            } catch (Exception e) {
                // If the conversion bombs for any reason, revert to the original
                ZimbraLog.mailbox.warn(
                    "MIME converter failed for message " + getId(), e);
                is = getRawMessage();
                mm = new ZMimeMessage(JMSession.getSession(), is);
                ByteUtil.closeStream(is);
            }

            return mm;
        } catch (MessagingException e) {
            throw ServiceException.FAILURE("MessagingException while getting MimeMessage for item " + mId, e);
        } finally {
            ByteUtil.closeStream(is);
        }
    }

    /**
     * @param subId
     * @return
     * @throws ServiceException
     */
    public MimeMessage getSubpartMessage(int subId) throws ServiceException {
        try {
            MimeBodyPart mbp = findBodyBySubId(subId);
            return mbp == null ? null : (MimeMessage) Mime.getMessageContent(mbp);
        } catch (IOException e) {
            throw ServiceException.FAILURE("IOException while getting MimeMessage for item " + mId, e);
        } catch (MessagingException e) {
            throw ServiceException.FAILURE("MessagingException while getting MimeMessage for item " + mId, e);
        }
    }

    /**
     * Returns a Pair containing a MimeMessage object and its estimated size for the requested sub part id.
     * The size returned is not exact and should not be used in such context.
     * @param subId
     * @return
     * @throws ServiceException
     */
    public Pair<MimeMessage,Integer> getSubpartMessageData(int subId) throws ServiceException {
        try {
            MimeBodyPart mbp = findBodyBySubId(subId);
            if (mbp == null)
                return null;
            return new Pair<MimeMessage,Integer>(Mime.getMessageContent(mbp), mbp.getSize());
        } catch (IOException e) {
            throw ServiceException.FAILURE("IOException while getting MimeMessage for item " + mId, e);
        } catch (MessagingException e) {
            throw ServiceException.FAILURE("MessagingException while getting MimeMessage for item " + mId, e);
        }
    }

    private MimeBodyPart findBodyBySubId(int subId) throws ServiceException {
        if (getSize() <= 0)
            return null;
        InputStream is = null;
        MimeMessage mm = null;
        try {
            is = getRawMessage();
            if (is == null)
                return null;
            mm = new Mime.FixedMimeMessage(JMSession.getSession(), is);
            ByteUtil.closeStream(is);

            if (!DebugConfig.disableMimeConvertersForCalendarBlobs) {
                try {
                    for (Class<? extends MimeVisitor> visitor : MimeVisitor.getConverters())
                        visitor.newInstance().accept(mm);
                } catch (Exception e) {
                    // If the conversion bombs for any reason, revert to the original
                    ZimbraLog.mailbox.warn("MIME converter failed for message " + getId(), e);
                    is = getRawMessage();
                    mm = new MimeMessage(JMSession.getSession(), is);
                    ByteUtil.closeStream(is);
                }
            }

            // It should be multipart/digest.
            MimeMultipart mmp;
            Object obj = mm.getContent();
            if (obj instanceof MimeMultipart)
                mmp = (MimeMultipart) obj;
            else
                throw ServiceException.FAILURE(
                        "Expected MimeMultipart, but got " + obj.getClass().getName() +
                        ": id=" + mId + ", content=" + obj.toString(), null);

            // find the matching parts...
            int numParts = mmp.getCount();

            for (int i = 0; i < numParts; i++) {
                MimeBodyPart mbp = (MimeBodyPart)mmp.getBodyPart(i);
                String[] hdrs = mbp.getHeader("invId");

                if (hdrs != null && hdrs.length > 0 && (Integer.parseInt(hdrs[0]) == subId))
                    return mbp;
            }
            return null;
        } catch (IOException e) {
            throw ServiceException.FAILURE("IOException while getting MimeMessage for item " + mId, e);
        } catch (MessagingException e) {
            throw ServiceException.FAILURE("MessagingException while getting MimeMessage for item " + mId, e);
        } finally {
            ByteUtil.closeStream(is);
        }
    }


    protected abstract String processPartStat(Invite invite,
                                              MimeMessage mmInv,
                                              boolean forCreate,
                                              String defaultPartStat)
    throws ServiceException;


    public static class AlarmData {
        private long mNextAt = Long.MAX_VALUE;
        private final long mNextInstStart;  // start time of the instance that mNextAt alarm is for
        private final int mInvId;
        private final int mCompNum;
        private final Alarm mAlarm;

        public AlarmData(long next, long nextInstStart, int invId, int compNum, Alarm alarm) {
            mNextAt = next;
            mNextInstStart = nextInstStart;
            mInvId = invId;
            mCompNum = compNum;
            mAlarm = alarm;
        }

        public long getNextAt() { return mNextAt; }
        public long getNextInstanceStart() { return mNextInstStart; }
        public int getInvId() { return mInvId; }
        public int getCompNum() { return mCompNum; }
        public Alarm getAlarm() { return mAlarm; }

        private static final String FNAME_NEXT_AT = "na";
        private static final String FNAME_NEXT_INSTANCE_START = "nis";
        private static final String FNAME_INV_ID = "invId";
        private static final String FNAME_COMP_NUM = "compNum";
        private static final String FNAME_ALARM = "alarm";

        static AlarmData decodeMetadata(Metadata meta) throws ServiceException {
            long nextAt = meta.getLong(FNAME_NEXT_AT);
            long nextInstStart = meta.getLong(FNAME_NEXT_INSTANCE_START);
            int invId = (int) meta.getLong(FNAME_INV_ID);
            int compNum = (int) meta.getLong(FNAME_COMP_NUM);
            Alarm alarm = null;
            Metadata metaAlarm = meta.getMap(FNAME_ALARM, true);
            if (metaAlarm != null)
                alarm = Alarm.decodeMetadata(metaAlarm);
            return new AlarmData(nextAt, nextInstStart, invId, compNum, alarm);
        }

        Metadata encodeMetadata() {
            Metadata meta = new Metadata();
            meta.put(FNAME_NEXT_AT, mNextAt);
            meta.put(FNAME_NEXT_INSTANCE_START, mNextInstStart);
            meta.put(FNAME_INV_ID, mInvId);
            meta.put(FNAME_COMP_NUM, mCompNum);
            if (mAlarm != null)
                meta.put(FNAME_ALARM, mAlarm.encodeMetadata());
            return meta;
        }
    }

    public boolean hasAlarm() {
        if (mInvites != null && mInvites.size() > 0) {
            for (Invite inv : mInvites) {
                if (inv.hasAlarm())
                    return true;
            }
        }
        return false;
    }

    public void updateNextAlarm(long nextAlarm) throws ServiceException {
        boolean hadAlarm = mAlarmData != null;
        recomputeNextAlarm(nextAlarm, true);
        if (mAlarmData != null) {
            long newNextAlarm = mAlarmData.getNextAt();
            if (newNextAlarm > 0 && newNextAlarm < mStartTime)
                mStartTime = newNextAlarm;
            if (ZimbraLog.calendar.isDebugEnabled()) {
                ZimbraLog.calendar.debug("Setting next alarm for %s to %d.",
                    getMailopContext(this), nextAlarm);
            }
            DbMailItem.updateInCalendarItemTable(this);
        }
        if (mAlarmData != null || hadAlarm)
            saveMetadata();
    }

    private static final long MILLIS_IN_YEAR = 365L * 24L * 60L * 60L * 1000L;

    private long getNextAlarmRecurrenceExpansionLimit() {
        long fromTime = Math.max(getStartTime(), System.currentTimeMillis());
        long endTime = fromTime + MILLIS_IN_YEAR;  // 1-year window to search for next alarm
        long itemEndTime = getEndTime();
        if (itemEndTime < endTime && itemEndTime >= getStartTime())  // avoid huge negative numbers due to wraparound
            endTime = itemEndTime;
        return endTime;
    }

    /**
     * Recompute the next non-EMAIL alarm trigger time that is at or later than "nextAlarm".
     * @param nextAlarm next alarm should go off at or after this time
     *                  special values:
     *                  CalendarItem.NEXT_ALARM_KEEP_CURRENT - keep current value
     *                  CalendarItem.NEXT_ALARM_ALL_DISMISSED - all alarms have been shown and dismissed
     *                  CalendarItem.NEXT_ALARM_FROM_NOW - compute next trigger time from current time
     * @param skipAlarmDefChangeCheck
     */
    private void recomputeNextAlarm(long nextAlarm, boolean skipAlarmDefChangeCheck)
    throws ServiceException {
        mAlarmData = getNextAlarm(nextAlarm, skipAlarmDefChangeCheck, mAlarmData, false);
    }

    /**
     * Gets the alarm data corresponding to the next (starting from current time) EMAIL alarm.
     *
     * @return
     * @throws ServiceException
     */
    public AlarmData getNextEmailAlarm() throws ServiceException {
        return getNextAlarm(System.currentTimeMillis(), true, null, true);
    }

    private AlarmData getNextAlarm(long nextAlarm, boolean skipAlarmDefChangeCheck, AlarmData currentNextAlarmData, boolean forEmailAction)
    throws ServiceException {
        if (nextAlarm == NEXT_ALARM_ALL_DISMISSED || !hasAlarm()) {
            return null;
        }

        long now = getMailbox().getOperationTimestampMillis();
        long atOrAfter;  // Chosen alarm must be at or after this time.
        if (nextAlarm == NEXT_ALARM_KEEP_CURRENT) {
            // special case to preserve current next alarm trigger time
            if (currentNextAlarmData != null) {
                atOrAfter = currentNextAlarmData.getNextAt();
            } else {
                atOrAfter = now;  // no existing alarm; pick the first alarm in the future
            }
        } else if (nextAlarm == NEXT_ALARM_FROM_NOW) {
            // another special case to mean starting from "now"; pick the first alarm in the future
            atOrAfter = now;
        } else {
            // else we just use the nextAlarm value that was passed in
            atOrAfter = nextAlarm;
        }
        if (atOrAfter <= 0)  // sanity check
            atOrAfter = now;

        // startTime and endTime limit the time range for meeting instances to be examined.
        // All instances that ended before startTime are ignored, and by extension the alarms for them.
        // endTime limit is set to avoid examining too many instances, for performance reason.
        long startTime = atOrAfter;
        if (startTime > now) {
            // Handle the case when appointment is brought back in time such that the new start time
            // is earlier than previously set alarm trigger time.
            startTime = now;
        }
        long endTime = getNextAlarmRecurrenceExpansionLimit();
        Collection<Instance> instances = expandInstances(startTime, endTime, false);

        // Special handling for modified alarm definition
        if (atOrAfter > 0 && !skipAlarmDefChangeCheck) {
            // Let's see if alarm definition has changed.  It changed if there is no alarm to go off at
            // previously saved nextAlarm time.
            boolean alarmDefChanged = true;
            long savedNextInstStart = currentNextAlarmData != null ? currentNextAlarmData.getNextInstanceStart() : 0;
            for (Instance inst : instances) {
                long instStart = inst.getStart();
                long instEnd = inst.getEnd();
                if (inst.hasStart() && inst.hasEnd()) {
                    // Ignore instances that ended already.
                    if (instEnd <= startTime)
                        continue;
                    // For appointments (but not tasks), ignore instances whose start time has come and gone.
                    if (instStart < startTime && (this instanceof Appointment))
                        continue;
                    // Done if we go beyond the previously saved alarm-associated instance.  The alarm definition
                    // must have changed, either directly in the alarm itself or indirectly via change in instance
                    // definition.
                    if (instStart > savedNextInstStart)
                        break;
                }
                InviteInfo invId = inst.getInviteInfo();
                Invite inv = getInvite(invId.getMsgId(), invId.getComponentId());
                Iterator<Alarm> alarmsIter = inv.alarmsIterator();
                for (; alarmsIter.hasNext(); ) {
                    Alarm alarm = alarmsIter.next();
                    long currTrigger = alarm.getTriggerTime(instStart, instEnd);
                    if (currTrigger == atOrAfter) {
                        // Detected alarm definition change.  Reset atOrAfter to 0 to force the next loop
                        // to choose the earliest alarm from an earliest instance at or after old nextAlarm time.
                        alarmDefChanged = false;
                        break;
                    }
                }
                break;  // no need to look at later instances
            }
            if (alarmDefChanged) {
                // If alarm definition changed, just pick the earliest alarm from now.  Without this,
                // we can't change alarm definition to an earlier trigger time, e.g. from 5 minutes before
                // to 10 minutes before. (bug 28630)
                atOrAfter = now;
            }
        }

        long triggerAt = Long.MAX_VALUE;
        Instance alarmInstance = null;
        Alarm theAlarm = null;

        for (Instance inst : instances) {
            long instStart = inst.getStart();
            long instEnd = inst.getEnd();
            if (inst.hasStart() && inst.hasEnd()) {
                // Ignore instances that ended already.
                if (instEnd <= startTime)
                    continue;
                // For appointments (but not tasks), ignore instances whose start time has come and gone.
                if (instStart < startTime && (this instanceof Appointment))
                    continue;
            }
            InviteInfo invId = inst.getInviteInfo();
            Invite inv = getInvite(invId.getMsgId(), invId.getComponentId());
            Pair<Long, Alarm> curr =
                getAlarmTriggerTime(atOrAfter, inv.alarmsIterator(), instStart, instEnd, forEmailAction);
            if (curr != null) {
                long currAt = curr.getFirst();
                if (atOrAfter <= currAt && currAt < triggerAt) {
                    triggerAt = currAt;
                    theAlarm = curr.getSecond();
                    alarmInstance = inst;
                }
            }
        }
        if (alarmInstance != null) {
            InviteInfo invInfo = alarmInstance.getInviteInfo();
            if (invInfo != null)
                return new AlarmData(triggerAt, alarmInstance.getStart(),
                                     invInfo.getMsgId(), invInfo.getComponentId(), theAlarm);
        }
        if (this instanceof Task) {
            // Unlike appointments, tasks may have alarms that go off after instance start time and even after
            // instance end time (as defined by DUE property), and these alarms must be computed differently.
            return getNextAbsoluteTriggerAlarm(atOrAfter, forEmailAction);
        } else {
            return null;
        }
    }

    /**
     * Finds the next absolute-trigger alarm that is at or later than "atOrAfter".
     * @param nextAlarm next alarm should go off at or after this time
     *                  special values:
     *                  CalendarItem.NEXT_ALARM_KEEP_CURRENT - keep current value
     *                  CalendarItem.NEXT_ALARM_ALL_DISMISSED - all alarms have been shown and dismissed
     *                  CalendarItem.NEXT_ALARM_FROM_NOW - compute next trigger time from current time
     * @param skipAlarmDefChangeCheck
     */
    private AlarmData getNextAbsoluteTriggerAlarm(long atOrAfter, boolean forEmailAction)
    throws ServiceException {
        long triggerAt = Long.MAX_VALUE;
        Alarm theAlarm = null;
        Invite theInvite = null;
        for (Invite inv : mInvites) {
            if (inv.isCancel())
                continue;
            for (Iterator<Alarm> alarms = inv.alarmsIterator(); alarms.hasNext(); ) {
                Alarm alarm = alarms.next();
                if (Action.EMAIL.equals(alarm.getAction()) == forEmailAction && alarm.getTriggerAbsolute() != null) {
                    long absTrig = alarm.getTriggerAbsolute().getUtcTime();
                    if (atOrAfter <= absTrig && absTrig < triggerAt) {
                        triggerAt = absTrig;
                        theAlarm = alarm;
                        theInvite = inv;
                    }
                }
            }
        }
        if (theAlarm != null) {
            long instStart = theInvite.getStartTime() != null ? theInvite.getStartTime().getUtcTime() : 0;
            return new AlarmData(triggerAt, instStart, theInvite.getMailItemId(), theInvite.getComponentNum(), theAlarm);
        } else {
            return null;
        }
    }

    // Find the earliest alarm whose trigger time is at or after nextAlarm.
    // Only alarms with email action are examined if forEmailAction==true.  If false, all other alarms are examined.
    private static Pair<Long, Alarm> getAlarmTriggerTime(
            long nextAlarm, Iterator<Alarm> alarms, long instStart, long instEnd, boolean forEmailAction) {
        long triggerAt = Long.MAX_VALUE;
        Alarm theAlarm = null;
        for (; alarms.hasNext(); ) {
            Alarm alarm = alarms.next();
            if (Action.EMAIL.equals(alarm.getAction()) == forEmailAction) {
                long currTrigger = alarm.getTriggerTime(instStart, instEnd);
                if (nextAlarm <= currTrigger && currTrigger < triggerAt) {
                    triggerAt = currTrigger;
                    theAlarm = alarm;
                }
            }
        }
        if (theAlarm != null)
            return new Pair<Long, Alarm>(triggerAt, theAlarm);
        else
            return null;
    }

    public static class NextAlarms {
        private final Map<Integer, Long> mTriggerMap;
        private final Map<Integer, Long> mInstStartMap;
        public NextAlarms() {
            mTriggerMap = new HashMap<Integer, Long>();
            mInstStartMap = new HashMap<Integer, Long>();
        }
        public void add(int pos, long triggerAt, long instStart) {
            mTriggerMap.put(pos, triggerAt);
            mInstStartMap.put(pos, instStart);
        }
        public long getTriggerTime(int pos) {
            Long triggerAt = mTriggerMap.get(pos);
            if (triggerAt != null)
                return triggerAt.longValue();
            else
                return 0;
        }
        public long getInstStart(int pos) {
            Long start = mInstStartMap.get(pos);
            if (start != null)
                return start.longValue();
            else
                return 0;
        }
        public Iterator<Integer> posIterator() {
            return mTriggerMap.keySet().iterator();
        }
    }

    /**
     * Returns the next trigger times for all alarms defined on the given Invite.
     * @param inv
     * @param lastAt Last trigger time for the alarms.  Next trigger times are later than this value.
     * @return NextAlarms object that tells trigger time and instance start time by alarm position.
     *         It will not contain alarm position if that alarm doesn't have next trigger time later
     *         than its last trigger time.
     */
    public NextAlarms computeNextAlarms(Invite inv, long lastAt) throws ServiceException {
        int numAlarms = inv.getAlarms().size();
        Map<Integer, Long> lastAlarmsAt = new HashMap<Integer, Long>(numAlarms);
        for (int i = 0; i < numAlarms; i++) {
            lastAlarmsAt.put(i, lastAt);
        }
        return computeNextAlarms(inv, lastAlarmsAt);
    }

    /**
     * Returns the next trigger times for alarms defined on the given Invite.
     * @param inv
     * @param lastAlarmsAt Map key is the 0-based alarm position and value is the last trigger time
     *                     for that alarm.
     * @return NextAlarms object that tells trigger time and instance start time by alarm position.
     *         It will not contain alarm position if that alarm doesn't have next trigger time later
     *         than its last trigger time.
     */
    public NextAlarms computeNextAlarms(Invite inv, Map<Integer, Long> lastAlarmsAt)
    throws ServiceException {
        NextAlarms result = new NextAlarms();

        if (inv.getRecurrence() == null) {
            // non-recurring appointment or exception instance
            long instStart = 0, instEnd = 0;
            ParsedDateTime dtstart = inv.getStartTime();
            if (dtstart != null)
                instStart = dtstart.getUtcTime();
            ParsedDateTime dtend = inv.getEffectiveEndTime();
            if (dtend != null)
                instEnd = dtend.getUtcTime();
            List<Alarm> alarms = inv.getAlarms();
            int index = 0;
            for (Iterator<Alarm> iter = alarms.iterator(); iter.hasNext(); index++) {
                Alarm alarm = iter.next();
                Long lastAtLong = lastAlarmsAt.get(index);
                if (lastAtLong != null) {
                    long lastAt = lastAtLong.longValue();
                    long triggerAt = alarm.getTriggerTime(instStart, instEnd);
                    if (lastAt < triggerAt)
                        result.add(index, triggerAt, instStart);
                }
            }
        } else {
            // series invite of recurring appointment
            long oldest;
            oldest = Long.MAX_VALUE;
            for (long lastAt : lastAlarmsAt.values()) {
                oldest = Math.min(oldest, lastAt);
            }
            long endTime = getNextAlarmRecurrenceExpansionLimit();
            Collection<Instance> instances = expandInstances(oldest, endTime, false);

            List<Alarm> alarms = inv.getAlarms();
            int index = 0;
            for (Iterator<Alarm> iter = alarms.iterator(); iter.hasNext(); index++) {
                Alarm alarm = iter.next();
                Long lastAtLong = lastAlarmsAt.get(index);
                if (lastAtLong != null) {
                    long lastAt = lastAtLong.longValue();
                    for (Instance inst : instances) {
                        if (inst.isException())  // only look at non-exception instances
                            continue;
                        long instStart = inst.getStart();
                        if (instStart < lastAt && inst.hasStart())
                            continue;
                        long triggerAt = alarm.getTriggerTime(instStart, inst.getEnd());
                        if (lastAt < triggerAt) {
                            result.add(index, triggerAt, instStart);
                            // We can break now because we know alarms on later instances are even later.
                            break;
                        }
                    }
                }
            }
        }
        return result;
    }

    @Override
    boolean move(Folder target) throws ServiceException {
        Invite defInv = getDefaultInviteOrNull();
        String sbj;
        if (defInv != null)
            sbj = defInv.isPublic() ? defInv.getName() : "(private)";
        else
            sbj = "(none)";
        ZimbraLog.calendar.info(
                "Moving CalendarItem: id=%d, src=%s, dest=%s, subject=\"%s\", UID=%s",
                mId, getMailopContext(getFolder()), getMailopContext(target), sbj, mUid);
        if (!isPublic()) {
            if (!canAccess(ACL.RIGHT_PRIVATE))
                throw ServiceException.PERM_DENIED(
                        "you do not have permission to move private calendar item from the current folder");
            if (target.getId() != Mailbox.ID_FOLDER_TRASH && !target.canAccess(ACL.RIGHT_PRIVATE))
                throw ServiceException.PERM_DENIED(
                        "you do not have permission to move private calendar item to the target folder");
        }

        addRevision(true);
        return super.move(target);
    }

    @Override
    void delete(DeleteScope scope, boolean writeTombstones) throws ServiceException {
        Invite defInv = getDefaultInviteOrNull();
        String sbj;
        if (defInv != null)
            sbj = defInv.isPublic() ? defInv.getName() : "(private)";
        else
            sbj = "(none)";
        ZimbraLog.calendar.info(
                "Deleting CalendarItem: id=%d, folderId=%d, subject=\"%s\", UID=%s",
                mId, getFolderId(), sbj, mUid);
        if (!isPublic() && !canAccess(ACL.RIGHT_PRIVATE))
            throw ServiceException.PERM_DENIED(
                    "you do not have permission to delete private calendar item from the current folder");
        super.delete(scope, writeTombstones);
    }

    @Override
    MailItem copy(Folder folder, int id, int parentId) throws IOException, ServiceException {
        if (!isPublic()) {
            boolean privateAccessSrc = canAccess(ACL.RIGHT_PRIVATE);
            boolean privateAccessDest = folder.canAccess(ACL.RIGHT_PRIVATE);
            if (!privateAccessSrc)
                throw ServiceException.PERM_DENIED(
                        "you do not have permission to copy private calendar item from the current folder");
            if (!privateAccessDest)
                throw ServiceException.PERM_DENIED(
                        "you do not have permission to copy private calendar item to the target folder");
        }
        return super.copy(folder, id, parentId);
    }

    @Override
    void rename(String name, Folder target) throws ServiceException {
        if (!isPublic()) {
            boolean privateAccessSrc = canAccess(ACL.RIGHT_PRIVATE);
            boolean privateAccessDest = target.canAccess(ACL.RIGHT_PRIVATE);
            if (!privateAccessSrc)
                throw ServiceException.PERM_DENIED(
                        "you do not have permission to rename/move private calendar item from the current folder");
            if (!privateAccessDest)
                throw ServiceException.PERM_DENIED(
                        "you do not have permission to move private calendar item to the target folder");
        }
        super.rename(name, target);
    }

    @Override
    boolean canAccess(short rightsNeeded, Account authuser, boolean asAdmin) throws ServiceException {
        return canAccess(rightsNeeded, authuser, asAdmin, false);
    }

    private boolean canAccess(short rightsNeeded, Account authuser, boolean asAdmin, boolean requirePrivateCheck)
    throws ServiceException {
        if (requirePrivateCheck && !isPublic()) {
            // If write/delete was requested on a private item, check private permission first.
            short writeAccess = ACL.RIGHT_WRITE | ACL.RIGHT_DELETE;
            if ((rightsNeeded & writeAccess) != 0) {
                if (!super.canAccess(ACL.RIGHT_PRIVATE, authuser, asAdmin))
                    return false;
            }
        }
        return super.canAccess(rightsNeeded, authuser, asAdmin);
    }

    // If we're adding a private invite, we must make sure the authenticated user has permission to
    // access private data.  If we're adding a public invite but the appointment currently has
    // some private data, private access permission is not needed as long as the instance(s) being
    // updated aren't currently private.
    private boolean requirePrivateCheck(Invite newInvite) {
        if (!newInvite.isPublic()) {
            // adding a private invite
            return true;
        }
        if (!isPublic()) {
            RecurId rid = newInvite.getRecurId();
            // If canceling whole series, requester must have private access permission.
            if (rid == null && newInvite.isCancel())
                return true;
            Invite current = getInvite(rid);
            // If no matching recurrence-id was found, look at the current series.
            if (current == null && rid != null)
                current = getInvite((RecurId) null);
            if (current != null && !current.isPublic()) {
                // updating a currently private invite to public
                return true;
            } else {
                // no matching rid found, or current is public
                return false;
            }
        } else {
            // Both old and new are public.
            return false;
        }
    }

    /**
     * Break up a multipart/digest blob into separate MimeMessages keyed by InvId header value.
     * @param digestStream
     * @return
     * @throws MessagingException
     */
    public static Map<Integer, MimeMessage> decomposeBlob(byte[] digestBlob)
    throws ServiceException {
        Map<Integer, MimeMessage> map = new HashMap<Integer, MimeMessage>();
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(digestBlob);
            FixedMimeMessage digestMm = new FixedMimeMessage(JMSession.getSession(), bais);

            // It should be multipart/digest.
            MimeMultipart mmp;
            Object obj = digestMm.getContent();
            if (obj instanceof MimeMultipart)
                mmp = (MimeMultipart) obj;
            else
                throw ServiceException.FAILURE(
                        "Expected MimeMultipart, but got " + obj.getClass().getName() + ": " +
                        obj.toString(), null);

            int numParts = mmp.getCount();
            for (int i = 0; i < numParts; i++) {
                MimeBodyPart mbp = (MimeBodyPart) mmp.getBodyPart(i);
                int invId = 0;
                String[] hdrs = mbp.getHeader("invId");
                if (hdrs != null && hdrs.length > 0) {
                    invId = Integer.parseInt(hdrs[0]);
                    MimeMessage mm;
                    Object objMbp = mbp.getContent();
                    if (objMbp instanceof MimeMessage)
                        mm = (MimeMessage) objMbp;
                    else
                        throw ServiceException.FAILURE(
                                "Expected MimeMessage, but got " + objMbp.getClass().getName() + ": " +
                                objMbp.toString(), null);
                    map.put(invId, mm);
                }
            }
        } catch (MessagingException e) {
            throw ServiceException.FAILURE("Can't parse calendar item blob", e);
        } catch (IOException e) {
            throw ServiceException.FAILURE("Can't parse calendar item blob", e);
        } catch (NumberFormatException e) {
            throw ServiceException.FAILURE("Can't parse calendar item blob", e);
        }
        return map;
    }

    public static boolean isAcceptableInvite(Account acct, CalendarPartInfo cpi) {
        if (cpi.wasForwarded &&
            !acct.getBooleanAttr(Provisioning.A_zimbraPrefCalendarAllowForwardedInvite, true))
            return false;
        return true;
    }

    @Override
    int getMaxRevisions() throws ServiceException {
        return getAccount().getIntAttr(Provisioning.A_zimbraCalendarMaxRevisions, 1);
    }

    public void snapshotRevision() throws ServiceException {
        addRevision(false);
    }

    @Override
    protected boolean trackUserAgentInMetadata() {
        return true;
    }
}
