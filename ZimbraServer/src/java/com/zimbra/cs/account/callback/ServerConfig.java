/*
 * 
 */
package com.zimbra.cs.account.callback;

import java.util.Map;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.account.AttributeCallback;
import com.zimbra.cs.account.Entry;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.db.DbPool;
import com.zimbra.cs.mailbox.MessageCache;
import com.zimbra.cs.store.BlobInputStream;
import com.zimbra.cs.store.StorageCallback;
import com.zimbra.cs.util.JMSession;
import com.zimbra.cs.util.Zimbra;

/**
 * Central place for updating server attributes that we cache in memory.
 */
public class ServerConfig extends AttributeCallback {

    @Override
    public void postModify(Map context, String attrName, Entry entry,
                           boolean isCreate) {
        
        // do not run this callback unless inside the server
        if (!Zimbra.started())
            return;
        
        try {
            if (attrName.equals(Provisioning.A_zimbraMailUncompressedCacheMaxBytes) ||
                attrName.equals(Provisioning.A_zimbraMailUncompressedCacheMaxFiles) ||
                attrName.equals(Provisioning.A_zimbraMailFileDescriptorCacheSize)) {
                BlobInputStream.getFileDescriptorCache().loadSettings();
            } else if (attrName.equals(Provisioning.A_zimbraMailDiskStreamingThreshold)) {
                StorageCallback.loadSettings();
            } else if (attrName.equals(Provisioning.A_zimbraMessageCacheSize)) {
                MessageCache.loadSettings();
            } else if (attrName.equals(Provisioning.A_zimbraSmtpHostname)) {
                JMSession.resetSmtpHosts();
            } else if (attrName.equals(Provisioning.A_zimbraDatabaseSlowSqlThreshold)) {
                DbPool.loadSettings();
            }
        } catch (ServiceException e) {
            ZimbraLog.account.warn("Unable to update %s.", attrName, e);
        }
    }

    @Override
    public void preModify(Map context, String attrName, Object attrValue,
                          Map attrsToModify, Entry entry, boolean isCreate)
    throws ServiceException {
    }

}
