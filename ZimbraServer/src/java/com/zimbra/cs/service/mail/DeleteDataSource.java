/*
 * 
 */
package com.zimbra.cs.service.mail;

import java.util.Map;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.MailConstants;
import com.zimbra.common.soap.SoapFaultException;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.DataSource;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.account.Provisioning.DataSourceBy;
import com.zimbra.cs.datasource.DataSourceManager;
import com.zimbra.cs.db.DbDataSource;
import com.zimbra.cs.db.DbImapFolder;
import com.zimbra.cs.db.DbPop3Message;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.common.soap.Element;
import com.zimbra.soap.ZimbraSoapContext;


public class DeleteDataSource extends MailDocumentHandler {

    @Override
    public Element handle(Element request, Map<String, Object> context)
    throws ServiceException, SoapFaultException {
        ZimbraSoapContext zsc = getZimbraSoapContext(context);
        Provisioning prov = Provisioning.getInstance();
        Account account = getRequestedAccount(zsc);

        if (!canModifyOptions(zsc, account))
            throw ServiceException.PERM_DENIED("can not modify options");

        Mailbox mbox = getRequestedMailbox(zsc);

        for (Element eDsrc : request.listElements()) {
            DataSource dsrc = null;
            String name, id = eDsrc.getAttribute(MailConstants.A_ID, null);
            
            if (id != null)
                dsrc = prov.get(account, DataSourceBy.id, id);
            else if ((name = eDsrc.getAttribute(MailConstants.A_NAME, null)) != null)
                dsrc = prov.get(account, DataSourceBy.name, name);
            else
                throw ServiceException.INVALID_REQUEST("must specify either 'id' or 'name'", null);

            // note that we're not checking the element name against the actual data source's type
            if (dsrc == null)
                continue;
            String dataSourceId = dsrc.getId();
            DataSource.Type dstype = dsrc.getType();

            prov.deleteDataSource(account, dataSourceId);
            if (dstype == DataSource.Type.pop3)
                DbPop3Message.deleteUids(mbox, dataSourceId);
            else if (dstype == DataSource.Type.imap)
                DbImapFolder.deleteImapData(mbox, dataSourceId);
            DbDataSource.deleteAllMappings(dsrc);
            DataSourceManager.cancelSchedule(account, dataSourceId);
        }
        
        Element response = zsc.createElement(MailConstants.DELETE_DATA_SOURCE_RESPONSE);
        return response;
    }
}
