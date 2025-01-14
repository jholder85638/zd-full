/*
 * 
 */
package com.zimbra.cs.service.formatter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.List;
import java.util.Set;

import javax.mail.MessagingException;
import javax.mail.Part;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimePart;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.zimbra.common.mime.MimeConstants;
import com.zimbra.common.mime.MimeDetect;
import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.ByteUtil;
import com.zimbra.common.util.HttpUtil;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.html.BrowserDefang;
import com.zimbra.cs.html.DefangFactory;
import com.zimbra.cs.index.MailboxIndex;
import com.zimbra.cs.mailbox.CalendarItem;
import com.zimbra.cs.mailbox.Contact;
import com.zimbra.cs.mailbox.Document;
import com.zimbra.cs.mailbox.Folder;
import com.zimbra.cs.mailbox.MailItem;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.Message;
import com.zimbra.cs.mailbox.MailServiceException.NoSuchItemException;
import com.zimbra.cs.mime.MPartInfo;
import com.zimbra.cs.mime.Mime;
import com.zimbra.cs.mime.ParsedDocument;
import com.zimbra.cs.mime.ParsedMessage;
import com.zimbra.cs.service.UserServlet;
import com.zimbra.cs.service.UserServletContext;
import com.zimbra.cs.service.UserServletException;
import com.zimbra.cs.service.formatter.FormatterFactory.FormatType;


public final class NativeFormatter extends Formatter {

    private static final String CONVERSION_PATH = "/extension/convertd";
    public static final String ATTR_INPUTSTREAM = "inputstream";
    public static final String ATTR_MSGDIGEST  = "msgdigest";
    public static final String ATTR_FILENAME  = "filename";
    public static final String ATTR_CONTENTURL = "contenturl";
    public static final String ATTR_CONTENTTYPE = "contenttype";
    public static final String ATTR_CONTENTLENGTH = "contentlength";

    private static final Set<String> SCRIPTABLE_CONTENT_TYPES = ImmutableSet.of(MimeConstants.CT_TEXT_HTML,
                                                                                MimeConstants.CT_APPLICATION_XHTML,
                                                                                MimeConstants.CT_TEXT_XML,
                                                                                MimeConstants.CT_IMAGE_SVG);


    @Override
    public FormatType getType() {
        return FormatType.HTML_CONVERTED;
    }

    public String getDefaultSearchTypes() {
        // TODO: all?
        return MailboxIndex.SEARCH_FOR_MESSAGES;
    }

    public void formatCallback(UserServletContext context) throws IOException, ServiceException, UserServletException, ServletException {
        try {
            sendZimbraHeaders(context.resp, context.target);
            HttpUtil.Browser browser = HttpUtil.guessBrowser(context.req);
            if (browser == HttpUtil.Browser.IE) {
                context.resp.addHeader("X-Content-Type-Options", "nosniff"); // turn off content detection..
            }
            if (context.target instanceof Message) {
                handleMessage(context, (Message) context.target);
            } else if (context.target instanceof CalendarItem) {
                // Don't return private appointments/tasks if the requester is not the mailbox owner.
                CalendarItem calItem = (CalendarItem) context.target;
                if (calItem.isPublic() || calItem.allowPrivateAccess(
                        context.getAuthAccount(), context.isUsingAdminPrivileges())) {
                    handleCalendarItem(context, calItem);
                } else {
                    context.resp.sendError(HttpServletResponse.SC_FORBIDDEN, "permission denied");
                }
            } else if (context.target instanceof Document) {
                handleDocument(context, (Document) context.target);
            } else if (context.target instanceof Contact) {
                handleContact(context, (Contact) context.target);
            } else {
                throw UserServletException.notImplemented("can only handle messages/appointments/tasks/documents");
            }
        } catch (MessagingException me) {
            throw ServiceException.FAILURE(me.getMessage(), me);
        }
    }

