/*
 * 
 */
package com.zimbra.cs.account.callback;

import java.util.Map;

import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.account.AttributeCallback;
import com.zimbra.cs.account.Domain;
import com.zimbra.cs.account.Entry;
import com.zimbra.cs.account.Provisioning;

public class DomainStatus extends AttributeCallback {

    public void preModify(Map context, String attrName, Object value,
            Map attrsToModify, Entry entry, boolean isCreate) throws ServiceException {
        
        if (!(value instanceof String))
            throw ServiceException.INVALID_REQUEST(Provisioning.A_zimbraDomainStatus+" is a single-valued attribute", null);
        
        String status = (String) value;

        if (status.equals(Provisioning.DOMAIN_STATUS_SHUTDOWN)) {
            throw ServiceException.INVALID_REQUEST("Setting " + Provisioning.A_zimbraDomainStatus + " to " + Provisioning.DOMAIN_STATUS_SHUTDOWN + " is not allowed.  It is an internal status and can only be set by server", null);
            
        } else if (status.equals(Provisioning.DOMAIN_STATUS_CLOSED)) {
            attrsToModify.put(Provisioning.A_zimbraMailStatus, Provisioning.MAIL_STATUS_DISABLED);
            
        } else {
            if (entry != null) {
                Domain domain = (Domain)entry;
                if (domain.beingRenamed())
                    throw ServiceException.INVALID_REQUEST("domain " + domain.getName() + " is being renamed, cannot change " + Provisioning.A_zimbraDomainStatus, null);
            }
            
            String alsoModifyingMailStatus = (String)attrsToModify.get(Provisioning.A_zimbraMailStatus);
            if (alsoModifyingMailStatus == null) {
                if (entry != null) {
                    String curMailStatus = entry.getAttr(Provisioning.A_zimbraMailStatus);
                    if (status.equals(Provisioning.DOMAIN_STATUS_SUSPENDED) && 
                        curMailStatus != null &&
                        curMailStatus.equals(Provisioning.MAIL_STATUS_DISABLED))
                        return;
                }
                
                attrsToModify.put(Provisioning.A_zimbraMailStatus, Provisioning.MAIL_STATUS_ENABLED);
            }
        }

    }
    
    public void postModify(Map context, String attrName, Entry entry, boolean isCreate) {
    }
    
}
