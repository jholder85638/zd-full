/*
 * 
 */
package org.jivesoftware.wildfire.server;

import com.jcraft.jzlib.JZlib;
import com.jcraft.jzlib.ZInputStream;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.XMPPPacketReader;
import org.jivesoftware.util.IMConfig;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.wildfire.*;
import org.jivesoftware.wildfire.auth.UnauthorizedException;
import org.jivesoftware.wildfire.net.DNSUtil;
import org.jivesoftware.wildfire.net.MXParser;
import org.jivesoftware.wildfire.net.SocketConnection;
import org.jivesoftware.wildfire.net.StdSocketConnection;
import org.jivesoftware.wildfire.spi.BasicStreamIDFactory;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmpp.packet.*;

import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.regex.Pattern;

/**
 * Server-to-server communication is done using two TCP connections between the servers. One
 * connection is used for sending packets while the other connection is used for receiving packets.
 * The <tt>OutgoingServerSession</tt> represents the connection to a remote server that will only
 * be used for sending packets.<p>
 *
 * Currently only the Server Dialback method is being used for authenticating with the remote
 * server. Use {@link #authenticateDomain(String, String)} to create a new connection to a remote
 * server that will be used for sending packets to the remote server from the specified domain.
 * Only the authenticated domains with the remote server will be able to effectively send packets
 * to the remote server. The remote server will reject and close the connection if a
 * non-authenticated domain tries to send a packet through this connection.<p>
 *
 * Once the connection has been established with the remote server and at least a domain has been
 * authenticated then a new route will be added to the routing table for this connection. For
 * optimization reasons the same outgoing connection will be used even if the remote server has
 * several hostnames. However, different routes will be created in the routing table for each
 * hostname of the remote server.
 *
 * @author Gaston Dombiak
 */
public class OutgoingServerSession extends Session {

    /**
     * Regular expression to ensure that the hostname contains letters.
     */
    private static Pattern pattern = Pattern.compile("[a-zA-Z]");

    private Collection<String> authenticatedDomains = new ArrayList<String>();
    private Collection<String> hostnames = new ArrayList<String>();
    private OutgoingServerSocketReader socketReader;
    /**
     * Flag that indicates if the session was created usign server-dialback.
     */
    private boolean usingServerDialback = true;

