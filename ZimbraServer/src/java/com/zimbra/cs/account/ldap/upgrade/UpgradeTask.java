/*
 * 
 */
package com.zimbra.cs.account.ldap.upgrade;

import com.zimbra.common.service.ServiceException;

public enum UpgradeTask {
    BUG_11562(Bug11562.class),
    BUG_14531(ZimbraGalLdapFilterDef_zimbraSync.class),
    BUG_18277(AdminRights.class),
    BUG_22033(ZimbraCreateTimestamp.class),
    BUG_27075(CosAndGlobalConfigDefault.class),   // e.g. -b 27075 5.0.12
    BUG_29978(DomainPublicServiceProtocolAndPort.class),
    // BUG_31284(ZimbraPrefFromDisplay.class),
    BUG_31694(ZimbraMessageCacheSize.class),
    BUG_32557(DomainObjectClassAmavisAccount.class),
    BUG_32719(ZimbraHsmPolicy.class),
    BUG_33814(ZimbraMtaAuthEnabled.class),
    BUG_41000(ZimbraGalLdapFilterDef_zimbraAutoComplete_zimbraSearch.class),
    BUG_42877(ZimbraGalLdapAttrMap.class),
    BUG_42896(ZimbraMailQuota_constraint.class),
    BUG_43147(GalSyncAccountContactLimit.class),
    BUG_46297(ZimbraContactHiddenAttributes.class),
    BUG_46883(ZimbraContactRankingTableSize.class),
    BUG_46961(ZimbraGalLdapAttrMap_fullName.class),
    BUG_42828(ZimbraGalLdapAttrMap_ZimbraContactHiddenAttributes_externalCRandGroup.class),
    BUG_43779(ZimbraGalLdapFilterDef_zimbraGroup.class),
    BUG_47934(Bug47934.class),
    BUG_50258(ZimbraMtaSaslAuthEnable.class),
    BUG_50465(DisableBriefcase.class),
    BUG_50458(Bug50458.class),
    BUG_53745(Bug53745.class),
    BUG_55649(Bug55649.class),
    BUG_57039(Bug57039.class),
    BUG_57425(Bug57425.class),
    BUG_57855(Bug57855.class),
    BUG_58084(Bug58084.class),
    BUG_58481(Bug58481.class),
    BUG_58514(Bug58514.class),
    BUG_59720(Bug59720.class),
    BUG_63475(Bug63475.class);

    
    private Class mUpgradeClass;
    
    UpgradeTask(Class klass) {
        mUpgradeClass = klass;
    }

    static UpgradeTask fromString(String bugNumber) throws ServiceException {
        String bug = "BUG_" + bugNumber;
        
        try {
            return UpgradeTask.valueOf(bug);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    
    String getBugNumber() {
        String bug = this.name();
        return bug.substring(4);
    }
    
    LdapUpgrade getUpgrader() throws ServiceException {
        try {
            Object obj = mUpgradeClass.newInstance();
            if (obj instanceof LdapUpgrade) {
                LdapUpgrade ldapUpgrade = (LdapUpgrade)obj;
                ldapUpgrade.setBug(getBugNumber());
                return ldapUpgrade;
            }
        } catch (IllegalAccessException e) {
            throw ServiceException.FAILURE("IllegalAccessException", e);
        } catch (InstantiationException e) {
            throw ServiceException.FAILURE("InstantiationException", e);
        }
        throw ServiceException.FAILURE("unable to instantiate upgrade object", null);
    }

    public static void main(String[] args) throws ServiceException {
        // sanity test
        for (UpgradeTask upgradeTask : UpgradeTask.values()) {
            LdapUpgrade upgrade = upgradeTask.getUpgrader();
            
            System.out.println("====================================");
            System.out.println("Testing " + upgrade.getBug() + " ");
            
            upgrade.setVerbose(true);
            upgrade.doUpgrade();
        }
    }
    
}

