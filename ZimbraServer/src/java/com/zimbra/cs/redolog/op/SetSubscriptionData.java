/*
 * 
 */
/*
 * Created on Nov 12, 2005
 */
package com.zimbra.cs.redolog.op;

import java.io.IOException;

import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.MailboxManager;
import com.zimbra.cs.redolog.RedoLogInput;
import com.zimbra.cs.redolog.RedoLogOutput;

public class SetSubscriptionData extends RedoableOp {

    private int mFolderId;
    private long mLastItemDate;
    private String mLastItemGuid;

    public SetSubscriptionData() {
        mFolderId = Mailbox.ID_AUTO_INCREMENT;
        mLastItemGuid = "";
    }

    public SetSubscriptionData(int mailboxId, int folderId, long date, String guid) {
        setMailboxId(mailboxId);
        mFolderId = folderId;
        mLastItemDate = date;
        mLastItemGuid = guid == null ? "" : guid;
    }

    @Override public int getOpCode() {
        return OP_SET_SUBSCRIPTION_DATA;
    }

    @Override protected String getPrintableData() {
        StringBuffer sb = new StringBuffer("id=").append(mFolderId);
        sb.append(", date=").append(mLastItemDate);
        sb.append(", guid=").append(mLastItemGuid);
        return sb.toString();
    }

    @Override protected void serializeData(RedoLogOutput out) throws IOException {
        out.writeInt(mFolderId);
        out.writeLong(mLastItemDate);
        out.writeUTF(mLastItemGuid);
    }

    @Override protected void deserializeData(RedoLogInput in) throws IOException {
        mFolderId = in.readInt();
        mLastItemDate = in.readLong();
        mLastItemGuid = in.readUTF();
    }

    @Override public void redo() throws Exception {
        Mailbox mbox = MailboxManager.getInstance().getMailboxById(getMailboxId());
        mbox.setSubscriptionData(getOperationContext(), mFolderId, mLastItemDate, mLastItemGuid);
    }
}
