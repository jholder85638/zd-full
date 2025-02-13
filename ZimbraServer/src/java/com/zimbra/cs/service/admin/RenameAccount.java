/*
 * 
 */

/*
 * Created on Jun 17, 2004
 */
package com.zimbra.cs.service.admin;

import java.util.List;
import java.util.Map;

import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.AccountServiceException;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.account.Provisioning.AccountBy;
import com.zimbra.cs.account.accesscontrol.AdminRight;
import com.zimbra.cs.account.accesscontrol.Rights.Admin;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.MailboxManager;
import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.common.soap.AdminConstants;
import com.zimbra.common.soap.Element;
import com.zimbra.soap.ZimbraSoapContext;

/**
 * @author schemers
 */
public class RenameAccount extends AdminDocumentHandler {

    private static final String[] TARGET_ACCOUNT_PATH = new String[] { AdminConstants.E_ID };
    protected String[] getProxiedAccountPath()  { return TARGET_ACCOUNT_PATH; }

    /**
     * must be careful and only allow renames from/to domains a domain admin can see
     */
    public boolean domainAuthSufficient(Map<String, Object> context) {
        return true;
    }

	public Element handle(Element request, Map<String, Object> context) throws ServiceException {

        ZimbraSoapContext zsc = getZimbraSoapContext(context);
	    Provisioning prov = Provisioning.getInstance();

	    String id = request.getAttribute(AdminConstants.E_ID);
        String newName = request.getAttribute(AdminConstants.E_NEW_NAME);

	    Account account = prov.get(AccountBy.id, id, zsc.getAuthToken());
        if (account == null)
            throw AccountServiceException.NO_SUCH_ACCOUNT(id);

        String oldName = account.getName();
        
        // check if the admin can rename the account
        checkAccountRight(zsc, account, Admin.R_renameAccount);

        // check if the admin can "create account" in the domain (can be same or diff)
        checkDomainRightByEmail(zsc, newName, Admin.R_createAccount);

        Mailbox mbox = Provisioning.onLocalServer(account) ? MailboxManager.getInstance().getMailboxByAccount(account) : null;
        prov.renameAccount(id, newName);
        if (mbox != null)
            mbox.renameMailbox(oldName, newName);

        ZimbraLog.security.info(ZimbraLog.encodeAttrs(
                new String[] {"cmd", "RenameAccount","name", oldName, "newName", newName})); 
        
        // get again with new name...
        account = prov.get(AccountBy.id, id, true, zsc.getAuthToken());
        if (account == null)
            throw ServiceException.FAILURE("unable to get account after rename: " + id, null);
	    Element response = zsc.createElement(AdminConstants.RENAME_ACCOUNT_RESPONSE);
        ToXML.encodeAccount(response, account);
	    return response;
	}
	
    @Override
    public void docRights(List<AdminRight> relatedRights, List<String> notes) {
        relatedRights.add(Admin.R_renameAccount);
        relatedRights.add(Admin.R_createAccount);
    }
}
