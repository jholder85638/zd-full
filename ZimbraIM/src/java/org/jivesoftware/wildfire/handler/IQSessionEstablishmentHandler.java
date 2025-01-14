/*
 * 
 */
package org.jivesoftware.wildfire.handler;

import org.jivesoftware.wildfire.IQHandlerInfo;
import org.jivesoftware.wildfire.auth.UnauthorizedException;
import org.xmpp.packet.IQ;

/**
 * Activate client sessions once resource binding has been done. Clients need to active their
 * sessions in order to engage in instant messaging and presence activities. The server may
 * deny sessions activations if the max number of sessions in the server has been reached or
 * if a user does not have permissions to create sessions.<p>
 *
 * Current implementation does not check any of the above conditions. However, future versions
 * may add support for those checkings.
 *
 * @author Gaston Dombiak
 */
public class IQSessionEstablishmentHandler extends IQHandler {

    private IQHandlerInfo info;

    public IQSessionEstablishmentHandler() {
        super("Session Establishment handler");
        info = new IQHandlerInfo("session", "urn:ietf:params:xml:ns:xmpp-session");
    }

    public IQ handleIQ(IQ packet) throws UnauthorizedException {
        // Just answer that the session has been activated
        IQ reply = IQ.createResultIQ(packet);
        reply.setChildElement(packet.getChildElement().createCopy());
        return reply;
    }

    public IQHandlerInfo getInfo() {
        return info;
    }
}
