/*
 * 
 */
package org.jivesoftware.wildfire.privacy;

import org.dom4j.DocumentFactory;
import org.dom4j.Element;
import org.jivesoftware.util.CacheSizes;
import org.jivesoftware.util.Cacheable;
import org.jivesoftware.util.Log;
import org.jivesoftware.wildfire.XMPPServer;
import org.jivesoftware.wildfire.roster.Roster;
import org.jivesoftware.wildfire.user.UserNotFoundException;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A privacy list contains a set of rules that define if communication with the list owner
 * is allowed or denied. Users may have zero, one or more privacy lists. When a list is the
 * default list then that list is going to be used by default for all user sessions or analyze,
 * when user is offline, if communication may proceed (e.g. define if a message should be stored
 * offline). A user may configure is he wants to have a default list or not. When no default list
 * is defined then communication will not be blocked. However, users may define an active list
 * for a particular session. Active lists override default list (if there is one) and will be used
 * only for the duration of the session.
 *
 * @author Gaston Dombiak
 */
public class PrivacyList implements Cacheable {

    private JID userJID;
    private String name;
    private boolean isDefault;
    private List<PrivacyItem> items = new ArrayList<PrivacyItem>();
    private Roster roster;

    public PrivacyList(String username, String name, boolean isDefault, Element listElement) {
        assert(username.indexOf('@')>0);
        this.userJID = new JID(username);
        this.name = name;
        this.isDefault = isDefault;
        // Set the new list items
        updateList(listElement);
    }

    /**
     * Returns the name that uniquely identifies this list among the users lists.
     *
     * @return the name that uniquely identifies this list among the users lists.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns true if this privacy list is the default list to apply for the user. Default
     * privacy lists can be overriden per session by setting an active privacy list.
     *
     * @return true if this privacy list is the default list to apply for the user.
     */
    public boolean isDefault() {
        return isDefault;
    }

    /**
     * Sets if this privacy list is the default list to apply for the user. Default
     * privacy lists can be overriden per session by setting an active privacy list.
     *
     * @param isDefault true if this privacy list is the default list to apply for the user.
     */
    public void setDefaultList(boolean isDefault) {
        this.isDefault = isDefault;
    }

    /**
     * Returns true if the specified packet must be blocked based on this privacy list rules.
     * Rules are going to be analyzed based on their order (in ascending order). When a rule
     * is matched then communication will be blocked or allowed based on that rule. No more
     * further analysis is going to be made.
     *
     * @param packet the packet to analyze if it must be blocked.
     * @return true if the specified packet must be blocked based on this privacy list rules.
     */
    public boolean shouldBlockPacket(Packet packet) {
        if (packet.getFrom() == null) {
            // Sender is the server so it's not denied
            return false;
        }
        // Iterate over the rules and check each rule condition
        for (PrivacyItem item : items) {
            if (item.matchesCondition(packet, roster, userJID)) {
                if (item.isAllow()) {
                    return false;
                }
                if (Log.isDebugEnabled()) {
                    Log.debug("Packet was blocked: " + packet);
                }
                return true;
            }
        }
        // If no rule blocked the communication then allow the packet to flow
        return false;
    }

    /**
     * Returns an Element with the privacy list XML representation.
     *
     * @return an Element with the privacy list XML representation.
     */
    public Element asElement() {
        //Element listElement = DocumentFactory.getInstance().createDocument().addElement("list");
        Element listElement = DocumentFactory.getInstance().createDocument()
                .addElement("list", "jabber:iq:privacy");
        listElement.addAttribute("name", getName());
        // Add the list items to the result
        for (PrivacyItem item : items) {
            listElement.add(item.asElement());
        }
        return listElement;
    }

    /**
     * Sets the new list items based on the specified Element. The Element must contain
     * a list of item elements.
     *
     * @param listElement the element containing a list of items.
     */
    public void updateList(Element listElement) {
        // Reset the list of items of this list
        items = new ArrayList<PrivacyItem>();

        List<Element> itemsElements = listElement.elements("item");
        for (Element itemElement : itemsElements) {
            PrivacyItem newItem = new PrivacyItem(itemElement);
            items.add(newItem);
            // If the user's roster is required to evaluation whether a packet must be blocked
            // then ensure that the roster is available
            if (roster == null && newItem.isRosterRequired()) {
                try {
                    roster = XMPPServer.getInstance().getRosterManager().getRoster(userJID.toBareJID());
                }
                catch (UserNotFoundException e) {
                    Log.warn("Privacy item removed since roster of user was not found: " +
                            userJID.toBareJID());
                    items.remove(newItem);
                }
            }
        }
        // Sort items collections
        Collections.sort(items);
    }

    public int getCachedSize() {
        // Approximate the size of the object in bytes by calculating the size
        // of each field.
        int size = 0;
        size += CacheSizes.sizeOfObject();                      // overhead of object
        size += CacheSizes.sizeOfString(userJID.toString());    // userJID
        size += CacheSizes.sizeOfString(name);                  // name
        size += CacheSizes.sizeOfBoolean();                     // isDefault
        size += CacheSizes.sizeOfCollection(items);             // items of the list
        if (roster != null) {
            size += roster.getCachedSize();                     // add size of roster
        }
        return size;
    }

    public int hashCode() {
        return name.hashCode();
    }

    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object != null && object instanceof PrivacyList) {
            return name.equals(((PrivacyList)object).getName());
        }
        else {
            return false;
        }
    }
}
