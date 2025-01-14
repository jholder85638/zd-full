/*
 * 
 */

package com.zimbra.cs.service.admin;

import java.util.List;
import java.util.Map;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.AdminConstants;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.Domain;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.account.accesscontrol.AdminRight;
import com.zimbra.cs.account.accesscontrol.Rights.Admin;
import com.zimbra.cs.account.ldap.Check;
import com.zimbra.cs.fb.ExchangeFreeBusyProvider;
import com.zimbra.cs.fb.ExchangeFreeBusyProvider.AuthScheme;
import com.zimbra.common.soap.Element;
import com.zimbra.soap.ZimbraSoapContext;

public class CheckExchangeAuth extends AdminDocumentHandler {

	public Element handle(Element request, Map<String, Object> context) throws ServiceException {

        ZimbraSoapContext zsc = getZimbraSoapContext(context);

        Account authedAcct = getAuthenticatedAccount(zsc);
        Domain domain = Provisioning.getInstance().getDomain(authedAcct);
        
        checkRight(zsc, context, domain, Admin.R_checkExchangeAuthConfig);
        Element auth = request.getElement(AdminConstants.E_AUTH);
        ExchangeFreeBusyProvider.ServerInfo sinfo = new ExchangeFreeBusyProvider.ServerInfo();
        sinfo.url = auth.getAttribute(AdminConstants.A_URL);
        sinfo.authUsername = auth.getAttribute(AdminConstants.A_USER);
        sinfo.authPassword = auth.getAttribute(AdminConstants.A_PASS);
        String scheme = auth.getAttribute(AdminConstants.A_SCHEME);
        sinfo.scheme = AuthScheme.valueOf(scheme);
        Check.Result r = Check.checkExchangeAuth(sinfo, authedAcct);

	    Element response = zsc.createElement(AdminConstants.CHECK_EXCHANGE_AUTH_RESPONSE);
        response.addElement(AdminConstants.E_CODE).addText(r.getCode());
        String message = r.getMessage();
        if (message != null)
            response.addElement(AdminConstants.E_MESSAGE).addText(message);
	    return response;
	}
	
	@Override
	public void docRights(List<AdminRight> relatedRights, List<String> notes) {
        relatedRights.add(Admin.R_checkExchangeAuthConfig);
    }
}