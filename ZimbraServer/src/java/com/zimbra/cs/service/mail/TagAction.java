/*
 * 
 */

/*
 * Created on May 26, 2004
 */
package com.zimbra.cs.service.mail;

import java.util.Map;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.MailConstants;
import com.zimbra.cs.mailbox.MailItem;
import com.zimbra.common.soap.Element;
import com.zimbra.common.soap.SoapFaultException;
import com.zimbra.soap.ZimbraSoapContext;

/**
 * @author schemers
 */
public class TagAction extends ItemAction  {

    public static final String OP_UNFLAG = '!' + OP_FLAG;
    public static final String OP_UNTAG  = '!' + OP_TAG;

	public Element handle(Element request, Map<String, Object> context) throws ServiceException, SoapFaultException {
		ZimbraSoapContext lc = getZimbraSoapContext(context);

        Element action = request.getElement(MailConstants.E_ACTION);
        String operation = action.getAttribute(MailConstants.A_OPERATION).toLowerCase();

        if (operation.equals(OP_TAG) || operation.equals(OP_FLAG) || operation.equals(OP_UNTAG) || operation.equals(OP_UNFLAG))
            throw ServiceException.INVALID_REQUEST("cannot tag/flag a tag", null);
        if (operation.endsWith(OP_MOVE) || operation.endsWith(OP_COPY) || operation.endsWith(OP_SPAM) || operation.endsWith(OP_TRASH))
            throw ServiceException.INVALID_REQUEST("invalid operation on tag: " + operation, null);
        String successes = handleCommon(context, request, operation, MailItem.TYPE_TAG);

        Element response = lc.createElement(MailConstants.TAG_ACTION_RESPONSE);
    	Element result = response.addUniqueElement(MailConstants.E_ACTION);
    	result.addAttribute(MailConstants.A_ID, successes);
    	result.addAttribute(MailConstants.A_OPERATION, operation);
        return response;
	}
}
