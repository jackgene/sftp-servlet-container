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
import java.net.URI;
import java.security.PublicKey;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Executor;

import org.apache.coyote.Adapter;
import org.apache.coyote.InputBuffer;
import org.apache.coyote.OutputBuffer;
import org.apache.coyote.ProtocolHandler;
import org.apache.coyote.Request;
import org.apache.coyote.RequestInfo;
import org.apache.coyote.Response;
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
import org.apache.sshd.server.keyprovider.PEMGeneratorHostKeyProvider;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.server.sftp.SftpSubsystem;
import org.apache.tomcat.util.http.MimeHeaders;

/**
 * {@link ProtocolHandler} for the SSH File Transfer Protocol.
 * 
 * @author Jack Leow
 */
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
    
    public Executor getExecutor() {
        return endpoint.getScheduledExecutorService();
    }
    
    public int getPort() { return endpoint.getPort(); }
    public void setPort(int port) { endpoint.setPort(port); }
    
    public String getHost() { return endpoint.getHost(); }
    public void setHost(String host) { endpoint.setHost(host); }
    
    /**
     * Submit a request to be serviced by Coyote.
     * 
     * @param path request path.
     * @param method request method.
     * @param userName request user name.
     * @param headers request headers.
     * @param inputBuffer PUT/POST contents.
     * @param outputBuffer response contents.
     * @return response objects (containing header information).
     */
    Response service(
            URI path, String method, String userName,
            Map<String,String> headers,
            InputBuffer inputBuffer, OutputBuffer outputBuffer) {
        Request request = new Request();
        request.setInputBuffer(inputBuffer);
        
        Response response = new Response();
        response.setOutputBuffer(outputBuffer);
        
        RequestInfo rp = request.getRequestProcessor();
        rp.setStage(org.apache.coyote.Constants.STAGE_PREPARE);
        request.scheme().setString("sftp");
        request.serverName().setString(endpoint.getHost());
        request.protocol().setString("SFTP");
        request.method().setString(method);
        request.requestURI().setString(path.normalize().toString());
        if (userName != null) {
            request.getRemoteUser().setString(userName);
        }
        MimeHeaders reqHeaders = request.getMimeHeaders();
        for (Map.Entry<String,String> header : headers.entrySet()) {
            reqHeaders.
                setValue(header.getKey()).
                setString(header.getValue());
        }
        request.setResponse(response);
        response.setRequest(request);
        
        rp.setStage(org.apache.coyote.Constants.STAGE_SERVICE);
        try {
            adapter.service(request, response);
        } catch (Exception e) {
            throw new RuntimeException(
                "An error occurred when " +
                userName + " requested " + path,
                e);
        }
        
        rp.setStage(org.apache.coyote.Constants.STAGE_ENDED);
        return response;
    }
    
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
            public FileSystemView createFileSystemView(Session session)
                    throws IOException {
                return new SftpServletFileSystemView(SftpProtocol.this, session.getUsername());
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
    public void stop() throws Exception {
        endpoint.stop();
    }
    
    // @Override - ProtocolHandler
    public void destroy() throws Exception {
        endpoint.stop(true);
    }
}