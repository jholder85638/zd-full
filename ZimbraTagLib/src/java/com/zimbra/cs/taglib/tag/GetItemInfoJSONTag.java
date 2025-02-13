/*
 * 
 */
package com.zimbra.cs.taglib.tag;

import com.zimbra.common.auth.ZAuthToken;
import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.Element;
import com.zimbra.common.soap.SoapTransport;
import com.zimbra.cs.taglib.ZJspSession;
import com.zimbra.common.soap.MailConstants;
import com.zimbra.common.soap.SoapProtocol;
import com.zimbra.common.zclient.ZClientException;
import com.zimbra.cs.taglib.tag.TagUtil.JsonDebugListener;

import javax.servlet.jsp.JspContext;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspTagException;
import javax.servlet.jsp.PageContext;
import java.io.IOException;
import java.util.regex.Pattern;

public class GetItemInfoJSONTag extends ZimbraSimpleTag {

    private static final Pattern sSCRIPT = Pattern.compile("</script>", Pattern.CASE_INSENSITIVE);

    private String mVar;
    private String mId;
    private String mAuthToken;
    

    public void setVar(String var) { this.mVar = var; }
    public void setId(String id) { this.mId = id; }
    public void setAuthtoken(String authToken) { this.mAuthToken = authToken; }

    public void doTag() throws JspException, IOException {
        try {
            JspContext ctxt = getJspContext();
            PageContext pageContext = (PageContext) ctxt;
            String url = ZJspSession.getSoapURL(pageContext);
            String remoteAddr = ZJspSession.getRemoteAddr(pageContext);
            ZAuthToken authToken = new ZAuthToken(mAuthToken);
            Element e = getItemInfoJSON(url, remoteAddr, authToken, mId);
            ctxt.setAttribute(mVar, sSCRIPT.matcher(e.toString()).replaceAll("</scr\"+\"ipt>"),  PageContext.REQUEST_SCOPE);
        } catch (ServiceException e) {
            throw new JspTagException(e.getMessage(), e);
        }
    }

    public static Element getItemInfoJSON(String url, String remoteAddr, ZAuthToken authToken, String mId) throws ServiceException{

        JsonDebugListener debug = new TagUtil.JsonDebugListener();
        SoapTransport transport = TagUtil.newJsonTransport(url, remoteAddr, authToken, debug);

        try {
            Element req = Element.create(SoapProtocol.SoapJS, MailConstants.GET_ITEM_REQUEST);            
            Element item = req.addElement(MailConstants.E_ITEM);
            item.addAttribute(MailConstants.A_ID, mId);
            transport.invokeWithoutSession(req);
            return debug.getEnvelope();
        } catch (IOException e) {
            throw ZClientException.IO_ERROR("invoke "+e.getMessage(), e);
        }
    }    
}
