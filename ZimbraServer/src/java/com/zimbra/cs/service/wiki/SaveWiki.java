/*
 * 
 */
package com.zimbra.cs.service.wiki;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.service.ServiceException.Argument;
import com.zimbra.common.soap.MailConstants;
import com.zimbra.common.soap.Element;
import com.zimbra.cs.mailbox.Document;
import com.zimbra.cs.mailbox.MailItem;
import com.zimbra.cs.mailbox.MailServiceException;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.MailboxManager;
import com.zimbra.cs.mailbox.OperationContext;
import com.zimbra.cs.mailbox.WikiItem;
import com.zimbra.cs.service.util.ItemId;
import com.zimbra.cs.service.util.ItemIdFormatter;
import com.zimbra.cs.wiki.WikiPage;
import com.zimbra.soap.ZimbraSoapContext;

public class SaveWiki extends WikiDocumentHandler {

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public Element handle(Element request, Map<String, Object> context) throws ServiceException {
        if (true)
            throw WikiServiceException.ERROR("Deprecated");
        ZimbraSoapContext zsc = getZimbraSoapContext(context);
        OperationContext octxt = getOperationContext(zsc, context);

        Element msgElem = request.getElement(MailConstants.E_WIKIWORD);
        String subject = msgElem.getAttribute(MailConstants.A_NAME, null);
        String id = msgElem.getAttribute(MailConstants.A_ID, null);
        int ver = (int)msgElem.getAttributeLong(MailConstants.A_VERSION, 0);
        int itemId;
        if (id == null) {
            itemId = 0;
        } else {
            ItemId iid = new ItemId(id, zsc);
            itemId = iid.getId();
        }

        ItemId fid = getRequestedFolder(request, zsc);
        ByteArrayInputStream is = null;
        try {
            byte[] rawData = msgElem.getText().getBytes("UTF-8");
            is = new ByteArrayInputStream(rawData);
        } catch (IOException ioe) {
            throw ServiceException.FAILURE("can't get the content", ioe);
        }
        Mailbox mbox = MailboxManager.getInstance().getMailboxByAccountId(zsc.getRequestedAccountId());
        Document wikiItem = null;
        WikiPage.WikiContext ctxt = new WikiPage.WikiContext(octxt, zsc.getAuthToken());
        if (itemId == 0) {
            // create a new page
            wikiItem = mbox.createDocument(octxt, fid.getId(), subject, WikiItem.WIKI_CONTENT_TYPE, getAuthor(zsc), null, true, is, MailItem.TYPE_WIKI);
        } else {
            // add a new revision
            WikiPage oldPage = WikiPage.findPage(ctxt, zsc.getRequestedAccountId(), itemId);
            if (oldPage == null)
                throw new WikiServiceException.NoSuchWikiException("page id="+id+" not found");
            if (oldPage.getLastVersion() != ver) {
                throw MailServiceException.MODIFY_CONFLICT(
                        new Argument(MailConstants.A_NAME, subject, Argument.Type.STR),
                        new Argument(MailConstants.A_ID, oldPage.getId(), Argument.Type.IID),
                        new Argument(MailConstants.A_VERSION, oldPage.getLastVersion(), Argument.Type.NUM));
            }
            wikiItem = mbox.addDocumentRevision(octxt, itemId, getAuthor(zsc), subject, null, true, is);
        }

        Element response = zsc.createElement(MailConstants.SAVE_WIKI_RESPONSE);
        Element m = response.addElement(MailConstants.E_WIKIWORD);
        m.addAttribute(MailConstants.A_ID, new ItemIdFormatter(zsc).formatItemId(wikiItem));
        m.addAttribute(MailConstants.A_VERSION, wikiItem.getVersion());
        return response;
    }
}
