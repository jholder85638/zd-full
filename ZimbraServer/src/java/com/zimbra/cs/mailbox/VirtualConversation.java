/*
 * 
 */

/*
 * Created on Jun 14, 2005
 */
package com.zimbra.cs.mailbox;

import java.util.ArrayList;
import java.util.List;

import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.db.DbMailItem;
import com.zimbra.cs.index.SortBy;
import com.zimbra.cs.mailbox.MailItem.CustomMetadata.CustomMetadataList;
import com.zimbra.cs.session.PendingModifications.Change;

public class VirtualConversation extends Conversation {

    VirtualConversation(Mailbox mbox, Message msg) throws ServiceException {
        this(mbox, wrapMessage(msg));
    }

    VirtualConversation(Mailbox mbox, UnderlyingData data) throws ServiceException {
        super(mbox, data);
        if (mData.type != TYPE_VIRTUAL_CONVERSATION)
            throw new IllegalArgumentException();
    }


    public int getMessageId() {
        return -mId;
    }

    @Override boolean loadSenderList() throws ServiceException {
        mSenderList = new SenderList(getMessage());
        return false;
    }

    @Override SenderList recalculateMetadata(List<Message> msgs) throws ServiceException {
        Message msg = msgs.get(0);
        mData = wrapMessage(msg);
        mExtendedData = MetadataCallback.duringConversationAdd(null, msg);
        return getSenderList();
    }


    Message getMessage() throws ServiceException {
        return mMailbox.getMessageById(getMessageId());
    }

    @Override List<Message> getMessages(SortBy sort) throws ServiceException {
        List<Message> msgs = new ArrayList<Message>(1);
        msgs.add(getMessage());
        return msgs;
    }


    static VirtualConversation create(Mailbox mbox, Message msg) throws ServiceException {
        VirtualConversation vconv = new VirtualConversation(mbox, msg);
        mbox.markItemCreated(vconv);
        return vconv;
    }

    private static UnderlyingData wrapMessage(Message msg) {
        CustomMetadataList extended = MetadataCallback.duringConversationAdd(null, msg);

        UnderlyingData data = new UnderlyingData();
        data.id          = -msg.getId();
        data.type        = TYPE_VIRTUAL_CONVERSATION;
        data.folderId    = Mailbox.ID_FOLDER_CONVERSATIONS;
        data.subject     = DbMailItem.truncateSubjectToMaxAllowedLength(msg.getSubject());
        data.date        = (int) (msg.getDate() / 1000);
        data.modMetadata = msg.getSavedSequence();
        data.modContent  = msg.getSavedSequence();
        data.size        = 1;
        data.unreadCount = msg.getUnreadCount();
        data.flags       = msg.getInternalFlagBitmask();
        data.tags        = msg.getTagBitmask();
        data.metadata    = encodeMetadata(DEFAULT_COLOR_RGB, 1, extended, new SenderList(msg));
        return data;
    }

    @Override void open(String hash) throws ServiceException {
        DbMailItem.openConversation(hash, getMessage());
    }

    @Override void close(String hash) throws ServiceException {
        DbMailItem.closeConversation(hash, getMessage());
    }

    @Override void alterTag(Tag tag, boolean add) throws ServiceException {
        getMessage().alterTag(tag, add);
    }

    @Override protected void inheritedTagChanged(Tag tag, boolean add) throws ServiceException {
        if (tag == null || add == isTagged(tag))
            return;
        markItemModified(tag instanceof Flag ? Change.MODIFIED_FLAGS : Change.MODIFIED_TAGS);
        tagChanged(tag, add);
    }

    @Override protected void inheritedCustomDataChanged(Message msg, CustomMetadata custom) {
        markItemModified(Change.MODIFIED_METADATA);
        mExtendedData = MetadataCallback.duringConversationAdd(null, msg);
    }

    @Override void addChild(MailItem child) throws ServiceException {
        throw MailServiceException.CANNOT_PARENT();
    }

    @Override void removeChild(MailItem child) throws ServiceException {
        if (child.getId() != getMessageId())
            throw MailServiceException.IS_NOT_CHILD();
        markItemDeleted();
        mMailbox.uncache(this);
    }
}
