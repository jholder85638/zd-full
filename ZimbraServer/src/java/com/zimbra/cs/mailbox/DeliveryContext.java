/*
 * 
 */

/*
 * Created on 2005. 6. 21.
 */
package com.zimbra.cs.mailbox;

import java.util.List;

import com.zimbra.cs.store.Blob;
import com.zimbra.cs.store.MailboxBlob;

/**
 * Class that facilitates blob file sharing when delivering a message to
 * multiple recipients or when a message is copied upon delivery to one
 * or more folders within the same mailbox due to filter rules.
 * 
 * This class is used to carry information across multiple calls to
 * Mailbox.addMessage() for a single message being delivered.
 */
public class DeliveryContext {

    private boolean mShared;
    private Blob mIncomingBlob;
    private MailboxBlob mMailboxBlob;
    private List<Integer> mMailboxIdList;
    private boolean mIsFirst = true;

    /**
     * Constructor for non-shared case
     */
    public DeliveryContext() {
    	mShared = false;
        mMailboxBlob = null;
        mMailboxIdList = null;
    }

    /**
     * Constructor for shared/non-shared cases
     * @param shared
     * @param mboxIdList list of ID of mailboxes being delivered to
     */
    public DeliveryContext(boolean shared, List<Integer> mboxIdList) {
    	mShared = shared;
        mMailboxBlob = null;
        mMailboxIdList = mboxIdList;
    }

    public boolean getShared() {
    	return mShared;
    }

    public List<Integer> getMailboxIdList() {
    	return mMailboxIdList;
    }

    public Blob getIncomingBlob() {
        return mIncomingBlob;
    }
    
    public DeliveryContext setIncomingBlob(Blob blob) {
        mIncomingBlob = blob;
        return this;
    }
    
    public MailboxBlob getMailboxBlob() {
    	return mMailboxBlob;
    }

    public DeliveryContext setMailboxBlob(MailboxBlob mailboxBlob) {
    	mMailboxBlob = mailboxBlob;
        return this;
    }

    /**
     * Tells the caller if this is the first mailbox being delivered to.
     */
    public boolean isFirst() {
        return mIsFirst;
    }
    
    public void setFirst(boolean isFirst) {
        mIsFirst = isFirst;
    }
}
