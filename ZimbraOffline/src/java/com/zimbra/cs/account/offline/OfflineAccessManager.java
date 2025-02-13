/*
 * 
 */
package com.zimbra.cs.account.offline;

import java.util.Map;
import java.util.Set;

import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.account.AccessManager;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.AuthToken;
import com.zimbra.cs.account.Cos;
import com.zimbra.cs.account.Domain;
import com.zimbra.cs.account.Entry;
import com.zimbra.cs.account.accesscontrol.Right;

public class OfflineAccessManager extends AccessManager {

    @Override
    public boolean isAdequateAdminAccount(Account acct) {
        return true;
    }
    
	@Override
	public boolean canAccessAccount(AuthToken at, Account target,
			boolean asAdmin) throws ServiceException {
		return true;
	}

	@Override
	public boolean canAccessAccount(AuthToken at, Account target)
			throws ServiceException {
		return true;
	}

	@Override
	public boolean canAccessAccount(Account credentials, Account target,
			boolean asAdmin) throws ServiceException {
		return true;
	}

	@Override
	public boolean canAccessAccount(Account credentials, Account target)
			throws ServiceException {
		return true;
	}

	@Override
	public boolean canAccessDomain(AuthToken at, String domainName)
			throws ServiceException {
		return true;
	}

	@Override
	public boolean canAccessDomain(AuthToken at, Domain domain)
			throws ServiceException {
		return true;
	}
	
	@Override
	public  boolean canAccessCos(AuthToken at, Cos cos)
			throws ServiceException {
		return true;
	}
	
	@Override
	public boolean canAccessEmail(AuthToken at, String email)
			throws ServiceException {
		return true;
	}

	@Override
	public boolean canModifyMailQuota(AuthToken at, Account targetAccount,
			long mailQuota) throws ServiceException {
		return true;
	}

	@Override
	public boolean isDomainAdminOnly(AuthToken at) {
		return false;
	}
	
	@Override
	public boolean canDo(AuthToken grantee, Entry target, Right rightNeeded, boolean asAdmin) {
	    return true;
	}
    
	@Override
	public boolean canDo(Account grantee, Entry target, Right rightNeeded, boolean asAdmin) {
	    return true;
	}
	
	@Override
	public boolean canDo(String grantee, Entry target, Right rightNeeded, boolean asAdmin) {
	    return true;
	}
	
	@Override
    public boolean canGetAttrs(Account grantee,   Entry target, Set<String> attrs, boolean asAdmin) throws ServiceException {
        return true;
    }
	
	@Override
    public boolean canGetAttrs(AuthToken grantee, Entry target, Set<String> attrs, boolean asAdmin) throws ServiceException {
	    return true;
    }
	
	@Override
    public boolean canSetAttrs(Account grantee,   Entry target, Set<String> attrs, boolean asAdmin) throws ServiceException {
        return true;
    }
	    
    @Override
    public boolean canSetAttrs(AuthToken grantee, Entry target, Set<String> attrs, boolean asAdmin) throws ServiceException {
        return true;
    }
	
	@Override
    public boolean canSetAttrs(Account grantee,   Entry target, Map<String, Object> attrs, boolean asAdmin) throws ServiceException {
	    return true;
    }
	
	@Override
    public boolean canSetAttrs(AuthToken grantee, Entry target, Map<String, Object> attrs, boolean asAdmin) throws ServiceException {
	    return true;
    }

	

}