    /**
     * Creates a new outgoing connection to the specified hostname if no one exists. The port of
     * the remote server could be configured by setting the <b>XMPP_SERVER_SOCKET_REMOTEPORT</b> 
     * property or otherwise the standard port 5269 will be used. Either a new connection was
     * created or already existed the specified hostname will be authenticated with the remote
     * server. Once authenticated the remote server will start accepting packets from the specified
     * domain.<p>
     *
     * The Server Dialback method is currently the only implemented method for server-to-server
     * authentication. This implies that the remote server will ask the Authoritative Server
     * to verify the domain to authenticate. Most probably this server will act as the
     * Authoritative Server. See {@link IncomingServerSession} for more information.
     *
     * @param domain the local domain to authenticate with the remote server.
     * @param hostname the hostname of the remote server.
     * @return True if the domain was authenticated by the remote server.
     */
    public static boolean authenticateDomain(String domain, String hostname) {
        Log.debug("OutgoingServerSession.authenticateDomain domain="+domain+" hostname="+hostname); 
        if (hostname == null || hostname.length() == 0 || hostname.trim().indexOf(' ') > -1) {
            // Do nothing if the target hostname is empty, null or contains whitespaces
            Log.debug("Could not authenticate domain: "+domain+" invalid or blank hostname::"+hostname);
            return false;
        }
        try {
            // Check if the remote hostname is in the blacklist
            if (!RemoteServerManager.canAccess(hostname)) {
                Log.debug("Failed to authenticate domain "+hostname+" because hostname is in the server blacklist");
                return false;
            }

            // Check if a session, that is using server dialback, already exists to the desired
            // hostname (i.e. remote server). If no one exists then create a new session. The same
            // session will be used for the same hostname for all the domains to authenticate
            SessionManager sessionManager = SessionManager.getInstance();
            OutgoingServerSession session = sessionManager.getOutgoingServerSession(hostname);
            if (session == null) {
                // Try locating if the remote server has previously authenticated with this server
                for (IncomingServerSession incomingSession : sessionManager
                        .getIncomingServerSessions(hostname)) {
                    for (String otherHostname : incomingSession.getValidatedDomains()) {
                        session = sessionManager.getOutgoingServerSession(otherHostname);
                        if (session != null) {
                            if (session.usingServerDialback) {
                                // A session to the same remote server but with different hostname
                                // was found. Use this session and add the new hostname to the
                                // session
                                session.addHostname(hostname);
                                break;
                            }
                            else {
                                session = null;
                            }
                        }
                    }
                }
            }
            if (session == null) {
                int port = RemoteServerManager.getPortForServer(hostname);
                // No session was found to the remote server so make sure that only one is created
                synchronized (hostname.intern()) {
                    session = sessionManager.getOutgoingServerSession(hostname);
                    if (session == null) {
                        session = createOutgoingSession(domain, hostname, port);
                        if (session != null) {
                            // Add the new hostname to the list of names that the server may have
                            session.addHostname(hostname);
                            // Add the validated domain as an authenticated domain
                            session.addAuthenticatedDomain(domain);
                            // Notify the SessionManager that a new session has been created
                            sessionManager.outgoingServerSessionCreated(session);
                            return true;
                        }
                        else {
                            // Ensure that the hostname is not an IP address (i.e. contains chars)
                            if (!pattern.matcher(hostname).find()) {
                                Log.debug("Failed to authenticate domain "+hostname+" because hostname failed dotted-decimal check (hostname contains no nondigit characters)");
                                return false;
                            }
                            // Check if hostname is a subdomain of an existing outgoing session
                            for (String otherHost : sessionManager.getOutgoingServers()) {
                                if (hostname.contains(otherHost)) {
                                    session = sessionManager.getOutgoingServerSession(otherHost);
                                    // Add the new hostname to the found session
                                    session.addHostname(hostname);
                                    return true;
                                }
                            }
                            // Try to establish a connection to candidate hostnames. Iterate on the
                            // substring after the . and try to establish a connection. If a
                            // connection is established then the same session will be used for
                            // sending packets to the "candidate hostname" as well as for the
                            // requested hostname (i.e. the subdomain of the candidate hostname)
                            // This trick is useful when remote servers haven't registered in their
                            // DNSs an entry for their subdomains
                            int index = hostname.indexOf('.');
                            while (index > -1 && index < hostname.length()) {
                                String newHostname = hostname.substring(index + 1);
                                Collection<String> localNames = XMPPServer.getInstance().getLocalDomains();
                                if ("com".equals(newHostname) || "net".equals(newHostname) ||
                                        "org".equals(newHostname) ||
                                        "gov".equals(newHostname) ||
                                        "edu".equals(newHostname) ||
                                        localNames.contains(newHostname)) {
                                    Log.debug("Unable to find subdomain to try.  Giving up: "+newHostname);
                                    return false;
                                }
                                session = createOutgoingSession(domain, newHostname, port);
                                if (session != null) {
                                    // Add the new hostname to the list of names that the server may have
                                    session.addHostname(hostname);
                                    // Add the validated domain as an authenticated domain
                                    session.addAuthenticatedDomain(domain);
                                    // Notify the SessionManager that a new session has been created
                                    sessionManager.outgoingServerSessionCreated(session);
                                    // Add the new hostname to the found session
                                    session.addHostname(newHostname);
                                    return true;
                                }
                                else {
                                    index = hostname.indexOf('.', index + 1);
                                }
                            }
                            Log.debug("exhausted all likely subdomains.  Giving up");
                            return false;
                        }
                    }
                }
            }
            // A session already exists. The session was established using server dialback so
            // it is possible to do piggybacking to authenticate more domains
            if (session.getAuthenticatedDomains().contains(domain)) {
                // Do nothing since the domain has already been authenticated
                return true;
            }
            Log.debug("Domain "+domain+" not in authenticated list for session "+session);
            // A session already exists so authenticate the domain using that session
            boolean toRet = session.authenticateSubdomain(domain, hostname);
            if (!toRet) {
                Log.debug("Session.authenticateSubdomain failed: domain="+domain+" hostname="+hostname); 
            }
            return toRet;
        }
        catch (Exception e) {
            Log.info("Error authenticating domain with remote server: " + hostname, e);
        }
        return false;
    }

