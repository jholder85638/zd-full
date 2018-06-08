/*
 * 
 */
package com.zimbra.cs.taglib.tag.conv;

import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.taglib.tag.ZimbraSimpleTag;
import com.zimbra.cs.taglib.bean.ZActionResultBean;
import com.zimbra.cs.zclient.ZMailbox.ZActionResult;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.PageContext;
import javax.servlet.jsp.JspTagException;

public class MarkConversationSpamTag extends ZimbraSimpleTag {

    private String mTc;
    private String mId;
    private boolean mSpam;
    private String mFolderid;
    private String mVar;

    public void setVar(String var) { mVar = var; }
    public void setTc(String tc) { mTc = tc; }
    public void setId(String id) { mId = id; }
    public void setSpam(boolean spam) { mSpam = spam; } 
    public void setFolderidid(String folderid) { mFolderid = folderid; }

    public void doTag() throws JspException {
        try {
            ZActionResult result = getMailbox().markConversationSpam(mId, mSpam, mFolderid, mTc);
            getJspContext().setAttribute(mVar, new ZActionResultBean(result), PageContext.PAGE_SCOPE);
        } catch (ServiceException e) {
            throw new JspTagException(e);
        }
    }
}