/*
 * DefaultServletResourceSshFile.java
 *
 * Copyright 2013 Jack Leow
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

import java.util.Collections;
import java.util.List;

import org.apache.sshd.server.SshFile;

class DefaultServletResourceSshFile extends AbstractServletResourceSshFile {
    DefaultServletResourceSshFile(
            SftpServletFileSystemView fileSystem, String path,
            boolean isDirectory, String lastModifiedRfc1123, long size) {
        super(fileSystem, path, isDirectory, lastModifiedRfc1123, size);
    }
    
    DefaultServletResourceSshFile(
            SftpServletFileSystemView fileSystem, String path,
            boolean isDirectory) {
        super(fileSystem, path, isDirectory, null, 0);
    }
    
    // @Override
    public boolean isFile() {
        return !isDirectory();
    }
    
    // @Override
    public boolean doesExist() {
        return true;
    }
    
    // @Override
    public List<SshFile> listSshFiles() {
        return Collections.<SshFile>singletonList(
            new ClassPathResourceSshFile(
                getAbsolutePath() + "/" +
                    SftpServletFileSystemView.HELP_FILENAME,
                SftpServletFileSystemView.HELP_FILENAME
            )
        );
    }

}