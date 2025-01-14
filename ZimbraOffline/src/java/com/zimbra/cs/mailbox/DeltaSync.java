/*
 * 
 */
package com.zimbra.cs.mailbox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.zimbra.common.mailbox.ContactConstants;
import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.Element;
import com.zimbra.common.soap.MailConstants;
import com.zimbra.common.soap.SoapFaultException;
import com.zimbra.common.util.Pair;
import com.zimbra.common.util.StringUtil;
import com.zimbra.cs.mailbox.ChangeTrackingMailbox.TracelessContext;
import com.zimbra.cs.mime.ParsedContact;
import com.zimbra.cs.offline.Offline;
import com.zimbra.cs.offline.OfflineLC;
import com.zimbra.cs.offline.OfflineLog;
import com.zimbra.cs.offline.OfflineSyncManager;
import com.zimbra.cs.offline.util.ExtractData;
import com.zimbra.cs.service.UserServlet;
import com.zimbra.cs.service.mail.Sync;
import com.zimbra.cs.service.offline.OfflineDialogAction;
import com.zimbra.cs.session.PendingModifications.Change;

public class DeltaSync {

    private static final TracelessContext sContext = new TracelessContext();

    private final ZcsMailbox ombx;
    private final MailboxSync mMailboxSync;
    private final Set<Integer> mSyncRenames = new HashSet<Integer>();
    private InitialSync isync;
    private static final Set<Integer> LAST_SYNC_FOLDERS = new HashSet<Integer>();

    static {
        LAST_SYNC_FOLDERS.add(Mailbox.ID_FOLDER_TRASH);
        LAST_SYNC_FOLDERS.add(Mailbox.ID_FOLDER_SPAM);
    }

    DeltaSync(ZcsMailbox mbox) {
        ombx = mbox;
        mMailboxSync = ombx.getMailboxSync();
    }

    DeltaSync(InitialSync initial) {
        ombx = initial.getMailbox();
        mMailboxSync = ombx.getMailboxSync();
        isync = initial;
    }

    ZcsMailbox getMailbox() {
        return ombx;
    }

    private InitialSync getInitialSync() {
        if (isync == null)
            isync = new InitialSync(this);
        return isync;
    }

    private TagSync getTagSync() {
        return ombx.getTagSync();
    }

    public static void sync(ZcsMailbox ombx) throws ServiceException {
        new DeltaSync(ombx).sync();
    }

    void sync() throws ServiceException {
        String oldToken = mMailboxSync.getSyncToken();
        assert (oldToken != null);

        Element response = null;
        String newToken;
        // keep delta sync'ing until the server tells us the delta sync is complete ("more=0")
        do {
            Element request = new Element.XMLElement(MailConstants.SYNC_REQUEST).addAttribute(MailConstants.A_TOKEN,
                    oldToken).addAttribute(MailConstants.A_TYPED_DELETES, true);
            try {
                response = ombx.sendRequestWithNotification(request);
            } catch (SoapFaultException e) {
                //TODO use MailServiceException.MUST_RESYNC when branched from main
                if ("mail.MUST_RESYNC".equals(e.getCode())) {
                    OfflineLog.offline.warn("sync token is too old, must resync");
                    this.ombx.cancelCurrentTask();
                    OfflineSyncManager.getInstance().registerDialog(this.ombx.getAccountId(),
                            new Pair<String, String>(OfflineDialogAction.DIALOG_TYPE_RESYNC,
                                    OfflineDialogAction.DIALOG_RESYNC_MSG));
                    throw OfflineServiceException.MUST_RESYNC();
                } else {
                    throw e;
                }
            }

            newToken = response.getAttribute(MailConstants.A_TOKEN);

            if (StringUtil.equal(newToken, oldToken) && !response.getAttributeBool(MailConstants.A_QUERY_MORE, false)) {
                OfflineLog.offline.debug("skipping delta sync [token " + oldToken + "] unchanged");
            } else {
                OfflineLog.offline.debug("starting delta sync [token " + oldToken + ']');
                deltaSync(response);
                SyncExceptionHandler.checkIOExceptionRate(ombx);
                // update the stored sync progress and loop again with new token if sync was incomplete
                mMailboxSync.recordSyncComplete(newToken);
                oldToken = newToken;
                OfflineLog.offline.debug("ending delta sync [token " + newToken + ']');
            }
        } while (response.getAttributeBool(MailConstants.A_QUERY_MORE, false));
    }

    private void deltaSync(Element response) throws ServiceException {
        boolean isInitSyncDone = mMailboxSync.isInitialSyncComplete();

        // make sure to handle deletes first, as tags can reuse ids
        Set<Integer> foldersToDelete = null;
        Element delement = response.getOptionalElement(MailConstants.E_DELETED);
        if (delement != null) {
            delement.detach();
            if (isInitSyncDone)
                foldersToDelete = processLeafDeletes(delement);
        }

        // sync down metadata changes and note items that need to be downloaded in full
        Map<Integer, List<Integer>> messages = new HashMap<Integer, List<Integer>>();
        Map<Integer, List<Integer>> modmsgs = new HashMap<Integer, List<Integer>>();
        Map<Integer, List<Integer>> chats = new HashMap<Integer, List<Integer>>();
        Map<Integer, List<Integer>> modchats = new HashMap<Integer, List<Integer>>();
        Map<Integer, Integer> deltamsgs = new HashMap<Integer, Integer>();
        Map<Integer, Integer> deltachats = new HashMap<Integer, Integer>();
        Multimap<Integer, String> contacts = ArrayListMultimap.create(); //<folderId, contactId>
        Map<Integer, Integer> appts = new HashMap<Integer, Integer>();
        Map<Integer, Integer> tasks = new HashMap<Integer, Integer>();
        List<Integer> documents = new ArrayList<Integer>();
        List<Element> lastToSyncItems = new ArrayList<Element>();

        for (Element change : response.listElements()) {
            OfflineSyncManager.getInstance().continueOK();
            int id = (int) change.getAttributeLong(MailConstants.A_ID);
            String type = change.getName();

            if (type.equals(MailConstants.E_TAG)) {
                syncTag(change);
                continue;
            } else if (type.equals(MailConstants.E_CONV)) {
                continue;
            }

            int folderId = (id == Mailbox.ID_FOLDER_ROOT ? Mailbox.ID_FOLDER_ROOT : (int) change
                    .getAttributeLong(MailConstants.A_FOLDER));
            if (!isInitSyncDone && getFolder(folderId) == null) {
                continue;
            }

            // for bug 32238, sync trash folder last
            if (LAST_SYNC_FOLDERS.contains(folderId)) {
                lastToSyncItems.add(change);
                continue;
            }

            prepareSync(messages, modmsgs, chats, modchats, deltamsgs, deltachats, contacts, appts, tasks, documents,
                    change, folderId);
        }

        syncItems(isInitSyncDone, foldersToDelete, messages, modmsgs, chats, modchats, deltamsgs, deltachats, contacts,
                appts, tasks, documents);

        messages.clear();
        modmsgs.clear();
        chats.clear();
        modchats.clear();
        deltamsgs.clear();
        deltachats.clear();
        contacts.clear();
        appts.clear();
        tasks.clear();
        documents.clear();

        for (Element change : lastToSyncItems) {
            OfflineSyncManager.getInstance().continueOK();
            int id = (int) change.getAttributeLong(MailConstants.A_ID);
            int folderId = (id == Mailbox.ID_FOLDER_ROOT ? Mailbox.ID_FOLDER_ROOT
                    : (int) change.getAttributeLong(MailConstants.A_FOLDER));
            prepareSync(messages, modmsgs, chats, modchats, deltamsgs, deltachats, contacts, appts, tasks, documents,
                    change, folderId);
        }

        syncItems(isInitSyncDone, foldersToDelete, messages, modmsgs, chats, modchats, deltamsgs, deltachats, contacts,
                appts, tasks, documents);
    }

