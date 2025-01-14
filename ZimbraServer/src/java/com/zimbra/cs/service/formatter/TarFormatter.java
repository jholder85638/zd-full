/*
 * 
 */
package com.zimbra.cs.service.formatter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.tar.TarEntry;
import com.zimbra.common.util.tar.TarInputStream;
import com.zimbra.common.util.tar.TarOutputStream;
import com.zimbra.cs.service.UserServletContext;
import com.zimbra.cs.service.UserServletException;
import com.zimbra.cs.service.formatter.FormatterFactory.FormatType;

public class TarFormatter extends ArchiveFormatter {
    public class TarArchiveInputStream implements ArchiveInputStream {
        public class TarArchiveInputEntry implements ArchiveInputEntry {
            private TarEntry entry;

            public TarArchiveInputEntry(TarInputStream is) throws IOException {
                entry = is.getNextEntry();
            }
            public long getModTime() { return entry.getModTime().getTime(); }
            public String getName() { return entry.getName(); }
            public long getSize() { return entry.getSize(); }
            public int getType() { return entry.getMajorDeviceId(); }
            public boolean isUnread() { return (entry.getMode() & 0200) == 0; }
        }
        
        private TarInputStream is;
        
        public TarArchiveInputStream(InputStream is, String cset) {
            this.is = new TarInputStream(is, cset);
        }
        
        public void close() throws IOException { is.close(); }
        public InputStream getInputStream() { return is; }
        public ArchiveInputEntry getNextEntry() throws IOException {
            TarArchiveInputEntry taie = new TarArchiveInputEntry(is);
            
            return taie.entry == null ? null : taie;
        }
        public int read(byte[] buf, int offset, int len) throws IOException {
            return is.read(buf, offset, len);
        }
    }
    
    public class TarArchiveOutputStream implements ArchiveOutputStream {
        public class TarArchiveOutputEntry implements ArchiveOutputEntry {
            private TarEntry entry;

            public TarArchiveOutputEntry(String path, String name, int type, long
                date) {
                entry = new TarEntry(path);
                entry.setGroupName(name);
                entry.setMajorDeviceId(type);
                entry.setModTime(date);
            }
            
            public void setUnread() { entry.setMode(entry.getMode() & ~0200); }
            public void setSize(long size) { entry.setSize(size); }
        }
        
        private TarOutputStream os;
        
        public TarArchiveOutputStream(OutputStream os, String cset) throws
            IOException {
            this.os = new TarOutputStream(os, cset);
            this.os.setLongFileMode(TarOutputStream.LONGFILE_GNU);
        }
        public void close() throws IOException { os.close(); }
        public void closeEntry() throws IOException { os.closeEntry(); }
        public OutputStream getOutputStream() { return os; }
        public int getRecordSize() { return os.getRecordSize(); }
        public ArchiveOutputEntry newOutputEntry(String path, String name,
            int type, long date) {
            return new TarArchiveOutputEntry(path, name, type, date);
        }
        public void putNextEntry(ArchiveOutputEntry entry) throws IOException {
            os.putNextEntry(((TarArchiveOutputEntry)entry).entry);
        }
        public void write(byte[] buf) throws IOException { os.write(buf); }
        public void write(byte[] buf, int offset, int len) throws IOException {
            os.write(buf, offset, len);
        }
    }

    @Override 
    public String[] getDefaultMimeTypes() {
        return new String[] { "application/x-tar" };
    }

    @Override 
    public FormatType getType() { 
        return FormatType.TAR;
     }
    
    protected ArchiveInputStream getInputStream(UserServletContext context,
        String charset) throws IOException, ServiceException, UserServletException {

        return new TarArchiveInputStream(context.getRequestInputStream(-1),
            charset);
    }

    protected ArchiveOutputStream getOutputStream(UserServletContext context, String
        charset) throws IOException {
        return new TarArchiveOutputStream(context.resp.getOutputStream(), charset);
    }
}
