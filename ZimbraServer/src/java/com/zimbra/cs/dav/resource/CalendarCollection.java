/*
 * 
 */
package com.zimbra.cs.dav.resource;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.mail.internet.MimeMessage;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.dom4j.QName;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.account.Provisioning.AccountBy;
import com.zimbra.cs.dav.DavContext;
import com.zimbra.cs.dav.DavElements;
import com.zimbra.cs.dav.DavException;
import com.zimbra.cs.dav.DavProtocol;
import com.zimbra.cs.dav.caldav.CalDavUtils;
import com.zimbra.cs.dav.caldav.TimeRange;
import com.zimbra.cs.dav.property.CalDavProperty;
import com.zimbra.cs.dav.property.ResourceProperty;
import com.zimbra.cs.dav.service.DavServlet;
import com.zimbra.cs.dav.service.method.Delete;
import com.zimbra.cs.dav.service.method.Get;
import com.zimbra.cs.fb.FreeBusy;
import com.zimbra.cs.fb.FreeBusyQuery;
import com.zimbra.cs.mailbox.CalendarItem;
import com.zimbra.cs.mailbox.Folder;
import com.zimbra.cs.mailbox.MailItem;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.MailboxManager;
import com.zimbra.cs.mailbox.CalendarItem.ReplyInfo;
import com.zimbra.cs.mailbox.Mailbox.SetCalendarItemData;
import com.zimbra.cs.mailbox.calendar.IcalXmlStrMap;
import com.zimbra.cs.mailbox.calendar.Invite;
import com.zimbra.cs.mailbox.calendar.RecurId;
import com.zimbra.cs.mailbox.calendar.ZAttendee;
import com.zimbra.cs.mailbox.calendar.ZCalendar;
import com.zimbra.cs.mailbox.calendar.ZOrganizer;
import com.zimbra.cs.mailbox.calendar.ZCalendar.ICalTok;
import com.zimbra.cs.mailbox.calendar.ZCalendar.ZComponent;
import com.zimbra.cs.mailbox.calendar.ZCalendar.ZVCalendar;
import com.zimbra.cs.mailbox.calendar.cache.CtagInfo;
import com.zimbra.cs.mime.ParsedMessage;
import com.zimbra.cs.util.AccountUtil.AccountAddressMatcher;
import com.zimbra.common.util.L10nUtil;
import com.zimbra.common.util.L10nUtil.MsgKey;
import com.zimbra.common.mime.MimeConstants;

/**
 * draft-dusseault-caldav-15 section 4.2
 * 
 * @author jylee
 *
 */
public class CalendarCollection extends Collection {

    public CalendarCollection(DavContext ctxt, Folder f) throws DavException, ServiceException {
        super(ctxt, f);
        Account acct = f.getAccount();

        if (f.getDefaultView() == MailItem.TYPE_APPOINTMENT || f.getDefaultView() == MailItem.TYPE_TASK)
            addResourceType(DavElements.E_CALENDAR);

        if (f.getId() == Provisioning.getInstance().getLocalServer().getCalendarCalDavDefaultCalendarId())
            addResourceType(DavElements.E_DEFAULT_CALENDAR);

        // the display name can be a user friendly string like "John Smith's Calendar".
        // but the problem is the name may be too long to fit into the field in UI.
        Locale lc = acct.getLocale();
        String description = L10nUtil.getMessage(MsgKey.caldavCalendarDescription, lc, acct.getAttr(Provisioning.A_displayName), f.getName());
        ResourceProperty desc = new ResourceProperty(DavElements.E_CALENDAR_DESCRIPTION);
        desc.setMessageLocale(lc);
        desc.setStringValue(description);
        desc.setVisible(false);
        addProperty(desc);
        addProperty(CalDavProperty.getSupportedCalendarComponentSet(f.getDefaultView()));
        addProperty(CalDavProperty.getSupportedCalendarData());
        addProperty(CalDavProperty.getSupportedCollationSet());
        addProperty(CalDavProperty.getCalendarTimezone(acct));

        mCtag = CtagInfo.makeCtag(f);
        setProperty(DavElements.E_GETCTAG, mCtag);

        addProperty(getIcalColorProperty());
        setProperty(DavElements.E_ALTERNATE_URI_SET, null, true);
        setProperty(DavElements.E_GROUP_MEMBER_SET, null, true);
        setProperty(DavElements.E_GROUP_MEMBERSHIP, null, true);

        // remaining recommented attributes: calendar-timezone, max-resource-size,
        // min-date-time, max-date-time, max-instances, max-attendees-per-instance,
        //
    }

