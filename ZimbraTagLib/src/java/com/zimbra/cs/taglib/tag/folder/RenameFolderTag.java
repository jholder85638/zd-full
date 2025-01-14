/*
 * 
 */
package com.zimbra.cs.taglib.tag.folder;

import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.taglib.tag.ZimbraSimpleTag;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspTagException;
import java.io.IOException;

public class RenameFolderTag extends ZimbraSimpleTag {

    private String mId;
    private String mNewName;

    public void setId(String id) { mId = id; }
    public void setNewname(String newname) { mNewName = newname; }

    public void doTag() throws JspException, IOException {
        try {
            getMailbox().renameFolder(mId, mNewName);
        } catch (ServiceException e) {
            throw new JspTagException(e);
        }
    }
}
