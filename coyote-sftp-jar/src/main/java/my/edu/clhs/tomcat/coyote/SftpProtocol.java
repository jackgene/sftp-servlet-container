/**
 * SftpProtocol.java
 *
 * Copyright 2011 Jack Leow
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.security.PublicKey;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.apache.coyote.Adapter;
import org.apache.coyote.InputBuffer;
import org.apache.coyote.OutputBuffer;
import org.apache.coyote.ProtocolHandler;
import org.apache.coyote.Request;
import org.apache.coyote.RequestInfo;
import org.apache.coyote.Response;
import org.apache.coyote.http11.Constants;
import org.apache.sshd.SshServer;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.util.SecurityUtils;
import org.apache.sshd.server.Command;
import org.apache.sshd.server.FileSystemFactory;
import org.apache.sshd.server.FileSystemView;
import org.apache.sshd.server.ForwardingFilter;
import org.apache.sshd.server.PasswordAuthenticator;
import org.apache.sshd.server.PublickeyAuthenticator;
import org.apache.sshd.server.SshFile;
import org.apache.sshd.server.keyprovider.PEMGeneratorHostKeyProvider;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.server.sftp.SftpSubsystem;
import org.apache.tomcat.util.buf.ByteChunk;

/**
 * {@link ProtocolHandler} for the SSH File Transfer Protocol.
 * 
 * @author Jack Leow
 */
public class SftpProtocol implements ProtocolHandler {
    private SshServer endpoint = SshServer.setUpDefaultServer();
    
    private HashMap<String,Object> attributes = new HashMap<String,Object>();
    
    // @Override - ProtocolHandler
    public Object getAttribute(String name) {
        return attributes.get(name);
    }
    
    // @Override - ProtocolHandler
    public void setAttribute(String name, Object value) {
        attributes.put(name, value);
    }
    
    // @Override - ProtocolHandler
    public Iterator<?> getAttributeNames() {
        return attributes.keySet().iterator();
    }
    
    /**
     * The adapter, used to call the connector.
     */
    private Adapter adapter;
    // @Override - ProtocolHandler
    public Adapter getAdapter() { return adapter; }
    // @Override - ProtocolHandler
    public void setAdapter(Adapter adapter) { this.adapter = adapter; }
    
    public int getPort() { return endpoint.getPort(); }
    public void setPort(int port) { endpoint.setPort(port); }
    
