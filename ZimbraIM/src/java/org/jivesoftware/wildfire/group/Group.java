/*
 * 
 */
package org.jivesoftware.wildfire.group;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.util.CacheSizes;
import org.jivesoftware.util.Cacheable;
import org.jivesoftware.util.Log;
import org.jivesoftware.wildfire.XMPPServer;
import org.jivesoftware.wildfire.event.GroupEventDispatcher;
import org.xmpp.packet.JID;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Groups organize users into a single entity for easier management.<p>
 *
 * The actual group implementation is controlled by the {@link GroupProvider}, which
 * includes things like the group name, the members, and adminstrators. Each group
 * also has properties, which are always stored in the Wildfire database.
 *
 * @see GroupManager#createGroup(String)
 *
 * @author Matt Tucker
 */
public class Group implements Cacheable {

    private static final String LOAD_PROPERTIES =
        "SELECT name, propValue FROM jiveGroupProp WHERE groupName=?";
    private static final String DELETE_PROPERTY =
        "DELETE FROM jiveGroupProp WHERE groupName=? AND name=?";
    private static final String UPDATE_PROPERTY =
        "UPDATE jiveGroupProp SET propValue=? WHERE name=? AND groupName=?";
    private static final String INSERT_PROPERTY =
        "INSERT INTO jiveGroupProp (groupName, name, propValue) VALUES (?, ?, ?)";
    private static final String LOAD_SHARED_GROUPS =
        "SELECT groupName FROM jiveGroupProp WHERE name='sharedRoster.showInRoster' " +
        "AND propValue IS NOT NULL AND propValue <> 'nobody'";

    private transient GroupProvider provider;
    private transient GroupManager groupManager;

    private String name;
    private String description;
    private Map<String, String> properties;
    private Set<JID> members;
    private Set<JID> administrators;