    private void syncItems(boolean isInitSyncDone, Set<Integer> foldersToDelete, Map<Integer, List<Integer>> messages,
            Map<Integer, List<Integer>> modmsgs, Map<Integer, List<Integer>> chats,
            Map<Integer, List<Integer>> modchats, Map<Integer, Integer> deltamsgs, Map<Integer, Integer> deltachats,
            Multimap<Integer, String> contacts, Map<Integer, Integer> appts, Map<Integer, Integer> tasks,
            List<Integer> documents) throws ServiceException {
        // for messages, chats, and contacts that are created or had their content modified, fetch new content
        if (OfflineLC.zdesktop_sync_messages.booleanValue() && !deltamsgs.isEmpty()) {
            Element request = new Element.XMLElement(MailConstants.GET_MSG_METADATA_REQUEST);
            request.addElement(MailConstants.E_MSG).addAttribute(MailConstants.A_IDS,
                    StringUtil.join(",", deltamsgs.keySet()));
            for (Element elt : ombx.sendRequest(request).listElements())
                syncMessage(elt, deltamsgs.get((int) elt.getAttributeLong(MailConstants.A_ID)), MailItem.TYPE_MESSAGE);
        }

        // sync appointments before messages so that new invite messages can be linked to appointments
        if (OfflineLC.zdesktop_sync_appointments.booleanValue() && !appts.isEmpty()) {
            for (Map.Entry<Integer, Integer> entry : appts.entrySet())
                getInitialSync().syncCalendarItem(entry.getKey(), entry.getValue(), true);
        }

        if (OfflineLC.zdesktop_sync_tasks.booleanValue() && !tasks.isEmpty()) {
            for (Map.Entry<Integer, Integer> entry : tasks.entrySet())
                getInitialSync().syncCalendarItem(entry.getKey(), entry.getValue(), false);
        }

        if (OfflineLC.zdesktop_sync_messages.booleanValue()) {
            for (int folderId : messages.keySet())
                getInitialSync().syncMessagelikeItems(messages.get(folderId), folderId, MailItem.TYPE_MESSAGE, false,
                        true);

            for (int folderId : modmsgs.keySet())
                getInitialSync().syncMessagelikeItems(modmsgs.get(folderId), folderId, MailItem.TYPE_MESSAGE, true,
                        true);
        }

        if (OfflineLC.zdesktop_sync_chats.booleanValue() && !deltachats.isEmpty()) {
            Element request = new Element.XMLElement(MailConstants.GET_MSG_METADATA_REQUEST);
            request.addElement(MailConstants.E_MSG).addAttribute(MailConstants.A_IDS,
                    StringUtil.join(",", deltachats.keySet()));
            for (Element elt : ombx.sendRequest(request).listElements())
                syncMessage(elt, deltachats.get((int) elt.getAttributeLong(MailConstants.A_ID)), MailItem.TYPE_CHAT);
        }

        if (OfflineLC.zdesktop_sync_chats.booleanValue()) {
            for (int folderId : chats.keySet())
                getInitialSync().syncMessagelikeItems(chats.get(folderId), folderId, MailItem.TYPE_CHAT, false, true);

            for (int folderId : modchats.keySet())
                getInitialSync().syncMessagelikeItems(modchats.get(folderId), folderId, MailItem.TYPE_CHAT, true, true);
        }

        if (OfflineLC.zdesktop_sync_contacts.booleanValue() && !contacts.isEmpty()) {
            for (Integer folderId: contacts.keySet()) {
                getInitialSync().syncContacts(contacts.get(folderId), folderId);
            }
        }

        if (isInitSyncDone && OfflineLC.zdesktop_sync_documents.booleanValue() && !documents.isEmpty())
            try {
                syncDocuments(documents);
            } catch (Exception t) {
                OfflineLog.offline.error("delta: error syncing Documents.", t);
            }

        // delete any deleted folders, starting from the bottom of the tree
        if (isInitSyncDone && foldersToDelete != null && !foldersToDelete.isEmpty()) {
            synchronized (ombx) {
                List<Folder> folders = ombx.getFolderById(sContext, Mailbox.ID_FOLDER_ROOT).getSubfolderHierarchy();
                Collections.reverse(folders);
                for (Folder folder : folders) {
                    if (foldersToDelete.remove(folder.getId()))
                        processFolderDelete(folder);
                }
            }
        }
    }

