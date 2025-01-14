/*
 * 
 */

package com.zimbra.cs.mina;

import com.zimbra.cs.server.ServerConfig;

import java.nio.ByteBuffer;
import java.io.IOException;
import java.io.OutputStream;

public class MinaOutputStream extends OutputStream {
    private MinaSession session;
    private ByteBuffer buf;

    public MinaOutputStream(MinaSession session) {
        this.session = session;
    }

    @Override
    public synchronized void write(byte[] b, int off, int len)
            throws IOException {
        if ((off | len | (b.length - (len + off)) | (off + len)) < 0) {
            throw new IndexOutOfBoundsException();
        }
        while (len > 0) {
            if (buf == null) {
                buf = ByteBuffer.allocate(
                    Math.max(len, getConfig().getNioWriteChunkSize()));
            }
            int count = Math.min(len, buf.remaining());
            buf.put(b, off, count);
            if (!buf.hasRemaining()) {
                flush();
            }
            len -= count;
            off += count;
        }
    }

     public void write(String s) throws IOException {
         write(s.getBytes("UTF8"));
     }

     @Override
     public void write(int b) throws IOException {
         write(new byte[] { (byte) b });
     }

     @Override
     public void flush() throws IOException {
         if (buf != null && buf.position() > 0) {
             buf.flip();
             send(buf);
             buf = null;
         }
     }

     private void send(ByteBuffer bb) throws IOException {
         session.send(bb);
         int threshold = getConfig().getNioMaxScheduledWriteBytes();
         if (threshold > 0 && threshold < session.getScheduledWriteBytes()) {
             long timeout = getConfig().getNioWriteTimeout() * 1000;
             if (!session.drainWriteQueue(threshold, timeout)) {
                 throw new IOException("Timed-out while writing data");
             }
         }
     }

     private ServerConfig getConfig() {
         return session.getServer().getConfig();
     }

     @Override
     public void close() throws IOException {
         flush();
         session.close();
     }
}
