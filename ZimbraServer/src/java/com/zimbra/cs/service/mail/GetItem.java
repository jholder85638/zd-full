/*
 * 
 */
package com.zimbra.cs.service.mail;

import java.util.Map;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.MailConstants;
import com.zimbra.common.soap.Element;
import com.zimbra.common.util.Pair;
import com.zimbra.cs.mailbox.Folder;
import com.zimbra.cs.mailbox.MailItem;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.MailServiceException;
import com.zimbra.cs.mailbox.Mountpoint;
import com.zimbra.cs.mailbox.OperationContext;
import com.zimbra.cs.service.util.ItemId;
import com.zimbra.cs.service.util.ItemIdFormatter;
import com.zimbra.soap.ZimbraSoapContext;

public class GetItem extends MailDocumentHandler {

    private static final String[] TARGET_ITEM_PATH = new String[] { MailConstants.E_ITEM, MailConstants.A_ID };
    private static final String[] TARGET_FOLDER_PATH = new String[] { MailConstants.E_ITEM, MailConstants.A_FOLDER };
    private static final String[] RESPONSE_ITEM_PATH = new String[] { };
    @Override protected String[] getProxiedIdPath(Element request) {
        if (getXPath(request, TARGET_ITEM_PATH) != null)    return TARGET_ITEM_PATH;
        if (getXPath(request, TARGET_FOLDER_PATH) != null)  return TARGET_FOLDER_PATH;
        return null;
    }
    @Override protected boolean checkMountpointProxy(Element request)  { return getXPath(request, TARGET_ITEM_PATH) == null; }
    @Override protected String[] getResponseItemPath()  { return RESPONSE_ITEM_PATH; }

    @Override public Element handle(Element request, Map<String, Object> context) throws ServiceException {
        ZimbraSoapContext zsc = getZimbraSoapContext(context);
        Mailbox mbox = getRequestedMailbox(zsc);
        OperationContext octxt = getOperationContext(zsc, context);
        ItemIdFormatter ifmt = new ItemIdFormatter(zsc);

        Element target = request.getElement(MailConstants.E_ITEM);
        String id = target.getAttribute(MailConstants.A_ID, null);
        String folderStr = target.getAttribute(MailConstants.A_FOLDER, null);
        String path = target.getAttribute(MailConstants.A_PATH, target.getAttribute(MailConstants.A_NAME, null));

        if (folderStr != null && path == null)
            throw ServiceException.INVALID_REQUEST("missing required attribute: " + MailConstants.A_PATH, null);

        MailItem item;
        if (id != null) {
            // get item by id
            item = mbox.getItemById(octxt, new ItemId(id, zsc).getId(), MailItem.TYPE_UNKNOWN);
        } else if (path != null) {
            // get item by name within containing folder id (from root by default)
            int folderId = folderStr == null ? Mailbox.ID_FOLDER_USER_ROOT : new ItemId(folderStr, zsc).getId();
            try {
                item = mbox.getItemByPath(octxt, path, folderId);
            } catch (MailServiceException.NoSuchItemException nsie) {
                Pair<Folder, String> match = mbox.getFolderByPathLongestMatch(octxt, folderId, path);
                if (match.getFirst() instanceof Mountpoint) {
                    Mountpoint mpt = (Mountpoint) match.getFirst();
                    target.addAttribute(MailConstants.A_FOLDER, mpt.getRemoteId()).addAttribute(MailConstants.A_PATH, match.getSecond());
                    return proxyRequest(request, context, mpt.getOwnerId());
                }
                throw nsie;
            }
        } else {
            throw ServiceException.INVALID_REQUEST("must specify 'id' or 'path'", null);
        }

        Element response = zsc.createElement(MailConstants.GET_ITEM_RESPONSE);
        ToXML.encodeItem(response, ifmt, octxt, item, ToXML.NOTIFY_FIELDS);
        return response;
    }
}
