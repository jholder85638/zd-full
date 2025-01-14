/*
 * 
 */
package com.zimbra.cs.service.mail;

import java.util.List;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.Element;
import com.zimbra.common.soap.MailConstants;
import com.zimbra.common.util.Log;
import com.zimbra.common.util.LogFactory;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.index.CalendarItemHit;
import com.zimbra.cs.index.ContactHit;
import com.zimbra.cs.index.ConversationHit;
import com.zimbra.cs.index.DocumentHit;
import com.zimbra.cs.index.MessageHit;
import com.zimbra.cs.index.MessagePartHit;
import com.zimbra.cs.index.NoteHit;
import com.zimbra.cs.index.ProxiedHit;
import com.zimbra.cs.index.QueryInfo;
import com.zimbra.cs.index.SearchParams;
import com.zimbra.cs.index.SortBy;
import com.zimbra.cs.index.ZimbraHit;
import com.zimbra.cs.index.SearchParams.ExpandResults;
import com.zimbra.cs.mailbox.Appointment;
import com.zimbra.cs.mailbox.CalendarItem;
import com.zimbra.cs.mailbox.Conversation;
import com.zimbra.cs.mailbox.Flag;
import com.zimbra.cs.mailbox.MailItem;
import com.zimbra.cs.mailbox.Message;
import com.zimbra.cs.mailbox.OperationContext;
import com.zimbra.cs.mailbox.WikiItem;
import com.zimbra.cs.mailbox.Mailbox.SearchResultMode;
import com.zimbra.cs.service.mail.GetCalendarItemSummaries.EncodeCalendarItemResult;
import com.zimbra.cs.service.mail.ToXML.EmailType;
import com.zimbra.cs.service.util.ItemId;
import com.zimbra.cs.service.util.ItemIdFormatter;
import com.zimbra.cs.session.PendingModifications;
import com.zimbra.soap.DocumentHandler;
import com.zimbra.soap.ZimbraSoapContext;

/**
 * A helper class to build a search SOAP response.
 *
 * @author ysasaki
 */
public class SearchResponse {
    protected static final Log LOG = LogFactory.getLog(SearchResponse.class);

    private final ZimbraSoapContext zsc;
    private final ItemIdFormatter ifmt;
    private final SearchParams params;
    private final Element element;
    private final OperationContext octxt;
    private boolean includeMailbox = false;
    private int size = 0;
    private ExpandResults expand;
    private SortBy sortOrder = SortBy.NONE;;

    public SearchResponse(ZimbraSoapContext zsc, OperationContext octxt,
            Element element, SearchParams params) {
        this.zsc = zsc;
        this.params = params;
        this.octxt = octxt;
        this.element = element;
        ifmt = new ItemIdFormatter(zsc);
        expand = params.getInlineRule();
    }

    /**
     * Set whether the response includes mailbox IDs or not.
     *
     * @param value true to include, otherwise false
     */
    public void setIncludeMailbox(boolean value) {
        includeMailbox = value;
    }

    public void setSortOrder(SortBy value) {
        sortOrder = value;
    }

    /**
     * Append a paging flag to the response.
     *
     * @param hasMore true if the search result has more pages, otherwise false
     */
    public void addHasMore(boolean hasMore) {
        element.addAttribute(MailConstants.A_QUERY_MORE, hasMore);
    }

    /**
     * Once you are done, call this method to get the result.
     *
     * @return result
     */
    public Element toElement() {
        return element;
    }

    /**
     * Returns the number of hits.
     *
     * @return number of hits
     */
    public int size() {
        return size;
    }

