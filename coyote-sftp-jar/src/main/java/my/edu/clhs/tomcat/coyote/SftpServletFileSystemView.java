/*
 * SftpServletFileSystemView.java
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

import static javax.servlet.http.HttpServletResponse.SC_CREATED;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static javax.servlet.http.HttpServletResponse.SC_OK;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.coyote.InputBuffer;
import org.apache.coyote.OutputBuffer;
import org.apache.coyote.Request;
import org.apache.coyote.Response;
import org.apache.coyote.http11.Constants;
import org.apache.sshd.server.FileSystemView;
import org.apache.sshd.server.SshFile;
import org.apache.tomcat.util.buf.ByteChunk;
import org.xml.sax.SAXException;

class SftpServletFileSystemView implements FileSystemView {
    public static final String DEFAULT_FILE_OWNER = "nobody";
    
    private static final int SC_MULTI_STATUS = 207;
    
    private final SftpProtocol protocol;
    private final String userName;
    
    SftpServletFileSystemView(SftpProtocol sftpProtocol, String userName) {
        protocol = sftpProtocol;
        this.userName = userName;
    }
    
    private static final String PROPFIND_ALLPROP_BODY =
        "<?xml version=\"1.0\" encoding=\"utf-8\" ?>" +
        "<D:propfind xmlns:D=\"DAV:\">" +
            "<D:allprop/>" +
        "</D:propfind>";
    private InputStream propFindResponseXmlBody(String absolutePath, int depth)
            throws DavProcessingException {
        InputBuffer propFindBuf = new InputBuffer() {
            public int doRead(ByteChunk chunk, Request request)
                    throws IOException {
                int len = PROPFIND_ALLPROP_BODY.length();
                
                chunk.setBytes(
                    PROPFIND_ALLPROP_BODY.getBytes(chunk.getCharset()), 0, len);
                return len;
            }
        };
        final ByteChunk webDavChunk = new ByteChunk();
        OutputBuffer webDavBuf = new OutputBuffer() {
            private long bytesWritten = 0;
            
            public int doWrite(ByteChunk chunk, Response response)
                    throws IOException {
                webDavChunk.append(chunk);
                int len = chunk.getLength();
                bytesWritten += len;
                return len;
            }
            
            public long getBytesWritten() {
                return bytesWritten;
            }
        };
        Map<String,String> propFindHeaders = new HashMap<String,String>();
        propFindHeaders.put("Depth", Integer.toString(depth));
        Response response = protocol.service(
            URI.create(absolutePath), "PROPFIND", userName, propFindHeaders,
            propFindBuf, webDavBuf);
        int status = response.getStatus();
        
        InputStream content;
        // Technically, only SC_MULTI_STATUS (207) is valid, however
        // it is almost impossible to get some web frameworks
        // (including Spring MVC) to return 207.
        // SC_NOT_FOUND is technically also valid, but for our purposes
        // we'd like to process it as if it's an invalid request.
        if (status == SC_MULTI_STATUS || status == SC_OK) {
            content = new ByteArrayInputStream(webDavChunk.getBuffer());
        } else if (status == SC_NOT_FOUND) {
            content = null;
        } else {
            throw new DavUnsupportedException(absolutePath);
        }
        
        return content;
    }
    
    private List<? extends SshFile> xmlToFiles(
            String absolutePath, InputStream multiStatusXml,
            String pathToDiscard) throws DavProcessingException {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(true);
        WebDavSaxHandler handler = new WebDavSaxHandler(this, pathToDiscard);
        
        try {
            SAXParser parser = factory.newSAXParser();
            parser.parse(multiStatusXml, handler);
        } catch (SAXException e) {
            throw new InvalidDavXmlException(absolutePath, e);
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException(e);
        } catch (IOException e) {
            throw new IOError(e);
        }
        
        return handler.getFiles();
    }
    
    private boolean isEquivalent(String path, SshFile file) {
        try {
            return (new File(path).getCanonicalPath()).
                equals(file.getAbsolutePath());
        } catch (IOException e) {
            return false;
        }
    }
    
    // @Override
    public SshFile getFile(String path) {
        SshFile sshFile = null;
        final String absolutePath =
            (path == null || path.equals(".")) ? "/" : path;
        
        try {
            InputStream responseXml = propFindResponseXmlBody(absolutePath, 0);
            if (responseXml != null) {
                List<? extends SshFile> files =
                    xmlToFiles(absolutePath, responseXml, null);
                
                // Strictly speaking, "files" should always contain exactly
                // one item.
                // However some broken DAV implementations may return more
                // than one item.
                for (SshFile file : files) {
                    if (isEquivalent(path, file)) {
                        sshFile = file;
                        break;
                    }
                }
                // And other broken DAV implementations may return none.
                if (sshFile == null) {
                    throw new InvalidDavContentException(absolutePath);
                }
            } else {
                sshFile = new ServletResourceSshFile.Builder(this).
                    path(absolutePath).
                    doesExist(false).
                    build();
            }
        } catch (DavProcessingException e) {
            Response response = protocol.service(
                URI.create(absolutePath), Constants.HEAD, userName,
                Collections.<String,String>emptyMap(), null, null);
            
            boolean isFile = response.getStatus() == SC_OK;
            if (!isFile && absolutePath.endsWith("/README.txt")) {
                sshFile = new ClassPathResourceSshFile(
                    absolutePath, "README.txt");
            } else {
                sshFile = new ServletResourceSshFile.Builder(this).
                    path(absolutePath).
                    isFile(isFile).
                    isDirectory(!isFile).
                    size(response.getContentLengthLong()).
                    lastModifiedRfc1123(
                        response.getMimeHeaders().getHeader("Last-Modified")
                    ).
                    build();
            }
        }
        
        return sshFile;
    }
    
    // @Override
    public SshFile getFile(SshFile baseDir, String file) {
        throw new UnsupportedOperationException();
    }
    
    public boolean deleteFile(String absolutePath) {
        Response response = protocol.service(
            URI.create(absolutePath), "DELETE", userName,
            Collections.<String,String>emptyMap(), null, null);
        
        return response.getStatus() == SC_NO_CONTENT;
    }
    
    public boolean createDirectory(String absolutePath) {
        Response response = protocol.service(
            URI.create(absolutePath), "MKCOL", userName,
            Collections.<String,String>emptyMap(), null, null);
        
        return response.getStatus() == SC_CREATED;
    }
    
    public List<SshFile> getDirectoryContents(String absolutePath) {
        List<SshFile> directoryContents = new ArrayList<SshFile>();
        
        try {
            InputStream responseXml = propFindResponseXmlBody(absolutePath, 1);
            if (responseXml != null) {
                directoryContents.addAll(
                    xmlToFiles(absolutePath, responseXml, absolutePath)
                );
            }
        } catch (DavProcessingException e) {
            directoryContents.add(
                new ClassPathResourceSshFile(
                    absolutePath + "/README.txt", "README.txt"
                )
            );
        }
        
        return directoryContents;
    }
    
    public OutputStream getFileOutputStream(final String absolutePath)
            throws IOException {
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
                protocol.service(
                    URI.create(absolutePath), "PUT", userName,
                    Collections.<String,String>emptyMap(),
                    inputBuffer, null);
            }
        }).start();
        
        return os;
    }
    
    public InputStream getFileInputStream(final String absolutePath)
            throws IOException {
        PipedInputStream is = new PipedInputStream();
        final OutputStream pos = new PipedOutputStream(is);
        
        new Thread(new Runnable() {
            public void run() {
                OutputBuffer outputBuffer = new OutputBuffer() {
                    private long bytesWritten = 0;
                    
                    public int doWrite(
                            ByteChunk chunk, Response response)
                            throws IOException {
                        pos.write(chunk.getBuffer(),
                            chunk.getStart(), chunk.getLength());
                        int len = chunk.getLength();
                        bytesWritten += len;
                        return len;
                    }
                    
                    public long getBytesWritten() {
                        return bytesWritten;
                    }
                };
                
                try {
                    protocol.service(
                        URI.create(absolutePath), Constants.GET, userName,
                        Collections.<String,String>emptyMap(),
                        null, outputBuffer);
                } finally {
                    try {
                        pos.close();
                    } catch (IOException e) {
                        // TODO user proper logger
                        e.printStackTrace();
                        // do nothing
                    }
                }
            }
        }).start();
        
        return is;
    }
}