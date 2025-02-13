/*
 * 
 */

package com.zimbra.cs.service.mail;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.MailConstants;
import com.zimbra.common.soap.Element;
import com.zimbra.cs.mailbox.Contact;
import com.zimbra.cs.mailbox.MailServiceException;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.OperationContext;
import com.zimbra.cs.service.formatter.ContactCSV;
import com.zimbra.cs.service.util.ItemId;
import com.zimbra.soap.ZimbraSoapContext;

/**
 * @author schemers
 */
public final class ExportContacts extends MailDocumentHandler  {

    private static final String[] TARGET_FOLDER_PATH = new String[] { MailConstants.A_FOLDER };

    @Override
    protected String[] getProxiedIdPath(Element request) {
        return TARGET_FOLDER_PATH;
    }

    @Override
    protected boolean checkMountpointProxy(Element request) {
        return true;
    }

    @Override
    public Element handle(Element request, Map<String, Object> context) throws ServiceException {
        ZimbraSoapContext zsc = getZimbraSoapContext(context);
        Mailbox mbox = getRequestedMailbox(zsc);
        OperationContext octxt = getOperationContext(zsc, context);

        String folder = request.getAttribute(MailConstants.A_FOLDER, null);
        ItemId iidFolder = folder == null ? null : new ItemId(folder, zsc);

        String ct = request.getAttribute(MailConstants.A_CONTENT_TYPE);
        if (!ct.equals("csv"))
            throw ServiceException.INVALID_REQUEST("unsupported content type: " + ct, null);

        String format = request.getAttribute(MailConstants.A_CSVFORMAT, null);
        String locale = request.getAttribute(MailConstants.A_CSVLOCALE, null);
        String separator = request.getAttribute(MailConstants.A_CSVSEPARATOR, null);
        Character sepChar = null;
        if ((separator != null) && (separator.length() > 0))
            sepChar = separator.charAt(0);

        List<Contact> contacts = mbox.getContactList(octxt, iidFolder != null ? iidFolder.getId() : -1);

        StringBuilder sb = new StringBuilder();
        if (contacts == null)
            contacts = new ArrayList<Contact>();

        try {
            ContactCSV contactCSV = new ContactCSV();
            contactCSV.toCSV(format, locale, sepChar, contacts.iterator(), sb);
        } catch (ContactCSV.ParseException e) {
            throw MailServiceException.UNABLE_TO_EXPORT_CONTACTS(e.getMessage(), e);
        }

        Element response = zsc.createElement(MailConstants.EXPORT_CONTACTS_RESPONSE);
        Element content = response.addElement(MailConstants.E_CONTENT);
        content.setText(sb.toString());

        return response;
    }
}
