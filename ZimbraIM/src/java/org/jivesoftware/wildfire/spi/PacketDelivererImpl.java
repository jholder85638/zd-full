/*
 * 
 */
package org.jivesoftware.wildfire.spi;

import org.jivesoftware.wildfire.*;
import org.jivesoftware.wildfire.auth.UnauthorizedException;
import org.jivesoftware.wildfire.container.BasicModule;
import org.jivesoftware.wildfire.net.SocketPacketWriteHandler;
import org.xmpp.packet.Packet;

/**
 * In-memory implementation of the packet deliverer service
 *
 * @author Iain Shigeoka
 */
public class PacketDelivererImpl extends BasicModule implements PacketDeliverer {

    /**
     * The handler that does the actual delivery (could be a channel instead)
     */
    protected SocketPacketWriteHandler deliverHandler;

    private OfflineMessageStrategy messageStrategy;
    private SessionManager sessionManager;

    public PacketDelivererImpl() {
        super("Packet Delivery");
    }

    public void deliver(Packet packet) throws UnauthorizedException, PacketException {
        if (packet == null) {
            throw new PacketException("Packet was null");
        }
        if (deliverHandler == null) {
            throw new PacketException("Could not send packet - no route" + packet.toString());
        }
        // Let the SocketPacketWriteHandler process the packet. SocketPacketWriteHandler may send
        // it over the socket or store it when user is offline or drop it.
        deliverHandler.process(packet);
    }

    public void initialize(XMPPServer server) {
        super.initialize(server);
        messageStrategy = server.getOfflineMessageStrategy();
        sessionManager = server.getSessionManager();
    }

    public void start() throws IllegalStateException {
        super.start();
        deliverHandler =
                new SocketPacketWriteHandler(sessionManager,
                        XMPPServer.getInstance().getRoutingTable(), messageStrategy);
    }

    public void stop() {
        super.stop();
        deliverHandler = null;
    }
}
