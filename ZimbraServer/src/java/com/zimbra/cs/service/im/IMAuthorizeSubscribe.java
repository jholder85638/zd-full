/*
 * 
 */
package com.zimbra.cs.service.im;

import java.util.Map;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.IMConstants;
import com.zimbra.common.soap.Element;
import com.zimbra.cs.im.IMAddr;
import com.zimbra.cs.mailbox.OperationContext;
import com.zimbra.common.soap.SoapFaultException;
import com.zimbra.soap.ZimbraSoapContext;

public class IMAuthorizeSubscribe extends IMDocumentHandler {
    @Override
    public Element handle(Element request, Map<String, Object> context) throws ServiceException, SoapFaultException
    {
        ZimbraSoapContext zsc = getZimbraSoapContext(context);
        Element response = zsc.createElement(IMConstants.IM_AUTHORIZE_SUBSCRIBE_RESPONSE);
        
        IMAddr addr = new IMAddr(request.getAttribute(IMConstants.A_ADDRESS));
        boolean authorized = request.getAttributeBool(IMConstants.A_AUTHORIZED);
        boolean add = request.getAttributeBool(IMConstants.A_ADD, false);
        String name = request.getAttribute(IMConstants.A_NAME, "");
        String groupStr = request.getAttribute(IMConstants.A_GROUPS, null);
        String[] groups;
        if (groupStr != null) 
            groups = groupStr.split(",");
        else
            groups = new String[0];

        OperationContext oc = getOperationContext(zsc, context);
        
        getRequestedMailbox(zsc).getPersona().authorizeSubscribe(oc, addr, authorized, add, name, groups);

        return response;
    }

}
