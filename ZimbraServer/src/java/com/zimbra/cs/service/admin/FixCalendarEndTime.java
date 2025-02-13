/* 
 */

package com.zimbra.cs.service.admin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.AdminConstants;
import com.zimbra.common.soap.Element;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.NamedEntry;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.account.Provisioning.AccountBy;
import com.zimbra.cs.account.accesscontrol.AdminRight;
import com.zimbra.cs.account.accesscontrol.Rights.Admin;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.MailboxManager;
import com.zimbra.soap.ZimbraSoapContext;

public class FixCalendarEndTime extends AdminDocumentHandler {

    public static final String ALL = "all";

    public Element handle(Element request, Map<String, Object> context) throws ServiceException {
        ZimbraSoapContext zsc = getZimbraSoapContext(context);

        // what to check for this SOAP?
        // allow only system admin for now
        checkRight(zsc, context, null, AdminRight.PR_SYSTEM_ADMIN_ONLY);
        
        boolean sync = request.getAttributeBool(AdminConstants.A_TZFIXUP_SYNC, false);
        List<Element> acctElems = request.listElements(AdminConstants.E_ACCOUNT);
        List<String> acctNames = parseAccountNames(acctElems);
        if (acctNames.isEmpty())
            throw ServiceException.INVALID_REQUEST("Accounts must be specified", null);
        if (sync) {
            fixAccounts(acctNames);
        } else {
            CalendarEndTimeFixupThread thread =
                new CalendarEndTimeFixupThread(acctNames);
            thread.start();
        }

        Element response = zsc.createElement(AdminConstants.FIX_CALENDAR_END_TIME_RESPONSE);
        return response;
    }

    protected List<String> parseAccountNames(List<Element> acctElems) throws ServiceException {
        List<String> a = new ArrayList<String>(acctElems.size());
        for (Element elem : acctElems) {
            String name = elem.getAttribute(AdminConstants.A_NAME);
            if (ALL.equals(name)) {
                List<String> all = new ArrayList<String>(1);
                all.add(ALL);
                return all;
            } else {
                String[] parts = name.split("@");
                if (parts.length != 2)
                    throw ServiceException.INVALID_REQUEST("invalid account email address: " + name, null);
            }
            a.add(name);
        }
        return a;
    }

    private static List<NamedEntry> getAccountsOnServer() throws ServiceException {
        Provisioning prov = Provisioning.getInstance();
        String serverName = prov.getLocalServer().getAttr(Provisioning.A_zimbraServiceHostname);
        List<NamedEntry> accts = prov.searchAccounts(
                "(zimbraMailHost=" + serverName + ")",
                new String[] { Provisioning.A_zimbraId }, null, false,
                Provisioning.SA_ACCOUNT_FLAG | Provisioning.SA_CALENDAR_RESOURCE_FLAG);
        ZimbraLog.calendar.info("Found " + accts.size() + " accounts on server " + serverName);
        return accts;
    }

    private static void fixAccounts(List<String> acctNames)
    throws ServiceException {
        int numAccts = acctNames.size();
        boolean all = (numAccts == 1 && ALL.equals(acctNames.get(0)));
        int numFixedAccts = 0;
        int numFixedAppts = 0;
        List<NamedEntry> accts;
        if (all) {
            accts = getAccountsOnServer();
        } else {
            accts = new ArrayList<NamedEntry>(acctNames.size());
            for (String name : acctNames) {
                try {
                    accts.add(Provisioning.getInstance().get(AccountBy.name, name));
                } catch (ServiceException e) {
                    ZimbraLog.calendar.error(
                            "Error looking up account " + name + ": " + e.getMessage(), e);
                }
            }
        }
        numAccts = accts.size();
        int every = 10;
        for (NamedEntry entry : accts) {
            if (!(entry instanceof Account))
                continue;
            Account acct = (Account) entry;
            Mailbox mbox = MailboxManager.getInstance().getMailboxByAccount(acct);
            try {
                numFixedAppts += mbox.fixAllCalendarItemEndTime(null);
            } catch (ServiceException e) {
                ZimbraLog.calendar.error(
                        "Error fixing calendar item end times in mailbox " + mbox.getId() +
                        ": " + e.getMessage(), e);
            }
            numFixedAccts++;
            if (numFixedAccts % every == 0) {
                ZimbraLog.calendar.info(
                        "Progress: fixed calendar item end times in " + numFixedAccts + "/" +
                        numAccts + " accounts");
            }
        }
        ZimbraLog.calendar.info(
                "Fixed end times in total " + numFixedAppts + " calendar items in " + numFixedAccts + " accounts");
    }

    private static class CalendarEndTimeFixupThread extends Thread {
        private List<String> mAcctNames;

        public CalendarEndTimeFixupThread(List<String> acctNames) {
            setName("CalendarEndTimeFixupThread");
            mAcctNames = acctNames;
        }

        public void run() {
            try {
                fixAccounts(mAcctNames);
            } catch (ServiceException e) {
                ZimbraLog.calendar.error(
                        "Error while fixing up calendar end times: " + e.getMessage(), e);
            }
        }
    }
    
    @Override
    public void docRights(List<AdminRight> relatedRights, List<String> notes) {
        notes.add(AdminRightCheckPoint.Notes.SYSTEM_ADMINS_ONLY);
    }
}
