/*
 * 
 */
package com.zimbra.cs.zclient.event;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.Element;
import com.zimbra.common.soap.MailConstants;
import com.zimbra.cs.zclient.ToZJSONObject;
import com.zimbra.cs.zclient.ZAppointment;
import com.zimbra.cs.zclient.ZItem;
import com.zimbra.cs.zclient.ZJSONObject;
import org.json.JSONException;

public class ZModifyAppointmentEvent implements ZModifyItemEvent, ZModifyItemFolderEvent, ToZJSONObject {

    protected Element mApptEl;

    public ZModifyAppointmentEvent(Element e) throws ServiceException {
        mApptEl = e;
    }

    /**
     * @return id
     * @throws com.zimbra.common.service.ServiceException on error
     */
    public String getId() throws ServiceException {
        return mApptEl.getAttribute(MailConstants.A_ID);
    }

    public ZJSONObject toZJSONObject() throws JSONException {
        try {
            ZJSONObject zjo = new ZJSONObject();
            zjo.put("id", getId());
            return zjo;
        } catch (ServiceException e) {
            throw new JSONException(e);
        }
    }

    public String toString() {
        try {
            return String.format("[ZModifyAppointmentEvent %s]", getId());
        } catch (ServiceException e) {
            throw new RuntimeException(e);
        }
    }

    public String dump() {
        return ZJSONObject.toString(this);
    }

    public ZItem getItem() throws ServiceException {
        return new ZAppointment(mApptEl);
    }

    /**
     * @param defaultValue value to return if unchanged
     * @return new flags or default value if flags didn't change
     */
    public String getFlags(String defaultValue) {
        return mApptEl.getAttribute(MailConstants.A_FLAGS, defaultValue);
    }

    /**
     * @param defaultValue value to return if unchanged
     * @return new tags or default value if tags didn't change
     */
    public String getTagIds(String defaultValue) {
        return mApptEl.getAttribute(MailConstants.A_TAGS, defaultValue);
    }

    public String getFolderId(String defaultValue) {
        return mApptEl.getAttribute(MailConstants.A_FOLDER, defaultValue);
    }

}