    private void prepareSync(Map<Integer, List<Integer>> messages, Map<Integer, List<Integer>> modmsgs,
            Map<Integer, List<Integer>> chats, Map<Integer, List<Integer>> modchats, Map<Integer, Integer> deltamsgs,
            Map<Integer, Integer> deltachats, Multimap<Integer, String> contacts, Map<Integer, Integer> appts,
            Map<Integer, Integer> tasks, List<Integer> documents, Element change, int folderId) throws ServiceException {
        int id = (int) change.getAttributeLong(MailConstants.A_ID);
        String type = change.getName();
        boolean create = (change.getAttribute(MailConstants.A_FLAGS, null) == null);
        if (type.equals(MailConstants.E_MSG)) {
            if (!OfflineLC.zdesktop_sync_messages.booleanValue())
                return;
            if (OfflineSyncManager.getInstance().isInSkipList(id)) {
                OfflineLog.offline.warn("Skipped message id=%d per zdesktop_sync_skip_idlist", id);
                return;
            }

            if (ombx.isPendingDelete(sContext, id, MailItem.TYPE_MESSAGE))
                return;

            if (create) {
                triageNewMessage(id, folderId, change, messages, modmsgs, deltamsgs);
            } else {
                syncMessage(change, folderId, MailItem.TYPE_MESSAGE);
            }
        } else if (type.equals(MailConstants.E_CHAT)) {
            if (!OfflineLC.zdesktop_sync_chats.booleanValue())
                return;
            if (OfflineSyncManager.getInstance().isInSkipList(id)) {
                OfflineLog.offline.warn("Skipped chat id=%d per zdesktop_sync_skip_idlist", id);
                return;
            }

            if (ombx.isPendingDelete(sContext, id, MailItem.TYPE_CHAT))
                return;

            if (create) {
                triageNewMessage(id, folderId, change, chats, modchats, deltachats);
            } else {
                syncMessage(change, folderId, MailItem.TYPE_CHAT);
            }
        } else if (type.equals(MailConstants.E_CONTACT)) {
            if (!OfflineLC.zdesktop_sync_contacts.booleanValue())
                return;
            if (OfflineSyncManager.getInstance().isInSkipList(id)) {
                OfflineLog.offline.warn("Skipped contact id=%d per zdesktop_sync_skip_idlist", id);
                return;
            }

            if (ombx.isPendingDelete(sContext, id, MailItem.TYPE_CONTACT))
                return;

            if (create) {
                contacts.put(folderId, id+"");
            } else {
                syncContact(change, folderId);
            }
        } else if (type.equals(MailConstants.E_APPOINTMENT)) {
            if (!OfflineLC.zdesktop_sync_appointments.booleanValue())
                return;
            if (OfflineSyncManager.getInstance().isInSkipList(id)) {
                OfflineLog.offline.warn("Skipped appointment id=%d per zdesktop_sync_skip_idlist", id);
                return;
            }

            if (ombx.isPendingDelete(sContext, id, MailItem.TYPE_APPOINTMENT))
                return;

            appts.put(id, folderId);
        } else if (type.equals(MailConstants.E_TASK)) {
            if (!OfflineLC.zdesktop_sync_tasks.booleanValue())
                return;
            if (OfflineSyncManager.getInstance().isInSkipList(id)) {
                OfflineLog.offline.warn("Skipped task id=%d per zdesktop_sync_skip_idlist", id);
                return;
            }

            if (ombx.isPendingDelete(sContext, id, MailItem.TYPE_TASK))
                return;

            tasks.put(id, folderId);
        } else if (type.equals(MailConstants.E_DOC)) {
            if (!OfflineLC.zdesktop_sync_documents.booleanValue()
                    || !ombx.getRemoteServerVersion().isAtLeast(InitialSync.sMinDocumentSyncVersion))
                return;
            if (OfflineSyncManager.getInstance().isInSkipList(id)) {
                OfflineLog.offline.warn("Skipped document id=%d per zdesktop_sync_skip_idlist", id);
                return;
            }

            if (ombx.isPendingDelete(sContext, id, MailItem.TYPE_UNKNOWN))
                return;

            documents.add(id);
        } else if (type.equals(MailConstants.E_WIKIWORD)) {
            return;
        } else if (InitialSync.KNOWN_FOLDER_TYPES.contains(type)) {
            // can't tell new folders from modified ones, so might as well go through the initial sync process
            syncContainer(change, id);
        }
    }

    private void triageNewMessage(int id, int folderId, Element change, Map<Integer, List<Integer>> messages,
            Map<Integer, List<Integer>> modmsgs, Map<Integer, Integer> deltamsgs) throws ServiceException {
        try {
            Message msg = ombx.getMessageById(sContext, id);
            if (msg.getSavedSequence() != change.getAttributeLong(MailConstants.A_REVISION, -1))
                addToMapByFolderId(id, folderId, modmsgs);
            else if (msg.getModifiedSequence() != change.getAttributeLong(MailConstants.A_MODIFIED_SEQUENCE))
                deltamsgs.put(id, folderId);
            else
                // could be because we are rerunning a failed sync
                OfflineLog.offline.debug("message %d (%s) already in sync", id, msg.getSubject());
        } catch (MailServiceException.NoSuchItemException e) {
            addToMapByFolderId(id, folderId, messages);
        }
    }

    private void addToMapByFolderId(int id, int folderId, Map<Integer, List<Integer>> messages) {
        List<Integer> idList = messages.get(folderId);
        if (idList == null) {
            idList = new ArrayList<Integer>();
            messages.put(folderId, idList);
        }
        idList.add(id);
    }

