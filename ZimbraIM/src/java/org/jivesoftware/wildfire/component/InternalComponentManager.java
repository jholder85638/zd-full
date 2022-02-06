/*
 * 
 */
package org.jivesoftware.wildfire.component;

import org.dom4j.Element;
import org.jivesoftware.openfire.IQResultListener;
import org.jivesoftware.util.IMConfig;
import org.jivesoftware.util.Log;
import org.jivesoftware.wildfire.PacketException;
import org.jivesoftware.wildfire.PacketRouter;
import org.jivesoftware.wildfire.RoutableChannelHandler;
import org.jivesoftware.wildfire.XMPPServer;
import org.xmpp.component.Component;
import org.xmpp.component.ComponentException;
import org.xmpp.component.ComponentManager;
import org.xmpp.component.ComponentManagerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages the registration and delegation of Components. The ComponentManager
 * is responsible for managing registration and delegation of {@link Component Components},
 * as well as offering a facade around basic server functionallity such as sending and
 * receiving of packets.<p>
 *
 * This component manager will be an internal service whose JID will be component.[domain]. So the
 * component manager will be able to send packets to other internal or external components and also
 * receive packets from other components or even from trusted clients (e.g. ad-hoc commands).
 *
 * @author Derek DeMoro
 */
public class InternalComponentManager implements ComponentManager, RoutableChannelHandler {

    private Map<String, Component> components = new ConcurrentHashMap<String, Component>();
    private Map<String, IQ> componentInfo = new ConcurrentHashMap<String, IQ>();
    private Map<JID, JID> presenceMap = new ConcurrentHashMap<JID, JID>();
    /**
     * Holds the list of listeners that will be notified of component events.
     */
    private List<ComponentEventListener> listeners =
            new CopyOnWriteArrayList<ComponentEventListener>();

    private static InternalComponentManager instance = new InternalComponentManager();
    /**
     * XMPP address of this internal service. The address is of the form: component.[domain]
     */
    private JID serviceAddress;
    
    /**
     * Holds the domain of the server. We are using an iv since we use this value many times
     * in many methods.
     */
    private String serverDomain;

    public static InternalComponentManager getInstance() {
        return instance;
    }

    public void start() {
        // Set this ComponentManager as the current component manager
        ComponentManagerFactory.setComponentManager(instance);

        XMPPServer server = XMPPServer.getInstance();
        serverDomain = server.getServerInfo().getDefaultName();
        // Set the address of this internal service. component.[domain]
        serviceAddress = new JID(null, "component." + serverDomain, null);
        if (!server.isSetupMode()) {
            // Add a route to this service
            server.getRoutingTable().addRoute(getAddress(), this);
        }
    }

    public void addComponent(String subdomain, Component component) throws ComponentException {
        // Check that the requested subdoman is not taken by another component
        Component existingComponent = components.get(subdomain);
        if (existingComponent != null && existingComponent != component) {
            throw new ComponentException(
                    "Domain already taken by another component: " + existingComponent);
        }
        // Register that the domain is now taken by the component
        components.put(subdomain, component);

        JID componentJID = new JID(subdomain + "." + serverDomain);

        // Add the route to the new service provided by the component
        XMPPServer.getInstance().getRoutingTable().addRoute(componentJID,
                new RoutableComponent(componentJID, component));

        // Initialize the new component
        try {
            component.initialize(componentJID, this);
            component.start();
        }
        catch (ComponentException e) {
            // Remove the route
            XMPPServer.getInstance().getRoutingTable().removeRoute(componentJID);
            // Rethrow the exception
            throw e;
        }

        // Notify listeners that a new component has been registered
        for (ComponentEventListener listener : listeners) {
            listener.componentRegistered(component, componentJID);
        }

        // Check for potential interested users.
        checkPresences();
        // Send a disco#info request to the new component. If the component provides information
        // then it will be added to the list of discoverable server items.
        checkDiscoSupport(component, componentJID);
    }