    /* Returns all the appoinments stored in the calendar as DavResource. */
    @Override
    public java.util.Collection<DavResource> getChildren(DavContext ctxt) throws DavException {
        return getChildren(ctxt, null);
    }

    protected Map<String,DavResource> mAppts;
    protected boolean mMetadataOnly;
    protected String mCtag;

    /* Returns all the appointments specified in hrefs */
    public java.util.Collection<DavResource> getChildren(DavContext ctxt, TimeRange range) throws DavException {
        Map<String,DavResource> requestedAppts = null;
        boolean fetchAppts = 
            range != null || // ranged request
            mAppts == null; // hasn't fetched before
        if (fetchAppts) {
            try {
                requestedAppts = getAppointmentMap(ctxt, range);
                // cache the full appt list
                if (range == null && needCalendarData(ctxt))
                    mAppts = requestedAppts;
            } catch (ServiceException se) {
                ZimbraLog.dav.error("can't get calendar items", se);
                return Collections.emptyList();
            }
        } else {
            requestedAppts = mAppts;
        }
        return requestedAppts.values();
    }

    protected Map<String,String> getUidToHrefMap(java.util.Collection<String> hrefs) {
        HashMap<String,String> uidmap = new HashMap<String,String>();
        for (String href : hrefs) {
            try {
                String hrefDecoded = URLDecoder.decode(href, "UTF-8");
                int start = hrefDecoded.lastIndexOf('/') + 1;
                int end = hrefDecoded.lastIndexOf(".ics");
                if (start > 0 && end > 0 && end > start)
                    uidmap.put(hrefDecoded.substring(start, end), href);
            } catch (IOException e) {
                ZimbraLog.dav.warn("can't decode href "+href, e);
            }
        }
        return uidmap;
    }

    // see if the request is only for the metadata, for which case we have a shortcut
    // to prevent scanning and improve performance.
    private static final HashSet<QName> sMetaProps;
    static {
        sMetaProps = new HashSet<QName>();
        sMetaProps.add(DavElements.E_GETETAG);
        sMetaProps.add(DavElements.E_RESOURCETYPE);
        sMetaProps.add(DavElements.E_DISPLAYNAME);
    }
    protected boolean needCalendarData(DavContext ctxt) throws DavException {
        String method = ctxt.getRequest().getMethod();
        if (method.equals(Get.GET) || method.equals(Delete.DELETE))
            return true;
        for (QName prop : ctxt.getRequestProp().getProps())
            if (!sMetaProps.contains(prop))
                return true;
        return false;
    }
    protected Mailbox getCalendarMailbox(DavContext ctxt) throws ServiceException, DavException {
        return getMailbox(ctxt);
    }
    protected Map<String,DavResource> getAppointmentMap(DavContext ctxt, TimeRange range) throws ServiceException, DavException {
        Mailbox mbox = getCalendarMailbox(ctxt);

        HashMap<String,DavResource> appts = new HashMap<String,DavResource>();
        ctxt.setCollectionPath(getUri());
        if (range == null)
            range = new TimeRange(getOwner());
        long start = range.getStart();
        long end = range.getEnd();
        start = start == Long.MIN_VALUE ? -1 : start;
        end = end == Long.MAX_VALUE ? -1 : end;
        if (!needCalendarData(ctxt)) {
            ZimbraLog.dav.debug("METADATA only");
            mMetadataOnly = true;
            for (CalendarItem.CalendarMetadata item : mbox.getCalendarItemMetadata(ctxt.getOperationContext(), getId(), start, end))
                appts.put(item.uid, new CalendarObject.LightWeightCalendarObject(getUri(), getOwner(), item));
        } else {
            for (CalendarItem calItem : mbox.getCalendarItemsForRange(ctxt.getOperationContext(), start, end, getId(), null))
                appts.put(calItem.getUid(), new CalendarObject.LocalCalendarObject(ctxt, calItem));
        }
        return appts;
    }