    private Set<Integer> processLeafDeletes(Element delement) throws ServiceException {
        // sort the deleted items into a bucket of leaf nodes to delete now and a set of folders to delete later
        List<Integer> leafIds = new ArrayList<Integer>(), tagIds = new ArrayList<Integer>();
        Set<Integer> foldersToDelete = new HashSet<Integer>();

        // sort the deleted items into two sets: leaves and folders
        for (Element deltype : delement.listElements()) {
            byte type = Sync.typeForElementName(deltype.getName());
            if (type == MailItem.TYPE_UNKNOWN || type == MailItem.TYPE_CONVERSATION)
                continue;
            boolean isTag = type == MailItem.TYPE_TAG;
            boolean isFolder = InitialSync.KNOWN_FOLDER_TYPES.contains(deltype.getName());
            for (String idStr : deltype.getAttribute(MailConstants.A_IDS).split(",")) {
                Integer id = Integer.valueOf(idStr);
                if (isTag) {
                    id = getTagSync().localTagId(id);
                    if (!Tag.validateId(id)) {
                        //if it's an overflow still need to delete the mapping
                        getTagSync().removeTagMapping(id);
                        continue;
                    }
                }
                Integer mask = ombx.getChangeMask(sContext, id, type);
                // tag numbering conflict issues: don't delete tags we've created locally
                if (isTag && (mask & Change.MODIFIED_CONFLICT) != 0)
                    continue;
                (isFolder ? foldersToDelete : leafIds).add(id);
                if (isTag)
                    tagIds.add(id);
            }
        }

        if (!ombx.getRemoteServerVersion().isAtLeast7xx()) {
            //original comment below indicates it's just for 4.5.1 and earlier...but not worth the time to regression test against ZCS 5+6
            // temporary workaround because tags are left off the "typed delete" list up to 4.5.2
            for (String idStr : delement.getAttribute(MailConstants.A_IDS).split(",")) {
                Integer id = Integer.valueOf(idStr);
                if (id < MailItem.TAG_ID_OFFSET || id >= MailItem.TAG_ID_OFFSET + MailItem.MAX_TAG_COUNT || tagIds.contains(id))
                    continue;
                // tag numbering conflict issues: don't delete tags we've created locally
                if ((ombx.getChangeMask(sContext, id, MailItem.TYPE_TAG) & Change.MODIFIED_CONFLICT) != 0)
                    continue;
                leafIds.add(id);
                tagIds.add(id);
            }
        }

        // delete all the leaves now
        int idx = 0, ids[] = new int[leafIds.size()];
        for (int id : leafIds)
            ids[idx++] = id;
        ombx.delete(sContext, ids, MailItem.TYPE_UNKNOWN, null);
        OfflineLog.offline.debug("delta: deleted leaves: " + Arrays.toString(ids));

        // avoid some nasty corner cases caused by tag id reuse
        for (int tagId : tagIds) {
            ombx.removePendingDelete(sContext, tagId, MailItem.TYPE_TAG);
            getTagSync().removeTagMapping(tagId);
        }
        // save the folder deletes for later
        return (foldersToDelete.isEmpty() ? null : foldersToDelete);
    }

    private void processFolderDelete(Folder folder) throws ServiceException {
        if (!folder.isDeletable()) {
            OfflineLog.offline.warn("delta: cannot delete " + MailItem.getNameForType(folder) + " (" + folder.getId()
                    + "): " + folder.getName());
            return;
        }

        if (folder.getItemCount() == 0 && !folder.hasSubfolders()) {
            // normal case: contents have already been deleted via processLeafDeletes()
            ombx.delete(sContext, folder.getId(), folder.getType());
            OfflineLog.offline.debug("delta: deleted folder: " + folder.getId());
            return;
        }

        // mark the remote folder for re-creation in order to hold its local contents
        ombx.setChangeMask(sContext, folder.getId(), folder.getType(), Change.MODIFIED_CONFLICT);
        // mark all the contents as moved so that they appear in the remote folder
        for (int contained : ombx.listItemIds(sContext, MailItem.TYPE_UNKNOWN, folder.getId())) {
            int change_mask = ombx.getChangeMask(sContext, contained, MailItem.TYPE_UNKNOWN);
            if ((change_mask & (Change.MODIFIED_FOLDER | Change.MODIFIED_CONFLICT)) == 0)
                ombx.setChangeMask(sContext, contained, MailItem.TYPE_UNKNOWN, change_mask | Change.MODIFIED_FOLDER);
        }
        OfflineLog.offline.debug("delta: queued folder for recreate: " + folder.getId());
    }

    private void syncContainer(Element elt, int id) throws ServiceException {
        String type = elt.getName();
        if (type.equalsIgnoreCase(MailConstants.E_SEARCH))
            syncSearchFolder(elt, id);
        else if (type.equalsIgnoreCase(MailConstants.E_FOLDER) || type.equalsIgnoreCase(MailConstants.E_MOUNT))
            syncFolder(elt, id, type);
    }

    void syncSearchFolder(Element elt, int id) throws ServiceException {
        String rgb = elt.getAttribute(MailConstants.A_RGB, null);
        byte color = (byte) elt.getAttributeLong(MailConstants.A_COLOR, MailItem.DEFAULT_COLOR);
        MailItem.Color itemColor = rgb != null ? new MailItem.Color(rgb) : new MailItem.Color(color);
        int flags = Flag.flagsToBitmask(elt.getAttribute(MailConstants.A_FLAGS, null));

        int date = (int) (elt.getAttributeLong(MailConstants.A_DATE, -1000) / 1000);

        String query = elt.getAttribute(MailConstants.A_QUERY);
        String searchTypes = elt.getAttribute(MailConstants.A_SEARCH_TYPES);
        String sort = elt.getAttribute(MailConstants.A_SORTBY);

        synchronized (ombx) {
            // deal with the case where the referenced search folder doesn't exist
            Folder folder = getFolder(id);
            if (folder == null) {
                // if it's been locally deleted but not pushed to the server yet, just return and let the delete happen later
                if (ombx.isPendingDelete(sContext, id, MailItem.TYPE_SEARCHFOLDER))
                    return;
                // resolve any naming conflicts and actually create the folder
                if (resolveFolderConflicts(elt, id, MailItem.TYPE_SEARCHFOLDER, folder)) {
                    getInitialSync().syncSearchFolder(elt, id);
                    return;
                } else {
                    folder = getFolder(id);
                }
            }

            // if the search folder was moved/renamed locally, that trumps any changes made remotely
            resolveFolderConflicts(elt, id, MailItem.TYPE_SEARCHFOLDER, folder);

            int parentId = (int) elt.getAttributeLong(MailConstants.A_FOLDER);
            String name = elt.getAttribute(MailConstants.A_NAME);

            int change_mask = ombx.getChangeMask(sContext, id, MailItem.TYPE_SEARCHFOLDER);
            ombx.rename(sContext, id, MailItem.TYPE_SEARCHFOLDER, name, parentId);
            if ((change_mask & Change.MODIFIED_QUERY) == 0)
                ombx.modifySearchFolder(sContext, id, query, searchTypes, sort);
            ombx.syncMetadata(sContext, id, MailItem.TYPE_SEARCHFOLDER, parentId, flags, 0, itemColor);
            ombx.syncDate(sContext, id, MailItem.TYPE_SEARCHFOLDER, date);

            if (elt.getAttributeBool(InitialSync.A_RELOCATED, false))
                ombx.setChangeMask(sContext, id, MailItem.TYPE_SEARCHFOLDER, change_mask | Change.MODIFIED_FOLDER
                        | Change.MODIFIED_NAME);

            OfflineLog.offline.debug("delta: updated search folder (" + id + "): " + name);
        }
    }