    public void removeComponent(String subdomain) {
        Component component = components.remove(subdomain);
        // Remove any info stored with the component being removed
        componentInfo.remove(subdomain);

        JID componentJID = new JID(subdomain + "." + serverDomain);

        // Remove the route for the service provided by the component
        if (XMPPServer.getInstance().getRoutingTable() != null) {
            XMPPServer.getInstance().getRoutingTable().removeRoute(componentJID);
        }

        // Remove the disco item from the server for the component that is being removed
        if (XMPPServer.getInstance().getIQDiscoItemsHandler() != null) {
            XMPPServer.getInstance().getIQDiscoItemsHandler().removeComponentItem(componentJID.toBareJID());
        }

        // Ask the component to shutdown
        if (component != null) {
            component.shutdown();
        }

        // Notify listeners that a new component has been registered
        for (ComponentEventListener listener : listeners) {
            listener.componentUnregistered(component, componentJID);
        }
    }

    public void sendPacket(Component component, Packet packet) {
        PacketRouter router = XMPPServer.getInstance().getPacketRouter();
        if (router != null) {
            router.route(packet);
        }
    }

    @Override
    public IQ query(Component component, IQ iq, int i) throws ComponentException {
        return null;
    }

    @Override
    public void query(Component component, IQ iq, IQResultListener iqResultListener) throws ComponentException {

    }

    /**
     * Adds a new listener that will be notified of component events. Events being
     * notified are: 1) when a component is added to the component manager, 2) when
     * a component is deleted and 3) when disco#info is received from a component.
     *
     * @param listener the new listener to notify of component events.
     */
    public void addListener(ComponentEventListener listener) {
        listeners.add(listener);
        // Notify the new listener about existing components
        for (Map.Entry<String, Component> entry : components.entrySet()) {
            String subdomain = entry.getKey();
            Component component = entry.getValue();
            JID componentJID = new JID(subdomain + "." + serverDomain);
            listener.componentRegistered(component, componentJID);
            // Check if there is disco#info stored for the component
            IQ disco = componentInfo.get(subdomain);
            if (disco != null) {
                listener.componentInfoReceived(component, disco);
            }
        }
    }

    /**
     * Removes the specified listener from the listeners being notified of component
     * events.
     *
     * @param listener the listener to remove.
     */
    public void removeListener(ComponentEventListener listener) {
        listeners.remove(listener);
    }

    public String getProperty(String name) {
        return IMConfig.getStrProperty(name);
    }

    public void setProperty(String name, String value) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public String getServerName() {
        return serverDomain;
    }

    public String getHomeDirectory() {
        return IMConfig.getHomeDirectory();
    }

