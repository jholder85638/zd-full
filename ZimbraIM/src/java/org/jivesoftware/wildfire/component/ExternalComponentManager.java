/*
 * 
 */
package org.jivesoftware.wildfire.component;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.util.IMConfig;
import org.jivesoftware.util.Log;
import org.jivesoftware.wildfire.Session;
import org.jivesoftware.wildfire.SessionManager;
import org.jivesoftware.wildfire.XMPPServer;
import org.jivesoftware.wildfire.component.ExternalComponentConfiguration.Permission;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Manages the connection permissions for external components. When an external component is
 * allowed to connect to this server then a special configuration for the component will be kept.
 * The configuration holds information such as the shared secret that the component should use
 * when authenticating with the server.
 *
 * @author Gaston Dombiak
 */
public class ExternalComponentManager {

    private static final String ADD_CONFIGURATION =
        "INSERT INTO jiveExtComponentConf (subdomain,secret,permission) VALUES (?,?,?)";
    private static final String DELETE_CONFIGURATION =
        "DELETE FROM jiveExtComponentConf WHERE subdomain=?";
    private static final String LOAD_CONFIGURATION =
        "SELECT secret,permission FROM jiveExtComponentConf where subdomain=?";
    private static final String LOAD_CONFIGURATIONS =
        "SELECT subdomain,secret FROM jiveExtComponentConf where permission=?";

    /**
     * Allows an external component to connect to the local server with the specified configuration.
     *
     * @param configuration the configuration for the external component.
     */
    public static void allowAccess(ExternalComponentConfiguration configuration) {
        // Remove any previous configuration for this external component
        deleteConfiguration(configuration.getSubdomain());
        // Update the database with the new granted permission and configuration
        configuration.setPermission(Permission.allowed);
        addConfiguration(configuration);
    }

    /**
     * Blocks an external component from connecting to the local server. If the component was
     * connected when the permission was revoked then the connection of the entity will be closed.
     *
     * @param subdomain the subdomain of the external component that is not allowed to connect.
     */
    public static void blockAccess(String subdomain) {
        // Remove any previous configuration for this external component
        deleteConfiguration(subdomain);
        // Update the database with the new revoked permission
        ExternalComponentConfiguration config = new ExternalComponentConfiguration(subdomain);
        config.setPermission(Permission.blocked);
        addConfiguration(config);
        // Check if the component was connected and proceed to close the connection
        String domain = subdomain + "." + XMPPServer.getInstance().getServerInfo().getDefaultName();
        Session session = SessionManager.getInstance().getComponentSession(domain);
        if (session != null) {
            session.getConnection().close();
        }
    }

    /**
     * Returns true if the external component with the specified subdomain can connect to the
     * local server.
     *
     * @param subdomain the subdomain of the external component.
     * @return true if the external component with the specified subdomain can connect to the
     *         local server.
     */
    public static boolean canAccess(String subdomain) {
        // By default there is no permission defined for the XMPP entity
        Permission permission = null;

        ExternalComponentConfiguration config = getConfiguration(subdomain);
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
     * Returns the list of registered external components that are allowed to connect to this
     * server when using a whitelist policy. However, when using a blacklist policy (i.e. anyone
     * may connect to the server) the returned list of configurations will be used for obtaining
     * the shared secret specific for each component.
     *
     * @return the configuration of the registered external components.
     */
    public static Collection<ExternalComponentConfiguration> getAllowedComponents() {
        return getConfigurations(Permission.allowed);
    }

    /**
     * Returns the list of external components that are NOT allowed to connect to this
     * server.
     *
     * @return the configuration of the blocked external components.
     */
    public static Collection<ExternalComponentConfiguration> getBlockedComponents() {
        return getConfigurations(Permission.blocked);
    }

    /**
     * Removes any existing defined permission and configuration for the specified
     * external component.
     *
     * @param subdomain the subdomain of the external component.
     */
    public static void deleteConfiguration(String subdomain) {
        // Remove the permission for the entity from the database
        java.sql.Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(DELETE_CONFIGURATION);
            pstmt.setString(1, subdomain);
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
     * Adds a new permission for the specified external component.
     */
    private static void addConfiguration(ExternalComponentConfiguration configuration) {
        // Remove the permission for the entity from the database
        java.sql.Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(ADD_CONFIGURATION);
            pstmt.setString(1, configuration.getSubdomain());
            pstmt.setString(2, configuration.getSecret());
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
     * Returns the configuration for an external component.
     *
     * @param subdomain the subdomain of the external component.
     * @return the configuration for an external component.
     */
    public static ExternalComponentConfiguration getConfiguration(String subdomain) {
        ExternalComponentConfiguration configuration = null;
        java.sql.Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_CONFIGURATION);
            pstmt.setString(1, subdomain);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                configuration = new ExternalComponentConfiguration(subdomain);
                configuration.setSecret(rs.getString(1));
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

    private static Collection<ExternalComponentConfiguration> getConfigurations(
            Permission permission) {
        Collection<ExternalComponentConfiguration> answer =
                new ArrayList<ExternalComponentConfiguration>();
        java.sql.Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_CONFIGURATIONS);
            pstmt.setString(1, permission.toString());
            ResultSet rs = pstmt.executeQuery();
            ExternalComponentConfiguration configuration;
            while (rs.next()) {
                configuration = new ExternalComponentConfiguration(rs.getString(1));
                configuration.setSecret(rs.getString(2));
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
     * Returns the default secret key to use for those external components that don't have an
     * individual configuration.
     *
     * @return the default secret key to use for those external components that don't have an
     *         individual configuration.
     */
    public static String getDefaultSecret() {
        return IMConfig.XMPP_COMPONENT_DEFAULT_SECRET.getString();
    }

    /**
     * Returns the shared secret with the specified external component. If no shared secret was
     * defined then use the default shared secret.
     *
     * @param subdomain the subdomain of the external component to get his shared secret.
     *        (e.g. conference)
     * @return the shared secret with the specified external component or the default shared secret.
     */
    public static String getSecretForComponent(String subdomain) {
        // By default there is no shared secret defined for the XMPP entity
        String secret = null;

        ExternalComponentConfiguration config = getConfiguration(subdomain);
        if (config != null) {
            secret = config.getSecret();
        }

        secret = (secret == null ? getDefaultSecret() : secret);
        if (secret == null) {
            Log.error("Setup for external components is incomplete. Property " +
                    "xmpp.component.defaultSecret does not exist.");
        }
        return secret;
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
            return PermissionPolicy.valueOf(IMConfig.XMPP_COMPONENT_PERMISSION_POLICY.getString());
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
        whitelist
    }
}
