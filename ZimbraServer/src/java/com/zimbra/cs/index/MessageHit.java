/*
 * 
 */

package com.zimbra.cs.index;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.document.DateTools;
import org.apache.lucene.document.Document;

import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.MailItem;
import com.zimbra.cs.mailbox.MailboxManager;
import com.zimbra.cs.mailbox.Message;
import com.zimbra.cs.mailbox.Tag;
import com.zimbra.cs.mime.ParsedAddress;

import com.zimbra.common.util.Log;
import com.zimbra.common.util.LogFactory;
import com.zimbra.common.util.ZimbraLog;

/**
 * Efficient Read-access to a {@link Message} returned from a query. APIs mirror
 * the APIs on {@link Message}, but are read-only. The real archive.mailbox.Message
 * can be retrieved, but this should only be done if write-access is necessary.
 *
 * @since Oct 15, 2004
 * @author tim
 */
public final class MessageHit extends ZimbraHit {

    private static Log mLog = LogFactory.getLog(MessageHit.class);

    private Document mDoc = null;
    private Message mMessage = null;
    private List<MessagePartHit> mMatchedParts = null;
    private int mConversationId = 0;
    private int mMessageId = 0;
    private ConversationHit mConversationHit = null;

    MessageHit(ZimbraQueryResultsImpl results, Mailbox mbx, int mailItemId, Document doc, Message message) {
        super(results, mbx);
        assert(mailItemId != 0);
        mMessageId = mailItemId;
        mDoc = doc;
        mMessage = message;
    }

    int getFolderId() throws ServiceException {
        return getMessage().getFolderId();
    }

    @Override
    public int getConversationId() throws ServiceException {
        if (mConversationId == 0) {
            mConversationId = getMessage().getConversationId();
        }
        return mConversationId;
    }

    @Override
    public long getDate() throws ServiceException {
        if (mCachedDate == -1) {
            if (mMessage == null && mDoc != null) {
                String sortDate = mDoc.get(LuceneFields.L_SORT_DATE);
                if (sortDate != null) {
                    try {
                        mCachedDate = DateTools.stringToTime(sortDate);
                        if (mCachedDate > 0) {
                            return mCachedDate;
                        } else { // fall back to DB date
                            ZimbraLog.index_search.warn("Index corrupted, re-indexing is recommended: id=%d,date=%s",
                                    mMessageId, sortDate);
                        }
                    } catch (ParseException e) { // fall back to DB date
                        ZimbraLog.index_search.warn("Index corrupted, re-indexing is recommended: id=%d,date=%s",
                                mMessageId, sortDate, e);
                    }
                }
            }
            mCachedDate = getMessage().getDate();
            if (mCachedDate <= 0) {
                ZimbraLog.index_search.error("Invalid sort-date from DB id=%d,date=%d", mMessageId, mCachedDate);
            }
        }
        return mCachedDate;
    }

    public void addPart(MessagePartHit part) {
        if (mMatchedParts == null)
            mMatchedParts = new ArrayList<MessagePartHit>();

        if (!mMatchedParts.contains(part)) {
            mMatchedParts.add(part);
        }
    }

    public List<MessagePartHit> getMatchedMimePartNames() {
        return mMatchedParts;
    }

    @Override
    public int getItemId() {
        return mMessageId;
    }

    public byte getItemType() {
        return MailItem.TYPE_MESSAGE;
    }

    @Override
    public String toString() {
        int convId = 0;
        boolean convIdUnknown = false;
        try {
            // don't load the message from the DB just to get the convid!
            if (mConversationId == 0 && mMessage == null) {
                convIdUnknown = true;
            } else {
                convId = getConversationId();
            }
        } catch (ServiceException e) {
            e.printStackTrace();
        }
        long size = 0;
        try {
            if (mCachedSize == -1 && mMessage == null) {
                size = -1;
            } else {
                size = getSize();
            }
        } catch (ServiceException e) {
            e.printStackTrace();
        }
        if (mMessage == null) {
            return "MS: " + this.getItemId();
        } else {
            return "MS: " + super.toString() +
                " C" + (convIdUnknown ? "?" : convId) +
                " M" + Integer.toString(getItemId()) +
                " S="+size;
        }
    }

    @Override
    public long getSize() throws ServiceException {
        if (mCachedSize == -1) {
            if (mMessage == null && mDoc != null) {
                String sizeStr = mDoc.get(LuceneFields.L_SORT_SIZE);
                if (sizeStr != null) {
                    mCachedSize = Long.parseLong(sizeStr);
                    return mCachedSize;
                }
            }
            mCachedSize = getMessage().getSize();
        }
        return mCachedSize;
    }

    public boolean isTagged(Tag tag) throws ServiceException {
        return getMessage().isTagged(tag);
    }

    @Override
    void setItem(MailItem item) {
        mMessage = (Message) item;
    }

    @Override
    boolean itemIsLoaded() {
        return mMessage != null;
    }

    @Override
    public MailItem getMailItem() throws ServiceException {
        return getMessage();
    }

    public Message getMessage() throws ServiceException {
        if (mMessage == null) {
            Mailbox mbox = MailboxManager.getInstance().getMailboxById(
                    getMailbox().getId());
            int messageId = getItemId();
            try {
                mMessage = mbox.getMessageById(null, messageId);
            } catch (ServiceException e) {
                mLog.error("Error getting message id=" + messageId +
                        " from mailbox " + mbox.getId(), e);
                e.printStackTrace();
                throw e;
            }
        }
        return mMessage;
    }

    @Override
    public String getSubject() throws ServiceException {
        if (mCachedSubj == null) {
            mCachedSubj = getMessage().getSortSubject();
        }
        return mCachedSubj;
    }

    @Override
    public String getName() throws ServiceException {
        if (mCachedName == null) {
            mCachedName = getSender();
        }
        return mCachedName;
    }

    public long getDateHeader() throws ServiceException {
        if (mMessage == null && mDoc != null) {
            String dateStr = mDoc.get(LuceneFields.L_SORT_DATE);
            if (dateStr != null) {
                try {
                    return DateTools.stringToTime(dateStr);
                } catch (ParseException e) {
                    return 0;
                }
            } else {
                return 0;
            }
        }
        return getMessage().getDate();
    }

    public String getSender() throws ServiceException {
        return new ParsedAddress(getMessage().getSender()).getSortString();
    }

    /**
     * @return a ConversationResult corresponding to this message's conversation
     */
    public ConversationHit getConversationResult() throws ServiceException {
        if (mConversationHit == null) {
            Integer cid = new Integer(getConversationId());
            mConversationHit = getResults().getConversationHit(getMailbox(), cid);
            mConversationHit.addMessageHit(this);
        }
        return mConversationHit;
    }
}
