/*
 * 
 */

package com.zimbra.cs.account;

import java.util.Map;

import com.zimbra.common.mailbox.ContactConstants;
import com.zimbra.common.service.ServiceException;

import com.zimbra.cs.gal.GalSearchConfig.GalType;

/**
 * @author schemers
 */
public class GalContact implements Comparable {
    
    public interface Visitor  {
        public void visit(GalContact gc) throws ServiceException;
    }
   
    private GalType mGalType;
    private Map<String, Object> mAttrs;
    private String mId;
    private String mSortField;

    public GalContact(String dn, Map<String,Object> attrs) {
        mId = dn;
        mAttrs = attrs;
    }
    
    public GalContact(GalType galType, String dn, Map<String,Object> attrs) {
        mGalType = galType;
        mId = dn;
        mAttrs = attrs;
    }

    public boolean isZimbraGal() {
        return GalType.zimbra == mGalType;
    }
    
    /* (non-Javadoc)
     * @see com.zimbra.cs.account.GalContact#getId()
     */
    public String getId() {
        return mId;
    }

    /* (non-Javadoc)
     * @see com.zimbra.cs.account.GalContact#getAttrs()
     */
    public Map<String, Object> getAttrs() {
        return mAttrs;
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("LdapGalContact: { ");
        sb.append("id="+mId);
        sb.append("}");
        return sb.toString();
    }

    public String getSingleAttr(String name) {
        Object val = mAttrs.get(name);
        if (val instanceof String) return (String) val;
        else if (val instanceof String[]) return ((String[])val)[0];
        else return null;
    }
    
    public boolean isGroup() {
        return ContactConstants.TYPE_GROUP.equals(getSingleAttr(ContactConstants.A_type));
    }
    
    private String getSortField() {
        if (mSortField != null) return mSortField;
        
        mSortField = getSingleAttr(ContactConstants.A_fullName);
        if (mSortField != null) return mSortField;

        String first = getSingleAttr(ContactConstants.A_firstName);
        String last = getSingleAttr(ContactConstants.A_lastName);
        
        if (first != null || last != null) {
            StringBuilder sb = new StringBuilder();
            if (first != null) {
                sb.append(first);
            }
            if (last != null) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(last);
            }
            mSortField = sb.toString();
        } else {
            mSortField = getSingleAttr(ContactConstants.A_email);
            if (mSortField == null) mSortField = "";
        }
        return mSortField;
    }
    
    public int compareTo(Object obj) {
        if (!(obj instanceof GalContact))
            return 0;
        GalContact other = (GalContact) obj;
        return getSortField().compareTo(other.getSortField());
    }
}