    private void handleMessage(UserServletContext context, Message msg) throws IOException, ServiceException, MessagingException, ServletException {
        if (context.hasBody()) {
            List<MPartInfo> parts = Mime.getParts(msg.getMimeMessage());
            MPartInfo body = Mime.getTextBody(parts, false);
            if (body != null) {
                handleMessagePart(context, body.getMimePart(), msg);
            } else {
                context.resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "body not found");
            }
        } else if (context.hasPart()) {
            MimePart mp = getMimePart(msg, context.getPart());
            handleMessagePart(context, mp, msg);
        } else {
            context.resp.setContentType(MimeConstants.CT_TEXT_PLAIN);
            long size = msg.getSize();
            if (size > 0)
                context.resp.setContentLength((int)size);
            InputStream is = msg.getContentStream();
            ByteUtil.copy(is, true, context.resp.getOutputStream(), false);
        }
    }

    private void handleCalendarItem(UserServletContext context, CalendarItem calItem) throws IOException, ServiceException, MessagingException, ServletException {
        if (context.hasPart()) {
            MimePart mp;
            if (context.itemId.hasSubpart()) {
                MimeMessage mbp = calItem.getSubpartMessage(context.itemId.getSubpartId());
                mp = Mime.getMimePart(mbp, context.getPart());
            } else {
                mp = getMimePart(calItem, context.getPart());
            }
            handleMessagePart(context, mp, calItem);
        } else {
            context.resp.setContentType(MimeConstants.CT_TEXT_PLAIN);
            InputStream is = calItem.getRawMessage();
            if (is != null)
                ByteUtil.copy(is, true, context.resp.getOutputStream(), false);
        }
    }

    private void handleContact(UserServletContext context, Contact con) throws IOException, ServiceException, MessagingException, ServletException {
        if (!con.hasAttachment()) {
            context.resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "body not found");
        } else if (context.hasPart()) {
            MimePart mp = Mime.getMimePart(con.getMimeMessage(false), context.getPart());
            handleMessagePart(context, mp, con);
        } else {
            context.resp.setContentType(MimeConstants.CT_TEXT_PLAIN);
            InputStream is = new ByteArrayInputStream(con.getContent());
            ByteUtil.copy(is, true, context.resp.getOutputStream(), false);
        }
    }

    private static final String HTML_VIEW = "html";

    private void handleMessagePart(UserServletContext context, MimePart mp, MailItem item) throws IOException, MessagingException, ServletException {
        if (mp == null) {
            context.resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "part not found");
        } else {
            String contentType = mp.getContentType();
            String shortContentType = Mime.getContentType(mp);

            if (contentType == null) {
                contentType = MimeConstants.CT_TEXT_PLAIN;
            } else if (shortContentType.equalsIgnoreCase(MimeConstants.CT_APPLICATION_OCTET_STREAM)) {
                if ((contentType = MimeDetect.getMimeDetect().detect(Mime.getFilename(mp), mp.getInputStream())) == null)
                    contentType = MimeConstants.CT_APPLICATION_OCTET_STREAM;
                else
                    shortContentType = contentType;
            }
            // CR or LF in Content-Type causes Chrome to barf, unfortunately
            contentType = contentType.replace('\r', ' ').replace('\n', ' ');

            // IE displays garbage if the content-type header is too long
            HttpUtil.Browser browser = HttpUtil.guessBrowser(context.req);
            if (browser == HttpUtil.Browser.IE && contentType.length() > 80)
                contentType = shortContentType;

            boolean html = checkGlobalOverride(Provisioning.A_zimbraAttachmentsViewInHtmlOnly,
                    context.getAuthAccount()) || (context.hasView() && context.getView().equals(HTML_VIEW));

            if (!html || contentType.startsWith(MimeConstants.CT_TEXT_HTML)) {
                String defaultCharset = context.targetAccount.getAttr(Provisioning.A_zimbraPrefMailDefaultCharset, null);
                sendbackOriginalDoc(mp, contentType, defaultCharset, context.req, context.resp);
            } else {
                handleConversion(context, mp.getInputStream(), Mime.getFilename(mp), contentType, item.getDigest(), mp.getSize());
            }
        }
    }

    private void handleDocument(UserServletContext context, Document doc) throws IOException, ServiceException, ServletException {
        String v = context.params.get(UserServlet.QP_VERSION);
        int version = v != null ? Integer.parseInt(v) : -1;
        String contentType = doc.getContentType();

        boolean neuter = doc.getAccount().getBooleanAttr(Provisioning.A_zimbraNotebookSanitizeHtml, true);
        String defaultCharset = context.targetAccount.getAttr(Provisioning.A_zimbraPrefMailDefaultCharset, null);

        doc = (version > 0 ? (Document)doc.getMailbox().getItemRevision(context.opContext, doc.getId(), doc.getType(), version) : doc);
        InputStream is = doc.getContentStream();
        if (HTML_VIEW.equals(context.getView()) && !(contentType != null && contentType.startsWith(MimeConstants.CT_TEXT_HTML))) {
            handleConversion(context, is, doc.getName(), doc.getContentType(), doc.getDigest(), doc.getSize());
        } else {
            if (neuter)
                sendbackOriginalDoc(is, contentType, defaultCharset, doc.getName(), null, doc.getSize(), context.req, context.resp);
            else
                sendbackBinaryData(context.req, context.resp, is, contentType, null, doc.getName(), doc.getSize());
        }
    }

    private void handleConversion(UserServletContext ctxt, InputStream is, String filename, String ct, String digest, long length) throws IOException, ServletException {
        try {
            ctxt.req.setAttribute(ATTR_INPUTSTREAM, is);
            ctxt.req.setAttribute(ATTR_MSGDIGEST, digest);
            ctxt.req.setAttribute(ATTR_FILENAME, filename);
            ctxt.req.setAttribute(ATTR_CONTENTTYPE, ct);
            ctxt.req.setAttribute(ATTR_CONTENTURL, ctxt.req.getRequestURL().toString());
            ctxt.req.setAttribute(ATTR_CONTENTLENGTH, length);
            RequestDispatcher dispatcher = ctxt.req.getRequestDispatcher(CONVERSION_PATH);
            dispatcher.forward(ctxt.req, ctxt.resp);
        } finally {
            ByteUtil.closeStream(is);
        }
    }

    public static MimePart getMimePart(CalendarItem calItem, String part) throws IOException, MessagingException, ServiceException {
        return Mime.getMimePart(calItem.getMimeMessage(), part);
    }

    public static MimePart getMimePart(Message msg, String part) throws IOException, MessagingException, ServiceException {
        return Mime.getMimePart(msg.getMimeMessage(), part);
    }

    public static void sendbackOriginalDoc(MimePart mp, String contentType, String defaultCharset, HttpServletRequest req, HttpServletResponse resp)
    throws IOException, MessagingException {
        String enc = mp.getEncoding();
        if (enc != null)
            enc = enc.toLowerCase();
        long size = enc == null || enc.equals("7bit") || enc.equals("8bit") || enc.equals("binary") ? mp.getSize() : 0;
        sendbackOriginalDoc(mp.getInputStream(), contentType, defaultCharset, Mime.getFilename(mp), mp.getDescription(), size, req, resp);
    }

    public static void sendbackOriginalDoc(InputStream is, String contentType, String defaultCharset, String filename,
            String desc, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        sendbackOriginalDoc(is, contentType, defaultCharset, filename, desc, 0, req, resp);
    }

    private static void sendbackOriginalDoc(InputStream is, String contentType, String defaultCharset, String filename,
            String desc, long size, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String disp = req.getParameter(UserServlet.QP_DISP);
        disp = (disp == null || disp.toLowerCase().startsWith("i") ) ? Part.INLINE : Part.ATTACHMENT;
        if (desc != null) {
            resp.addHeader("Content-Description", desc);
        }
        // defang when the html and svg attachment was requested with disposition inline
        if (disp.equals(Part.INLINE) && isScriptableContent(contentType)) {
            BrowserDefang defanger = DefangFactory.getDefanger(contentType);
            String content = defanger.defang(Mime.getTextReader(is, contentType, defaultCharset), false);
            resp.setContentType(contentType);
            String charset = Mime.getCharset(contentType);
            resp.setCharacterEncoding(Strings.isNullOrEmpty(charset) ? Charsets.UTF_8.name() : charset);
            if (!content.isEmpty()) {
                resp.setContentLength(content.length());
            }
            resp.getWriter().write(content);
        } else {
            // flash attachment may contain a malicious script hence..
            if (contentType.startsWith(MimeConstants.CT_APPLICATION_SHOCKWAVE_FLASH)) {
                disp = Part.ATTACHMENT;
            }
            resp.setContentType(contentType);
            sendbackBinaryData(req, resp, is, contentType, disp, filename, size);
        }
    }

    public boolean supportsSave() {
        return true;
    }

    public void saveCallback(UserServletContext context, String contentType, Folder folder, String filename) throws IOException, ServiceException, UserServletException {
        Mailbox mbox = folder.getMailbox();
        MailItem item = null;
        if (filename == null) {
            try {
                ParsedMessage pm = new ParsedMessage(context.getPostBody(), mbox.attachmentsIndexingEnabled());
                item = mbox.addMessage(context.opContext, pm, folder.getId(), true, 0, null);
                return;
            } catch (ServiceException e) {
                throw new UserServletException(HttpServletResponse.SC_BAD_REQUEST, "error parsing message");
            }
        }

        String creator = context.getAuthAccount() == null ? null : context.getAuthAccount().getName();
        InputStream is = context.getRequestInputStream();
        ParsedDocument pd = null;

        try {
            if (contentType == null)
                contentType = MimeDetect.getMimeDetect().detect(filename);
            if (contentType == null)
                contentType = MimeConstants.CT_APPLICATION_OCTET_STREAM;
            pd = new ParsedDocument(is, filename, contentType, System.currentTimeMillis(), creator, context.req.getHeader("X-Zimbra-Description"));
            item = mbox.getItemByPath(context.opContext, filename, folder.getId());
            // XXX: should we just overwrite here instead?
            if (!(item instanceof Document))
                throw new UserServletException(HttpServletResponse.SC_BAD_REQUEST, "cannot overwrite existing object at that path");

            item = mbox.addDocumentRevision(context.opContext, item.getId(), pd);
        } catch (NoSuchItemException nsie) {
            item = mbox.createDocument(context.opContext, folder.getId(), pd, MailItem.TYPE_DOCUMENT);
        } finally {
            is.close();
        }
        sendZimbraHeaders(context.resp, item);
    }

    private void sendZimbraHeaders(HttpServletResponse resp, MailItem item) {
        if (resp == null || item == null)
            return;
        resp.addHeader("X-Zimbra-ItemId", item.getId() + "");
        resp.addHeader("X-Zimbra-Version", item.getVersion() + "");
        resp.addHeader("X-Zimbra-Modified", item.getChangeDate() + "");
        resp.addHeader("X-Zimbra-Change", item.getModifiedSequence() + "");
        resp.addHeader("X-Zimbra-Revision", item.getSavedSequence() + "");
        resp.addHeader("X-Zimbra-ItemType", MailItem.getNameForType(item));
        resp.addHeader("X-Zimbra-ItemName", item.getName());
        try {
            resp.addHeader("X-Zimbra-ItemPath", item.getPath());
        } catch (ServiceException e) {
        }
    }

    private static final int READ_AHEAD_BUFFER_SIZE = 256;
    private static final byte[][] SCRIPT_PATTERN = {
        { '<', 's', 'c', 'r', 'i', 'p', 't' },
        { '<', 'S', 'C', 'R', 'I', 'P', 'T' }
    };

    public static void sendbackBinaryData(HttpServletRequest req,
                                          HttpServletResponse resp,
                                          InputStream in,
                                          String contentType,
                                          String disposition,
                                          String filename,
                                          long size) throws IOException {
        if (disposition == null) {
            String disp = req.getParameter(UserServlet.QP_DISP);
            disposition = (disp == null || disp.toLowerCase().startsWith("i") ) ? Part.INLINE : Part.ATTACHMENT;
        }

        PushbackInputStream pis = new PushbackInputStream(in, READ_AHEAD_BUFFER_SIZE);
        boolean isSafe = false;
        HttpUtil.Browser browser = HttpUtil.guessBrowser(req);
        if (browser != HttpUtil.Browser.IE) {
            isSafe = true;
        } else if (disposition.equals(Part.ATTACHMENT)) {
            isSafe = true;

            // only set no-open for 'script type content'
            if (isScriptableContent(contentType)) {
                // ask it to save the file
                resp.addHeader("X-Download-Options", "noopen");
            }
        }

        if (!isSafe) {
            byte[] buf = new byte[READ_AHEAD_BUFFER_SIZE];
            int bytesRead = pis.read(buf, 0, READ_AHEAD_BUFFER_SIZE);
            boolean hasScript;
            for (int i = 0; i < bytesRead; i++) {
                if (buf[i] == SCRIPT_PATTERN[0][0] || buf[i] == SCRIPT_PATTERN[1][0]) {
                    hasScript = true;
                    for (int pos = 1; pos < 7 && (i + pos) < bytesRead; pos++) {
                        if (buf[i+pos] != SCRIPT_PATTERN[0][pos] &&
                                buf[i+pos] != SCRIPT_PATTERN[1][pos]) {
                            hasScript = false;
                            break;
                        }
                    }
                    if (hasScript) {
                        resp.addHeader("Cache-Control", "no-transform");
                        disposition = Part.ATTACHMENT;
                        break;
                    }
                }
            }
            if (bytesRead > 0)
                pis.unread(buf, 0, bytesRead);
        }
        String cd = disposition + "; filename=" + HttpUtil.encodeFilename(req, filename == null ? "unknown" : filename);
        resp.addHeader("Content-Disposition", cd);
        if (size > 0)
            resp.setContentLength((int)size);
        ByteUtil.copy(pis, true, resp.getOutputStream(), false);
    }

    /**
     * Determines whether or not the contentType passed might contain script or other unsavory tags.
     * @param contentType The content type to check
     * @return true if there's a possiblilty that <script> is valid, false if not
     */
    private static boolean isScriptableContent(String contentType) {
        // Make sure we don't have to worry about a null content type;
        if (Strings.isNullOrEmpty(contentType)) {
            return false;
        }
        contentType = Mime.getContentType(contentType).toLowerCase();
        // only set no-open for 'script type content'
        return SCRIPTABLE_CONTENT_TYPES.contains(contentType);

    }
}