    void syncFolder(Element elt, int id, String type) throws ServiceException {
        int flags = Flag.flagsToBitmask(elt.getAttribute(MailConstants.A_FLAGS, null)) & ~Flag.BITMASK_UNREAD;
        String rgb = elt.getAttribute(MailConstants.A_RGB, null);
        byte color = (byte) elt.getAttributeLong(MailConstants.A_COLOR, MailItem.DEFAULT_COLOR);
        MailItem.Color itemColor = rgb != null ? new MailItem.Color(rgb) : new MailItem.Color(color);
        String url = elt.getAttribute(MailConstants.A_URL, null);
        byte itemType = type.equals(MailConstants.E_FOLDER) ? MailItem.TYPE_FOLDER : MailItem.TYPE_MOUNTPOINT;

        ACL acl = getInitialSync().parseACL(elt.getOptionalElement(MailConstants.E_ACL));

        int date = (int) (elt.getAttributeLong(MailConstants.A_DATE, -1000) / 1000);

        synchronized (ombx) {
            // deal with the case where the referenced folder doesn't exist
            Folder folder = getFolder(id);
            if (folder == null) {
                // if it's been locally deleted but not pushed to the server
                // yet, just return and let the delete happen later
                if (ombx.isPendingDelete(sContext, id, itemType))
                    return;
                // resolve any naming conflicts and actually create the folder
                if (resolveFolderConflicts(elt, id, itemType, folder)) {
                    getInitialSync().syncFolder(elt, id, type);
                    return;
                } else {
                    folder = getFolder(id);
                }
            }

            // if the folder was moved/renamed locally, that trumps any changes made remotely
            resolveFolderConflicts(elt, id, itemType, folder);

            int parentId = (id == Mailbox.ID_FOLDER_ROOT) ? id : (int) elt.getAttributeLong(MailConstants.A_FOLDER);
            String name = (id == Mailbox.ID_FOLDER_ROOT) ? "ROOT" : elt.getAttribute(MailConstants.A_NAME);

            int change_mask = ombx.getChangeMask(sContext, id, MailItem.TYPE_FOLDER);
            if (id != Mailbox.ID_FOLDER_ROOT)
                ombx.rename(sContext, id, itemType, name, parentId);
            // XXX: do we need to sync if the folder has perms but the new ACL is empty?
            if ((change_mask & Change.MODIFIED_ACL) == 0 && acl != null)
                ombx.setPermissions(sContext, id, acl);
            if ((change_mask & Change.MODIFIED_URL) == 0)
                ombx.setFolderUrl(sContext, id, url);

            if (itemType == MailItem.TYPE_FOLDER) {
                // don't care about current feed syncpoint; sync can't be done offline
                ombx.syncMetadata(sContext, id, MailItem.TYPE_FOLDER, parentId, flags, 0, itemColor);
                ombx.syncDate(sContext, id, MailItem.TYPE_FOLDER, date);
            }

            if (elt.getAttributeBool(InitialSync.A_RELOCATED, false))
                ombx.setChangeMask(sContext, id, itemType, change_mask | Change.MODIFIED_FOLDER | Change.MODIFIED_NAME);

            OfflineLog.offline.debug("delta: updated folder (" + id + "): " + name);
        }
    }

    private Folder getFolder(int id) throws ServiceException {
        try {
            return ombx.getFolderById(sContext, id);
        } catch (MailServiceException.NoSuchItemException nsie) {
            return null;
        }
    }

