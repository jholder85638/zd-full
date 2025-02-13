/*
 * 
 */

package com.zimbra.cs.service.mail;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.Element;
import com.zimbra.common.soap.MailConstants;
import com.zimbra.common.util.StringUtil;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.common.mime.MimeConstants;
import com.zimbra.cs.account.ldap.LdapUtil;
import com.zimbra.cs.mailbox.MailServiceException;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.OperationContext;
import com.zimbra.cs.mailbox.Mailbox.AddInviteData;
import com.zimbra.cs.mailbox.calendar.Invite;
import com.zimbra.cs.mailbox.calendar.ZCalendar.ZCalendarBuilder;
import com.zimbra.cs.mailbox.calendar.ZCalendar.ZVCalendar;
import com.zimbra.cs.service.FileUploadServlet;
import com.zimbra.cs.service.FileUploadServlet.Upload;
import com.zimbra.cs.service.util.ItemId;
import com.zimbra.soap.ZimbraSoapContext;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author schemers
 */
public class ImportAppointments extends MailDocumentHandler  {

    private static final String[] TARGET_FOLDER_PATH = new String[] { MailConstants.A_FOLDER };
    protected String[] getProxiedIdPath(Element request)     { return TARGET_FOLDER_PATH; }
    protected boolean checkMountpointProxy(Element request)  { return true; }

    String DEFAULT_FOLDER_ID = Mailbox.ID_FOLDER_CALENDAR + "";

    public Element handle(Element request, Map<String, Object> context) throws ServiceException {
        ZimbraSoapContext zsc = getZimbraSoapContext(context);
        Mailbox mbox = getRequestedMailbox(zsc);
        OperationContext octxt = getOperationContext(zsc, context);

        String folder = request.getAttribute(MailConstants.A_FOLDER, DEFAULT_FOLDER_ID);
        ItemId iidFolder = new ItemId(folder, zsc);

        String ct = request.getAttribute(MailConstants.A_CONTENT_TYPE);
        if (!ct.equalsIgnoreCase("ics") && !ct.equalsIgnoreCase(MimeConstants.CT_TEXT_CALENDAR))
            throw ServiceException.INVALID_REQUEST("unsupported content type: " + ct, null);

        Element content = request.getElement(MailConstants.E_CONTENT);
        List<Upload> uploads = null;
        InputStream is = null;
        String attachment = content.getAttribute(MailConstants.A_ATTACHMENT_ID, null);
        String messageId = content.getAttribute(MailConstants.A_MESSAGE_ID, null);
        if (attachment != null && messageId != null)
            throw ServiceException.INVALID_REQUEST("use either aid or mid but not both", null);
        try {
            if (attachment != null) {
                is = parseUploadedContent(zsc, attachment, uploads = new ArrayList<Upload>());
            } else if (messageId != null) {
                // part of existing message
                ItemId iid = new ItemId(messageId, zsc);
                String part = content.getAttribute(MailConstants.A_PART);
                String partStr = CreateContact.fetchItemPart(
                        zsc, octxt, mbox, iid, part, null, MimeConstants.P_CHARSET_UTF8);
                is = new ByteArrayInputStream(partStr.getBytes(MimeConstants.P_CHARSET_UTF8));
            } else {
                // Convert LF to CRLF because the XML parser normalizes element text to LF.
                String text = StringUtil.lfToCrlf(content.getText());
                is = new ByteArrayInputStream(text.getBytes(MimeConstants.P_CHARSET_UTF8));
            }

            List<ZVCalendar> icals = ZCalendarBuilder.buildMulti(is, MimeConstants.P_CHARSET_UTF8);
            is.close();
            is = null;

            List<Invite> invites = Invite.createFromCalendar(mbox.getAccount(), null, icals, true, true, null);

            Set<String> uidsSeen = new HashSet<String>();
            StringBuilder ids = new StringBuilder();

            for (Invite inv : invites) {
                // handle missing UIDs on remote calendars by generating them as needed
                String uid = inv.getUid();
                if (uid == null) {
                    uid = LdapUtil.generateUUID();
                    inv.setUid(uid);
                }
                boolean addRevision;
                if (!uidsSeen.contains(uid)) {
                    addRevision = true;
                    uidsSeen.add(uid);
                } else {
                    addRevision = false;
                }
                // and add the invite to the calendar!
                try {
                    AddInviteData aid = mbox.addInvite(octxt, inv, iidFolder.getId(), false, addRevision);
                    if (aid != null) {
                        if (ids.length() > 0) ids.append(",");
                        ids.append(aid.calItemId).append("-").append(aid.invId);
                    }
                } catch (ServiceException e) {
                    ZimbraLog.calendar.warn("Skipping bad iCalendar object during import: uid=" + inv.getUid(), e);
                }
            }
            
            Element response = zsc.createElement(MailConstants.IMPORT_APPOINTMENTS_RESPONSE);
            Element cn = response.addElement(MailConstants.E_APPOINTMENT);
            cn.addAttribute(MailConstants.A_IDS, ids.toString());
            cn.addAttribute(MailConstants.A_NUM, invites.size());
            return response;

        } catch (IOException e) {
            throw MailServiceException.UNABLE_TO_IMPORT_APPOINTMENTS(e.getMessage(), e);
        } finally {
            if (is != null)
                try { is.close(); } catch (IOException e) { }
            if (attachment != null)
                FileUploadServlet.deleteUploads(uploads);
        }

    }

    private static InputStream parseUploadedContent(ZimbraSoapContext lc, String attachId, List<Upload> uploads)
    throws ServiceException {
        Upload up = FileUploadServlet.fetchUpload(lc.getAuthtokenAccountId(), attachId, lc.getAuthToken());
        if (up == null)
            throw MailServiceException.NO_SUCH_UPLOAD(attachId);
        uploads.add(up);
        try {
            return up.getInputStream();
        } catch (IOException e) {
            throw ServiceException.FAILURE(e.getMessage(), e);
        }
    }
}
