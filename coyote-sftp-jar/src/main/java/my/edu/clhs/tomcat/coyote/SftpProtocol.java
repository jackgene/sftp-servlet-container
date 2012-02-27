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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.security.PublicKey;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import static org.apache.catalina.connector.Response.*;
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
import org.apache.tomcat.util.http.MimeHeaders;

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
    
    private static final DateFormat HTTP_DATE_FORMAT =
        new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
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
        private final String userName;
        private final int httpStatus;
        private final MimeHeaders httpHeaders;
        private final long contentLength;
        
        /**
         * Submit a request to be serviced by Coyote.
         * 
         * @param path request path.
         * @param method request method.
         * @param inputBuffer PUT/POST contents.
         * @param outputBuffer response contents.
         * @return response objects (containing header information).
         */
        private Response service(String method,
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
            request.requestURI().setString(absolutePath);
            if (userName != null) {
                request.getRemoteUser().setString(userName);
            }
            request.setResponse(response);
            response.setRequest(request);
            
            rp.setStage(org.apache.coyote.Constants.STAGE_SERVICE);
            try {
                adapter.service(request, response);
            } catch (Exception e) {
                throw new RuntimeException(
                    "An error occurred when " +
                    userName + " requested " + absolutePath,
                    e);
            }
            
            rp.setStage(org.apache.coyote.Constants.STAGE_ENDED);
            return response;
        }
        
        public SftpServletFile(String path, String userName) {
            this.userName = userName;
            if (path == null || path.equals(".")) {
                absolutePath = "/";
            } else if (path.startsWith("/")) {
                absolutePath = path;
            } else {
                absolutePath = "/" + path;
            }
            
            if (!absolutePath.endsWith("/")) {
                OutputBuffer outputBuffer = new OutputBuffer() {
                    public int doWrite(ByteChunk chunk, Response response)
                            throws IOException {
                        return chunk.getLength();
                    }
                };
                
                Response response = service(Constants.HEAD, null, outputBuffer);
                
                httpStatus = response.getStatus();
                contentLength = response.getContentLengthLong();
                httpHeaders = response.getMimeHeaders();
            } else {
                httpStatus = SC_NOT_FOUND;
                contentLength = 0;
                httpHeaders = new MimeHeaders();
            }
        }
        
        private String absolutePath;
        
        // @Override
        public String getAbsolutePath() {
            return absolutePath;
        }
        
        // @Override
        public String getName() {
            int lastSlashIdx = absolutePath.lastIndexOf('/');
            return lastSlashIdx >= 0 ?
                absolutePath.substring(lastSlashIdx + 1) : absolutePath;
        }
        
        // @Override
        public void truncate() throws IOException {
            // do nothing
        }
        
        // @Override
        public boolean setLastModified(long time) {
            return false;
        }
        
        // @Override
        public boolean move(SshFile destination) {
            return false;
        }
        
        // @Override
        public boolean mkdir() {
            return false;
        }
        
        // @Override
        public List<SshFile> listSshFiles() {
            // TODO use absolute paths when creating files.
            return Collections.singletonList(
                (SshFile)new SftpServletFile(README_FILENAME, userName));
        }
        
        // @Override
        public boolean isWritable() {
            return false;
        }
        
        // @Override
        public boolean isRemovable() {
            return false;
        }
        
        // @Override
        public boolean isReadable() {
            return true;
        }
        
        // @Override
        public boolean isFile() {
            return httpStatus == SC_OK || README_FILENAME.equals(getName());
        }
        
        // @Override
        public boolean isDirectory() {
            return !isFile();
        }
        
        // @Override
        public void handleClose() throws IOException {
            // do nothing
        }
        
        // @Override
        public long getSize() {
            long size = 0;
            
            if (httpStatus == SC_OK) {
                size = contentLength;
            } else if (README_FILENAME.equals(getName())) {
                size = README_FILE_SIZE;
            }
            
            return size;
        }
        
        // @Override
        public SshFile getParentFile() {
            throw new UnsupportedOperationException();
        }
        
        // @Override
        public long getLastModified() {
            try {
                if (README_FILENAME.equals(getName())) {
                    return README_FILE_URL.openConnection().getLastModified();
                } else {
                    String lastModified =
                        httpHeaders.getHeader("Last-Modified");
                    
                    if (lastModified != null) {
                        return HTTP_DATE_FORMAT.
                            parse(lastModified).getTime();
                    }
                    
                    return System.currentTimeMillis();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }
        }
        
        // @Override
        public boolean doesExist() {
            return true; // TODO this makes it HttpServlet compatible.
        }
        
        // @Override
        public boolean delete() {
            // TODO implement for HTTP DELETE.
            return false;
        }
        
        // @Override
        public OutputStream createOutputStream(long offset) throws IOException {
            PipedOutputStream os = new PipedOutputStream();
            final PipedInputStream is = new PipedInputStream(os);
            
            new Thread(new Runnable() {
                public void run() {
                    InputBuffer inputBuffer = new InputBuffer() {
                        public int doRead(ByteChunk chunk, Request request)
                                throws IOException {
                            byte[] buffer = new byte[1024];
                            int len = is.read(buffer);
                            chunk.setBytes(buffer, 0, len);
                            
                            return len;
                        }
                    };
                    OutputBuffer outputBuffer = new OutputBuffer() {
                        public int doWrite(
                                ByteChunk chunk, Response response)
                                throws IOException {
                            // Do nothing
                            return chunk.getLength();
                        }
                    };
                    
                    service("PUT", inputBuffer, outputBuffer);
                }
            }).start();
            
            return os;
        }
        
        // @Override
        public InputStream createInputStream(long offset) throws IOException {
            InputStream is;
            
            if (httpStatus == SC_OK) {
                is = new PipedInputStream();
                final OutputStream pos =
                    new PipedOutputStream((PipedInputStream)is);
                
                new Thread(new Runnable() {
                    public void run() {
                        OutputBuffer outputBuffer = new OutputBuffer() {
                            public int doWrite(
                                    ByteChunk chunk, Response response)
                                    throws IOException {
                                pos.write(chunk.getBuffer(),
                                    chunk.getStart(), chunk.getLength());
                                return chunk.getLength();
                            }
                        };
                        
                        try {
                            service(Constants.GET, null, outputBuffer);
                        } finally {
                            try {
                                pos.close();
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                }).start();
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
            public FileSystemView createFileSystemView(final String userName) {
                return new FileSystemView() {
                    // @Override
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