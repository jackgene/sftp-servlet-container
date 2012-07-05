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

import static javax.servlet.http.HttpServletResponse.SC_OK;

import java.io.ByteArrayInputStream;
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
    // TODO revisit exception handling
    private InputStream propFindResponseXmlBody(String absolutePath, int depth)
            throws SftpServletException {
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
            public int doWrite(ByteChunk chunk, Response response)
                    throws IOException {
                webDavChunk.append(chunk);
                return chunk.getLength();
            }
        };
        Map<String,String> propFindHeaders = new HashMap<String,String>();
        propFindHeaders.put("Depth", Integer.toString(depth));
        Response response = protocol.service(
            URI.create(absolutePath), "PROPFIND", userName, propFindHeaders,
            propFindBuf, webDavBuf);
        int status = response.getStatus();
        // Technically, only SC_MULTI_STATUS (207) is valid, however it is
        // almost impossible to get some web frameworks (including Spring MVC)
        // to return 207.
        if (status != SC_MULTI_STATUS && status != SC_OK) {
            throw new SftpServletException(
                "DAV not supported by web application.");
        }
        
        return new ByteArrayInputStream(webDavChunk.getBuffer());
    }
    
    private List<? extends SshFile> xmlToFiles(
            InputStream multiStatusXml, String pathToDiscard) {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(true);
        WebDavSaxHandler handler = new WebDavSaxHandler(this, pathToDiscard);
        
        try {
            SAXParser parser = factory.newSAXParser();
            parser.parse(multiStatusXml, handler);
        } catch (SAXException e) {
            throw new RuntimeException(e);
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        
        return handler.getFiles();
    }
    
    // @Override
    public SshFile getFile(String path) {
        SshFile sshFile = null;
        final String absolutePath =
            (path == null || path.equals(".")) ? "/" : path;
        
        try {
            List<? extends SshFile> files =
                xmlToFiles(propFindResponseXmlBody(absolutePath, 0), null);
            
            if (files.size() != 1) {
                throw new RuntimeException("expected 1 result");
            }
            sshFile = files.get(0);
        } catch (SftpServletException e) { // TODO more specific exception? (e.g., DavNotSupportedException)
            ServletResourceSshFile.Builder fileBuilder =
                new ServletResourceSshFile.Builder(this).path(absolutePath);
            if (!absolutePath.endsWith("/")) {
                OutputBuffer outputBuffer = new OutputBuffer() {
                    public int doWrite(ByteChunk chunk, Response response)
                            throws IOException {
                        return chunk.getLength();
                    }
                };
                
                Response response = protocol.service(
                    URI.create(absolutePath), Constants.HEAD, userName,
                    Collections.<String,String>emptyMap(),
                    null, outputBuffer);
                
                // TODO should a file also be a directory?
                boolean isFile = response.getStatus() == SC_OK;
                // TODO this whole thing is "money", remove
                if (!isFile && absolutePath.endsWith("/README.txt")) {
                    return new ClassPathResourceSshFile(
                        absolutePath, "README.txt");
                }
                fileBuilder.
                    isFile(isFile).
                    isDirectory(!isFile).
                    size(response.getContentLengthLong()).
                    lastModifiedRfc1123(
                        response.getMimeHeaders().getHeader("Last-Modified"));
            } else {
                fileBuilder.
                    isFile(false).
                    isDirectory(true).
                    size(0);
            }
            // TODO readme support must go here.
            sshFile = fileBuilder.build();
        }
        
        return sshFile;
    }
    
    // @Override
    public SshFile getFile(SshFile baseDir, String file) {
        throw new UnsupportedOperationException();
    }
    
    public List<SshFile> getDirectoryContents(String absolutePath) {
        List<SshFile> directoryContents = new ArrayList<SshFile>();
        
        // First, add "." and ".." directories.
        directoryContents.add(
            new ServletResourceSshFile.Builder(this).
                path(absolutePath + "/.").
                isDirectory(true).
                build()
        );
        directoryContents.add(
            new ServletResourceSshFile.Builder(this).
                path(absolutePath + "/..").
                isDirectory(true).
                build()
        );
        try {
            // TODO try and make "." and ".." display the correct metadata (date)
            directoryContents.addAll(
                xmlToFiles(
                    propFindResponseXmlBody(absolutePath, 1),
                    absolutePath)
            );
        } catch (SftpServletException e) { // TODO more specific exception? (e.g., DavNotSupportedException)
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
                OutputBuffer outputBuffer = new OutputBuffer() {
                    public int doWrite(
                            ByteChunk chunk, Response response)
                            throws IOException {
                        // Do nothing
                        return chunk.getLength();
                    }
                };
                
                protocol.service(
                    URI.create(absolutePath), "PUT", userName,
                    Collections.<String,String>emptyMap(),
                    inputBuffer, outputBuffer);
            }
        }).start();
        
        return os;
    }
    
    public InputStream getFileInputStream(final String absolutePath)
            throws IOException {
        InputStream is = new PipedInputStream();
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
                    protocol.service(
                        URI.create(absolutePath), Constants.GET, userName,
                        Collections.<String,String>emptyMap(),
                        null, outputBuffer);
                } finally {
                    try {
                        pos.close();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }).start();
        
        return is;
    }
}