    public java.util.Collection<DavResource> getAppointmentsByUids(DavContext ctxt, List<String> hrefs) throws ServiceException, DavException {
        Map<String,String> uidmap = getUidToHrefMap(hrefs);
        Mailbox mbox = getCalendarMailbox(ctxt);

        ArrayList<DavResource> appts = new ArrayList<DavResource>();
        ctxt.setCollectionPath(getUri());
        Map<String,CalendarItem> calItems = mbox.getCalendarItemsByUid(ctxt.getOperationContext(), new ArrayList<String>(uidmap.keySet()));
        for (String uid : calItems.keySet()) {
            CalendarItem calItem = calItems.get(uid);
            if (calItem == null)
                appts.add(new DavResource.InvalidResource(uidmap.get(uid), getOwner()));
            else
                appts.add(new CalendarObject.LocalCalendarObject(ctxt, calItem));
        }
        return appts;
    }

    private String findSummary(ZVCalendar cal) {
        Iterator<ZComponent> iter = cal.getComponentIterator();
        while (iter.hasNext()) {
            ZComponent comp = iter.next();
            String summary = comp.getPropVal(ICalTok.SUMMARY, null);
            if (summary != null)
                return summary;
        }
        return "calendar event";
    }

    private String findEventUid(List<Invite> invites) throws DavException {
        String uid = null;
        LinkedList<Invite> inviteList = new LinkedList<Invite>();
        for (Invite i : invites) {
            byte type = i.getItemType();
            if (type == MailItem.TYPE_APPOINTMENT || type == MailItem.TYPE_TASK) {
                if (uid != null && uid.compareTo(i.getUid()) != 0)
                    throw new DavException("too many events", HttpServletResponse.SC_BAD_REQUEST, null);
                uid = i.getUid();
            }
            if (i.isRecurrence())
                inviteList.addFirst(i);
            else
                inviteList.addLast(i);
        }
        if (uid == null)
            throw new DavException("no event in the request", HttpServletResponse.SC_BAD_REQUEST, null);
        invites.clear();
        invites.addAll(inviteList);
        return uid;
    }

