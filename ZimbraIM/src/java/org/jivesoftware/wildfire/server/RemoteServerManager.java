/*
 * 
 */
package org.jivesoftware.wildfire.server;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.wildfire.Session;
import org.jivesoftware.wildfire.SessionManager;
import org.jivesoftware.wildfire.server.RemoteServerConfiguration.Permission;
import org.jivesoftware.util.IMConfig;
import org.jivesoftware.util.Log;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Manages the connection permissions for remote servers. When a remote server is allowed to
 * connect to this server then a special configuration for the remote server will be kept.
 * The configuration holds information such as the port to use when creating an outgoing connection.
 *
 * @author Gaston Dombiak
 */
public class RemoteServerManager {

    private static final String ADD_CONFIGURATION =
        "INSERT INTO jiveRemoteServerConf (domain,remotePort,permission) VALUES (?,?,?)";
    private static final String DELETE_CONFIGURATION =
        "DELETE FROM jiveRemoteServerConf WHERE domain=?";
    private static final String LOAD_CONFIGURATION =
        "SELECT remotePort,permission FROM jiveRemoteServerConf where domain=?";
    private static final String LOAD_CONFIGURATIONS =
        "SELECT domain,remotePort FROM jiveRemoteServerConf where permission=?";

    /**
     * Allows a remote server to connect to the local server with the specified configuration.
     *
     * @param configuration the configuration for the remote server.
     */
    public static void allowAccess(RemoteServerConfiguration configuration) {
        // Remove any previous configuration for this remote server
        deleteConfiguration(configuration.getDomain());
        // Update the database with the new granted permission and configuration
        configuration.setPermission(Permission.allowed);
        addConfiguration(configuration);
    }

    /**
     * Blocks a remote server from connecting to the local server. If the remote server was
     * connected when the permission was revoked then the connection of the entity will be closed.
     *
     * @param domain the domain of the remote server that is not allowed to connect.
     */
    public static void blockAccess(String domain) {
        // Remove any previous configuration for this remote server
        deleteConfiguration(domain);
        // Update the database with the new revoked permission
        RemoteServerConfiguration config = new RemoteServerConfiguration(domain);
        config.setPermission(Permission.blocked);
        addConfiguration(config);
        // Check if the remote server was connected and proceed to close the connection
        for (Session session : SessionManager.getInstance().getIncomingServerSessions(domain)) {
            session.getConnection().close();
        }
        Session session = SessionManager.getInstance().getOutgoingServerSession(domain);
        if (session != null) {
            session.getConnection().close();
        }
    }

    /**
     * Returns true if the remote server with the specified domain can connect to the
     * local server.
     *
     * @param domain the domain of the remote server.
     * @return true if the remote server with the specified domain can connect to the
     *         local server.
     */
    public static boolean canAccess(String domain) {
        // If s2s is disabled then it is not possible to send packets to remote servers or
        // receive packets from remote servers
        if (!IMConfig.XMPP_SERVER_SOCKET_ACTIVE.getBoolean()) {
            return false;
        }

        // By default there is no permission defined for the XMPP entity
        Permission permission = null;

        RemoteServerConfiguration config = getConfiguration(domain);
        if (config != null) {
            permission = config.getPermission();
        }

        if (PermissionPolicy.blacklist == getPermissionPolicy()) {
            // Anyone can access except those entities listed in the blacklist
            if (Permission.blocked == permission) {
                return false;
            }
            else {
                return true;
            }
        }
        else {
            // Access is limited to those present in the whitelist
            if (Permission.allowed == permission) {
                return true;
            }
            else {
                return false;
            }
        }
    }

    /**
     * Returns the list of registered remote servers that are allowed to connect to/from this
     * server when using a whitelist policy. However, when using a blacklist policy (i.e. anyone
     * may connect to the server) the returned list of configurations will be used for obtaining
     * the specific connection configuration for each remote server.
     *
     * @return the configuration of the registered external components.
     */
    public static Collection<RemoteServerConfiguration> getAllowedServers() {
        return getConfigurations(Permission.allowed);
    }

    /**
     * Returns the list of remote servers that are NOT allowed to connect to/from this
     * server.
     *
     * @return the configuration of the blocked external components.
     */
    public static Collection<RemoteServerConfiguration> getBlockedServers() {
        return getConfigurations(Permission.blocked);
    }

