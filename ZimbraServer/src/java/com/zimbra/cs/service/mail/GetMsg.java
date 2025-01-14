/*
 * 
 */

/*
 * Created on May 26, 2004
 */
package com.zimbra.cs.service.mail;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.MailConstants;
import com.zimbra.common.soap.Element;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.mailbox.CalendarItem;
import com.zimbra.cs.mailbox.Flag;
import com.zimbra.cs.mailbox.MailItem;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.Message;
import com.zimbra.cs.mailbox.OperationContext;
import com.zimbra.cs.mailbox.calendar.Invite;
import com.zimbra.cs.mailbox.calendar.RecurId;
import com.zimbra.cs.redolog.RedoLogProvider;
import com.zimbra.cs.service.util.ItemId;
import com.zimbra.cs.service.util.ItemIdFormatter;
import com.zimbra.soap.ZimbraSoapContext;

/**
 * @author schemers
 */
public class GetMsg extends MailDocumentHandler {

    private static final String[] TARGET_MSG_PATH = new String[] { MailConstants.E_MSG, MailConstants.A_ID };
    protected String[] getProxiedIdPath(Element request)     { return TARGET_MSG_PATH; }
    protected boolean checkMountpointProxy(Element request)  { return false; }

    @Override
    public Element handle(Element request, Map<String, Object> context) throws ServiceException {
        ZimbraSoapContext zsc = getZimbraSoapContext(context);
        Mailbox mbox = getRequestedMailbox(zsc);
        OperationContext octxt = getOperationContext(zsc, context);
        ItemIdFormatter ifmt = new ItemIdFormatter(zsc);

        Element eMsg = request.getElement(MailConstants.E_MSG);
        ItemId iid = new ItemId(eMsg.getAttribute(MailConstants.A_ID), zsc);
        String part = eMsg.getAttribute(MailConstants.A_PART, null);

        boolean raw = eMsg.getAttributeBool(MailConstants.A_RAW, false);
        boolean read = eMsg.getAttributeBool(MailConstants.A_MARK_READ, false);
        int maxSize = (int) eMsg.getAttributeLong(MailConstants.A_MAX_INLINED_LENGTH, 0);

        boolean wantHTML = eMsg.getAttributeBool(MailConstants.A_WANT_HTML, false);
        boolean neuter = eMsg.getAttributeBool(MailConstants.A_NEUTER, true);

        Set<String> headers = null;
        for (Element eHdr : eMsg.listElements(MailConstants.A_HEADER)) {
            if (headers == null)  headers = new HashSet<String>();
            headers.add(eHdr.getAttribute(MailConstants.A_ATTRIBUTE_NAME));
        }

        Element response = zsc.createElement(MailConstants.GET_MSG_RESPONSE);
        if (iid.hasSubpart()) {
            // calendar item
            CalendarItem calItem = getCalendarItem(octxt, mbox, iid);
            if (raw) {
                throw ServiceException.INVALID_REQUEST("Cannot request RAW formatted subpart message", null);
            } else {
                String recurIdZ = eMsg.getAttribute(MailConstants.A_CAL_RECURRENCE_ID_Z, null);
                if (recurIdZ == null) {
                    // If not specified, try to get it from the Invite.
                    int invId = iid.getSubpartId();
                    Invite[] invs = calItem.getInvites(invId);
                    if (invs.length > 0) {
                        RecurId rid = invs[0].getRecurId();
                        if (rid != null)
                            recurIdZ = rid.getDtZ();
                    }
                }
                ToXML.encodeInviteAsMP(response, ifmt, octxt, calItem, recurIdZ, iid, part, maxSize, wantHTML, neuter, headers, false);
            }
        } else {
            Message msg = getMsg(octxt, mbox, iid, read);
            if (raw) {
                ToXML.encodeMessageAsMIME(response, ifmt, msg, part, false);
            } else {
                ToXML.encodeMessageAsMP(response, ifmt, octxt, msg, part, maxSize, wantHTML, neuter, headers, false);
            }
        }
        
        if (eMsg.getAttributeBool(MailConstants.A_NEED_EXP, false))
            ToXML.encodeMsgAddrsWithGroupInfo(response, getRequestedAccount(zsc), getAuthenticatedAccount(zsc));
        
        return response;
    }

    @Override
    public boolean isReadOnly() {
        return RedoLogProvider.getInstance().isSlave();
    }
    
    public static CalendarItem getCalendarItem(OperationContext octxt, Mailbox mbox, ItemId iid) throws ServiceException {
        assert(iid.hasSubpart());
        return mbox.getCalendarItemById(octxt, iid.getId());
    }
    
    public static Message getMsg(OperationContext octxt, Mailbox mbox, ItemId iid, boolean read) throws ServiceException {
        assert(!iid.hasSubpart());
        Message msg = mbox.getMessageById(octxt, iid.getId());
        if (read && msg.isUnread() && !RedoLogProvider.getInstance().isSlave()) {
            try {
                mbox.alterTag(octxt, msg.getId(), MailItem.TYPE_MESSAGE, Flag.ID_FLAG_UNREAD, false);
            } catch (ServiceException e) {
                if (e.getCode().equals(ServiceException.PERM_DENIED))
                    ZimbraLog.mailbox.info("no permissions to mark message as read (ignored): " + msg.getId());
                else
                    ZimbraLog.mailbox.warn("problem marking message as read (ignored): " + msg.getId(), e);
            }
        }
        return msg;
    }
    
    public static List<Message> getMsgs(OperationContext octxt, Mailbox mbox, List<Integer> iids, boolean read) throws ServiceException {
        int i = 0;
        int[] msgIdArray = new int[iids.size()];
        for (int id : iids)
            msgIdArray[i++] = id;
        
        List<Message> toRet = new ArrayList<Message>(msgIdArray.length);
        for (MailItem item : mbox.getItemById(octxt, msgIdArray, MailItem.TYPE_MESSAGE)) {
            toRet.add((Message) item);
            if (read && item.isUnread() && !RedoLogProvider.getInstance().isSlave())
                mbox.alterTag(octxt, item.getId(), MailItem.TYPE_MESSAGE, Flag.ID_FLAG_UNREAD, false);
        }
        return toRet;
    }
}
