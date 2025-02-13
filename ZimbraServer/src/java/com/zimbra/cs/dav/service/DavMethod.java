/*
 * 
 */
package com.zimbra.cs.dav.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.methods.ByteArrayRequestEntity;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.InputStreamRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.dom4j.io.XMLWriter;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.ByteUtil;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.dav.DavContext;
import com.zimbra.cs.dav.DavException;
import com.zimbra.cs.dav.DavProtocol;

/**
 * Base class for DAV methods.
 * 
 * @author jylee
 *
 */
public abstract class DavMethod {
	public abstract String getName();
	public abstract void handle(DavContext ctxt) throws DavException, IOException, ServiceException;
	
	public void checkPrecondition(DavContext ctxt) throws DavException {
	}
	
	public void checkPostcondition(DavContext ctxt) throws DavException {
	}
	
	public String toString() {
		return "DAV method " + getName();
	}
	
	public String getMethodName() {
		return getName();
	}
	
	protected static final int STATUS_OK = HttpServletResponse.SC_OK;
	
	protected void sendResponse(DavContext ctxt) throws IOException {
	    if (ctxt.isResponseSent())
	        return;
	    HttpServletResponse resp = ctxt.getResponse();
	    resp.setStatus(ctxt.getStatus());
	    String compliance = ctxt.getDavCompliance();
	    if (compliance != null)
	        setResponseHeader(resp, DavProtocol.HEADER_DAV, compliance);
	    if (ctxt.hasResponseMessage()) {
	        resp.setContentType(DavProtocol.DAV_CONTENT_TYPE);
	        DavResponse respMsg = ctxt.getDavResponse();
	        respMsg.writeTo(resp.getOutputStream());
	    }
	    ctxt.responseSent();
	}
	
	public static void setResponseHeader(HttpServletResponse resp, String name, String value) {
	    while (value != null) {
	        String val = value;
	        if (value.length() > 70) {
	            int index = value.lastIndexOf(',', 70);
	            if (index == -1) {
	                ZimbraLog.dav.warn("header value is too long for %s : %s", name, value);
	                return;
	            }
	            val = value.substring(0, index);
	            value = value.substring(index+1).trim();
	        } else {
	            value = null;
	        }
            resp.addHeader(name, val);
	    }
	}
	
	public HttpMethod toHttpMethod(DavContext ctxt, String targetUrl) throws IOException, DavException {
		if (ctxt.getUpload() != null && ctxt.getUpload().getSize() > 0) {
			PostMethod method = new PostMethod(targetUrl) {
				public String getName() { return getMethodName(); }
			};
			RequestEntity reqEntry;
			if (ctxt.hasRequestMessage()) {
			    ByteArrayOutputStream baos = new ByteArrayOutputStream();
				XMLWriter writer = new XMLWriter(baos);
				writer.write(ctxt.getRequestMessage());
				reqEntry = new ByteArrayRequestEntity(baos.toByteArray());
			} else { // this could be a huge upload
				reqEntry = new InputStreamRequestEntity(ctxt.getUpload().getInputStream(), ctxt.getUpload().getSize());
			}
			method.setRequestEntity(reqEntry);
			return method;
		}
    	return new GetMethod(targetUrl) {
    		public String getName() { return getMethodName(); }
    	};
	}
}
