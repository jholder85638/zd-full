/*
 * 
 */

/*
 * Created on Jun 17, 2004
 */
package com.zimbra.cs.service.admin;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.AdminConstants;
import com.zimbra.common.soap.Element;
import com.zimbra.cs.account.AccountServiceException;
import com.zimbra.cs.account.Domain;
import com.zimbra.cs.account.NamedEntry;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.account.Provisioning.DomainBy;
import com.zimbra.cs.account.accesscontrol.AdminRight;
import com.zimbra.cs.account.accesscontrol.Rights.Admin;
import com.zimbra.cs.session.AdminSession;
import com.zimbra.cs.session.Session;
import com.zimbra.soap.ZimbraSoapContext;

import java.util.List;
import java.util.Map;

/**
 * @author schemers
 */
public class SearchAccounts extends AdminDocumentHandler {

    /**
     * must be careful and only allow access to domain if domain admin
     */
    public boolean domainAuthSufficient(Map context) {
        return true;
    }
    
    public Element handle(Element request, Map<String, Object> context) throws ServiceException {
        ZimbraSoapContext zsc = getZimbraSoapContext(context);
        Provisioning prov = Provisioning.getInstance();

        String query = request.getAttribute(AdminConstants.E_QUERY);

        int limit = (int) request.getAttributeLong(AdminConstants.A_LIMIT, Integer.MAX_VALUE);
        if (limit == 0)
            limit = Integer.MAX_VALUE;
        int offset = (int) request.getAttributeLong(AdminConstants.A_OFFSET, 0);
        String domain = request.getAttribute(AdminConstants.A_DOMAIN, null);
        boolean applyCos = request.getAttributeBool(AdminConstants.A_APPLY_COS, true);
        String attrsStr = request.getAttribute(AdminConstants.A_ATTRS, null);
        String sortBy = request.getAttribute(AdminConstants.A_SORT_BY, null);
        String types = request.getAttribute(AdminConstants.A_TYPES, "accounts");
        boolean sortAscending = request.getAttributeBool(AdminConstants.A_SORT_ASCENDING, true);

        int flags = Provisioning.searchAccountStringToMask(types);

        String[] attrs = attrsStr == null ? null : attrsStr.split(",");

        // if we are a domain admin only, restrict to domain
        //
        // Note: isDomainAdminOnly *always* returns false for pure ACL based AccessManager 
        if (isDomainAdminOnly(zsc)) {
            if ((flags & Provisioning.SA_DOMAIN_FLAG) == Provisioning.SA_DOMAIN_FLAG)
                throw ServiceException.PERM_DENIED("can not search for domains");

            if (domain == null) {
                domain = getAuthTokenAccountDomain(zsc).getName();
            } else {
                checkDomainRight(zsc, domain, AdminRight.PR_ALWAYS_ALLOW);
            }
        }

        Domain d = null;
        if (domain != null) {
            d = prov.get(DomainBy.name, domain);
            if (d == null)
                throw AccountServiceException.NO_SUCH_DOMAIN(domain);
        }

        AdminAccessControl aac = AdminAccessControl.getAdminAccessControl(zsc);
        AdminAccessControl.SearchDirectoryRightChecker rightChecker = 
            new AdminAccessControl.SearchDirectoryRightChecker(aac, prov, null);
        
        List accounts;
        AdminSession session = (AdminSession) getSession(zsc, Session.Type.ADMIN);
        if (session != null) {
            accounts = session.searchAccounts(d, query, attrs, sortBy, sortAscending, flags, offset, 0, rightChecker);
        } else {
            if (d != null) {
                accounts = prov.searchAccounts(d, query, attrs, sortBy, sortAscending, flags);
            } else {
                accounts = prov.searchAccounts(query, attrs, sortBy, sortAscending, flags);
            }
            accounts = rightChecker.getAllowed(accounts);
        }

        Element response = zsc.createElement(AdminConstants.SEARCH_ACCOUNTS_RESPONSE);
        int i, limitMax = offset+limit;
        for (i=offset; i < limitMax && i < accounts.size(); i++) {
            NamedEntry entry = (NamedEntry) accounts.get(i);
            SearchDirectory.encodeEntry(prov, response, entry, applyCos, null, aac);
        }          

        response.addAttribute(AdminConstants.A_MORE, i < accounts.size());
        response.addAttribute(AdminConstants.A_SEARCH_TOTAL, accounts.size());
        return response;
    }

    @Override
    public void docRights(List<AdminRight> relatedRights, List<String> notes) {
        relatedRights.add(Admin.R_getAccount);
        relatedRights.add(Admin.R_getCalendarResource);
        relatedRights.add(Admin.R_getDistributionList);
        relatedRights.add(Admin.R_getDomain);
        relatedRights.add(Admin.R_listAccount);
        relatedRights.add(Admin.R_listCalendarResource);
        relatedRights.add(Admin.R_listDistributionList);
        relatedRights.add(Admin.R_listDomain);
        
        notes.add(AdminRightCheckPoint.Notes.LIST_ENTRY);
    }
}