    /**
     * Returns the name of the groups that are shared groups.
     *
     * @return the name of the groups that are shared groups.
     */
    public static Set<String> getSharedGroupsNames() {
        Set<String> groupNames = new HashSet<String>();
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_SHARED_GROUPS);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                groupNames.add(rs.getString(1));
            }
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return groupNames;
    }

    /**
     * Constructs a new group. Note: this constructor is intended for implementors of the
     * {@link GroupProvider} interface. To create a new group, use the
     * {@link GroupManager#createGroup(String)} method. 
     *
     * @param name the name.
     * @param description the description.
     * @param members a Collection of the group members.
     * @param administrators a Collection of the group administrators.
     */
    public Group(String name, String description, Collection<JID> members,
            Collection<JID> administrators)
    {
        this.groupManager = GroupManager.getInstance();
        this.provider = groupManager.getProvider();
        this.name = name;
        this.description = description;
        this.members = new HashSet<JID>(members);
        this.administrators = new HashSet<JID>(administrators);
    }

    /**
     * Returns the name of the group. For example, 'XYZ Admins'.
     *
     * @return the name of the group.
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of the group. For example, 'XYZ Admins'. This
     * method is restricted to those with group administration permission.
     *
     * @param name the name for the group.
     */
    public void setName(String name) {
        if (name == this.name || (name != null && name.equals(this.name)) || provider.isReadOnly())
        {
            // Do nothing
            return;
        }
        try {
            String originalName = this.name;
            provider.setName(this.name, name);
            groupManager.groupCache.remove(this.name);
            this.name = name;
            groupManager.groupCache.put(name, this);

            // Fire event.
            Map<String, Object> params = new HashMap<String, Object>();
            params.put("type", "nameModified");
            params.put("originalValue", originalName);
            GroupEventDispatcher.dispatchEvent(this, GroupEventDispatcher.EventType.group_modified,
                    params);
        }
        catch (Exception e) {
            Log.error(e);
        }
    }

    /**
     * Returns the description of the group. The description often
     * summarizes a group's function, such as 'Administrators of the XYZ forum'.
     *
     * @return the description of the group.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the description of the group. The description often
     * summarizes a group's function, such as 'Administrators of
     * the XYZ forum'. This method is restricted to those with group
     * administration permission.
     *
     * @param description the description of the group.
     */
    public void setDescription(String description) {
        if (description == this.description ||
                (description != null && description.equals(this.description)) ||
                provider.isReadOnly())
        {
            // Do nothing
            return;
        }
        try {
            String originalDescription = this.description;
            provider.setDescription(name, description);
            this.description = description;
            // Fire event.
            Map<String, Object> params = new HashMap<String, Object>();
            params.put("type", "descriptionModified");
            params.put("originalValue", originalDescription);
            GroupEventDispatcher.dispatchEvent(this,
                    GroupEventDispatcher.EventType.group_modified, params);
        }
        catch (Exception e) {
            Log.error(e);
        }
    }

    public String toString() {
        return name;
    }

    /**
     * Returns all extended properties of the group. Groups
     * have an arbitrary number of extended properties.
     *
     * @return the extended properties.
     */
    public Map<String,String> getProperties() {
        synchronized (this) {
            if (properties == null) {
                properties = new ConcurrentHashMap<String, String>();
                loadProperties();
            }
        }
        // Return a wrapper that will intercept add and remove commands.
        return new PropertiesMap();
    }

    /**
     * Returns a Collection of the group administrators.
     *
     * @return a Collection of the group administrators.
     */
    public Collection<JID> getAdmins() {
        // Return a wrapper that will intercept add and remove commands.
        return new MemberCollection(administrators, true);
    }

    /**
     * Returns a Collection of the group members.
     *
     * @return a Collection of the group members.
     */
    public Collection<JID> getMembers() {
        // Return a wrapper that will intercept add and remove commands.
        return new MemberCollection(members, false);
    }

    /**
     * Returns true if the provided username belongs to a user that is part of the group.
     *
     * @param user the JID address of the user to check.
     * @return true if the specified user is a group user.
     */
    public boolean isUser(JID user) {
        return user != null && (members.contains(user) || administrators.contains(user));
    }

    /**
     * Returns true if the provided username belongs to a user of the group.
     *
     * @param username the username to check.
     * @return true if the provided username belongs to a user of the group.
     */
    public boolean isUser(String username) {
        if  (username != null) {
            assert(username.indexOf('@') > 0);
            return isUser(new JID(username));
        }
        else {
            return false;
        }
    }

    private void writeObject(java.io.ObjectOutputStream out)  throws IOException {
        out.defaultWriteObject();
    }

    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        groupManager = GroupManager.getInstance();
        provider = groupManager.getProvider();
    }

    public int getCachedSize() {
        // Approximate the size of the object in bytes by calculating the size
        // of each field.
        int size = 0;
        size += CacheSizes.sizeOfObject();              // overhead of object
        size += CacheSizes.sizeOfString(name);
        size += CacheSizes.sizeOfString(description);
        size += CacheSizes.sizeOfMap(properties);

        for (JID member: members) {
            size += CacheSizes.sizeOfString(member.toString());
        }
        for (JID admin: administrators) {
            size += CacheSizes.sizeOfString(admin.toString());
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
        if (object != null && object instanceof Group) {
            return name.equals(((Group)object).getName());
        }
        else {
            return false;
        }
    }
    /**
     * Collection implementation that notifies the GroupProvider of any
     * changes to the collection.
     */
    private class MemberCollection extends AbstractCollection {

        private Collection<JID> users;
        private boolean adminCollection;

        public MemberCollection(Collection<JID> users, boolean adminCollection) {
            this.users = users;
            this.adminCollection = adminCollection;
        }

        public Iterator<JID> iterator() {
            return new Iterator() {

                Iterator<JID> iter = users.iterator();
                Object current = null;

                public boolean hasNext() {
                    return iter.hasNext();
                }

                public Object next() {
                    current = iter.next();
                    return current;
                }

                public void remove() {
                    if (current == null) {
                        throw new IllegalStateException();
                    }
                    // Do nothing if the provider is read-only.
                    if (provider.isReadOnly()) {
                        return;
                    }
                    JID user = (JID)current;
                    // Remove the user from the collection in memory.
                    iter.remove();
                    // Remove the group user from the backend store.
                    provider.deleteMember(name, user);
                    // Fire event.
                    if (adminCollection) {
                        Map<String, String> params = new HashMap<String, String>();
                        params.put("admin", user.toString());
                        GroupEventDispatcher.dispatchEvent(Group.this,
                                GroupEventDispatcher.EventType.admin_removed, params);
                    }
                    else {
                        Map<String, String> params = new HashMap<String, String>();
                        params.put("member", user.toString());
                        GroupEventDispatcher.dispatchEvent(Group.this,
                                GroupEventDispatcher.EventType.member_removed, params);
                    }
                }
            };
        }

        public int size() {
            return users.size();
        }

        public boolean add(Object member) {
            // Do nothing if the provider is read-only.
            if (provider.isReadOnly()) {
                return false;
            }
            JID user = (JID) member;
            // Find out if the user was already a group user.
            boolean alreadyGroupUser;
            if (adminCollection) {
                alreadyGroupUser = members.contains(user);
            }
            else {
                alreadyGroupUser = administrators.contains(user);
            }
            if (users.add(user)) {
                if (alreadyGroupUser) {
                    // Update the group user privileges in the backend store.
                    provider.updateMember(name, user, adminCollection);
                }
                else {
                    // Add the group user to the backend store.
                    provider.addMember(name, user, adminCollection);
                }

                // Fire event.
                if (adminCollection) {
                    Map<String, String> params = new HashMap<String, String>();
                    params.put("admin", user.toString());
                    if (alreadyGroupUser) {
                        GroupEventDispatcher.dispatchEvent(Group.this,
                                    GroupEventDispatcher.EventType.member_removed, params);
                    }
                    GroupEventDispatcher.dispatchEvent(Group.this,
                                GroupEventDispatcher.EventType.admin_added, params);
                }
                else {
                    Map<String, String> params = new HashMap<String, String>();
                    params.put("member", user.toString());
                    if (alreadyGroupUser) {
                        GroupEventDispatcher.dispatchEvent(Group.this,
                                    GroupEventDispatcher.EventType.admin_removed, params);
                    }
                    GroupEventDispatcher.dispatchEvent(Group.this,
                                GroupEventDispatcher.EventType.member_added, params);
                }
                // If the user was a member that became an admin or vice versa then remove the
                // user from the other collection
                if (alreadyGroupUser) {
                    if (adminCollection) {
                        if (members.contains(user)) {
                            members.remove(user);
                        }
                    }
                    else {
                        if (administrators.contains(user)) {
                            administrators.remove(user);
                        }
                    }
                }
                return true;
            }
            return false;
        }
    }

    /**
     * Map implementation that updates the database when properties are modified.
     */
    private class PropertiesMap extends AbstractMap {

        public Object put(Object key, Object value) {
            if (key == null || value == null) {
                throw new NullPointerException();
            }
            Map<String, Object> eventParams = new HashMap<String, Object>();
            Object answer;
            String keyString = (String) key;
            synchronized (keyString.intern()) {
                if (properties.containsKey(keyString)) {
                    String originalValue = properties.get(keyString);
                    answer = properties.put(keyString, (String)value);
                    updateProperty(keyString, (String)value);
                    // Configure event.
                    eventParams.put("type", "propertyModified");
                    eventParams.put("propertyKey", key);
                    eventParams.put("originalValue", originalValue);
                }
                else {
                    answer = properties.put(keyString, (String)value);
                    insertProperty(keyString, (String)value);
                    // Configure event.
                    eventParams.put("type", "propertyAdded");
                    eventParams.put("propertyKey", key);
                }
            }
            // Fire event.
            GroupEventDispatcher.dispatchEvent(Group.this,
                    GroupEventDispatcher.EventType.group_modified, eventParams);
            return answer;
        }

        public Set<Entry> entrySet() {
            return new PropertiesEntrySet();
        }
    }

    /**
     * Set implementation that updates the database when properties are deleted.
     */
    private class PropertiesEntrySet extends AbstractSet {

        public int size() {
            return properties.entrySet().size();
        }

        public Iterator iterator() {
            return new Iterator() {

                Iterator iter = properties.entrySet().iterator();
                Map.Entry current = null;

                public boolean hasNext() {
                    return iter.hasNext();
                }

                public Object next() {
                    current = (Map.Entry)iter.next();
                    return current;
                }

                public void remove() {
                    if (current == null) {
                        throw new IllegalStateException();
                    }
                    String key = (String)current.getKey();
                    deleteProperty(key);
                    iter.remove();
                    // Fire event.
                    Map<String, Object> params = new HashMap<String, Object>();
                    params.put("type", "propertyDeleted");
                    params.put("propertyKey", key);
                    GroupEventDispatcher.dispatchEvent(Group.this,
                        GroupEventDispatcher.EventType.group_modified, params);
                }
            };
        }
    }

    private void loadProperties() {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_PROPERTIES);
            pstmt.setString(1, name);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                String key = rs.getString(1);
                String value = rs.getString(2);
                if (key != null) {
                    if (value == null) {
                        value = "";
                        Log.warn("There is a group property whose value is null of Group: " + name);
                    }
                    properties.put(key, value);
                }
                else {
                    Log.warn("There is a group property whose key is null of Group: " + name);
                }
            }
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
    }

    private void insertProperty(String propName, String propValue) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(INSERT_PROPERTY);
            pstmt.setString(1, name);
            pstmt.setString(2, propName);
            pstmt.setString(3, propValue);
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            Log.error(e);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    private void updateProperty(String propName, String propValue) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(UPDATE_PROPERTY);
            pstmt.setString(1, propValue);
            pstmt.setString(2, propName);
            pstmt.setString(3, name);
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            Log.error(e);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    private void deleteProperty(String propName) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(DELETE_PROPERTY);
            pstmt.setString(1, name);
            pstmt.setString(2, propName);
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            Log.error(e);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }
}