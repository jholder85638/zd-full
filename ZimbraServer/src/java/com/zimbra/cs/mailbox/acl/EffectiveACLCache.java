/*
 * 
 */

package com.zimbra.cs.mailbox.acl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.common.util.memcached.MemcachedMap;
import com.zimbra.common.util.memcached.MemcachedSerializer;
import com.zimbra.common.util.memcached.ZimbraMemcachedClient;
import com.zimbra.cs.index.SortBy;
import com.zimbra.cs.mailbox.ACL;
import com.zimbra.cs.mailbox.Folder;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.MetadataList;
import com.zimbra.cs.memcached.MemcachedConnector;
import com.zimbra.cs.session.PendingModifications;
import com.zimbra.cs.session.PendingModifications.Change;
import com.zimbra.cs.session.PendingModifications.ModificationKey;

public class EffectiveACLCache {
    
    private static EffectiveACLCache sTheInstance = new EffectiveACLCache();
    
    private MemcachedMap<EffectiveACLCacheKey, ACL> mMemcachedLookup;

    public static EffectiveACLCache getInstance() { return sTheInstance; }

    EffectiveACLCache() {
        ZimbraMemcachedClient memcachedClient = MemcachedConnector.getClient();
        ACLSerializer serializer = new ACLSerializer();
        mMemcachedLookup = new MemcachedMap<EffectiveACLCacheKey, ACL>(memcachedClient, serializer); 
    }

    private static class ACLSerializer implements MemcachedSerializer<ACL> {
        
        public Object serialize(ACL value) {
            return value.encode().toString();
        }

        public ACL deserialize(Object obj) throws ServiceException {
            MetadataList meta = new MetadataList((String) obj);
            return new ACL(meta);
        }
    }
    
    private ACL get(EffectiveACLCacheKey key) throws ServiceException {
        return mMemcachedLookup.get(key);
    }
    
    private void put(EffectiveACLCacheKey key, ACL data) throws ServiceException {
        mMemcachedLookup.put(key, data);
    }
    
    public static ACL get(String acctId, int folderId) throws ServiceException {
        EffectiveACLCacheKey key = new EffectiveACLCacheKey(acctId, folderId);
        return sTheInstance.get(key);
    }
    
    public static void put(String acctId, int folderId, ACL acl) throws ServiceException {
        EffectiveACLCacheKey key = new EffectiveACLCacheKey(acctId, folderId);
        
        // if no effective ACL, return an empty ACL
        if (acl == null)
            acl = new ACL();
        sTheInstance.put(key, acl);
    }

    public void purgeMailbox(Mailbox mbox) throws ServiceException {
        String accountId = mbox.getAccountId();
        List<Folder> folders = mbox.getFolderList(null, SortBy.NONE);
        List<EffectiveACLCacheKey> keys = new ArrayList<EffectiveACLCacheKey>(folders.size());
        for (Folder folder : folders) {
            EffectiveACLCacheKey key = new EffectiveACLCacheKey(accountId, folder.getId());
            keys.add(key);
        }
        mMemcachedLookup.removeMulti(keys);
    }

    public void notifyCommittedChanges(PendingModifications mods, int changeId) {
        Set<EffectiveACLCacheKey> keysToInvalidate = new HashSet<EffectiveACLCacheKey>();
        if (mods.modified != null) {
            for (Map.Entry<ModificationKey, Change> entry : mods.modified.entrySet()) {
                Change change = entry.getValue();
                Object whatChanged = change.what;
                // We only need to pay attention to modified folders whose modification involves
                // permission change or move to a new parent folder.
                if (whatChanged instanceof Folder &&
                    (change.why & (Change.MODIFIED_ACL | Change.MODIFIED_FOLDER)) != 0) {
                    Folder folder = (Folder) whatChanged;
                    // Invalidate all child folders because their inherited ACL will need to be recomputed.
                    String acctId = folder.getMailbox().getAccountId();
                    List<Folder> subfolders = folder.getSubfolderHierarchy();  // includes "folder" folder
                    for (Folder subf : subfolders) {
                        EffectiveACLCacheKey key = new EffectiveACLCacheKey(acctId, subf.getId());
                        keysToInvalidate.add(key);
                    }
                }
            }
        }
        if (mods.deleted != null) {
            // This code gets called even for non-folder items, for example it's called for every email
            // being emptied from Trash.  But there's no way to short circuit out of here because the delete
            // notification doesn't tell us the item type of what's being deleted.  Oh well.
            for (Map.Entry<ModificationKey, Object> entry : mods.deleted.entrySet()) {
                Object deletedObj = entry.getValue();
                if (deletedObj instanceof Folder) {
                    Folder folder = (Folder) deletedObj;
                    EffectiveACLCacheKey key = new EffectiveACLCacheKey(folder.getMailbox().getAccountId(), folder.getId());
                    keysToInvalidate.add(key);
                } else if (deletedObj instanceof Integer) {
                    // We only have item id.  Assume it's a folder id and issue a delete.
                    String acctId = entry.getKey().getAccountId();
                    if (acctId == null) continue;  // just to be safe
                    int itemId = ((Integer) deletedObj).intValue();
                    EffectiveACLCacheKey key = new EffectiveACLCacheKey(acctId, itemId);
                    keysToInvalidate.add(key);
                }
            }
        }
        try {
            mMemcachedLookup.removeMulti(keysToInvalidate);
        } catch (ServiceException e) {
            ZimbraLog.calendar.warn("Unable to notify folder acl cache.  Some cached data may become stale.", e);
        }
    }
}
