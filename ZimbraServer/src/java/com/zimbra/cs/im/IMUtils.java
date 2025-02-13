/*
 * 
 */
package com.zimbra.cs.im;

import org.xmpp.packet.JID;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.account.Provisioning.AccountBy;

/**
 * 
 */
public class IMUtils {

    /**
     * XMPP doesn't support aliases, so we need to resolve the requested
     * address to a canonical address.     
     *  
     * @param addr Address or alias (may or may not be local)
     * @return The resolved address, or 'addr' if it could not be resolved.
     */
    public static String resolveAddress(String addr) throws ServiceException {
        try {
            Account acct = Provisioning.getInstance().get(AccountBy.name, addr);
            if (acct != null) {
                return acct.getName();
            }
        } catch (ServiceException ex) {
            ZimbraLog.im.info("Caught ServiceException while attemptin to resolve alias to local Account", ex);
        }
        // couldn't resolve it, so just return the original addr...
        
        if (addr.indexOf("@") <= 0)
            throw IMServiceException.INVALID_ADDRESS(addr);
        
        new JID(addr); // do JID-acceptable tests on it
        
        return addr;
    }
    
}
