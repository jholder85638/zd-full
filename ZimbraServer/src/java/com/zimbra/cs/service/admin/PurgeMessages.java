/*
 * 
 */

/*
 * Created on Apr 2, 2005
 */
package com.zimbra.cs.service.admin;

import java.util.List;
import java.util.Map;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.common.soap.AdminConstants;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.AccountServiceException;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.account.Provisioning.AccountBy;
import com.zimbra.cs.account.accesscontrol.AdminRight;
import com.zimbra.cs.account.accesscontrol.Rights.Admin;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.MailboxManager;
import com.zimbra.common.soap.Element;
import com.zimbra.soap.ZimbraSoapContext;

/**
 * @author dkarp
 */
public class PurgeMessages extends AdminDocumentHandler {

	public Element handle(Element request, Map<String, Object> context) throws ServiceException {
        ZimbraSoapContext zsc = getZimbraSoapContext(context);

        Element mreq = request.getOptionalElement(AdminConstants.E_MAILBOX);
        String[] accounts;
        if (mreq != null) {
            accounts = new String[] { mreq.getAttribute(AdminConstants.A_ACCOUNTID) };
            
            // accounts are specified, check right or each account
            Provisioning prov = Provisioning.getInstance();
            for (String acctId : accounts) {
                Account acct = prov.get(AccountBy.id, acctId);
                if (acct == null)
                    throw AccountServiceException.NO_SUCH_ACCOUNT(acctId);
                checkAccountRight(zsc, acct, Admin.R_purgeMessages);
            }
            
        } else {
            // all accounts on the system, has to be a system admin
            checkRight(zsc, context, null, AdminRight.PR_SYSTEM_ADMIN_ONLY);
            
            accounts = MailboxManager.getInstance().getAccountIds();
        }
        
        Element response = zsc.createElement(AdminConstants.PURGE_MESSAGES_RESPONSE);
        for (int i = 0; i < accounts.length; i++) {
            Mailbox mbox = null;
            try {
                mbox = MailboxManager.getInstance().getMailboxByAccountId(accounts[i]);
                mbox.purgeMessages(null);
            } catch (ServiceException e) {
                // ignore NO_SUCH_ACCOUNT and WRONG_HOST errors
                if (e.getCode() == ServiceException.WRONG_HOST) {
                    ZimbraLog.mailbox.warn("ignoring mailbox found on wrong host (not cleaned up after migrate?): " + accounts[i], e);
                    // fall through to the "continue" statement
                } else if (e.getCode() != AccountServiceException.NO_SUCH_ACCOUNT) {
                    throw e;
                }
            }
            if (mbox == null)
                continue;

            Element mresp = response.addElement(AdminConstants.E_MAILBOX);
            mresp.addAttribute(AdminConstants.A_MAILBOXID, mbox.getId());
            mresp.addAttribute(AdminConstants.A_SIZE, mbox.getSize());
        }
        return response;
	}
	
    
    @Override
    public void docRights(List<AdminRight> relatedRights, List<String> notes) {
        relatedRights.add(Admin.R_purgeMessages);
        notes.add("If account ids are specified, needs effective " +
                Admin.R_purgeMessages.getName() + " right for each account.  " +
                "If account ids are not specified, the authed account has to be a system admin.");
    }
}
