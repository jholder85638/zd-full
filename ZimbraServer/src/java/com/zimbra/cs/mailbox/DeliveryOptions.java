/*
 * 
 */

package com.zimbra.cs.mailbox;

import com.zimbra.cs.mailbox.MailItem.CustomMetadata;

/**
 * Specifies options for new messages created with {@link Mailbox#addMessage}.
 */
public class DeliveryOptions {

    private int mFolderId = -1;
    private boolean mNoICal = false;
    private int mFlags = 0;
    private String mTagString = null;
    private int mConversationId = Mailbox.ID_AUTO_INCREMENT;
    private String mRecipientEmail = ":API:";
    private Message.DraftInfo mDraftInfo = null;
    private CustomMetadata mCustomMetadata = null;
    
    public int getFolderId() { return mFolderId; }
    public boolean getNoICal() { return mNoICal; }
    public int getFlags() { return mFlags; }
    public String getTagString() { return mTagString; }
    public int getConversationId() { return mConversationId; }
    public String getRecipientEmail() { return mRecipientEmail; }
    public Message.DraftInfo getDraftInfo() { return mDraftInfo; }
    public CustomMetadata getCustomMetadata() { return mCustomMetadata; }
    
    public DeliveryOptions setFolderId(int folderId) {
        mFolderId = folderId;
        return this;
    }
    
    public DeliveryOptions setNoICal(boolean noICal) {
        mNoICal = noICal;
        return this;
    }
    
    public DeliveryOptions setFlags(int flags) {
        mFlags = flags;
        return this;
    }
    
    public DeliveryOptions setTagString(String tagString) {
        mTagString = tagString;
        return this;
    }
    
    public DeliveryOptions setConversationId(int conversationId) {
        mConversationId = conversationId;
        return this;
    }
    
    public DeliveryOptions setRecipientEmail(String recipientEmail) {
        mRecipientEmail = recipientEmail;
        return this;
    }
    
    public DeliveryOptions setDraftInfo(Message.DraftInfo draftInfo) {
        mDraftInfo = draftInfo;
        return this;
    }
    
    public DeliveryOptions setCustomMetadata(CustomMetadata customMetadata) {
        mCustomMetadata = customMetadata;
        return this;
    }
}
