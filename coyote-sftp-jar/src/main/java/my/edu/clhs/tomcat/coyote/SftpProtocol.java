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

import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.net.HttpCookie;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Executor;

import javax.management.ObjectName;

import org.apache.catalina.Context;
import org.apache.catalina.Manager;
import org.apache.catalina.Realm;
import org.apache.catalina.connector.CoyoteAdapter;
import org.apache.catalina.realm.NullRealm;
import org.apache.coyote.Adapter;
import org.apache.coyote.Constants;
import org.apache.coyote.InputBuffer;
import org.apache.coyote.OutputBuffer;
import org.apache.coyote.ProtocolHandler;
import org.apache.coyote.Request;
import org.apache.coyote.RequestInfo;
import org.apache.coyote.Response;
import org.apache.coyote.http11.filters.VoidOutputFilter;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.sshd.SshServer;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.Session;
import org.apache.sshd.common.Session.AttributeKey;
import org.apache.sshd.common.SessionListener;
import org.apache.sshd.common.util.SecurityUtils;
import org.apache.sshd.server.Command;
import org.apache.sshd.server.FileSystemFactory;
import org.apache.sshd.server.FileSystemView;
import org.apache.sshd.server.ForwardingFilter;
import org.apache.sshd.server.PasswordAuthenticator;
import org.apache.sshd.server.ServerFactoryManager;
import org.apache.sshd.server.command.ScpCommandFactory;
import org.apache.sshd.server.keyprovider.PEMGeneratorHostKeyProvider;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.server.sftp.SftpSubsystem;
import org.apache.tomcat.util.http.MimeHeaders;
import org.apache.tomcat.util.http.mapper.MappingData;
import org.apache.tomcat.util.res.StringManager;

/**
 * {@link ProtocolHandler} for the SSH File Transfer Protocol.
 * 
 * @author Jack Leow
 */
public class SftpProtocol implements ProtocolHandler {
    private static final Log log = LogFactory.getLog(SftpProtocol.class);
    private static final StringManager sm =
        StringManager.getManager(Constants.Package);
    private static final AttributeKey<Set<org.apache.catalina.Session>>
        SESSIONS_KEY = new AttributeKey<Set<org.apache.catalina.Session>>();
    private static final AttributeKey<Set<HttpCookie>> COOKIES_KEY =
        new AttributeKey<Set<HttpCookie>>();
    
    private final SshServer endpoint = SshServer.setUpDefaultServer();
    
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
    
    // @Override - ProtocolHandler
    public Executor getExecutor() {
        return endpoint.getScheduledExecutorService();
    }
    
    // @Override - ProtocolHandler
    public boolean isAprRequired() { return false; }
    
    public int getPort() { return endpoint.getPort(); }
    public void setPort(int port) { endpoint.setPort(port); }
    
    public String getHost() { return endpoint.getHost(); }
    public void setHost(String host) { endpoint.setHost(host); }
    
    private String anonymousUsername = "anonymous";
    public void setAnonymousUsername(String anonymousUsername) {
        this.anonymousUsername = anonymousUsername;
    }
    
    public void setSessionTimeout(String sessionTimeoutMillis) {
        endpoint.getProperties().put(
            ServerFactoryManager.IDLE_TIMEOUT, sessionTimeoutMillis);
    }
    
    private Collection<HttpCookie> getCookiesFrom(
            Session sshSession, String normalizedPath) {
        Map<String,HttpCookie> cookies = new HashMap<String,HttpCookie>();
        Set<HttpCookie> cookiesByPath = sshSession.getAttribute(COOKIES_KEY);
        
        for (HttpCookie cookie : cookiesByPath) {
            String cookieName = cookie.getName();
            String cookiePath = cookie.getPath();
            if (!cookie.hasExpired() &&
                    (cookiePath == null ||
                    normalizedPath.startsWith(cookiePath)) &&
                    !cookies.containsKey(cookieName)) {
                cookies.put(cookieName, cookie);
            }
        }
        
        return cookies.values();
    }
    
    private void insertCookieHeaders(
            MimeHeaders headers, Collection<HttpCookie> cookies) {
        StringBuilder cookiesHeader = new StringBuilder();
        Iterator<HttpCookie> iter = cookies.iterator();
        
        if (iter.hasNext()) {
            cookiesHeader.append(iter.next());
            while (iter.hasNext()) {
                cookiesHeader.append("; ");
                cookiesHeader.append(iter.next());
            }
            byte[] cookiesHeaderBytes = cookiesHeader.toString().getBytes();
            headers.setValue("Cookie").setBytes(
                cookiesHeaderBytes, 0, cookiesHeaderBytes.length);
        }
    }
    
