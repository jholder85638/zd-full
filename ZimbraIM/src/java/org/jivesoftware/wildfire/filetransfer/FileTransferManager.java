/*
 * 
 */
package org.jivesoftware.wildfire.filetransfer;

import org.jivesoftware.wildfire.auth.UnauthorizedException;
import org.jivesoftware.wildfire.container.Module;
import org.jivesoftware.wildfire.filetransfer.proxy.ProxyTransfer;

/**
 * Manages all file transfer currently happening originating from and/or ending at users of the
 * server. From here, file transfers can be administered and stats can be tracked.
 *
 * @author Alexander Wenckus
 */
public interface FileTransferManager extends Module {
    /**
     * The Stream Initiation, SI, namespace.
     */
    static final String NAMESPACE_SI = "http://jabber.org/protocol/si";

    /**
     * Namespace for the file transfer profile of Stream Initiation.
     */
    static final String NAMESPACE_SI_FILETRANSFER =
            "http://jabber.org/protocol/si/profile/file-transfer";

    /**
     * Bytestreams namespace
     */
    static final String NAMESPACE_BYTESTREAMS = "http://jabber.org/protocol/bytestreams";

    /**
     * Checks an incoming file transfer request to see if it should be accepted or rejected.
     * If it is accepted true will be returned and if it is rejected false will be returned.
     *
     * @param transfer the transfer to test for acceptance
     * @return true if it should be accepted false if it should not.
     */
    boolean acceptIncomingFileTransferRequest(FileTransfer transfer) throws FileTransferRejectedException;

    /**
     * Registers that a transfer has begun through the proxy connected to the server.
     *
     * @param transferDigest the digest of the initiator + target + sessionID that uniquely
     * identifies a file transfer
     * @param proxyTransfer the related proxy transfer.
     * @throws UnauthorizedException when in the current server configuration this transfer
     * should not be permitted.
     */
    void registerProxyTransfer(String transferDigest, ProxyTransfer proxyTransfer)
            throws UnauthorizedException;

    void addFileTransferInterceptor(FileTransferInterceptor interceptor);

    void removeFileTransferInterceptor(FileTransferInterceptor interceptor);

    void fireFileTransferIntercept(FileTransferProgress transfer, boolean isReady)
            throws FileTransferRejectedException;
}
