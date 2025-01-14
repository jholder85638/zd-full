/*
 * 
 */

/*
 * Created on 2004. 12. 14.
 */
package com.zimbra.cs.redolog.op;

import java.io.IOException;

import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.MailboxManager;
import com.zimbra.cs.redolog.RedoLogInput;
import com.zimbra.cs.redolog.RedoLogOutput;

public class EditNote extends RedoableOp {

    private int mId;
    private String mContent;

    public EditNote() {
        mId = UNKNOWN_ID;
    }

    public EditNote(int mailboxId, int id, String content) {
        setMailboxId(mailboxId);
        mId = id;
        mContent = content != null ? content : "";
    }

    @Override public int getOpCode() {
        return OP_EDIT_NOTE;
    }

    @Override protected String getPrintableData() {
        StringBuffer sb = new StringBuffer("id=");
        sb.append(mId).append(", content=").append(mContent);
        return sb.toString();
    }

    @Override protected void serializeData(RedoLogOutput out) throws IOException {
        out.writeInt(mId);
        out.writeUTF(mContent);
    }

    @Override protected void deserializeData(RedoLogInput in) throws IOException {
        mId = in.readInt();
        mContent = in.readUTF();
    }

    @Override public void redo() throws Exception {
        Mailbox mailbox = MailboxManager.getInstance().getMailboxById(getMailboxId());
        mailbox.editNote(getOperationContext(), mId, mContent);
    }
}