    /**
     * Append the hit to this response.
     *
     * @param hit hit to append
     * @throws ServiceException error
     */
    public void add(ZimbraHit hit) throws ServiceException {
        boolean inline = (size == 0 && expand == ExpandResults.FIRST) ||
            expand == ExpandResults.ALL || expand == ExpandResults.HITS ||
            expand.matches(hit.getParsedItemID());

        Element el = null;
        if (params.getMode() == SearchResultMode.IDS) {
            if (hit instanceof ConversationHit) {
                // need to expand the contained messages
                el = element.addElement("hit");
                el.addAttribute(MailConstants.A_ID,
                        ifmt.formatItemId(hit.getParsedItemID()));
            } else {
                el = element.addElement("hit");
                el.addAttribute(MailConstants.A_ID,
                        ifmt.formatItemId(hit.getParsedItemID()));
            }
        } else if (hit instanceof ProxiedHit) {
            element.addElement(((ProxiedHit) hit).getElement().detach());
            size++;
            return;
        } else {
            if (hit instanceof ConversationHit) {
                el = add((ConversationHit) hit);
            } else if (hit instanceof MessageHit) {
                el = add((MessageHit) hit, inline);
            } else if (hit instanceof MessagePartHit) {
                el = add((MessagePartHit) hit);
            } else if (hit instanceof ContactHit) {
                el = add((ContactHit) hit);
            } else if (hit instanceof NoteHit) {
                el = add((NoteHit) hit);
            } else if (hit instanceof CalendarItemHit) {
                el = add((CalendarItemHit) hit); // el could be null
            } else if (hit instanceof DocumentHit) {
                el = add((DocumentHit) hit);
            } else {
                LOG.error("Got an unknown hit type putting search hits: " + hit);
                return;
            }
        }

        if (el != null) {
            size++;
            el.addAttribute(MailConstants.A_SORT_FIELD,
                    hit.getSortField(sortOrder).toString());
            if (includeMailbox) {
                el.addAttribute(MailConstants.A_ID, new ItemId(
                        hit.getAcctIdStr(), hit.getItemId()).toString());
            }
        }
    }

    private Element add(ConversationHit hit) throws ServiceException {
        if (params.getMode() == SearchResultMode.IDS) {
            Element el = element.addElement(MailConstants.E_CONV);
            for (MessageHit mhit : hit.getMessageHits()) {
                el.addElement(MailConstants.E_MSG).addAttribute(
                        MailConstants.A_ID, ifmt.formatItemId(mhit.getItemId()));
            }
            return el;
        } else {
            Conversation conv = hit.getConversation();
            MessageHit mhit = hit.getFirstMessageHit();
            Element el = ToXML.encodeConversationSummary(element, ifmt, octxt, conv,
                    mhit == null ? null : mhit.getMessage(), params.getWantRecipients());

            for (MessageHit mh : hit.getMessageHits()) {
                Message msg = mh.getMessage();
                Element mel = el.addElement(MailConstants.E_MSG).addAttribute(
                        MailConstants.A_ID, ifmt.formatItemId(msg));
                // if it's a 1-message conversation,
                // hand back the folder and size for the lone message
                if (el.getAttributeLong(MailConstants.A_NUM, 0) == 1) {
                    mel.addAttribute(MailConstants.A_SIZE, msg.getSize()).addAttribute(
                            MailConstants.A_FOLDER, msg.getFolderId());
                }
                if (msg.isDraft() && msg.getDraftAutoSendTime() != 0)
                    mel.addAttribute(MailConstants.A_AUTO_SEND_TIME, msg.getDraftAutoSendTime());
            }
            return el;
        }
    }

    private Element add(MessageHit hit, boolean inline)
        throws ServiceException {

        Message msg = hit.getMessage();

        // for bug 7568, mark-as-read must happen before the response is encoded.
        if (inline && msg.isUnread() && params.getMarkRead()) {
            // Mark the message as READ
            try {
                msg.getMailbox().alterTag(octxt, msg.getId(), msg.getType(),
                        Flag.ID_FLAG_UNREAD, false);
            } catch (ServiceException e) {
                if (e.getCode().equals(ServiceException.PERM_DENIED)) {
                    LOG.info("no permissions to mark message as read (ignored): " +
                            msg.getId());
                } else {
                    LOG.warn("problem marking message as read (ignored): " +
                            msg.getId(), e);
                }
            }
        }

        Element el;
        if (inline) {
            el = ToXML.encodeMessageAsMP(element, ifmt, octxt, msg, null,
                    params.getMaxInlinedLength(), params.getWantHtml(),
                    params.getNeuterImages(), params.getInlinedHeaders(), true);
        } else {
            el = ToXML.encodeMessageSummary(element, ifmt, octxt, msg,
                    params.getWantRecipients());
        }

        el.addAttribute(MailConstants.A_CONTENTMATCHED, true);

        List<MessagePartHit> parts = hit.getMatchedMimePartNames();
        if (parts != null) {
            for (MessagePartHit mph : parts) {
                String partNameStr = mph.getPartName();
                if (partNameStr.length() > 0)
                    el.addElement(MailConstants.E_HIT_MIMEPART).addAttribute(
                            MailConstants.A_PART, partNameStr);
            }
        }

        return el;
    }

