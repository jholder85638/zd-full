/*
 * 
 */

package com.zimbra.cs.mina;

import java.nio.ByteBuffer;

/**
 * Represents a MINA-request that is a single line of text. This is used for
 * both POP3 and IMAP command line requests. IMAP command line requests are
 * handled specially in MinaImapRequest with additional support for literal
 * command bytes.
 */
public class MinaTextLineRequest implements MinaRequest {
    private final LineBuffer mBuffer;

    /**
     * Creates a new request for parsing a text line.
     */
    public MinaTextLineRequest() {
        mBuffer = new LineBuffer();
    }

    /**
     * Parses a line of text from the specified byte buffer.
     * 
     * @param bb the ByteBuffer containing the line bytes
     */
    public void parse(ByteBuffer bb) {
        mBuffer.parse(bb);
    }

    /**
     * Returns true if the line is complete, or false if more bytes are needed
     *
     * @return if request is complete, false otherwise
     */
    public boolean isComplete() {
        return mBuffer.isComplete();
    }

    /**
     * Returns the request line as a string.
     * 
     * @return the request line, or null if not yet complete
     */
    public String getLine() {
        return mBuffer.isComplete() ? mBuffer.toString() : null;
    }

    public String toString() {
        return getLine();
    }
}
