/*
 * 
 */
package com.zimbra.cs.service.offline;

import java.util.Map;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.AdminConstants;
import com.zimbra.common.soap.Element;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.offline.OfflineAccount;
import com.zimbra.cs.gal.GalGroupMembers.ContactDLMembers;
import com.zimbra.cs.mailbox.Contact;
import com.zimbra.cs.mailbox.GalSyncUtil;
import com.zimbra.cs.service.account.GetDistributionListMembers;
import com.zimbra.soap.ZimbraSoapContext;

public class OfflineGetDistributionListMembers extends GetDistributionListMembers {

    @Override
    public Element handle(Element request, Map<String, Object> context) throws ServiceException {
        ZimbraSoapContext zsc = getZimbraSoapContext(context);
        Account account = getRequestedAccount(getZimbraSoapContext(context));

        int limit = (int) request.getAttributeLong(AdminConstants.A_LIMIT, 0);
        if (limit < 0) {
            throw ServiceException.INVALID_REQUEST("limit" + limit + " is negative", null);
        }

        int offset = (int) request.getAttributeLong(AdminConstants.A_OFFSET, 0);
        if (offset < 0) {
            throw ServiceException.INVALID_REQUEST("offset" + offset + " is negative", null);
        }

        Element d = request.getElement(AdminConstants.E_DL);
        String dlName = d.getText();
        Contact con = GalSyncUtil.getGalDlistContact((OfflineAccount) account, dlName);
        ContactDLMembers dlMembers = new ContactDLMembers(con);
        return processDLMembers(zsc, dlName, account, limit, offset, dlMembers);
    }
}
