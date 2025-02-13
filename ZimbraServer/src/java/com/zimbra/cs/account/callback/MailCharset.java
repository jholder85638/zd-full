/*
 * 
 */

package com.zimbra.cs.account.callback;

import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Map;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.StringUtil;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.AttributeCallback;
import com.zimbra.cs.account.Cos;
import com.zimbra.cs.account.Entry;
import com.zimbra.cs.account.Provisioning;
 
public class MailCharset extends AttributeCallback {

    public void preModify(Map context, String attrName, Object value,
            Map attrsToModify, Entry entry, boolean isCreate) throws ServiceException {

        String charset = null;
        SingleValueMod mod = singleValueMod(attrName, value);
        if (mod.unsetting())
            return;
        else
            charset = mod.value();

        
         try {
            Charset.forName(charset);
        } catch (IllegalCharsetNameException e) {
            throw ServiceException.INVALID_REQUEST("charset name " + charset + " is illegal", e);
        } catch (UnsupportedCharsetException e) {
            throw ServiceException.INVALID_REQUEST("no support for charset " + charset + " is available in this instance of the Java virtual machine", e);
        }
    }

    /**
     * need to keep track in context on whether or not we have been called yet, only 
     * reset info once
     */

    public void postModify(Map context, String attrName, Entry entry, boolean isCreate) {

    }
}
