/*
 * AbstractServletResourceSshFile.java
 *
 * Copyright 2011-2013 Jack Leow
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
import java.net.URI;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;

import javax.xml.bind.DatatypeConverter;

import org.apache.sshd.server.SshFile;

abstract class AbstractServletResourceSshFile implements SshFile {
    private static final DateFormat RFC1123_DATE_FORMAT =
        new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
    
    protected final SftpServletFileSystemView fileSystem;
    /**
     * The path of this file. {@link File} is used here to take
     * advantage of common file operations it provides, such as
     * {@link File#getParentFile()}, {@link File#getAbsolutePath()}
     * and {@link File#equals(Object)} to name a few.
     */
    private final File path;
    private final String lastModifiedRfc1123;
    
    AbstractServletResourceSshFile(
            SftpServletFileSystemView fileSystem, String path,
            boolean isDirectory, String lastModifiedRfc1123, long size) {
        this.fileSystem = fileSystem;
        this.path = new File(path);
        // Do not use File#getCanonicalPath(), as it resolves symlinks
        this.absolutePath =
            URI.create(
                this.path.getAbsolutePath()
            ).normalize().getPath();
        this.isDirectory = isDirectory;
        this.lastModifiedRfc1123 = lastModifiedRfc1123;
        this.size = size;
    }
    
    AbstractServletResourceSshFile(
            SftpServletFileSystemView fileSystem, String path,
            boolean isDirectory) {
        this(fileSystem, path, isDirectory, null, 0);
    }
    
    private final String absolutePath;
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
    
    private final boolean isDirectory;
    // @Override
    public boolean isDirectory() {
        return isDirectory;
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
        long lastModified;
        
        if (lastModifiedRfc1123 != null) {
            try {
                // The WebDAV specs require dates to be RFC1123 formatted.
                lastModified = RFC1123_DATE_FORMAT.
                    parse(lastModifiedRfc1123).getTime();
            } catch (ParseException e) {
                // Handle ISO8601 formatted dates to support Artifactory.
                try {
                    lastModified = DatatypeConverter.
                        parseDateTime(lastModifiedRfc1123).getTimeInMillis();
                } catch (Exception e1) {
                    throw new IllegalStateException(
                        String.format(
                            "unparseable lastModifiedRfc1123 (\"%s\")",
                            lastModifiedRfc1123
                        ),
                        e
                    );
                }
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
    
    private final long size;
    // @Override
    public long getSize() {
        return size;
    }
    
    // @Override
    public boolean mkdir() {
        return fileSystem.createDirectory(getAbsolutePath());
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