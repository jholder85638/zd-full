/*
 * 
 */

/*
 * Created on 2004. 12. 13.
 */
package com.zimbra.cs.redolog.op;

import java.io.IOException;

import com.zimbra.cs.mailbox.MailItem;
import com.zimbra.cs.redolog.RedoLogInput;
import com.zimbra.cs.redolog.RedoLogOutput;

public class RenameFolder extends RenameItem {

    public RenameFolder() {
        super();
        mType = MailItem.TYPE_FOLDER;
    }

    public RenameFolder(int mailboxId, int id, String name, int parentId) {
        super(mailboxId, id, MailItem.TYPE_FOLDER, name, parentId);
    }

    @Override public int getOpCode() {
        return OP_RENAME_FOLDER;
    }

    @Override protected void serializeData(RedoLogOutput out) throws IOException {
        out.writeInt(mId);
        out.writeInt(mFolderId);
        out.writeUTF(mName);
    }

    @Override protected void deserializeData(RedoLogInput in) throws IOException {
        mId = in.readInt();
        mFolderId = in.readInt();
        mName = in.readUTF();
    }
}