    /**
     * Establishes a new outgoing session to a remote server. If the remote server supports TLS
     * and SASL then the new outgoing connection will be secured with TLS and authenticated
     * using SASL. However, if TLS or SASL is not supported by the remote server or if an
     * error occured while securing or authenticating the connection using SASL then server
     * dialback method will be used.
     *
     * @param domain the local domain to authenticate with the remote server.
     * @param hostname the hostname of the remote server.
     * @param port default port to use to establish the connection.
     * @return new outgoing session to a remote server.
     */
    private static OutgoingServerSession createOutgoingSession(String domain, String hostname,
            int port) {
        boolean useTLS = IMConfig.XMPP_SERVER_TLS_ENABLED.getBoolean();
        RemoteServerConfiguration configuration = RemoteServerManager.getConfiguration(hostname);
        if (configuration != null) {
            // TODO Use the specific TLS configuration for this remote server
            //useTLS = configuration.isTLSEnabled();
        }

        if (useTLS) {
            // Connect to remote server using TLS + SASL
            StdSocketConnection connection = null;
            String realHostname = null;
            int realPort = port;
            Socket socket = new Socket();
            try {
                // Get the real hostname to connect to using DNS lookup of the specified hostname
                DNSUtil.HostAddress address = DNSUtil.resolveXMPPServerDomain(hostname, port);
                realHostname = address.getHost();
                realPort = address.getPort();
                Log.debug("OS - Trying to connect to " + hostname + ":" + port +
                        "(DNS lookup: " + realHostname + ":" + realPort + ")");
                // Establish a TCP connection to the Receiving Server
                socket.connect(new InetSocketAddress(realHostname, realPort),
                        RemoteServerManager.getSocketTimeout());
                Log.debug("OS - Plain connection to " + hostname + ":" + port + " successful");
            }
            catch (Exception e) {
                if (Log.isDebugEnabled()) {
                    Log.debug("Error trying to connect to remote server: " + hostname +
                              "(DNS lookup: " + realHostname + ":" + realPort + ")", e);
                } else if (Log.isInfoEnabled()) {
                    Log.info("Error trying to connect to remote server: " + hostname +
                             "(DNS lookup: " + realHostname + ":" + realPort + ")");
                }
                return null;
            }

            try {
                connection =
                        new StdSocketConnection(XMPPServer.getInstance().getPacketDeliverer(), socket,
                                false);

                // Send the stream header
                StringBuilder openingStream = new StringBuilder();
                openingStream.append("<stream:stream");
                openingStream.append(" xmlns:stream=\"http://etherx.jabber.org/streams\"");
                openingStream.append(" xmlns=\"jabber:server\"");
                openingStream.append(" to=\"").append(hostname).append("\"");
                openingStream.append(" version=\"1.0\">");
                connection.deliverRawText(openingStream.toString());

                // Set a read timeout (of 5 seconds) so we don't keep waiting forever
                int soTimeout = socket.getSoTimeout();
                socket.setSoTimeout(RemoteServerManager.getSocketTimeout());

                XMPPPacketReader reader = new XMPPPacketReader();
                reader.getXPPParser().setInput(new InputStreamReader(socket.getInputStream(),
                        CHARSET));
                // Get the answer from the Receiving Server
                XmlPullParser xpp = reader.getXPPParser();
                for (int eventType = xpp.getEventType(); eventType != XmlPullParser.START_TAG;) {
                    eventType = xpp.next();
                }

                String serverVersion = xpp.getAttributeValue("", "version");
                
                // Check if the remote server is XMPP 1.0 compliant
                if (serverVersion != null && decodeVersion(serverVersion)[0] >= 1) {
                    // Restore default timeout
                    socket.setSoTimeout(soTimeout);
                    // Get the stream features
                    Element features = reader.parseDocument().getRootElement();
                    // Check if TLS is enabled
                    if (features != null && features.element("starttls") != null) {
                        // Secure the connection with TLS and authenticate using SASL
                        OutgoingServerSession answer;
                        answer = secureAndAuthenticate(hostname, connection, reader, openingStream,
                                domain);
                        if (answer != null) {
                            // Everything went fine so return the secured and
                            // authenticated connection
                            return answer;
                        }
                    }
                    else {
                        Log.debug("OS - Error, <starttls> was not received ");
                    }
                } else {
                    Log.debug("OS - no server version, or unable to decode server version", xpp == null ? "" : xpp.toString());
                }
                // Something went wrong so close the connection and try server dialback over
                // a plain connection
                if (connection != null) {
                    Log.debug("OS - unknown error, giving up on TLS");
                    connection.close();
                }
            }
            catch (SSLHandshakeException e) {
                Log.info("Handshake error while creating secured outgoing session to remote " +
                        "server: " + hostname + "(DNS lookup: " + realHostname + ":" + realPort +
                        ")", e);
                // Close the connection
                if (connection != null) {
                    connection.close();
                }
            }
            catch (XmlPullParserException e) {
                Log.info("Error creating secured outgoing session to remote server: " + hostname +
                        "(DNS lookup: " + realHostname + ":" + realPort + ")", e);
                // Close the connection
                if (connection != null) {
                    connection.close();
                }
            }
            catch (Exception e) {
                Log.info("Error creating secured outgoing session to remote server: " + hostname +
                        "(DNS lookup: " + realHostname + ":" + realPort + ")", e);
                // Close the connection
                if (connection != null) {
                    connection.close();
                }
            }
        }
        if (ServerDialback.isEnabled()) {
            Log.debug("OS - Going to try connecting using server dialback with: " + hostname);
            // Use server dialback over a plain connection
            return new ServerDialback().createOutgoingSession(domain, hostname, port);
        }
        return null;
    }