    private static final String README_FILENAME = "README.txt";
    private static final URL README_FILE_URL =
        SftpServletFile.class.getResource(README_FILENAME);
    private static final int README_FILE_SIZE;
    static {
        try {
            README_FILE_SIZE = README_FILE_URL.
                openConnection().getContentLength();
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
    
    private class SftpServletFile implements SshFile {
        private int httpStatus = 404;
        private ByteChunk contents = new ByteChunk();
        
        public SftpServletFile(String path, String userName) {
            if (path == null || path.equals(".")) {
                absolutePath = "/";
            } else if (path.startsWith("/")) {
                absolutePath = path;
            } else {
                absolutePath = "/" + path;
            }
            
            if (!absolutePath.endsWith("/")) {
                InputBuffer inputBuffer = new InputBuffer() {
                    public int doRead(ByteChunk chunk, Request request)
                            throws IOException {
                        return 0;
                    }
                };
                Request request = new Request();
                request.setInputBuffer(inputBuffer);
                
                OutputBuffer outputBuffer = new OutputBuffer() {
                    public int doWrite(ByteChunk chunk, Response response)
                            throws IOException {
                        contents.append(chunk);
                        return chunk.getLength();
                    }
                };
                Response response = new Response();
                response.setOutputBuffer(outputBuffer);
                
                RequestInfo rp = request.getRequestProcessor();
                rp.setStage(org.apache.coyote.Constants.STAGE_PREPARE);
                request.scheme().setString("sftp");
                request.serverName().setString(endpoint.getHost());
                request.protocol().setString("SFTP");
                request.method().setString(Constants.GET); // TODO HEAD here, GET later?
                request.requestURI().setString(path);
                if (userName != null) {
                    request.getRemoteUser().setString(userName);
                }
                
                rp.setStage(org.apache.coyote.Constants.STAGE_SERVICE);
                try {
                    adapter.service(request, response);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                
                rp.setStage(org.apache.coyote.Constants.STAGE_ENDED);
                
                httpStatus = response.getStatus();
            }
        }
        
        private String absolutePath;
        
        public String getAbsolutePath() {
            return absolutePath;
        }
        
        public String getName() {
            int lastSlashIdx = absolutePath.lastIndexOf('/');
            return lastSlashIdx >= 0 ?
                absolutePath.substring(lastSlashIdx + 1) : absolutePath;
        }
        
        public void truncate() throws IOException {
            // do nothing
            // TODO implement for HTTP DELETE.
        }
        
        public boolean setLastModified(long time) {
            return false;
        }
        
        public boolean move(SshFile destination) {
            return false;
        }
        
        public boolean mkdir() {
            return false;
        }
        
        public List<SshFile> listSshFiles() {
            return Collections.singletonList(
                (SshFile)new SftpServletFile(README_FILENAME, null));
        }
        
        public boolean isWritable() {
            return false;
        }
        
        public boolean isRemovable() {
            return false;
        }
        
        public boolean isReadable() {
            return true;
        }
        
        public boolean isFile() {
            return httpStatus == 200 || README_FILENAME.equals(getName());
        }
        
        public boolean isDirectory() {
            return !isFile();
        }
        
        public void handleClose() throws IOException {
            // do nothing
        }
        
        public long getSize() {
            int size = 0;
            
            if (httpStatus == 200) {
                size = contents.getLength();
            } else if (README_FILENAME.equals(getName())) {
                size = README_FILE_SIZE;
            }
            
            return size;
        }
        
        public SshFile getParentFile() {
            throw new UnsupportedOperationException();
        }
        
        public long getLastModified() {
            return System.currentTimeMillis(); // TODO servlet's last modified
        }
        
        public boolean doesExist() {
            return true; // TODO only on HTTP 200? "readme"?
        }
        
        public boolean delete() {
            return false;
        }
        
        public OutputStream createOutputStream(long offset) throws IOException {
            return new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                    // do nothing
                    // TODO implement for HTTP PUT? POST?
                }
            };
        }
        
        public InputStream createInputStream(long offset) throws IOException {
            InputStream is;
            
            if (httpStatus == 200) {
                is = new ByteArrayInputStream(contents.getBuffer(),
                    contents.getStart(), contents.getLength());
            } else if (README_FILENAME.equals(getName())) {
                is = README_FILE_URL.openStream();
            } else {
                is = null;
            }
            if (is != null) {
                try {
                    is.skip(offset);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            
            return is;
        }
    }
    
    public void init() throws Exception {
        if (SecurityUtils.isBouncyCastleRegistered()) {
            endpoint.setKeyPairProvider(
                new PEMGeneratorHostKeyProvider("key.pem"));
        } else {
            endpoint.setKeyPairProvider(
                new SimpleGeneratorHostKeyProvider("key.ser"));
        }
        endpoint.setPasswordAuthenticator(new PasswordAuthenticator() {
            public boolean authenticate(
                    String username, String password, ServerSession session) {
                return username != null && username.equals(password);
            }
        });
        endpoint.setPublickeyAuthenticator(new PublickeyAuthenticator() {
            public boolean authenticate(
                    String username, PublicKey key, ServerSession session) {
                return true;
            }
        });
        endpoint.setForwardingFilter(new ForwardingFilter() {
            public boolean canForwardAgent(ServerSession session) {
                return true;
            }
            
            public boolean canForwardX11(ServerSession session) {
                return true;
            }
            
            public boolean canListen(
                    InetSocketAddress address, ServerSession session) {
                return true;
            }
            
            public boolean canConnect(
                    InetSocketAddress address, ServerSession session) {
                return true;
            }
        });
        endpoint.setFileSystemFactory(new FileSystemFactory() {
            public FileSystemView createFileSystemView(final String userName) {
                return new FileSystemView() {
                    public SshFile getFile(String file) {
                        return new SftpServletFile(file, userName);
                    }
                };
            }
        });
        endpoint.setSubsystemFactories(
            Collections.<NamedFactory<Command>>singletonList(
            new SftpSubsystem.Factory()));
    }
    
    // @Override - ProtocolHandler
    public void start() throws Exception {
        endpoint.start();
    }
    
    // @Override - ProtocolHandler
    public void pause() throws Exception {
        endpoint.stop();
    }
    
    // @Override - ProtocolHandler
    public void resume() throws Exception {
        endpoint.start();
    }
    
    // @Override - ProtocolHandler
    public void destroy() throws Exception {
        endpoint.stop(true);
    }
}