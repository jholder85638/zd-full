/*
 * 
 */
package com.zimbra.cs.service.admin;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.BooleanClause.Occur;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.AdminConstants;
import com.zimbra.common.soap.Element;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.account.Server;
import com.zimbra.cs.account.Provisioning.ServerBy;
import com.zimbra.cs.account.accesscontrol.AdminRight;
import com.zimbra.cs.account.accesscontrol.Rights.Admin;
import com.zimbra.cs.rmgmt.RemoteMailQueue;
import com.zimbra.cs.rmgmt.RemoteMailQueue.QueueAttr;
import com.zimbra.cs.rmgmt.RemoteMailQueue.SummaryItem;
import com.zimbra.soap.ZimbraSoapContext;

public class GetMailQueue extends AdminDocumentHandler {

    public static final int MAIL_QUEUE_QUERY_DEFAULT_LIMIT = 30;
    public static final int MAIL_QUEUE_SCAN_DEFUALT_WAIT_SECONDS = 3;
    public static final int MAIL_QUEUE_SUMMARY_CUTOFF = 100;

    public Element handle(Element request, Map<String, Object> context) throws ServiceException {
        ZimbraSoapContext zsc = getZimbraSoapContext(context);
        Provisioning prov = Provisioning.getInstance();

        Element serverElem = request.getElement(AdminConstants.E_SERVER);
        String serverName = serverElem.getAttribute(AdminConstants.A_NAME);

        Server server = prov.get(ServerBy.name, serverName);
        if (server == null) {
            throw ServiceException.INVALID_REQUEST("server with name " + serverName + " could not be found", null);
        }
        
        checkRight(zsc, context, server, Admin.R_manageMailQueue);

        Element queueElem = serverElem.getElement(AdminConstants.E_QUEUE);
        String queueName = queueElem.getAttribute(AdminConstants.A_NAME);
        boolean scan = queueElem.getAttributeBool(AdminConstants.A_SCAN, false);
        long waitMillis = (queueElem.getAttributeLong(AdminConstants.A_WAIT, MAIL_QUEUE_SCAN_DEFUALT_WAIT_SECONDS)) * 1000;

        Element queryElem = queueElem.getElement(AdminConstants.E_QUERY);
        int offset = (int)queryElem.getAttributeLong(AdminConstants.A_OFFSET, 0);
        int limit = (int)queryElem.getAttributeLong(AdminConstants.A_LIMIT, MAIL_QUEUE_QUERY_DEFAULT_LIMIT);
        Query query = buildLuceneQuery(queryElem);

        RemoteMailQueue rmq = RemoteMailQueue.getRemoteMailQueue(server, queueName, scan);
        boolean stillScanning = rmq.waitForScan(waitMillis);
        RemoteMailQueue.SearchResult sr = rmq.search(query, offset, limit);

        Element response = zsc.createElement(AdminConstants.GET_MAIL_QUEUE_RESPONSE);
        serverElem = response.addElement(AdminConstants.E_SERVER);
        serverElem.addAttribute(AdminConstants.A_NAME, serverName);

        queueElem = serverElem.addElement(AdminConstants.E_QUEUE);
        queueElem.addAttribute(AdminConstants.A_NAME, queueName);
        queueElem.addAttribute(AdminConstants.A_TIME, rmq.getScanTime());
        queueElem.addAttribute(AdminConstants.A_SCAN, stillScanning);
        queueElem.addAttribute(AdminConstants.A_TOTAL, rmq.getNumMessages());
        queueElem.addAttribute(AdminConstants.A_MORE, ((offset + limit) < sr.hits));

        for (QueueAttr attr : sr.sitems.keySet()) {
            List<SummaryItem> slist = sr.sitems.get(attr);
            Collections.sort(slist);
            Element qsElem = queueElem.addElement(AdminConstants.A_QUEUE_SUMMARY);
            qsElem.addAttribute(AdminConstants.A_TYPE, attr.toString());
            int i = 0;
            for (SummaryItem sitem : slist) {
                i++;
                if (i > MAIL_QUEUE_SUMMARY_CUTOFF) {
                    break;
                }
                Element qsiElem = qsElem.addElement(AdminConstants.A_QUEUE_SUMMARY_ITEM);
                qsiElem.addAttribute(AdminConstants.A_N, sitem.count());
                qsiElem.addAttribute(AdminConstants.A_T, sitem.term());
            }
        }

        for (Map<QueueAttr,String> qitem : sr.qitems) {
            Element qiElem = queueElem.addElement(AdminConstants.A_QUEUE_ITEM);
            for (QueueAttr attr : qitem.keySet()) {
                qiElem.addAttribute(attr.toString(), qitem.get(attr));
            }
        }
        return response;
    }

    public static Query buildLuceneQuery(Element queryElem) throws ServiceException {
        BooleanQuery fq = new BooleanQuery();
        boolean emptyQuery = true;
        for (Iterator fieldIter = queryElem.elementIterator(AdminConstants.E_FIELD); fieldIter.hasNext();) {
            emptyQuery = false;
            Element fieldElement = (Element)fieldIter.next();
            String fieldName = fieldElement.getAttribute(AdminConstants.A_NAME);
            BooleanQuery mq = new BooleanQuery();
            for (Iterator matchIter = fieldElement.elementIterator(AdminConstants.E_MATCH); matchIter.hasNext();) {
                Element matchElement = (Element)matchIter.next();
                String matchValue = matchElement.getAttribute(AdminConstants.A_VALUE);
                Term term = new Term(fieldName, matchValue);
                mq.add(new TermQuery(term), Occur.SHOULD);
            }
            fq.add(mq, Occur.MUST);
        }
        if (emptyQuery) {
            return null;
        } else {
            return fq;
        }
    }

    @Override
    public void docRights(List<AdminRight> relatedRights, List<String> notes) {
        relatedRights.add(Admin.R_manageMailQueue);
    }
    
}
