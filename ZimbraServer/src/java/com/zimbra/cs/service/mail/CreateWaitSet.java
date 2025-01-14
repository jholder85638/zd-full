/*
 * 
 */
package com.zimbra.cs.service.mail;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.Element;
import com.zimbra.common.soap.MailConstants;
import com.zimbra.common.util.Pair;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.mailbox.MailServiceException;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.MailboxManager;
import com.zimbra.cs.session.WaitSetAccount;
import com.zimbra.cs.session.WaitSetError;
import com.zimbra.cs.session.WaitSetMgr;
import com.zimbra.soap.DocumentHandler;
import com.zimbra.soap.ZimbraSoapContext;

/**
 * 
 */
public class CreateWaitSet extends MailDocumentHandler {
    /*
     <!--*************************************
          CreateWaitSet: must be called once to initialize the WaitSet
          and to set its "default interest types"
         ************************************* -->
        <CreateWaitSetRequest defTypes="DEFAULT_INTEREST_TYPES" [all="1"]>
          [ <add>
            [<a id="ACCTID" [token="lastKnownSyncToken"] [types="if_not_default"]/>]+
            </add> ]
        </CreateWaitSetRequest>

        <CreateWaitSetResponse waitSet="setId" defTypes="types" seq="0">
          [ <error ...something.../>]*
        </CreateWaitSetResponse>  
     */

    @Override
    public Element handle(Element request, Map<String, Object> context) throws ServiceException {
        ZimbraSoapContext zsc = getZimbraSoapContext(context);
        Element response = zsc.createElement(MailConstants.CREATE_WAIT_SET_RESPONSE);
        return staticHandle(this, request, context, response);
    }
    
    static public Element staticHandle(DocumentHandler handler, Element request, Map<String, Object> context, Element response) throws ServiceException {
        ZimbraSoapContext zsc = getZimbraSoapContext(context);
        
        String defInterestStr = request.getAttribute(MailConstants.A_DEFTYPES);
        int defaultInterests = WaitSetRequest.parseInterestStr(defInterestStr, 0);
        boolean adminAllowed = zsc.getAuthToken().isAdmin();
        
        boolean allAccts = request.getAttributeBool(MailConstants.A_ALL_ACCOUNTS, false);
        if (allAccts) {
            WaitSetMgr.checkRightForAllAccounts(zsc);
        }
        
        List<WaitSetAccount> add = WaitSetRequest.parseAddUpdateAccounts(zsc, 
            request.getOptionalElement(MailConstants.E_WAITSET_ADD), defaultInterests);
        
        // workaround for 27480: load the mailboxes NOW, before we grab the waitset lock
        List<Mailbox> referencedMailboxes = new ArrayList<Mailbox>();
        for (WaitSetAccount acct : add) {
            try {
                MailboxManager.FetchMode fetchMode = MailboxManager.FetchMode.AUTOCREATE;
                Mailbox mbox = MailboxManager.getInstance().getMailboxByAccountId(acct.getAccountId(), fetchMode);
                referencedMailboxes.add(mbox);
            } catch (ServiceException e) {
                ZimbraLog.session.debug("Caught exception preloading mailbox for waitset", e);
            }
        }
        

        Pair<String, List<WaitSetError>> result = WaitSetMgr.create(zsc.getRequestedAccountId(), adminAllowed, defaultInterests, allAccts, add);
        String wsId = result.getFirst();
        List<WaitSetError> errors = result.getSecond();
        
        response.addAttribute(MailConstants.A_WAITSET_ID, wsId);
        response.addAttribute(MailConstants.A_DEFTYPES, WaitSetRequest.interestToStr(defaultInterests));
        response.addAttribute(MailConstants.A_SEQ, 0);
        
        WaitSetRequest.encodeErrors(response, errors);
        
        return response;
    }

}
