/*
 * SftpServletSshFile.java
 *
 * Copyright 2011-2012 Jack Leow
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package my.edu.clhs.tomcat.coyote;

import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;

import org.apache.sshd.server.SshFile;

class ServletResourceSshFile implements SshFile {
    private static final DateFormat RFC1123_DATE_FORMAT =
        new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
    
    private final SftpServletFileSystemView fileSystem;
    /**
     * The path of this file. {@link File} is used here to take
     * advantage of common file operations it provides, such as
     * {@link File#getParentFile()}, {@link File#getAbsolutePath()}
     * and {@link File#equals(Object)} to name a few.
     */
    private final File path;
    private final String absolutePath;
    private final String lastModifiedRfc1123;
    private final boolean isFile;
    private final boolean isDirectory;
    private final long contentLength;
    
    private ServletResourceSshFile(Builder builder) {
        fileSystem = builder.fileSystemView;
        path = new File(builder.path);
        try {
            absolutePath = path.getCanonicalPath();
        } catch (IOException e) {
            throw new IllegalArgumentException(
                "Cannot create ServletResourceSshFile with malformed path spec",
                e
            );
        }
        lastModifiedRfc1123 = builder.lastModifiedRfc1123;
        isFile = builder.isFile;
        isDirectory = builder.isDirectory;
        contentLength = builder.size;
    }
    
    public static class Builder {
        private final SftpServletFileSystemView fileSystemView;
        public Builder(SftpServletFileSystemView fileSystemView) {
            this.fileSystemView = fileSystemView;
        }
        
        private String path;
        public Builder path(String path) {
            this.path = path;
            return this;
        }
        
        private boolean isFile;
        public Builder isFile(boolean isFile) {
            this.isFile = isFile;
            return this;
        }
        
        private boolean isDirectory;
        public Builder isDirectory(boolean isDirectory) {
            this.isDirectory = isDirectory;
            return this;
        }
        
        private long size = 0L;
        public Builder size(long size) {
            this.size = size;
            return this;
        }
        
        private String lastModifiedRfc1123;
        public Builder lastModifiedRfc1123(String lastModifiedRfc1123) {
            this.lastModifiedRfc1123 = lastModifiedRfc1123;
            return this;
        }
       
        public ServletResourceSshFile build() {
            return new ServletResourceSshFile(this);
        }
    }
    
    // @Override
    public String getAbsolutePath() {
        return absolutePath;
    }
    
    // @Override
    public String getName() {
        return path.getName();
    }
    
    // @Override
    public String getOwner() {
        return SftpServletFileSystemView.DEFAULT_FILE_OWNER;
    }
    
    // @Override
    public boolean isDirectory() {
        return isDirectory;
    }
    
    // @Override
    public boolean isFile() {
        return isFile;
    }
    
    // @Override
    public boolean doesExist() {
        return true; // This makes it HttpServlet compatible.
    }
    
    // @Override
    public boolean isReadable() {
        return true;
    }
    
    // @Override
    public boolean isWritable() {
        return true;
    }
    
    public boolean isExecutable() {
        return isDirectory();
    }
    
    // @Override
    public boolean isRemovable() {
        return false;
    }
    
    // @Override
    public SshFile getParentFile() {
        throw new UnsupportedOperationException();
    }
    
    // @Override
    public long getLastModified() {
        final long lastModified;
        
        if (lastModifiedRfc1123 != null) {
            try {
                lastModified = RFC1123_DATE_FORMAT.
                    parse(lastModifiedRfc1123).getTime();
            } catch (ParseException e) {
                throw new IllegalStateException(
                    String.format(
                        "lastModifiedRfc1123 value (\"%s\") cannot be parsed",
                        lastModifiedRfc1123
                    ),
                    e
                );
            }
        } else {
            lastModified = System.currentTimeMillis();
        }
        
        return lastModified;
    }
    
    // @Override
    public boolean setLastModified(long time) {
        return false;
    }
    
    // @Override
    public long getSize() {
        return contentLength;
    }
    
    // @Override
    public boolean mkdir() {
        // TODO implement for HTTP MKCOL.
        throw new UnsupportedOperationException("Pending");
    }
    
    // @Override
    public boolean delete() {
        return fileSystem.deleteFile(getAbsolutePath());
    }
    
    // @Override
    public boolean create() throws IOException {
        return true;
    }
    
    // @Override
    public void truncate() throws IOException {
        // do nothing
    }
    
    // @Override
    public boolean move(SshFile destination) {
        return false;
    }
    
    // @Override
    public List<SshFile> listSshFiles() {
        return fileSystem.getDirectoryContents(getAbsolutePath());
    }
    
    // @Override
    public OutputStream createOutputStream(long offset) throws IOException {
        return fileSystem.getFileOutputStream(getAbsolutePath());
    }
    
    // @Override
    public InputStream createInputStream(long offset) throws IOException {
        InputStream is = fileSystem.getFileInputStream(getAbsolutePath());
        if (is != null) {
            try {
                is.skip(offset);
            } catch (IOException e) {
                throw new IOError(e);
            }
        }
        
        return is;
    }
    
    @Override
    public String toString() {
        return path.toString();
    }
    
    // @Override
    public void handleClose() throws IOException {
        // do nothing
        // TODO check for unclosed input/output streams?
    }
}