    private Element add(MessagePartHit hit) throws ServiceException {
        Message msg = hit.getMessageResult().getMessage();
        Element el = element.addElement(MailConstants.E_MIMEPART);
        el.addAttribute(MailConstants.A_SIZE, msg.getSize());
        el.addAttribute(MailConstants.A_DATE, msg.getDate());
        el.addAttribute(MailConstants.A_CONV_ID, msg.getConversationId());
        el.addAttribute(MailConstants.A_MESSAGE_ID, msg.getId());
        el.addAttribute(MailConstants.A_CONTENT_TYPE, hit.getType());
        el.addAttribute(MailConstants.A_CONTENT_NAME, hit.getFilename());
        el.addAttribute(MailConstants.A_PART, hit.getPartName());

        ToXML.encodeEmail(el, msg.getSender(), EmailType.FROM);
        String subject = msg.getSubject();
        if (subject != null) {
            el.addAttribute(MailConstants.E_SUBJECT, subject,
                    Element.Disposition.CONTENT);
        }

        return el;
    }

    private Element add(ContactHit hit) throws ServiceException {
        return ToXML.encodeContact(element, ifmt, hit.getContact(), true, null);
    }

    private Element add(NoteHit hit) throws ServiceException {
        return ToXML.encodeNote(element, ifmt, hit.getNote());
    }

    private Element add(DocumentHit hit) throws ServiceException {
        if (hit.getItemType() == MailItem.TYPE_DOCUMENT) {
            return ToXML.encodeDocument(element, ifmt, octxt, hit.getDocument());
        } else if (hit.getItemType() == MailItem.TYPE_WIKI) {
            return ToXML.encodeWiki(element, ifmt, octxt,
                    (WikiItem) hit.getDocument());
        } else {
            throw ServiceException.UNKNOWN_DOCUMENT(
                    "invalid document type " + hit.getItemType(), null);
        }
    }

    /**
     * The encoded element OR NULL if the search params contained a calItemExpand
     * range AND the calendar item did not have any instances in the specified range.
     *
     * @return could be NULL
     */
    private Element add(CalendarItemHit hit) throws ServiceException {

        CalendarItem item = hit.getCalendarItem();
        Account acct = DocumentHandler.getRequestedAccount(zsc);
        long rangeStart = params.getCalItemExpandStart();
        long rangeEnd = params.getCalItemExpandEnd();
        if (rangeStart < 0 && rangeEnd < 0 && (item instanceof Appointment)) {
            // If no time range was given, force first instance only. (bug 51267)
            rangeStart = item.getStartTime();
            rangeEnd = rangeStart + 1;
        }
        EncodeCalendarItemResult encoded =
            GetCalendarItemSummaries.encodeCalendarItemInstances(zsc, octxt, item, acct, rangeStart, rangeEnd, true);

        Element el = encoded.element;
        if (el != null) {
            element.addElement(el);
            ToXML.setCalendarItemFields(el, ifmt, octxt, item, PendingModifications.Change.ALL_FIELDS, false,
                    params.getNeuterImages());
            el.addAttribute(MailConstants.A_CONTENTMATCHED, true);
        }
        return el;
    }

    /**
     * Append the query information to this response.
     *
     * @param qinfo query information
     * @param estimatedResultSize estimated result size
     */
    public void add(List<QueryInfo> qinfo, int estimatedResultSize) {
        if ((qinfo.size() > 0) || params.getEstimateSize()) {
            Element el = element.addElement(MailConstants.E_INFO);
            el.addElement("sizeEstimate").addAttribute("value",
                    estimatedResultSize);
            for (QueryInfo inf : qinfo) {
                inf.toXml(el);
            }
        }
    }

}
