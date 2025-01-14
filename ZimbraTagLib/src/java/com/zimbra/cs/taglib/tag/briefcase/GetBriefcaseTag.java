/*
 * 
 */
package com.zimbra.cs.taglib.tag.briefcase;

import com.zimbra.cs.taglib.bean.*;
import com.zimbra.cs.taglib.tag.ZimbraSimpleTag;
import com.zimbra.cs.zclient.*;

import com.zimbra.common.service.ServiceException;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspContext;
import javax.servlet.jsp.PageContext;
import javax.servlet.jsp.JspTagException;
import java.io.IOException;
import java.util.TimeZone;

public class GetBriefcaseTag extends ZimbraSimpleTag {

    private String mVarSearch;
    private String mId;
    private String mVarFolder;
    private ZMailboxBean mMailbox;
    private TimeZone mTimeZone = TimeZone.getDefault();
    private String mRestTargetAccountId;

    public void setId(String id) { this.mId = id; }
    public void setVarSearch(String varSearch) { this.mVarSearch = varSearch; }
    public void setVarFolder(String varFolder) { this.mVarFolder = varFolder; }
    public void setBox(ZMailboxBean mailbox) { this.mMailbox = mailbox; }
    public void setTimezone(TimeZone timeZone) { this.mTimeZone = timeZone; }
    public void setResttargetaccountid(String targetId) { this.mRestTargetAccountId = targetId; }

    public void doTag() throws JspException, IOException {
        JspContext jctxt = getJspContext();
        try {
            ZMailbox mbox = mMailbox != null ? mMailbox.getMailbox() : getMailbox();
            ZFolder briefcaseFolder = mbox.getFolderById(mId);
            if (briefcaseFolder == null) {
                // Folder cache miss. Search again this time with the fully qualified folder id
                briefcaseFolder = mbox.getFolderById(mRestTargetAccountId + ":" + mId);
            }
            // create SearchParams and fire the search request.
            ZSearchParams params = new ZSearchParams("inid:" + "\"" + briefcaseFolder.getId() + "\"");
            if (briefcaseFolder.getDefaultView() != null) params.setTypes(briefcaseFolder.getDefaultView().name());
            params.setTimeZone(mTimeZone);
            ZSearchContext searchCnt = new ZSearchContext(params, mbox);
            if (searchCnt.getResult() == null) {
                searchCnt.getNextHit();
            }
            jctxt.setAttribute(mVarSearch, new ZSearchResultBean(searchCnt.getResult(), searchCnt.getParams()), PageContext.PAGE_SCOPE);
            jctxt.setAttribute(mVarFolder, new ZFolderBean(briefcaseFolder), PageContext.PAGE_SCOPE);
        } catch (ServiceException e) {
            throw new JspTagException(e.getMessage(), e);
        }
    }

}