    private boolean resolveFolderConflicts(Element elt, int id, byte type, Folder local) throws ServiceException {
        int change_mask = (local == null ? 0 : ombx.getChangeMask(sContext, id, type));

        // if the folder was moved/renamed locally, that trumps any changes made remotely
        int parentId = (id == Mailbox.ID_FOLDER_ROOT) ? id : (int) elt.getAttributeLong(MailConstants.A_FOLDER);
        if ((change_mask & Change.MODIFIED_FOLDER) != 0) {
            parentId = local.getFolderId();
            elt.addAttribute(MailConstants.A_FOLDER, parentId);
        }

        String name = (id == Mailbox.ID_FOLDER_ROOT) ? "ROOT" : elt.getAttribute(MailConstants.A_NAME);
        if ((change_mask & Change.MODIFIED_NAME) != 0 && !mSyncRenames.contains(id)) {
            name = local.getName();
            elt.addAttribute(MailConstants.A_NAME, name);
        } else {
            // handle the off-chance that the server's folder name is invalid
            String validName = MailItem.normalizeItemName(name);
            if (!validName.equals(name)) {
                name = validName;
                elt.addAttribute(MailConstants.A_NAME, name).addAttribute(InitialSync.A_RELOCATED, true);
            }
        }

        // if the parent folder doesn't exist or is of an incompatible type, default to using the top-level user folder as the container
        Folder parent = getFolder(parentId);
        if (parent == null || !parent.canContain(type)) {
            parentId = Mailbox.ID_FOLDER_USER_ROOT;
            parent = getFolder(parentId);
            elt.addAttribute(MailConstants.A_FOLDER, parentId).addAttribute(InitialSync.A_RELOCATED, true);
        }

        Folder conflict = parent.findSubfolder(name);
        if (conflict != null) {
            if (conflict.getId() != id) {
                int conflict_mask = ombx.getChangeMask(sContext, conflict.getId(), conflict.getType());

                String uuid = '{' + UUID.randomUUID().toString() + '}', newName;
                if (name.length() + uuid.length() > MailItem.MAX_NAME_LENGTH)
                    newName = name.substring(0, MailItem.MAX_NAME_LENGTH - uuid.length()) + uuid;
                else
                    newName = name + uuid;

                if (local == null && (conflict_mask & Change.MODIFIED_CONFLICT) != 0
                        && isCompatibleFolder(conflict, elt, type)) {
                    // if the new and existing folders are identical and being created, try to merge them
                    ombx.renumberItem(sContext, conflict.getId(), type, id);
                    ombx.setChangeMask(sContext, id, type, conflict_mask & ~Change.MODIFIED_CONFLICT);
                    return false;
                } else if (!conflict.isMutable() || (conflict_mask & Change.MODIFIED_NAME) != 0) {
                    // either the local user also renamed the folder or the folder's immutable, so the local client wins
                    name = newName;
                    elt.addAttribute(MailConstants.A_NAME, name).addAttribute(InitialSync.A_RELOCATED, true);
                } else {
                    // if there's a folder naming conflict within the target folder, usually push the local folder out of the way
                    ombx.rename(null, conflict.getId(), conflict.getType(), newName);
                    if ((conflict_mask & Change.MODIFIED_NAME) == 0)
                        mSyncRenames.add(conflict.getId());
                }
            } else {
                String viewStr = elt.getAttribute(MailConstants.A_DEFAULT_VIEW, null);
                if (viewStr != null) {
                    byte defaultView = MailItem.getTypeForName(viewStr);
                    if (conflict.getDefaultView() != defaultView) {
                        ombx.syncFolderDefaultView(sContext, id, type, defaultView);
                    }
                }
            }
        }

        // if conflicts have forced us to deviate from the specified sync, update the local store such that these changes are pushed during the next sync
        if (local != null && elt.getAttributeBool(InitialSync.A_RELOCATED, false))
            ombx.rename(null, id, type, name, parentId);

        return true;
    }

    private boolean isCompatibleFolder(Folder folder, Element elt, byte type) {
        if (type != folder.getType())
            return false;

        if (type == MailItem.TYPE_FOLDER) {
            byte localView = folder.getDefaultView();
            byte remoteView = MailItem.getTypeForName(elt.getAttribute(MailConstants.A_DEFAULT_VIEW, null));
            return localView == remoteView || localView == MailItem.TYPE_MESSAGE && remoteView == MailItem.TYPE_UNKNOWN
                    || localView == MailItem.TYPE_UNKNOWN && remoteView == MailItem.TYPE_MESSAGE;
            // bug 33871: fileinto auto-created folder can be of UNKNOWN type
        } else
            return false;
    }

    void syncTag(Element elt) throws ServiceException {
        int id = getTagSync().localTagId(elt);
        String name = elt.getAttribute(MailConstants.A_NAME);
        String rgb = elt.getAttribute(MailConstants.A_RGB, null);
        byte color = (byte) elt.getAttributeLong(MailConstants.A_COLOR, MailItem.DEFAULT_COLOR);
        MailItem.Color itemColor = rgb != null ? new MailItem.Color(rgb) : new MailItem.Color(color);

        int date = (int) (elt.getAttributeLong(MailConstants.A_DATE) / 1000);

        synchronized (ombx) {
            Tag tag = null;
            if (id > 0) {
                tag = getTag(id);
            }

            // we're reusing tag IDs, so it's possible that we've got a conflict with a locally-created tag with that ID
            if (tag != null && !name.equalsIgnoreCase(tag.getName())
                    && (ombx.getChangeMask(sContext, id, MailItem.TYPE_TAG) & Change.MODIFIED_CONFLICT) != 0) {
                int newId = getAvailableTagId(ombx);
                if (newId < 0)
                    ombx.delete(sContext, id, MailItem.TYPE_TAG);
                else
                    ombx.renumberItem(sContext, id, MailItem.TYPE_TAG, newId);
                tag = null;
            }

            // deal with the case where the referenced tag doesn't exist
            if (tag == null) {
                // if it's been locally deleted but not pushed to the server yet, just return and let the delete happen later
                if (ombx.isPendingDelete(sContext, id, MailItem.TYPE_TAG))
                    return;
                // resolve any naming conflicts and actually create the tag
                if (resolveTagConflicts(elt, id, tag)) {
                    getInitialSync().syncTag(elt);
                    return;
                } else {
                    tag = getTag(getTagSync().localTagId(elt)); //should be mapped now; but id was set to -1 while we tried to map
                    id = tag.getId();
                }
            }

            // if the tag was renamed locally, that trumps any changes made remotely
            resolveTagConflicts(elt, id, tag);

            name = elt.getAttribute(MailConstants.A_NAME);

            int change_mask = ombx.getChangeMask(sContext, id, MailItem.TYPE_TAG);
            // FIXME: if FOO was renamed BAR and BAR was renamed FOO, this will break
            if ((change_mask & Change.MODIFIED_NAME) == 0)
                ombx.rename(sContext, id, MailItem.TYPE_TAG, name);
            if ((change_mask & Change.MODIFIED_COLOR) == 0)
                ombx.setColor(sContext, new int[] { id }, MailItem.TYPE_TAG, itemColor);
            ombx.syncDate(sContext, id, MailItem.TYPE_TAG, date);

            if (elt.getAttributeBool(InitialSync.A_RELOCATED, false))
                ombx.setChangeMask(sContext, id, MailItem.TYPE_TAG, change_mask | Change.MODIFIED_NAME);

            OfflineLog.offline.debug("delta: updated tag (" + id + "): " + name);
        }
    }

