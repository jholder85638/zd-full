/*
 * 
 */
 
package com.zimbra.cs.im.provider;

import org.jivesoftware.wildfire.auth.AuthProvider;
import org.jivesoftware.wildfire.auth.UnauthorizedException;
import org.jivesoftware.wildfire.user.UserNotFoundException;

import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.account.auth.AuthContext;

public class ZimbraAuthProvider implements AuthProvider {

    public ZimbraAuthProvider() {
        
    }
    
    public String getServerDialbackHmac(String data) throws Exception {
        return ServerDialbackKey.getHmac(data);
    }

    public void authenticate(String username, String password) throws UnauthorizedException {
        try {
            Account acct = ZimbraUserProvider.getInstance().lookupAccount(username);
            if (acct == null) {
                throw new UnauthorizedException("Unknown user: "+username);
            }
            Provisioning.getInstance().authAccount(acct, password, AuthContext.Protocol.im); 
        } catch (ServiceException e) {
            throw new UnauthorizedException(e);
        }
    }

    public void authenticate(String username, String token, String digest) throws UnauthorizedException {
        throw new UnsupportedOperationException("Digest authentication not currently supported.");
    }

    public boolean isDigestSupported() {
        return false;
    }

    public boolean isPlainSupported() {
        return true;
    }

    public String getPassword(String username) throws UserNotFoundException, UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    public void setPassword(String username, String password) throws UserNotFoundException, UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    public boolean supportsPasswordRetrieval() {
        return false;
    }

}
