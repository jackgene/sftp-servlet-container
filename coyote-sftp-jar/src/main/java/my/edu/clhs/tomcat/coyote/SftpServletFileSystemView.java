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
import java.io.UnsupportedEncodingException;
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
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.sshd.common.Session;
import org.apache.sshd.server.FileSystemView;
import org.apache.sshd.server.SshFile;
import org.apache.tomcat.util.buf.ByteChunk;
import org.xml.sax.SAXException;

class SftpServletFileSystemView implements FileSystemView {
    public static final String DEFAULT_FILE_OWNER = "nobody";
    public static final String HELP_FILENAME = "WHERE_ARE_MY_FILES.txt";
    
    private static final int SC_MULTI_STATUS = 207;
    private static final Log log = LogFactory.getLog(SftpServletFileSystemView.class);
    
    private final SftpProtocol protocol;
    private final Session session;
    
    SftpServletFileSystemView(SftpProtocol sftpProtocol, Session session) {
        protocol = sftpProtocol;
        this.session = session;
    }
    
    private static final byte[] PROPFIND_ALLPROP_BODY;
    static {
        try {
            PROPFIND_ALLPROP_BODY= (
                "<?xml version=\"1.0\" encoding=\"US-ASCII\" ?>\n" +
                "<D:propfind xmlns:D=\"DAV:\">" +
                    "<D:allprop/>" +
                "</D:propfind>"
            ).getBytes("US-ASCII");
        } catch (UnsupportedEncodingException e) {
            // This should never happen
            throw new ExceptionInInitializerError(e);
        }
    }
    
    private InputStream propFindResponseXmlBody(String absolutePath, int depth)
            throws DavProcessingException {
        InputBuffer propFindBuf = new InputBuffer() {
            private boolean read = false;
            
            // @Override
            public int doRead(ByteChunk chunk, Request request)
                    throws IOException {
                if (!read) {
                    int len = PROPFIND_ALLPROP_BODY.length;
                    
                    chunk.setBytes(PROPFIND_ALLPROP_BODY, 0, len);
                    read = true;
                    return len;
                } else {
                    return -1;
                }
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
            absolutePath, "PROPFIND", session, propFindHeaders,
            propFindBuf, webDavBuf);
        int status = response.getStatus();
        
        InputStream content;
        // Technically, only SC_MULTI_STATUS (207) is valid, however
        // it is almost impossible to get some web frameworks
        // (including Spring MVC) to return 207.
        // SC_NOT_FOUND is technically also valid, but for our purposes
        // we'd like to process it as if it's an invalid request.
        // TODO handle 302?
        if (status == SC_MULTI_STATUS || status == SC_OK) {
            content = new ByteArrayInputStream(
                webDavChunk.getBuffer(), webDavChunk.getOffset(), webDavChunk.getLength());
        } else if (status == SC_NOT_FOUND) {
            content = null;
        } else {
            throw new DavUnsupportedException(absolutePath, status);
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
        final String absolutePath;
        try {
            absolutePath =
                new File("/", (path == null || path.equals(".")) ? "/" : path).
                getCanonicalPath();
        } catch (IOException e) {
            throw new IOError(e);
        }
        
        try {
            // If DAV is supported use DAV response
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
                sshFile = new WebDAVServletResourceSshFile.Builder(this).
                    path(absolutePath).
                    doesExist(false).
                    build();
            }
        } catch (DavProcessingException e) {
            // If DAV isn't supported...
            if (!absolutePath.endsWith("/") && !absolutePath.endsWith("..")) {
                // If the the requested URI does not end with a /
                Response response = protocol.service(
                    absolutePath, Constants.HEAD, session, null, null, null);
                
                boolean isFile = response.getStatus() == SC_OK;
                if (!isFile && absolutePath.endsWith("/" + HELP_FILENAME)) {
                    // If the path name is HELP_FILENAME and
                    // is not a real resource
                    sshFile = new ClassPathResourceSshFile(
                        absolutePath, HELP_FILENAME);
                } else {
                    // If the path represents a real resource or
                    // it does not, but is not HELP_FILENAME
                    sshFile = new DefaultServletResourceSshFile(
                        this, absolutePath, !isFile,
                        response.getMimeHeaders().
                            getHeader("Last-Modified"),
                        response.getContentLengthLong()
                    );
                }
            } else {
                // If the the requested URI ends with a /
                sshFile = new DefaultServletResourceSshFile(
                    this, absolutePath, true
                );
            }
        }
        
        return sshFile;
    }
    
    // @Override
    public SshFile getFile(SshFile baseDir, String file) {
        return getFile(baseDir.getAbsolutePath() + "/" + file);
    }
    
    public boolean deleteFile(String absolutePath) {
        Response response = protocol.service(
            absolutePath, "DELETE", session, null, null, null);
        
        return response.getStatus() == SC_NO_CONTENT;
    }
    
    public boolean createDirectory(String absolutePath) {
        Response response = protocol.service(
            absolutePath, "MKCOL", session, null, null, null);
        
        return response.getStatus() == SC_CREATED;
    }
    
    public List<SshFile> getDirectoryContents(String absolutePath) {
        List<SshFile> directoryContents;
        
        try {
            InputStream responseXml = propFindResponseXmlBody(absolutePath, 1);
            if (responseXml != null) {
                directoryContents = Collections.unmodifiableList(
                    xmlToFiles(absolutePath, responseXml, absolutePath)
                );
            } else {
                directoryContents = Collections.emptyList();
            }
        } catch (DavProcessingException e) {
            log.debug(
                "PROPFIND failed while getting directory contents, " +
                "falling back to directory with help file.",
                e
            );
            directoryContents = Collections.<SshFile>singletonList(
                new ClassPathResourceSshFile(
                    absolutePath + "/" + HELP_FILENAME, HELP_FILENAME
                )
            );
        }
        
        return directoryContents;
    }
    
    public OutputStream getFileOutputStream(final String absolutePath)
            throws IOException {
        final PipedOutputStream os = new PipedOutputStream();
        final PipedInputStream is = new PipedInputStream(os);
        
        new Thread(new Runnable() {
            public void run() {
                try {
                    InputBuffer inputBuffer = new InputBuffer() {
                        public int doRead(ByteChunk chunk, Request request)
                                throws IOException {
                            byte[] buffer = new byte[8192];
                            int len = is.read(buffer);
                            chunk.setBytes(buffer, 0, len);
                            
                            return len;
                        }
                    };
                    protocol.service(
                        absolutePath, "PUT", session, null, inputBuffer, null);
                } finally {
                    try {
                        os.close();
                        is.close();
                    } catch (IOException e) {
                        throw new IOError(e);
                    }
                }
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
                        absolutePath, Constants.GET, session,
                        null, null, outputBuffer);
                } finally {
                    try {
                        pos.close();
                    } catch (IOException e) {
                        log.error("Unable to close PipedOutputStream", e);
                        // do nothing
                    }
                }
            }
        }).start();
        
        return is;
    }
}