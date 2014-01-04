/*
 * ServletResourceSshFile.java
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

import java.util.List;

import org.apache.sshd.server.SshFile;

public class WebDAVServletResourceSshFile
        extends AbstractServletResourceSshFile {
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
        
        private boolean isDirectory;
        public Builder isDirectory(boolean isDirectory) {
            this.isDirectory = isDirectory;
            return this;
        }
        
        private boolean isFile;
        public Builder isFile(boolean isFile) {
            this.isFile = isFile;
            return this;
        }
        
        private boolean exists = true; // This makes it HttpServlet compatible.
        public Builder doesExist(boolean exists) {
            this.exists = exists;
            return this;
        }
        
        private String lastModifiedRfc1123;
        public Builder lastModifiedRfc1123(String lastModifiedRfc1123) {
            this.lastModifiedRfc1123 = lastModifiedRfc1123;
            return this;
        }
       
        private long size = 0L;
        public Builder size(long size) {
            this.size = size;
            return this;
        }
        
        public WebDAVServletResourceSshFile build() {
            return new WebDAVServletResourceSshFile(this);
        }
    }
    
    private WebDAVServletResourceSshFile(Builder builder) {
        super(
            builder.fileSystemView, builder.path, builder.isDirectory,
            builder.lastModifiedRfc1123, builder.size
        );
        isFile = builder.isFile;
        exists = builder.exists;
    }
    
    private final boolean isFile;
    // @Override
    public boolean isFile() {
        return isFile;
    }
    
    private final boolean exists;
    // @Override
    public boolean doesExist() {
        return exists;
    }
    
    // @Override
    public List<SshFile> listSshFiles() {
        return fileSystem.getDirectoryContents(getAbsolutePath());
    }
}