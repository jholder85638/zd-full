/*
 * 
 */
package com.zimbra.cs.account;

import java.util.Map;
import java.util.Set;

public class Zimlet extends NamedEntry {
	public Zimlet(String name, String id, Map<String, Object> attrs, Provisioning prov) {
        super(name, id, attrs, null, prov);
    }

    public boolean isEnabled() {
        return getBooleanAttr(Provisioning.A_zimbraZimletEnabled, false);
    }
    
    public String getPriority() {
        return getAttr(Provisioning.A_zimbraZimletPriority);
    }
    
    public boolean isExtension() {
        return getBooleanAttr(Provisioning.A_zimbraZimletIsExtension, false);
    }

    public String getType() {
        return getAttr(Provisioning.A_cn);
    }
    
    public String getDescription() {
        return getAttr(Provisioning.A_zimbraZimletDescription);
    }
    
    public boolean isIndexingEnabled() {
        return getBooleanAttr(Provisioning.A_zimbraZimletIndexingEnabled, false);
    }
    
    public String getHandlerClassName() {
        return getAttr(Provisioning.A_zimbraZimletHandlerClass);
    }
    
    public String getHandlerConfig() {
        return getAttr(Provisioning.A_zimbraZimletHandlerConfig);
    }

    public String getServerIndexRegex() {
        return getAttr(Provisioning.A_zimbraZimletServerIndexRegex);
    }
    
	
	public boolean checkTarget(String target) {
		Set<String> lTiers = getMultiAttrSet(Provisioning.A_zimbraZimletTarget); 
		return ((lTiers == null) ? false : lTiers.contains(target));
	}

}