    private Collection<String> extractCookieHeaders(Response response) {
        org.apache.catalina.connector.Response servletRes =
            (org.apache.catalina.connector.Response)response.
            getNote(CoyoteAdapter.ADAPTER_NOTES);
        
        return servletRes.getHeaders("Set-Cookie");
    }
    
    private void addCookieTo(Session sshSession, HttpCookie cookie) {
        sshSession.getAttribute(COOKIES_KEY).add(cookie);
    }
    
    private org.apache.catalina.Session extractHttpSession(
            Response response, String sessionId) {
        org.apache.catalina.Session catalinaSession;
        Request coyoteReq = response.getRequest();
        org.apache.catalina.connector.Request servletReq =
            (org.apache.catalina.connector.Request)coyoteReq.
            getNote(CoyoteAdapter.ADAPTER_NOTES);
        
        try {
            MappingData mappingData = new MappingData();
            servletReq.getConnector().getMapper().
                map(coyoteReq.serverName(), coyoteReq.requestURI(), null,
                mappingData);
            Manager manager = ((Context)mappingData.context).getManager();
            catalinaSession = manager.findSession(sessionId);
        } catch (Exception e) {
            catalinaSession = null;
        }
        
        return catalinaSession;
    }
    
    private void addHttpSessionTo(
            Session sshSession, org.apache.catalina.Session catalinaSession) {
        if (catalinaSession != null) {
            sshSession.getAttribute(SESSIONS_KEY).add(catalinaSession);
        }
    }
    
    /**
     * Submit a request to be serviced by Coyote.
     * 
     * @param path request path.
     * @param method request method.
     * @param session the current SSH session.
     * @param headers request headers.
     * @param inputBuffer PUT/POST contents.
     * @param outputBuffer response contents.
     * @return response objects (containing header information).
     */
    Response service(
            String uri, String method, Session session,
            Map<String,String> headers,
            InputBuffer inputBuffer, OutputBuffer outputBuffer) {
        Request request = new Request();
        request.setInputBuffer(inputBuffer);
        
        Response response = new Response();
        if (outputBuffer == null) outputBuffer = new VoidOutputFilter();
        response.setOutputBuffer(outputBuffer);
        
        RequestInfo rp = request.getRequestProcessor();
        rp.setStage(org.apache.coyote.Constants.STAGE_PREPARE);
        request.scheme().setString("sftp");
        request.serverName().setString(endpoint.getHost());
        request.protocol().setString("SFTP");
        request.method().setString(method);
        String normalizedPath;
        try {
            String[] uriComponents = uri.split("\\?", 2);
            normalizedPath = new File(uriComponents[0]).getCanonicalPath();
            request.requestURI().setString(normalizedPath);
            if (uriComponents.length > 1) {
                request.queryString().setString(uriComponents[1]);
            }
        } catch (IOException e) {
            // By the time we get here, the path should have been validated
            // so this should really never happen.
            throw new IOError(e);
        }
        if (session != null) {
            String username = session.getUsername();
            if (!anonymousUsername.equals(username)) {
                request.getRemoteUser().setString(username);
            }
        }
        MimeHeaders reqHeaders = request.getMimeHeaders();
        if (headers != null) {
            for (Map.Entry<String,String> header : headers.entrySet()) {
                reqHeaders.
                setValue(header.getKey()).
                setString(header.getValue());
            }
        }
        if (session != null) {
            insertCookieHeaders(
                reqHeaders, getCookiesFrom(session, normalizedPath));
        }
        request.setResponse(response);
        response.setRequest(request);
        
        rp.setStage(org.apache.coyote.Constants.STAGE_SERVICE);
        try {
            adapter.service(request, response);
        } catch (Exception e) {
            throw new RuntimeException(
                "An error occurred requesting " + uri,
                e);
        }
        if (session != null) {
            for (String cookieHeader : extractCookieHeaders(response)) {
                for (HttpCookie cookie : HttpCookie.parse(cookieHeader)) {
                    addCookieTo(session, cookie);
                    if ("JSESSIONID".equals(cookie.getName())) {
                        addHttpSessionTo(
                            session,
                            extractHttpSession(response, cookie.getValue())
                        );
                    }
                }
            }
        }
        
        rp.setStage(org.apache.coyote.Constants.STAGE_ENDED);
        return response;
    }
    
    private String getName() {
        StringBuilder name = new StringBuilder("sftp");
        name.append('-');
        String host = getHost();
        if (host != null) {
            name.append(host);
            name.append('-');
        }
        name.append(getPort());
        return ObjectName.quote(name.toString());
    }
    
