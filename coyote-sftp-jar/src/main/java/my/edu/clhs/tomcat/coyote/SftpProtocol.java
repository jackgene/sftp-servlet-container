/*
 * SftpProtocol.java
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


import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.PublicKey;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;

import org.apache.coyote.Adapter;
import org.apache.coyote.ProtocolHandler;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.sshd.SshServer;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.Session;
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

/**
 * {@link ProtocolHandler} for the SSH File Transfer Protocol.
 * 
 * @author Jack Leow
 */
// TODO where do we canonicalize the "path"? getAbsolutePath()? before service()?
public class SftpProtocol implements ProtocolHandler {
    private static final Log log = LogFactory.getLog(SftpProtocol.class);
    
    private final SshServer endpoint = SshServer.setUpDefaultServer();
    SshServer getEndpoint() { return endpoint; }
    
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
    
    public String getHost() { return endpoint.getHost(); }
    public void setHost(String host) { endpoint.setHost(host); }
    
    // @Override - ProtocolHandler
    public void init() throws Exception {
        if (SecurityUtils.isBouncyCastleRegistered()) {
            endpoint.setKeyPairProvider(
                new PEMGeneratorHostKeyProvider("key.pem"));
        } else {
            endpoint.setKeyPairProvider(
                new SimpleGeneratorHostKeyProvider("key.ser"));
        }
        endpoint.setPasswordAuthenticator(new PasswordAuthenticator() {
            // @Override
            public boolean authenticate(
                    String username, String password, ServerSession session) {
                return username != null && username.equals(password);
            }
        });
        endpoint.setPublickeyAuthenticator(new PublickeyAuthenticator() {
            // @Override
            public boolean authenticate(
                    String username, PublicKey key, ServerSession session) {
                return true;
            }
        });
        endpoint.setForwardingFilter(new ForwardingFilter() {
            // @Override
            public boolean canForwardAgent(ServerSession session) {
                return true;
            }
            
            // @Override
            public boolean canForwardX11(ServerSession session) {
                return true;
            }
            
            // @Override
            public boolean canListen(
                    InetSocketAddress address, ServerSession session) {
                return true;
            }
            
            // @Override
            public boolean canConnect(
                    InetSocketAddress address, ServerSession session) {
                return true;
            }
        });
        endpoint.setFileSystemFactory(new FileSystemFactory() {
            class SftpServletFileSystemView implements FileSystemView {
                private final String userName;
                
                SftpServletFileSystemView(String userName) {
                    this.userName = userName;
                }
                
                // @Override
                public SshFile getFile(String file) {
                    return new SftpServletFile(
                        SftpProtocol.this, file, userName);
                }
                
                // @Override
                public SshFile getFile(SshFile baseDir, String file) {
                    return new SftpServletFile(
                        SftpProtocol.this,
                        baseDir.getAbsolutePath() + "/" + file,
                        userName);
                }
            }
            
            public FileSystemView createFileSystemView(Session session)
                    throws IOException {
                return new SftpServletFileSystemView(session.getUsername());
            }
        });
        endpoint.setSubsystemFactories(
            Collections.<NamedFactory<Command>>singletonList(
            new SftpSubsystem.Factory()));
    }
    
    // @Override - ProtocolHandler
    public void start() throws Exception {
        String listenHost = getHost();
        log.info(
            "Starting Coyote SFTP/ssh-2.0 on /" +
            (listenHost != null ? listenHost : "0.0.0.0") + ":" +
            getPort());
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