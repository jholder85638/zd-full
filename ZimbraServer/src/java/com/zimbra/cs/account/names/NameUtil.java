/*
 * 
 */
package com.zimbra.cs.account.names;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.account.Config;
import com.zimbra.cs.account.Provisioning;

public class NameUtil {
    private static final int MAX_DOMAIN_NAME_LEN = 255; // bug 15919, (RFC 1035)
    
    private static boolean isDot(int c) {
        if (c == '.' || c == '\u3002' || c == '\uff0e' || c == '\uff61')
            return true;
        else
            return false;
    }

    private static boolean containsNonLDH(String input) {
        for (int i = 0; i < input.length(); i++) {
            int c = input.charAt(i);
            
            // skip dot characters
            // The following characters are recognized as dots: 
            //     \u002E (full stop), \u3002 (ideographic full stop), \uFF0E (fullwidth full stop), and \uFF61 (halfwidth ideographic full stop)
            //
            if (isDot(c))
                continue;
            
            if ((c <= 0x2c) || 
                (c >= 0x2e && c <= 0x2f) || 
                (c >= 0x3a && c <= 0x40) ||
                (c >= 0x5b && c <= 0x60) ||
                (c >= 0x7b && c <= 0x7f)) {
                return true;
            }
        }
        return false;
    }
    
    public static void validEmailAddress(String addr) throws ServiceException {
        try {
            InternetAddress ia = new InternetAddress(addr, true);
            if (ia.getPersonal() != null && !ia.getPersonal().equals(""))
                throw ServiceException.INVALID_REQUEST("invalid email address", null);
        } catch (AddressException e) {
            throw ServiceException.INVALID_REQUEST("invalid email address", e);
        }
    }
    
    /*
     * returns if a domain can be created or renamed into the given name
     * 
     * Note: this has to be called with an ASCII domain name.
     *       i.e. for IDN, it has to converted to ACE first before calling
     *       this method.
     */
    public static void validNewDomainName(String domain) throws ServiceException {
        
        // bug 15919, domain name should be restricted to 255 chars (RFC 1035)
        if (domain.length() > MAX_DOMAIN_NAME_LEN)
            throw ServiceException.INVALID_REQUEST("invalid domain name " + domain + 
                    ", domain name cannot exceed " + MAX_DOMAIN_NAME_LEN + " chars", null);
        
        String email = "test" + "@" + domain;
        try {
            validEmailAddress(email); 
        } catch (ServiceException e) {
            throw ServiceException.INVALID_REQUEST("invalid domain name " + domain, null);
        }
        
        Config config = Provisioning.getInstance().getConfig();
        boolean allowNonLDH = config.getBooleanAttr(Provisioning.A_zimbraAllowNonLDHCharsInDomain, true);
        if (!allowNonLDH && containsNonLDH(domain))
            throw ServiceException.INVALID_REQUEST("invalid domain name " + domain + 
                    ": " + " containing non-LDH characters and " + 
                    Provisioning.A_zimbraAllowNonLDHCharsInDomain + " is false", null);
    }
    
}