    /* creates an appointment sent in PUT request in this calendar. */
    @Override
    public DavResource createItem(DavContext ctxt, String name) throws DavException, IOException {
        if (!ctxt.getUpload().getContentType().startsWith(MimeConstants.CT_TEXT_CALENDAR) ||
                ctxt.getUpload().getSize() <= 0)
            throw new DavException("empty request", HttpServletResponse.SC_BAD_REQUEST, null);

        /*
         * some of the CalDAV clients do not behave very well when it comes to
         * etags.
         * 
         * chandler doesn't set User-Agent header, doesn't understand 
         * If-None-Match or If-Match headers.
         * 
         * evolution 2.8 always sets If-None-Match although we return etag in REPORT.
         * 
         * ical correctly understands etag and sets If-Match for existing etags, but
         * does not use If-None-Match for new resource creation.
         */
        HttpServletRequest req = ctxt.getRequest();
        String etag = req.getHeader(DavProtocol.HEADER_IF_MATCH);
        boolean useEtag = (etag != null);

        //String noneMatch = req.getHeader(DavProtocol.HEADER_IF_NONE_MATCH);

        if (name.endsWith(CalendarObject.CAL_EXTENSION))
            name = name.substring(0, name.length()-CalendarObject.CAL_EXTENSION.length());

        Provisioning prov = Provisioning.getInstance();
        try {
            String user = ctxt.getUser();
            Account account = prov.get(AccountBy.name, user);
            if (account == null)
                throw new DavException("no such account "+user, HttpServletResponse.SC_NOT_FOUND, null);

            InputStream is = ctxt.getUpload().getInputStream();
            List<Invite> invites;
            try {
                ZCalendar.ZVCalendar vcalendar = ZCalendar.ZCalendarBuilder.build(is, MimeConstants.P_CHARSET_UTF8);
                CalDavUtils.removeAttendeeForOrganizer(vcalendar);  // Apple iCal fixup
                if (ctxt.isIcalClient()) // Apple iCal fixup for todos
                    CalDavUtils.adjustPercentCompleteForToDos(vcalendar);
                invites = Invite.createFromCalendar(account,
                        findSummary(vcalendar), 
                        vcalendar, 
                        true);
            } catch (ServiceException se) {
                throw new DavException("cannot parse ics", HttpServletResponse.SC_BAD_REQUEST, se);
            }

            String uid = findEventUid(invites);
            if (!uid.equals(name)) {
                // because we are keying off the URI, we don't have
                // much choice except to use the UID of VEVENT for calendar item URI.
                // Evolution doesn't use UID as the URI, so we'll force it
                // by issuing redirect to the URI we want it to be at.
                StringBuilder url = new StringBuilder();
                url.append(DavServlet.getDavUrl(user)).append(mPath).append("/").append(uid).append(CalendarObject.CAL_EXTENSION);
                ctxt.getResponse().sendRedirect(url.toString());
                throw new DavException("wrong url", HttpServletResponse.SC_MOVED_PERMANENTLY, null);
            }
            Mailbox mbox = MailboxManager.getInstance().getMailboxByAccount(account);
            CalendarItem calItem = mbox.getCalendarItemByUid(ctxt.getOperationContext(), name);
            if (calItem == null && useEtag)
                throw new DavException("event not found", HttpServletResponse.SC_NOT_FOUND, null);

            boolean isNewItem = true;
            if (useEtag) {
                String itemEtag = MailItemResource.getEtag(calItem);
                if (!itemEtag.equals(etag))
                    throw new DavException("CalDAV client has stale event: event has different etag ("+itemEtag+") vs "+etag, HttpServletResponse.SC_CONFLICT);
                isNewItem = false;
            }

            // prepare to call Mailbox.setCalendarItem()
            int flags = 0; long tags = 0; List<ReplyInfo> replies = null;
            if (calItem != null) {
                flags = calItem.getFlagBitmask();
                tags = calItem.getTagBitmask();
                replies = calItem.getAllReplies();
            }
            SetCalendarItemData scidDefault = new SetCalendarItemData();
            SetCalendarItemData scidExceptions[] = null;
            if (invites.size() > 1)
                scidExceptions = new SetCalendarItemData[invites.size() - 1];

            int idxExceptions = 0;
            boolean first = true;
            for (Invite i : invites) {
                // check for valid uid.
                if (i.getUid() == null)
                    i.setUid(uid);
                // check for valid organizer field.
                if (i.hasOrganizer() || i.hasOtherAttendees()) {
                    ZOrganizer org = i.getOrganizer();
                    // if ORGANIZER field is unset, set the field value with authUser's email addr.
                    if (org == null) {
                        org = new ZOrganizer(ctxt.getAuthAccount().getName(), null);
                        i.setOrganizer(org);
                    }
                    /*
                     * this hack was to work around iCal setting ORGANIZER field
                     * with principalURL.  iCal seemed to have fixed that bug.
                     * 
					String addr = i.getOrganizer().getAddress();
					String newAddr = getAddressFromPrincipalURL(addr);
					if (!addr.equals(newAddr)) {
						i.setOrganizer(new ZOrganizer(newAddr, null));
						ZProperty href = null;
						Iterator<ZProperty> xprops = i.xpropsIterator();
						while (xprops.hasNext()) {
							href = xprops.next();
							if (href.getName().equals(DavElements.ORGANIZER_HREF))
								break;
							href = null;
						}
						if (href == null) {
							href = new ZProperty(DavElements.ORGANIZER_HREF);
							i.addXProp(href);
						}
						href.setValue(addr);
					}
                     */
                }
                // Carry over the MimeMessage/ParsedMessage to preserve any attachments.
                // CalDAV clients don't support attachments, and on edit we have to either
                // retain existing attachments or drop them.  Retaining is better.
                ParsedMessage oldPm = null;
                if (calItem != null) {
                    Invite oldInv = calItem.getInvite(i.getRecurId());
                    if (oldInv == null && i.hasRecurId()) {
                        // It's a new exception instance.  Inherit from series.
                        oldInv = calItem.getInvite((RecurId) null);
                    }
                    if (oldInv != null) {
                        MimeMessage mmInv = calItem.getSubpartMessage(oldInv.getMailItemId());
                        oldPm = mmInv != null ? new ParsedMessage(mmInv, false) : null;
                    }
                }
                if (first) {
                    scidDefault.mInv = i;
                    scidDefault.mPm = oldPm;
                    first = false;
                } else {
                    SetCalendarItemData scid = new SetCalendarItemData();
                    scid.mInv = i;
                    scid.mPm = oldPm;
                    scidExceptions[idxExceptions++] = scid;
                }

                // For attendee case, update replies list with matching ATTENDEE from the invite.
                if (!i.isOrganizer() && replies != null) {
                    ZAttendee at = i.getMatchingAttendee(account);
                    if (at != null) {
                        AccountAddressMatcher acctMatcher = new AccountAddressMatcher(account);
                        ReplyInfo newReply = null;
                        for (Iterator<ReplyInfo> replyIter = replies.iterator(); replyIter.hasNext(); ) {
                            ReplyInfo reply = replyIter.next();
                            if (acctMatcher.matches(reply.getAttendee().getAddress())) {
                                RecurId ridR = reply.getRecurId(), ridI = i.getRecurId();
                                if ((ridR == null && ridI == null) || (ridR != null && ridR.equals(ridI))) {  // matching RECURRENCE-ID
                                    // No need to compare SEQUENCE and DTSTAMP of existing reply and new invite.
                                    // We're just going to take what the caldav client sent, even if it's older than the existing reply.
                                    replyIter.remove();
                                    if (!IcalXmlStrMap.PARTSTAT_NEEDS_ACTION.equalsIgnoreCase(at.getPartStat())) {
                                        newReply = new ReplyInfo(at, i.getSeqNo(), i.getDTStamp(), ridI);
                                    }
                                    break;
                                }
                            }
                        }
                        if (newReply != null) {
                            replies.add(newReply);
                        }
                    }
                }
            }
            calItem = mbox.setCalendarItem(ctxt.getOperationContext(), mId, flags, tags,
                    scidDefault, scidExceptions, replies, CalendarItem.NEXT_ALARM_KEEP_CURRENT);
            return new CalendarObject.LocalCalendarObject(ctxt, calItem, isNewItem);
        } catch (ServiceException e) {
            throw new DavException("cannot create icalendar item", HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e);
        }
    }

    /* Returns iCalalendar (RFC 2445) representation of freebusy report for specified time range. */
    public String getFreeBusyReport(DavContext ctxt, TimeRange range) throws ServiceException, DavException {
        Mailbox mbox = getCalendarMailbox(ctxt);
        FreeBusy fb = mbox.getFreeBusy(ctxt.getOperationContext(), range.getStart(), range.getEnd(), FreeBusyQuery.CALENDAR_FOLDER_ALL);
        return fb.toVCalendar(FreeBusy.Method.REPLY, ctxt.getAuthAccount().getName(), mbox.getAccount().getName(), null);
    }
}