    private static OutgoingServerSession secureAndAuthenticate(String hostname,
            StdSocketConnection connection, XMPPPacketReader reader, StringBuilder openingStream,
            String domain) throws Exception {
        Element features;
        Log.debug("OS - Indicating we want TLS to " + hostname);
        connection.deliverRawText("<starttls xmlns='urn:ietf:params:xml:ns:xmpp-tls'/>");

        MXParser xpp = reader.getXPPParser();
        // Wait for the <proceed> response
        Element proceed = reader.parseDocument().getRootElement();
        if (proceed != null && proceed.getName().equals("proceed")) {
            Log.debug("OS - Negotiating TLS with " + hostname);
            connection.startTLS(true, hostname);
            Log.debug("OS - TLS negotiation with " + hostname + " was successful");

            // TLS negotiation was successful so initiate a new stream
            connection.deliverRawText(openingStream.toString());

            // Reset the parser to use the new secured reader
            xpp.setInput(new InputStreamReader(connection.getTLSStreamHandler().getInputStream(),
                    CHARSET));
            // Skip new stream element
            for (int eventType = xpp.getEventType(); eventType != XmlPullParser.START_TAG;) {
                eventType = xpp.next();
            }
            // Get new stream features
            features = reader.parseDocument().getRootElement();
            if (features != null && features.element("mechanisms") != null) {
                // Check if we can use stream compression
                String policyName = IMConfig.XMPP_SERVER_COMPRESSION_POLICY.getString();
                Connection.CompressionPolicy compressionPolicy =
                        Connection.CompressionPolicy.valueOf(policyName);
                if (Connection.CompressionPolicy.optional == compressionPolicy) {
                    // Verify if the remote server supports stream compression
                    Element compression = features.element("compression");
                    if (compression != null) {
                        boolean zlibSupported = false;
                        Iterator it = compression.elementIterator("method");
                        while (it.hasNext()) {
                            Element method = (Element) it.next();
                            if ("zlib".equals(method.getTextTrim())) {
                                zlibSupported = true;
                            }
                        }
                        if (zlibSupported) {
                            // Request Stream Compression
                            connection.deliverRawText("<compress xmlns='http://jabber.org/protocol/compress'><method>zlib</method></compress>");
                            // Check if we are good to start compression
                            Element answer = reader.parseDocument().getRootElement();
                            if ("compressed".equals(answer.getName())) {
                                // Server confirmed that we can use zlib compression
                                connection.startCompression();
                                Log.debug("OS - Stream compression was successful with " + hostname);
                                // Stream compression was successful so initiate a new stream
                                connection.deliverRawText(openingStream.toString());
                                // Reset the parser to use stream compression over TLS
                                ZInputStream in = new ZInputStream(
                                        connection.getTLSStreamHandler().getInputStream());
                                in.setFlushMode(JZlib.Z_PARTIAL_FLUSH);
                                xpp.setInput(new InputStreamReader(in, CHARSET));
                                // Skip the opening stream sent by the server
                                for (int eventType = xpp.getEventType(); eventType != XmlPullParser.START_TAG;)
                                {
                                    eventType = xpp.next();
                                }
                                // Get new stream features
                                features = reader.parseDocument().getRootElement();
                                if (features == null || features.element("mechanisms") == null) {
                                    Log.debug("OS - Error, EXTERNAL SASL was not offered by " + hostname);
                                    return null;
                                }
                            }
                            else {
                                Log.debug("OS - Stream compression was rejected by " + hostname);
                            }
                        }
                        else {
                            Log.debug(
                                    "OS - Stream compression found but zlib method is not supported by" +
                                            hostname);
                        }
                    }
                    else {
                        Log.debug("OS - Stream compression not supoprted by " + hostname);
                    }
                }

                Iterator it = features.element("mechanisms").elementIterator();
                while (it.hasNext()) {
                    Element mechanism = (Element) it.next();
                    if ("EXTERNAL".equals(mechanism.getTextTrim())) {
                        Log.debug("OS - Starting EXTERNAL SASL with " + hostname);
                        if (doExternalAuthentication(domain, connection, reader)) {
                            Log.debug("OS - EXTERNAL SASL with " + hostname + " was successful");
                            // SASL was successful so initiate a new stream
                            connection.deliverRawText(openingStream.toString());

                            // Reset the parser
                            xpp.resetInput();
                            // Skip the opening stream sent by the server
                            for (int eventType = xpp.getEventType();
                                 eventType != XmlPullParser.START_TAG;) {
                                eventType = xpp.next();
                            }

                            // SASL authentication was successful so create new
                            // OutgoingServerSession
                            String id = xpp.getAttributeValue("", "id");
                            StreamID streamID = new BasicStreamIDFactory().createStreamID(id);
                            OutgoingServerSession session = new OutgoingServerSession(domain,
                                    connection, new OutgoingServerSocketReader(reader), streamID);
                            connection.init(session);
                            // Set the hostname as the address of the session
                            session.setAddress(new JID(null, hostname, null));
                            // Set that the session was created using TLS+SASL (no server dialback)
                            session.usingServerDialback = false;
                            return session;
                        }
                        else {
                            Log.debug("OS - Error, EXTERNAL SASL authentication with " + hostname +
                                    " failed");
                            return null;
                        }
                    }
                }
                Log.debug("OS - Error, EXTERNAL SASL was not offered by " + hostname);
            }
            else {
                Log.debug("OS - Error, no SASL mechanisms were offered by " + hostname);
            }
        }
        else {
            Log.debug("OS - Error, <proceed> was not received");
        }
        return null;
    }