    private boolean resolveTagConflicts(Element elt, int id, Tag local) throws ServiceException {
        int change_mask = (local == null ? 0 : ombx.getChangeMask(sContext, id, MailItem.TYPE_TAG));

        String name = elt.getAttribute(MailConstants.A_NAME);
        if ((change_mask & Change.MODIFIED_NAME) != 0 && !mSyncRenames.contains(id)) {
            name = local.getName();
            elt.addAttribute(MailConstants.A_NAME, name);
        } else {
            // handle the off-chance that the server's folder name is invalid
            String validName = MailItem.normalizeItemName(name);
            if (!validName.equals(name)) {
                name = validName;
                elt.addAttribute(MailConstants.A_NAME, name).addAttribute(InitialSync.A_RELOCATED, true);
            }
        }

        Tag conflict = getTag(name);
        if (conflict == null)
            return true;

        int conflict_mask = ombx.getChangeMask(sContext, conflict.getId(), MailItem.TYPE_TAG);
        if (conflict.getId() == id) {
            // new tag remotely, new tag locally, same name
            if ((conflict_mask & Change.MODIFIED_CONFLICT) != 0)
                ombx.setChangeMask(sContext, conflict.getId(), MailItem.TYPE_TAG, conflict_mask & ~Change.MODIFIED_CONFLICT);
            return false;
        }

        String uuid = '{' + UUID.randomUUID().toString() + '}', newName;
        if (name.length() + uuid.length() > MailItem.MAX_NAME_LENGTH)
            newName = name.substring(0, MailItem.MAX_NAME_LENGTH - uuid.length()) + uuid;
        else
            newName = name + uuid;

        if (local == null && (conflict_mask & Change.MODIFIED_CONFLICT) != 0) {
            // if the new and existing tags are identical and being created, try to merge them
            if (getTagSync().isMappingRequired()) {
                getTagSync().mapTag(Integer.valueOf(elt.getAttribute(MailConstants.A_ID)), conflict.getId());
            } else {
                ombx.renumberItem(sContext, conflict.getId(), MailItem.TYPE_TAG, id);
            }
            ombx.setChangeMask(sContext, conflict.getId(), MailItem.TYPE_TAG, conflict_mask & ~Change.MODIFIED_CONFLICT);
            return false;
        } else if ((conflict_mask & Change.MODIFIED_NAME) != 0) {
            // the local user also renamed the tag, so the local client wins
            name = newName;
            elt.addAttribute(MailConstants.A_NAME, name).addAttribute(InitialSync.A_RELOCATED, true);
            if (local != null)
                ombx.rename(null, id, MailItem.TYPE_TAG, name);
        } else {
            // if there's a naming conflict, usually rename the local tag out of the way
            ombx.rename(null, conflict.getId(), MailItem.TYPE_TAG, newName);
            mSyncRenames.add(conflict.getId());
        }

        return true;
    }

    static int getAvailableTagId(Mailbox mbox) throws ServiceException {
        int tagId;
        for (tagId = MailItem.TAG_ID_OFFSET + MailItem.MAX_TAG_COUNT - 1; tagId >= MailItem.TAG_ID_OFFSET; tagId--)
            if (getTag(mbox, tagId) == null)
                break;
        return (tagId < MailItem.TAG_ID_OFFSET ? -1 : tagId);
    }

    private Tag getTag(int id) throws ServiceException {
        return getTag(ombx, id);
    }

    static Tag getTag(Mailbox mbox, int id) throws ServiceException {
        try {
            if (mbox instanceof ZcsMailbox) {
                return ((ZcsMailbox) mbox).getTagById(sContext, id, false);
            }
            return mbox.getTagById(sContext, id);
        } catch (MailServiceException.NoSuchItemException nsie) {
            return null;
        }
    }

    private Tag getTag(String name) throws ServiceException {
        try {
            return ombx.getTagByName(name);
        } catch (MailServiceException.NoSuchItemException nsie) {
            return null;
        }
    }

    void syncContact(Element elt, int folderId) throws ServiceException {
        int id = (int) elt.getAttributeLong(MailConstants.A_ID);
        ombx.recordItemSync(id);
        Contact cn = null;
        try {
            // make sure that the contact we're delta-syncing actually exists
            cn = ombx.getContactById(sContext, id);
        } catch (MailServiceException.NoSuchItemException nsie) {
            // if it's been locally deleted but not pushed to the server yet, just return and let the delete happen later
            if (ombx.isPendingDelete(sContext, id, MailItem.TYPE_CONTACT))
                return;
            // before passing off to initial sync, make sure we have the contact's fields
            if (elt.listElements(Element.XMLElement.E_ATTRIBUTE).isEmpty())
                elt = InitialSync.fetchContacts(ombx, id + "").get(0);
            getInitialSync().syncContact(elt, folderId);
            return;
        }

        String rgb = elt.getAttribute(MailConstants.A_RGB, null);
        byte color = (byte) elt.getAttributeLong(MailConstants.A_COLOR, MailItem.DEFAULT_COLOR);
        MailItem.Color itemColor = rgb != null ? new MailItem.Color(rgb) : new MailItem.Color(color);
        int flags = Flag.flagsToBitmask(elt.getAttribute(MailConstants.A_FLAGS, null));
        long tags = Tag.tagsToBitmask(getTagSync().localTagsFromElement(elt, null));

        boolean hasBlob = false;
        Map<String, String> fields = new HashMap<String, String>();
        for (Element eField : elt.listElements()) {
            if (!eField.getName().equals(MailConstants.E_METADATA)) {
                if (eField.getAttribute(MailConstants.A_PART, null) != null)
                    hasBlob = true;
                else {
                    if (eField.getName().equals(MailConstants.E_CONTACT_GROUP_MEMBER) && ombx.getRemoteServerVersion().getMajor() >= 8) {
                        String eName = "";
                        String email = "";
                        if (eField.getAttribute(MailConstants.A_CONTACT_TYPE).equals(MailConstants.GROUP_MEMBER_TYPE_CONTACT_REF) ||
                                eField.getAttribute(MailConstants.A_CONTACT_TYPE).equals(MailConstants.GROUP_MEMBER_TYPE_GAL_REF)) {
                            eName = ExtractData.getAttributefromChildren(eField, MailConstants.A_FILE_AS_STR, null);
                            if (eName == null) {
                                String fullName = ExtractData.getAttributefromChildren(eField, MailConstants.A_FULLNAME, null);
                                eName = fullName != null ? fullName : "";
                            }
                            if (!eName.isEmpty()) {
                                email = "\"" + eName + "\" <" + ExtractData.getAttributefromChildren(eField, MailConstants.A_EMAIL, null) + ">";
                            } else {
                                email = ExtractData.getAttributefromChildren(eField, MailConstants.A_EMAIL, null);
                            }
                        } else if (eField.getAttribute(MailConstants.A_CONTACT_TYPE).equals(MailConstants.GROUP_MEMBER_TYPE_INLINE)) {
                            email = eField.getAttribute(MailConstants.A_VALUE);
                        }

                        if (fields.get(ContactConstants.A_dlist) == null) {
                            fields.put(ContactConstants.A_dlist, email);
                        } else {
                            String dlist = fields.get(ContactConstants.A_dlist) + ", " + email;
                            fields.put(ContactConstants.A_dlist, dlist);
                        }
                    } else {
                        fields.put(eField.getAttribute(Element.XMLElement.A_ATTR_NAME), eField.getText());
                    }
                }
            }
        }
        assert (fields.isEmpty() || hasBlob == ((flags & Flag.BITMASK_ATTACHED) != 0));

        int date = (int) (elt.getAttributeLong(MailConstants.A_DATE) / 1000);

        byte[] blob = null;
        if (hasBlob) {
            String url = Offline.getServerURI(ombx.getAccount(), UserServlet.SERVLET_PATH + "/~/?fmt=native&id=" + id);
            if (ombx.getOfflineAccount().isDebugTraceEnabled())
                OfflineLog.request.debug("GET " + url);
            try {
                blob = UserServlet.getRemoteContent(ombx.getAuthToken(), url);
            } catch (MailServiceException.NoSuchItemException nsie) {
                OfflineLog.offline.warn("delta: no blob available for contact " + id);
            }
        }
        ParsedContact pc = (fields.isEmpty() ? null : new ParsedContact(fields, blob));

        synchronized (ombx) {
            int change_mask = ombx.getChangeMask(sContext, id, MailItem.TYPE_CONTACT);
            if ((change_mask & Change.MODIFIED_CONTENT) == 0 && pc != null)
                ombx.modifyContact(sContext, id, pc);
            ombx.syncMetadata(sContext, id, MailItem.TYPE_CONTACT, folderId, flags, tags, itemColor);
            ombx.syncDate(sContext, id, MailItem.TYPE_CONTACT, date);
        }
        OfflineLog.offline.debug("delta: updated contact (" + id + "): " + cn.getFileAsString());
    }

