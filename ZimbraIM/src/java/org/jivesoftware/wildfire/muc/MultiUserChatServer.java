/*
 * 
 */
package org.jivesoftware.wildfire.muc;

import org.jivesoftware.wildfire.auth.UnauthorizedException;
import org.jivesoftware.wildfire.user.UserNotFoundException;
import org.xmpp.component.Component;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;

import java.util.Collection;
import java.util.List;

/**
 * Manages groupchat conversations, chatrooms, and users. This class is designed to operate
 * independently from the rest of the Jive server infrastruture. This theoretically allows
 * deployment of the groupchat on a separate server from the main IM server.
 * 
 * @author Gaston Dombiak
 */
public interface MultiUserChatServer extends Component {

    /**
     * Returns the fully-qualifed domain name of this chat service.
     * The domain is composed by the service name and the
     * name of the XMPP server where the service is running.
     * 
     * @return the chat server domain (service name + host name).
     */
    String getServiceDomain();

    /**
     * Returns the subdomain of the chat service.
     *
     * @return the subdomain of the chat service.
     */
    String getServiceName();

    /**
     * @param userJID
     * @return TRUE if the passed-in JID has sysadmin access
     */
    boolean isSysadmin(String userJID);

    /**
     * Obtain the server-wide default message history settings.
     * 
     * @return The message history strategy defaults for the server.
     */
    HistoryStrategy getHistoryStrategy();

    /**
     * Obtains a chatroom by name. A chatroom is created for that name if none exists and the user
     * has permission. The user that asked for the chatroom will be the room's owner if the chatroom
     * was created.
     * 
     * @param roomName Name of the room to get.
     * @param userjid The user's normal jid, not the chat nickname jid.
     * @return The chatroom for the given name.
     * @throws NotAllowedException If the caller doesn't have permission to create a new room.
     */
    MUCRoom getChatRoom(String roomName, JID userjid) throws NotAllowedException;

    /**
     * Obtains a chatroom by name. If the chatroom does not exists then null will be returned.
     * 
     * @param roomName Name of the room to get.
     * @return The chatroom for the given name or null if the room does not exists.
     */
    MUCRoom getChatRoom(String roomName);

    /**
     * Retuns a list with a snapshot of all the rooms in the server (i.e. persistent or not,
     * in memory or not).
     *
     * @return a list with a snapshot of all the rooms.
     */
    List<MUCRoom> getChatRooms();

    /**
     * Returns true if the server includes a chatroom with the requested name.
     * 
     * @param roomName the name of the chatroom to check.
     * @return true if the server includes a chatroom with the requested name.
     */
    boolean hasChatRoom(String roomName);

    /**
     * Removes the room associated with the given name.
     * 
     * @param roomName The room to remove.
     */
    void removeChatRoom(String roomName);

    /**
     * Removes a user from all chat rooms.
     * 
     * @param jabberID The user's normal jid, not the chat nickname jid.
     */
    void removeUser(JID jabberID);

    /**
     * Obtain a chat user by XMPPAddress.
     * 
     * @param userjid The XMPPAddress of the user.
     * @return The chatuser corresponding to that XMPPAddress.
     * @throws UserNotFoundException If the user is not found and can't be auto-created.
     */
    MUCUser getChatUser(JID userjid) throws UserNotFoundException;

    /**
     * Broadcast a given message to all members of this chat room. The sender is always set to be
     * the chatroom.
     * 
     * @param msg The message to broadcast.
     */
    void serverBroadcast(String msg) throws UnauthorizedException;

    /**
     * Returns the total chat time of all rooms combined.
     * 
     * @return total chat time in milliseconds.
     */
    public long getTotalChatTime();

    /**
     * Logs that a given message was sent to a room as part of a conversation. Every message sent
     * to the room that is allowed to be broadcasted and that was sent either from the room itself 
     * or from an occupant will be logged.<p>
     * 
     * Note: For performane reasons, the logged message won't be immediately saved. Instead we keep
     * the logged messages in memory until the logging process saves them to the database. It's 
     * possible to configure the logging process to run every X milliseconds and also the number 
     * of messages to log on each execution. 
     * @see org.jivesoftware.wildfire.muc.spi.MultiUserChatServerImpl#initialize(org.jivesoftware.wildfire.XMPPServer)
     * 
     * @param room the room that received the message.
     * @param message the message to log as part of the conversation in the room.
     * @param sender the real XMPPAddress of the sender (e.g. john@example.org). 
     */
    void logConversation(MUCRoom room, Message message, JID sender);

    /**
     * Notification message indicating the server that an incoming message was broadcasted
     * to a given number of occupants.
     *
     * @param numOccupants number of occupants that received the message.
     */
    void messageBroadcastedTo(int numOccupants);

    /**
     * @return true if the MUC service is available.
     */
    boolean isServiceEnabled();
}