    public boolean isExternalMode() {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public org.xmpp.component.Log getLog() {
        return new  org.xmpp.component.Log() {
            public void error(String msg) {
                Log.error(msg);
            }

            public void error(String msg, Throwable throwable) {
                Log.error(msg, throwable);
            }

            public void error(Throwable throwable) {
                Log.error(throwable);
            }

            public void warn(String msg) {
                Log.warn(msg);
            }

            public void warn(String msg, Throwable throwable) {
                Log.warn(msg, throwable);
            }

            public void warn(Throwable throwable) {
                Log.warn(throwable);
            }

            public void info(String msg) {
                Log.info(msg);
            }

            public void info(String msg, Throwable throwable) {
                Log.info(msg, throwable);
            }

            public void info(Throwable throwable) {
                Log.info(throwable);
            }

            public void debug(String msg) {
                Log.debug(msg);
            }

            public void debug(String msg, Throwable throwable) {
                Log.debug(msg, throwable);
            }

            public void debug(Throwable throwable) {
                Log.debug(throwable);
            }
        };
    }
    
    /**
     * @param jid
     * @return TRUE if this JID matches a component in the local cloud
     */
    public boolean isCloudComponent(JID jid) {
        return getComponent(jid) != null;
    }

    /**
     * Returns the list of components that are currently installed in the server.
     * This includes internal and external components.
     *
     * @return the list of installed components.
     */
    public Collection<Component> getComponents() {
        return Collections.unmodifiableCollection(components.values());
    }

    /**
     * Retrieves the <code>Component</code> which is mapped
     * to the specified JID.
     *
     * @param componentJID the jid mapped to the component.
     * @return the component with the specified id.
     */
    public Component getComponent(JID componentJID) {
        Component component = components.get(componentJID.getDomain());
        if (component != null) {
            return component;
        }
        else {
            // Search again for those JIDs whose domain include the server name but this
            // time remove the server name from the JID's domain
            String serverName = componentJID.getDomain();
            int index = serverName.lastIndexOf("." + serverDomain);
            if (index > -1) {
                return components.get(serverName.substring(0, index));
            }
        }
        return null;
    }

    /**
     * Retrieves the <code>Component</code> which is mapped
     * to the specified JID.
     *
     * @param jid the jid mapped to the component.
     * @return the component with the specified id.
     */
    public Component getComponent(String jid) {
        return getComponent(new JID(jid));
    }

    /**
     * Registers Probeers who have not yet been serviced.
     *
     * @param prober the jid probing.
     * @param probee the presence being probed.
     */
    public void addPresenceRequest(JID prober, JID probee) {
        presenceMap.put(prober, probee);
    }

    private void checkPresences() {
        for (JID prober : presenceMap.keySet()) {
            JID probee = presenceMap.get(prober);

            Component component = getComponent(probee);
            if (component != null) {
                Presence presence = new Presence();
                presence.setFrom(prober);
                presence.setTo(probee);
                component.processPacket(presence);

                // No reason to hold onto prober reference.
                presenceMap.remove(prober);
            }
        }
    }

    /**
     *  Send a disco#info request to the new component. If the component provides information
     *  then it will be added to the list of discoverable server items.
     *
     * @param component the new component that was added to this manager.
     * @param componentJID the XMPP address of the new component.
     */
    private void checkDiscoSupport(Component component, JID componentJID) {
        // Build a disco#info request that will be sent to the component
        IQ iq = new IQ(IQ.Type.get);
        iq.setFrom(getAddress());
        iq.setTo(componentJID);
        iq.setChildElement("query", "http://jabber.org/protocol/disco#info");
        // Send the disco#info request to the component. The reply (if any) will be processed in
        // #process(Packet)
        sendPacket(component, iq);
    }

    public JID getAddress() {
        return serviceAddress;
    }

    /**
     * Processes packets that were sent to this service. Currently only packets that were sent from
     * registered components are being processed. In the future, we may also process packet of
     * trusted clients. Trusted clients may be able to execute ad-hoc commands such as adding or
     * removing components.
     *
     * @param packet the packet to process.
     */
    public void process(Packet packet) throws PacketException {
        Component component = getComponent(packet.getFrom());
        // Only process packets that were sent by registered components
        if (component != null) {
            if (packet instanceof IQ && IQ.Type.result == ((IQ) packet).getType()) {
                IQ iq = (IQ) packet;
                Element childElement = iq.getChildElement();
                String namespace = null;
                if (childElement != null) {
                    namespace = childElement.getNamespaceURI();
                }
                if ("http://jabber.org/protocol/disco#info".equals(namespace)) {
                    // Add a disco item to the server for the component that supports disco
                    Element identity = childElement.element("identity");
                    if (identity == null) {
                        // Do nothing since there are no identities in the disco#info packet
                        return;
                    }
                    try {
                        XMPPServer.getInstance().getIQDiscoItemsHandler().addComponentItem(packet.getFrom()
                                .toBareJID(),
                                identity.attributeValue("name"));
                        if (component instanceof ComponentSession.ExternalComponent) {
                            ComponentSession.ExternalComponent externalComponent =
                                    (ComponentSession.ExternalComponent) component;
                            externalComponent.setName(identity.attributeValue("name"));
                            externalComponent.setType(identity.attributeValue("type"));
                            externalComponent.setCategory(identity.attributeValue("category"));
                        }
                    }
                    catch (Exception e) {
                        Log.error("Error processing disco packet of component: " + component +
                                " - " + packet.toXML(), e);
                    }
                    // Store the IQ disco#info returned by the component
                    String subdomain = packet.getFrom().getDomain().replace("." + serverDomain, "");
                    componentInfo.put(subdomain, iq);
                    // Notify listeners that a component answered the disco#info request
                    for (ComponentEventListener listener : listeners) {
                        listener.componentInfoReceived(component, iq);
                    }
                }
            }
        }
    }

    /**
     * Exposes a Component as a RoutableChannelHandler.
     */
    public static class RoutableComponent implements RoutableChannelHandler {

        private JID jid;
        private Component component;

        public RoutableComponent(JID jid, Component component) {
            this.jid = jid;
            this.component = component;
        }

        public JID getAddress() {
            return jid;
        }

        public void process(Packet packet) throws PacketException {
            component.processPacket(packet);
        }
    }
}