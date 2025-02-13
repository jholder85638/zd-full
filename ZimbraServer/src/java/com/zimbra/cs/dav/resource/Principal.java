/*
 * 
 */
package com.zimbra.cs.dav.resource;

import javax.servlet.http.HttpServletResponse;

import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.Config;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.dav.DavContext;
import com.zimbra.cs.dav.DavElements;
import com.zimbra.cs.dav.DavException;

public class Principal extends DavResource {

    public Principal(Account authUser, String mainUrl) throws ServiceException {
        this(getOwner(authUser, mainUrl), mainUrl);
    }
    
    public Principal(String user, String mainUrl) throws ServiceException {
    	super(mainUrl, user);
        if (!mainUrl.endsWith("/")) mainUrl = mainUrl + "/";
        setProperty(DavElements.E_HREF, mainUrl);
		setProperty(DavElements.E_GROUP_MEMBER_SET, null, true);
		setProperty(DavElements.E_GROUP_MEMBERSHIP, null, true);
        addResourceType(DavElements.E_PRINCIPAL);
        mUri = mainUrl;
    }
	public static String getOwner(Account acct, String url) throws ServiceException {
		String owner = acct.getName();
		Provisioning prov = Provisioning.getInstance();
        Config config = prov.getConfig();
        String defaultDomain = config.getAttr(Provisioning.A_zimbraDefaultDomainName, null);
        if (url.indexOf('@') < 0 && defaultDomain != null && defaultDomain.equalsIgnoreCase(acct.getDomainName()))
        	owner = owner.substring(0, owner.indexOf('@'));
        return owner;
	}
	
    @Override
    public void delete(DavContext ctxt) throws DavException {
        throw new DavException("cannot delete this resource", HttpServletResponse.SC_FORBIDDEN, null);
    }

    @Override
    public boolean isCollection() {
        return false;
    }
    
    public Account getAccount() {
    	return mAccount;
    }
    
    protected Account mAccount;
}