    /**
     * Returns the number of milliseconds to wait to connect to a remote server or read
     * data from a remote server. Default timeout value is 180 seconds. Configure the
     * <tt>XMPP_SERVER_READ_TIMEOUT</tt> global property to override the default value.
     *
     * @return the number of milliseconds to wait to connect to a remote server or read
     *         data from a remote server.
     */
    public static int getSocketTimeout() {
        int toRet = IMConfig.XMPP_SERVER_READ_TIMEOUT.getInt();
        return toRet;
    }

    /**
     * Removes any existing defined permission and configuration for the specified
     * remote server.
     *
     * @param domain the domain of the remote server.
     */
    public static void deleteConfiguration(String domain) {
        // Remove the permission for the entity from the database
        java.sql.Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(DELETE_CONFIGURATION);
            pstmt.setString(1, domain);
            pstmt.executeUpdate();
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
        }
    }

    /**
     * Adds a new permission for the specified remote server.
     */
    private static void addConfiguration(RemoteServerConfiguration configuration) {
        // Remove the permission for the entity from the database
        java.sql.Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(ADD_CONFIGURATION);
            pstmt.setString(1, configuration.getDomain());
            pstmt.setInt(2, configuration.getRemotePort());
            pstmt.setString(3, configuration.getPermission().toString());
            pstmt.executeUpdate();
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
        }
    }

    /**
     * Returns the configuration for a remote server or <tt>null</tt> if none was found.
     *
     * @param domain the domain of the remote server.
     * @return the configuration for a remote server or <tt>null</tt> if none was found.
     */
    public static RemoteServerConfiguration getConfiguration(String domain) {
        RemoteServerConfiguration configuration = null;
        java.sql.Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_CONFIGURATION);
            pstmt.setString(1, domain);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                configuration = new RemoteServerConfiguration(domain);
                configuration.setRemotePort(rs.getInt(1));
                configuration.setPermission(Permission.valueOf(rs.getString(2)));
            }
            rs.close();
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
        }
        return configuration;
    }

    private static Collection<RemoteServerConfiguration> getConfigurations(
            Permission permission) {
        Collection<RemoteServerConfiguration> answer =
                new ArrayList<RemoteServerConfiguration>();
        java.sql.Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_CONFIGURATIONS);
            pstmt.setString(1, permission.toString());
            ResultSet rs = pstmt.executeQuery();
            RemoteServerConfiguration configuration;
            while (rs.next()) {
                configuration = new RemoteServerConfiguration(rs.getString(1));
                configuration.setRemotePort(rs.getInt(2));
                configuration.setPermission(permission);
                answer.add(configuration);
            }
            rs.close();
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
        }
        return answer;
    }

    /**
     * Returns the remote port to connect for the specified remote server. If no port was
     * defined then use the default port (e.g. 5269).
     *
     * @param domain the domain of the remote server to get the remote port to connect to.
     * @return the remote port to connect for the specified remote server.
     */
    public static int getPortForServer(String domain) {
        int port = 0;
        RemoteServerConfiguration config = getConfiguration(domain);
        if (config != null)
            port = config.getRemotePort();
        if (port == 0)
            port = IMConfig.XMPP_SERVER_SOCKET_REMOTEPORT.getInt();
        return port;
    }

    /**
     * Returns the permission policy being used for new XMPP entities that are trying to
     * connect to the server. There are two types of policies: 1) blacklist: where any entity
     * is allowed to connect to the server except for those listed in the black list and
     * 2) whitelist: where only the entities listed in the white list are allowed to connect to
     * the server.
     *
     * @return the permission policy being used for new XMPP entities that are trying to
     *         connect to the server.
     */
    public static PermissionPolicy getPermissionPolicy() {
        try {
            String policy = IMConfig.XMPP_SERVER_PERMISSION.getString();
            return PermissionPolicy.valueOf(policy);
        }
        catch (Exception e) {
            Log.error(e);
            return PermissionPolicy.blacklist;
        }
    }

    public enum PermissionPolicy {
        /**
         * Any XMPP entity is allowed to connect to the server except for those listed in
         * the <b>not allowed list</b>.
         */
        blacklist,

        /**
         * Only the XMPP entities listed in the <b>allowed list</b> are able to connect to
         * the server.
         */
        whitelist;
    }
}
