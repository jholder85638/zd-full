/*
 * 
 */

/*
 * Created on Aug 31, 2004
 */
package com.zimbra.cs.service.mail;

import java.util.Map;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.Element;
import com.zimbra.common.soap.MailConstants;
import com.zimbra.common.soap.SoapFaultException;
import com.zimbra.common.util.Pair;
import com.zimbra.cs.mailbox.Folder;
import com.zimbra.cs.mailbox.MailServiceException;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.Mountpoint;
import com.zimbra.cs.mailbox.OperationContext;
import com.zimbra.cs.mailbox.OperationContextData;
import com.zimbra.cs.mailbox.Mailbox.FolderNode;
import com.zimbra.cs.service.util.ItemId;
import com.zimbra.cs.service.util.ItemIdFormatter;
import com.zimbra.cs.session.SoapSession;
import com.zimbra.soap.ZimbraSoapContext;

public class GetFolder extends MailDocumentHandler {

    private static final String[] TARGET_FOLDER_PATH = new String[] { MailConstants.E_FOLDER, MailConstants.A_FOLDER };
    private static final String[] RESPONSE_ITEM_PATH = new String[] { MailConstants.E_FOLDER };
    @Override protected String[] getProxiedIdPath(Element request)     { return TARGET_FOLDER_PATH; }
    @Override protected boolean checkMountpointProxy(Element request)  { return false; }
    @Override protected String[] getResponseItemPath()  { return RESPONSE_ITEM_PATH; }

    static final String DEFAULT_FOLDER_ID = "" + Mailbox.ID_FOLDER_USER_ROOT;

    @Override public Element handle(Element request, Map<String, Object> context)
    throws ServiceException, SoapFaultException {
        ZimbraSoapContext zsc = getZimbraSoapContext(context);
        Mailbox mbox = getRequestedMailbox(zsc);
        OperationContext octxt = getOperationContext(zsc, context);
        ItemIdFormatter ifmt = new ItemIdFormatter(zsc);

        ItemId iid;
        Element eFolder = request.getOptionalElement(MailConstants.E_FOLDER);
        if (eFolder != null) {
            iid = new ItemId(eFolder.getAttribute(MailConstants.A_FOLDER, DEFAULT_FOLDER_ID), zsc);

            String path = eFolder.getAttribute(MailConstants.A_PATH, null);
            if (path != null) {
                Pair<Folder, String> resolved = mbox.getFolderByPathLongestMatch(octxt, iid.getId(), path);
                Folder folder = resolved.getFirst();
                String overflow = resolved.getSecond();

                if (overflow == null) {
                    iid = new ItemId(folder);
                } else if (folder instanceof Mountpoint) {
                    // path crosses a mountpoint; update request and proxy to target mailbox
                    ItemId iidTarget = ((Mountpoint) folder).getTarget();
                    eFolder.addAttribute(MailConstants.A_FOLDER, iidTarget.toString()).addAttribute(MailConstants.A_PATH, overflow);
                    return proxyRequest(request, context, new ItemId(folder), iidTarget);
                } else {
                    throw MailServiceException.NO_SUCH_FOLDER(path);
                }
            }
        } else {
            iid = new ItemId(DEFAULT_FOLDER_ID, zsc);
        }

        boolean visible = request.getAttributeBool(MailConstants.A_VISIBLE, false);
        boolean needGranteeName = request.getAttributeBool(MailConstants.A_NEED_GRANTEE_NAME, true);

        FolderNode rootnode = mbox.getFolderTree(octxt, iid, visible);

        Element response = zsc.createElement(MailConstants.GET_FOLDER_RESPONSE);
        if (rootnode != null) {
            if (needGranteeName)
                OperationContextData.addGranteeNames(octxt, rootnode);
            else
                OperationContextData.setNeedGranteeName(octxt, false);
            Element folderRoot = encodeFolderNode(ifmt, octxt, response, rootnode, true);
            if (rootnode.mFolder != null && rootnode.mFolder instanceof Mountpoint)
                handleMountpoint(request, context, iid, (Mountpoint) rootnode.mFolder, folderRoot);			
        }

        return response;
    }

    public static Element encodeFolderNode(ItemIdFormatter ifmt, OperationContext octxt, Element parent, FolderNode node) {
        return encodeFolderNode(ifmt, octxt, parent, node, false);
    }

    private static Element encodeFolderNode(ItemIdFormatter ifmt, OperationContext octxt, Element parent, FolderNode node, boolean exposeAclAccessKey) {
        Element eFolder;
        if (node.mFolder != null)
            eFolder = ToXML.encodeFolder(parent, ifmt, octxt, node.mFolder, ToXML.NOTIFY_FIELDS, exposeAclAccessKey);
        else
            eFolder = parent.addElement(MailConstants.E_FOLDER).addAttribute(MailConstants.A_ID, ifmt.formatItemId(node.mId)).addAttribute(MailConstants.A_NAME, node.mName);

        for (FolderNode subNode : node.mSubfolders)
            encodeFolderNode(ifmt, octxt, eFolder, subNode, exposeAclAccessKey);

        return eFolder;
    }


    private void handleMountpoint(Element request, Map<String, Object> context, ItemId iidLocal, Mountpoint mpt, Element eRoot)
    throws ServiceException {
        // gotta nuke the old <folder> element, lest it interfere with our proxying...
        ItemId iidRemote = mpt.getTarget();
        request.getElement(MailConstants.E_FOLDER).detach();
        request.addElement(MailConstants.E_FOLDER).addAttribute(MailConstants.A_FOLDER, iidRemote.toString());

        Element proxied = proxyRequest(request, context, iidLocal, iidRemote);
        // return the children of the remote folder as children of the mountpoint
        proxied = proxied.getOptionalElement(MailConstants.E_FOLDER);
        if (proxied != null) {
            SoapSession.transferMountpointContents(eRoot, proxied); //args: to,from
        }
    }
}
