/*
 * 
 */

/*
 * Created on May 26, 2004
 */
package com.zimbra.cs.service.account;

import java.util.Map;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.AccountConstants;
import com.zimbra.common.soap.Element;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.AccountServiceException;
import com.zimbra.cs.account.Domain;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.account.Server;
import com.zimbra.cs.account.Provisioning.AccountBy;
import com.zimbra.cs.httpclient.URLUtil;
import com.zimbra.soap.ZimbraSoapContext;

/**
 * @author schemers
 */
public class GetAccountInfo extends AccountDocumentHandler  {

    public Element handle(Element request, Map<String, Object> context) throws ServiceException {
        ZimbraSoapContext zsc = getZimbraSoapContext(context);

        Element a = request.getElement(AccountConstants.E_ACCOUNT);
        String key = a.getAttribute(AccountConstants.A_BY);
        String value = a.getText();

        Provisioning prov = Provisioning.getInstance();
        Account account = prov.get(AccountBy.fromString(key), value, zsc.getAuthToken());

        // prevent directory harvest attack, mask no such account as permission denied
        if (account == null)
            throw ServiceException.PERM_DENIED("can not access account");

        Element response = zsc.createElement(AccountConstants.GET_ACCOUNT_INFO_RESPONSE);
        response.addAttribute(AccountConstants.E_NAME, account.getName(), Element.Disposition.CONTENT);
        response.addKeyValuePair(Provisioning.A_zimbraId, account.getId(), AccountConstants.E_ATTR, AccountConstants.A_NAME);
        response.addKeyValuePair(Provisioning.A_zimbraMailHost, account.getAttr(Provisioning.A_zimbraMailHost), AccountConstants.E_ATTR, AccountConstants.A_NAME);
        addUrls(response, account);

        return response;
    }

    static void addUrls(Element response, Account account) throws ServiceException {
        Provisioning prov = Provisioning.getInstance();
        
        Server server = prov.getServer(account);
        if (server == null) return;
        String hostname = server.getAttr(Provisioning.A_zimbraServiceHostname);        
        if (hostname == null) return;
        
        Domain domain = prov.getDomain(account);

        String httpSoap = URLUtil.getSoapPublicURL(server, domain, false);
        String httpsSoap = URLUtil.getSoapPublicURL(server, domain, true);
        
        if (httpSoap != null)
            response.addAttribute(AccountConstants.E_SOAP_URL, httpSoap, Element.Disposition.CONTENT);

        if (httpsSoap != null && !httpsSoap.equalsIgnoreCase(httpSoap))
            response.addAttribute(AccountConstants.E_SOAP_URL, httpsSoap, Element.Disposition.CONTENT);
        
        String pubUrl = URLUtil.getPublicURLForDomain(server, domain, "", true);
        if (pubUrl != null)
            response.addAttribute(AccountConstants.E_PUBLIC_URL, pubUrl, Element.Disposition.CONTENT);
        
        String changePasswordUrl = null;
        if (domain != null)
            changePasswordUrl = domain.getAttr(Provisioning.A_zimbraChangePasswordURL);
        if (changePasswordUrl != null)
            response.addAttribute(AccountConstants.E_CHANGE_PASSWORD_URL, changePasswordUrl, Element.Disposition.CONTENT);
    }
}