    private static boolean doExternalAuthentication(String domain, SocketConnection connection,
            XMPPPacketReader reader) throws DocumentException, IOException, XmlPullParserException {

        StringBuilder sb = new StringBuilder();
        sb.append("<auth xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\" mechanism=\"EXTERNAL\">");
        sb.append(StringUtils.encodeBase64(domain));
        sb.append("</auth>");
        connection.deliverRawText(sb.toString());

        Element response = reader.parseDocument().getRootElement();
        if (response != null && "success".equals(response.getName())) {
            return true;
        }
        return false;
    }

    OutgoingServerSession(String serverName, Connection connection,
            OutgoingServerSocketReader socketReader, StreamID streamID) {
        super(serverName, connection, streamID);
        this.socketReader = socketReader;
        socketReader.setSession(this);
    }

    public void process(Packet packet) throws UnauthorizedException, PacketException {
        try {
            String senderDomain = packet.getFrom().getDomain();
            if (!getAuthenticatedDomains().contains(senderDomain)) {
                synchronized (senderDomain.intern()) {
                    if (!getAuthenticatedDomains().contains(senderDomain) &&
                            !authenticateSubdomain(senderDomain, packet.getTo().getDomain())) {
                        // Return error since sender domain was not validated by remote server
                        returnErrorToSender(packet);
                        return;
                    }
                }
            }

            if (conn != null && !conn.isClosed()) {
                conn.deliver(packet);
            }
        }
        catch (Exception e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
    }

    /**
     * Authenticates a subdomain of this server with the specified remote server over an exsiting
     * outgoing connection. If the existing session was using server dialback then a new db:result
     * is going to be sent to the remote server. But if the existing session was TLS+SASL based
     * then just assume that the subdomain was authenticated by the remote server.
     *
     * @param domain the local subdomain to authenticate with the remote server.
     * @param hostname the hostname of the remote server.
     * @return True if the subdomain was authenticated by the remote server.
     */
    private boolean authenticateSubdomain(String domain, String hostname) {
        if (!usingServerDialback) {
            // Using SASL so just assume that the domain was validated
            // (note: this may not be correct)
            addAuthenticatedDomain(domain);
            return true;
        }
        ServerDialback method = new ServerDialback(getConnection(), domain);
        if (method.authenticateDomain(socketReader, domain, hostname, getStreamID().getID())) {
            // Add the validated domain as an authenticated domain
            addAuthenticatedDomain(domain);
            return true;
        }
        return false;
    }

    private void returnErrorToSender(Packet packet) {
        RoutingTable routingTable = XMPPServer.getInstance().getRoutingTable();
        try {
            if (packet instanceof IQ) {
                IQ reply = new IQ();
                reply.setID(packet.getID());
                reply.setTo(packet.getFrom());
                reply.setFrom(packet.getTo());
                reply.setChildElement(((IQ) packet).getChildElement().createCopy());
                reply.setError(PacketError.Condition.remote_server_not_found);
                ChannelHandler route = routingTable.getRoute(reply.getTo());
                if (route != null) {
                    route.process(reply);
                }
            }
            else if (packet instanceof Presence) {
                Presence reply = new Presence();
                reply.setID(packet.getID());
                reply.setTo(packet.getFrom());
                reply.setFrom(packet.getTo());
                reply.setError(PacketError.Condition.remote_server_not_found);
                ChannelHandler route = routingTable.getRoute(reply.getTo());
                if (route != null) {
                    route.process(reply);
                }
            }
            else if (packet instanceof Message) {
                Message reply = new Message();
                reply.setID(packet.getID());
                reply.setTo(packet.getFrom());
                reply.setFrom(packet.getTo());
                reply.setType(((Message)packet).getType());
                reply.setThread(((Message)packet).getThread());
                reply.setError(PacketError.Condition.remote_server_not_found);
                ChannelHandler route = routingTable.getRoute(reply.getTo());
                if (route != null) {
                    route.process(reply);
                }
            }
        }
        catch (UnauthorizedException e) {
            // Do nothing
        }
        catch (Exception e) {
            Log.warn("Error returning error to sender. Original packet: " + packet, e);
        }
    }

    /**
     * Returns a collection with all the domains, subdomains and virtual hosts that where
     * authenticated. The remote server will accept packets sent from any of these domains,
     * subdomains and virtual hosts.
     *
     * @return domains, subdomains and virtual hosts that where validated.
     */
    public Collection<String> getAuthenticatedDomains() {
        return Collections.unmodifiableCollection(authenticatedDomains);
    }

    /**
     * Adds a new authenticated domain, subdomain or virtual host to the list of
     * authenticated domains for the remote server. The remote server will accept packets
     * sent from this new authenticated domain.
     *
     * @param domain the new authenticated domain, subdomain or virtual host to add.
     */
    public void addAuthenticatedDomain(String domain) {
        authenticatedDomains.add(domain);
    }

    /**
     * Removes an authenticated domain from the list of authenticated domains. The remote
     * server will no longer be able to accept packets sent from the removed domain, subdomain or
     * virtual host.
     *
     * @param domain the domain, subdomain or virtual host to remove from the list of
     *               authenticated domains.
     */
    public void removeAuthenticatedDomain(String domain) {
        authenticatedDomains.remove(domain);
    }

    /**
     * Returns the list of hostnames related to the remote server. This tracking is useful for
     * reusing the same session for the same remote server even if the server has many names.
     *
     * @return the list of hostnames related to the remote server.
     */
    public Collection<String> getHostnames() {
        return Collections.unmodifiableCollection(hostnames);
    }

    /**
     * Adds a new hostname to the list of known hostnames of the remote server. This tracking is
     * useful for reusing the same session for the same remote server even if the server has
     * many names.
     *
     * @param hostname the new known name of the remote server
     */
    private void addHostname(String hostname) {
        if (hostnames.add(hostname)) {
            // Register the outgoing session in the SessionManager. If the session
            // was already registered nothing happens
            sessionManager.registerOutgoingServerSession(hostname, this);
            // Add a new route for this new session
            XMPPServer.getInstance().getRoutingTable().addRoute(new JID(hostname), this);
        }
    }

    public String getAvailableStreamFeatures() {
        // Nothing special to add
        return null;
    }
}