    // @Override - ProtocolHandler
    public void init() throws Exception {
        if (log.isInfoEnabled()) {
            log.info(sm.getString("abstractProtocolHandler.init", getName()));
        }
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
                final boolean authenticated;
                
                if (anonymousUsername.equals(username)) {
                    authenticated = true;
                } else {
                    // Ideally, we'd be able to access the realm without
                    // making a request, since this does not appear to be
                    // possible, just make a cheap request
                    // TODO see if there are other ways to get access of Realm
                    Response response = service(
                        "/", "FAKEVERB", null, null, null, null);
                    
                    org.apache.catalina.connector.Request servletReq =
                        (org.apache.catalina.connector.Request)response.
                        getRequest().getNote(CoyoteAdapter.ADAPTER_NOTES);
                    Realm realm = servletReq.getConnector().getService().
                        getContainer().getRealm();
                    
                    authenticated = realm instanceof NullRealm ||
                        realm.authenticate(username, password) != null;
                }
                
                return authenticated;
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
                session.setAttribute(SESSIONS_KEY,
                    new HashSet<org.apache.catalina.Session>());
                session.setAttribute(COOKIES_KEY,
                    new TreeSet<HttpCookie>(
                        // Compares by path length from longest to shortest
                        new Comparator<HttpCookie>() {
                            public int compare(HttpCookie l, HttpCookie r) {
                                String lPath = l.getPath();
                                String rPath = r.getPath();
                                int lLen = lPath != null ? lPath.length() : 0;
                                int rLen = rPath != null ? rPath.length() : 0;
                                
                                int diff = rLen - lLen;
                                
                                if (diff != 0) {
                                    return diff;
                                } else {
                                    return rPath.compareTo(lPath);
                                }
                            }
                        }
                    )
                );
                session.addListener(new SessionListener() {
                    public void sessionCreated(Session session) {
                        // no-op
                    }
                    
                    public void sessionClosed(Session session) {
                        log.info(
                            "SSH session closing, invalidating HttpSessions.");
                        Set<org.apache.catalina.Session> catalinaSessions =
                            session.getAttribute(SESSIONS_KEY);
                        for (org.apache.catalina.Session catalinaSession :
                                catalinaSessions) {
                            catalinaSession.expire();
                        }
                        log.debug(
                            String.format(
                                "%s session(s) invalidated",
                                catalinaSessions.size()
                            )
                        );
                    }
                });
                return new SftpServletFileSystemView(
                    SftpProtocol.this, session);
            }
        });
        endpoint.setSubsystemFactories(
            Collections.<NamedFactory<Command>>singletonList(
            new SftpSubsystem.Factory()));
        endpoint.setCommandFactory(new ScpCommandFactory());
    }
    
    // @Override - ProtocolHandler
    public void start() throws Exception {
        if (log.isInfoEnabled()) {
            log.info(sm.getString("abstractProtocolHandler.start", getName()));
        }
        try {
            endpoint.start();
        } catch (Exception e) {
            log.error(
                sm.getString("abstractProtocolHandler.startError", getName()),
                e
            );
            throw e;
        }
    }
    
    // @Override - ProtocolHandler
    public void pause() throws Exception {
        if (log.isInfoEnabled()) {
            log.info(sm.getString("abstractProtocolHandler.pause", getName()));
        }
        try {
            endpoint.stop();
        } catch (Exception e) {
            log.error(
                sm.getString("abstractProtocolHandler.pauseError", getName()),
                e
            );
            throw e;
        }
    }
    
    // @Override - ProtocolHandler
    public void resume() throws Exception {
        if (log.isInfoEnabled()) {
            log.info(sm.getString("abstractProtocolHandler.resume", getName()));
        }
        try {
            endpoint.start();
        } catch (Exception e) {
            log.error(
                sm.getString("abstractProtocolHandler.resumeError", getName()),
                e
            );
            throw e;
        }
    }
    
    // @Override - ProtocolHandler
    public void stop() throws Exception {
        if (log.isInfoEnabled()) {
            log.info(sm.getString("abstractProtocolHandler.stop", getName()));
        }
        try {
            endpoint.stop();
        } catch (Exception e) {
            log.error(
                sm.getString("abstractProtocolHandler.stopError", getName()),
                e
            );
            throw e;
        }
   }
    
    // @Override - ProtocolHandler
    public void destroy() throws Exception {
        if (log.isInfoEnabled()) {
            log.info(
                sm.getString("abstractProtocolHandler.destroy", getName()));
        }
        try {
            endpoint.stop(true);
        } catch (Exception e) {
            log.error(
                sm.getString("abstractProtocolHandler.destroyError", getName()),
                e
            );
            throw e;
        }
    }
}