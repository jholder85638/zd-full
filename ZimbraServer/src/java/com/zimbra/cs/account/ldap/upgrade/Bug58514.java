/*
 * 
 */
package com.zimbra.cs.account.ldap.upgrade;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.StringUtil;
import com.zimbra.cs.account.Config;
import com.zimbra.cs.account.Entry;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.account.Server;
import com.zimbra.cs.account.accesscontrol.TargetType;

public class Bug58514 extends LdapUpgrade {
	Bug58514() throws ServiceException {
    }
    
	@Override
    void doUpgrade() throws ServiceException {
        upgradeZimbraGalLdapAttrMap();
        upgradeZimbraContactHiddenAttributes();
    }
    
    private void upgradeZimbraGalLdapAttrMap() throws ServiceException {
        final String attrName = Provisioning.A_zimbraGalLdapAttrMap;
    	
        final String valueToRemove = "binary zimbraPrefMailSMIMECertificate,userCertificate,userSMIMECertificate=SMIMECertificate";
        
        final String[] valuesToAdd = new String[] {
	        "(certificate) userCertificate=userCertificate",
	        "(binary) userSMIMECertificate=userSMIMECertificate"
        };
        
        Config config = mProv.getConfig();
        
        Map<String, Object> attrs = new HashMap<String, Object>();
        
        Set<String> curValues = config.getMultiAttrSet(attrName);
        if (curValues.contains(valueToRemove)) {
        	StringUtil.addToMultiMap(attrs, "-" + attrName, valueToRemove);
        }
        
        for (String valueToAdd : valuesToAdd) {
        	if (!curValues.contains(valueToAdd)) {
            	StringUtil.addToMultiMap(attrs, "+" + attrName, valueToAdd);
            }
        }
        
        modifyAttrs(config, attrs);
    }
    
    private void upgradeZimbraContactHiddenAttributes(Entry entry) throws ServiceException {
    	final String attrName = Provisioning.A_zimbraContactHiddenAttributes;
    	final String SMIMECertificate = "SMIMECertificate";
    	
    	String curValue = entry.getAttr(attrName, false);
    	
    	if (curValue == null || !curValue.contains(SMIMECertificate)) {
    		return;
    	}
    	
    	String[] hiddenAttrs = curValue.split(",");
    	
    	StringBuilder sb = new StringBuilder();
    	boolean first = true;
    	for (String hiddenAttr : hiddenAttrs) {
    		if (!hiddenAttr.equals(SMIMECertificate)) {
    			if (!first) {
    				sb.append(",");
    			} else {
    				first = false;
    			}
    			sb.append(hiddenAttr);
    		}
    	}
    	
    	System.out.println("Upgrading " + TargetType.getTargetType(entry).getPrettyName() + " " + entry.getLabel());
    	System.out.println("    Current value of " + attrName + ": " + curValue);
    	System.out.println("    New value of " + attrName + ": " + sb.toString());
    	
    	Map<String, Object> attrs = new HashMap<String, Object>();
    	attrs.put(attrName, sb.toString());
    	modifyAttrs(entry, attrs);
    	
    }
    
    private void upgradeZimbraContactHiddenAttributes() throws ServiceException {
    	Config config = mProv.getConfig();
    	upgradeZimbraContactHiddenAttributes(config);
    	
    	List<Server> servers = mProv.getAllServers();
        
        for (Server server : servers) {
        	upgradeZimbraContactHiddenAttributes(server);
        }
    }
}