    void syncMessage(Element elt, int folderId, byte type) throws ServiceException {
        int id = (int) elt.getAttributeLong(MailConstants.A_ID);
        ombx.recordItemSync(id);
        try {
            Message msg = null;
            try {
                // make sure that the message we're delta-syncing actually exists
                msg = ombx.getMessageById(sContext, id);
            } catch (MailServiceException.NoSuchItemException nsie) {
                // if it's been locally deleted but not pushed to the server yet, just return and let the delete happen later
                if (!ombx.isPendingDelete(sContext, id, type))
                    getInitialSync().syncMessage(id, folderId, type);
                return;
            }

            String rgb = elt.getAttribute(MailConstants.A_RGB, null);
            byte color = (byte) elt.getAttributeLong(MailConstants.A_COLOR, MailItem.DEFAULT_COLOR);
            MailItem.Color itemColor = rgb != null ? new MailItem.Color(rgb) : new MailItem.Color(color);
            int flags = Flag.flagsToBitmask(elt.getAttribute(MailConstants.A_FLAGS, null));
            long tags = Tag.tagsToBitmask(getTagSync().localTagsFromElement(elt, null));
            int convId = (int) elt.getAttributeLong(MailConstants.A_CONV_ID);

            int date = (int) (elt.getAttributeLong(MailConstants.A_DATE) / 1000);
            synchronized (ombx) {
                try {
                    ombx.setConversationId(sContext, id, convId <= 0 ? -id : convId);
                    ombx.syncMetadata(sContext, id, type, folderId, flags, tags, itemColor);
                    ombx.syncDate(sContext, id, type, date);
                } catch (MailServiceException.NoSuchItemException nsie) {
                    OfflineLog.offline.warn("NoSuchItemException in delta sync. Item [" + id
                            + "] must have been deleted while sync was in progress");
                    return;
                }
            }
            OfflineLog.offline.debug("delta: updated " + MailItem.getNameForType(type) + " (" + id + "): "
                    + msg.getSubject());
        } catch (Exception x) {
            if (x instanceof ServiceException
                    && ((ServiceException) x).getCode().equals(MailServiceException.NO_SUCH_FOLDER)) {
                // message could be moved during
                OfflineLog.offline.debug("delta: moved" + MailItem.getNameForType(type) + " (" + id + ")");
            } else {
                if (!SyncExceptionHandler.isRecoverableException(ombx, id, "DeltaSync.syncMessage", x)) {
                    SyncExceptionHandler.syncMessageFailed(ombx, id, x);
                }
            }
        }
    }

    void syncDocuments(List<Integer> documents) throws ServiceException {
        StringBuilder query = null;
        if (ombx.getRemoteServerVersion().isAtLeast(InitialSync.sDocumentSyncHistoryVersion)) {
            for (int docId : documents) {
                if (query == null) {
                    query = new StringBuilder("list=");
                } else {
                    query.append(",");
                }
                query.append(docId);
            }
            if (query != null) {
                getInitialSync().syncDocument(query.toString());
            }
        } else {
            for (int docId : documents) {
                if (query == null) {
                    query = new StringBuilder("item:{");
                } else {
                    query.append(",");
                }
                query.append(docId);
            }
            query.append("}");
            Element request = new Element.XMLElement(MailConstants.SEARCH_REQUEST);
            request.addAttribute(MailConstants.A_QUERY_LIMIT, 1024); // XXX pagination
            request.addAttribute(MailConstants.A_TYPES, "wiki,document");
            request.addElement(MailConstants.E_QUERY).setText(query.toString());
            if (ombx.getOfflineAccount().isDebugTraceEnabled())
                OfflineLog.response.debug(request);

            Element response = ombx.sendRequest(request);

            if (ombx.getOfflineAccount().isDebugTraceEnabled())
                OfflineLog.response.debug(response);

            for (Element doc : response.listElements(MailConstants.E_DOC))
                getInitialSync().syncDocument(doc);
        }
    }
}
