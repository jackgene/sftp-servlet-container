/*
 * ClassPathResourceSshFile.java
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
import java.net.URL;
import java.net.URLConnection;
import java.util.List;

import org.apache.sshd.server.SshFile;

class ClassPathResourceSshFile implements SshFile {
    private final URL resourceUrl;
    /**
     * The path of this file. {@link File} is used here to take
     * advantage of common file operations it provides, such as
     * {@link File#getParentFile()}, {@link File#getAbsolutePath()}
     * and {@link File#equals(Object)} to name a few.
     */
    private final File absolutePath;
    private final long size;
    private final long lastModified;
    
    ClassPathResourceSshFile(String absolutePath, String resourceName) {
        // Validate if we ever genericize this.
        this.absolutePath = new File(absolutePath);
        resourceUrl = getClass().getResource(resourceName);
        if (resourceUrl == null) {
            throw new IllegalArgumentException(
                String.format("%s does not exist", resourceName));
        }
        
        URLConnection conn = null;
        try {
            conn = resourceUrl.openConnection();
            size = conn.getContentLength();
            lastModified = conn.getLastModified();
        } catch (IOException e) {
            throw new IOError(e);
        } finally {
            try {
                if (conn != null) conn.getInputStream().close();
            } catch (IOException e) {
                // TODO user proper logger
                e.printStackTrace();
                // do nothing
            }
        }
    }
    
    // @Override
    public String getAbsolutePath() {
        return absolutePath.getPath();
    }
    
    // @Override
    public String getName() {
        return absolutePath.getName();
    }
    
    // @Override
    public String getOwner() {
        return SftpServletFileSystemView.DEFAULT_FILE_OWNER;
    }
    
    // @Override
    public boolean isDirectory() {
        return false;
    }
    
    // @Override
    public boolean isFile() {
        return true;
    }
    
    // @Override
    public boolean doesExist() {
        return true;
    }
    
    // @Override
    public boolean isReadable() {
        return true;
    }
    
    // @Override
    public boolean isWritable() {
        return false;
    }
    
    public boolean isExecutable() {
        return false;
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
        return lastModified;
    }
    
    // @Override
    public boolean setLastModified(long time) {
        return false;
    }
    
    // @Override
    public long getSize() {
        return size;
    }
    
    // @Override
    public boolean mkdir() {
        return false;
    }
    
    // @Override
    public boolean delete() {
        return false;
    }
    
    // @Override
    public boolean create() throws IOException {
        return false;
    }
    
    // @Override
    public void truncate() throws IOException {
        throw new UnsupportedOperationException();
    }
    
    // @Override
    public boolean move(SshFile destination) {
        return false;
    }
    
    // @Override
    public List<SshFile> listSshFiles() {
        throw new UnsupportedOperationException();
    }
    
    // @Override
    public OutputStream createOutputStream(long offset) throws IOException {
        throw new UnsupportedOperationException(
            "Cannot write to read-only file");
    }
    
    // @Override
    public InputStream createInputStream(long offset) throws IOException {
        URLConnection conn = resourceUrl.openConnection();
        return conn.getInputStream();
    }
    
    // @Override
    public void handleClose() throws IOException {
        // do nothing
        // TODO check for unclosed input/output streams?
    }
    
    @Override
    public String toString() {
        return getAbsolutePath();
    }
}