package my.edu.clhs.tomcat.coyote;

import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_OK;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URL;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.coyote.InputBuffer;
import org.apache.coyote.OutputBuffer;
import org.apache.coyote.Request;
import org.apache.coyote.RequestInfo;
import org.apache.coyote.Response;
import org.apache.coyote.http11.Constants;
import org.apache.sshd.server.SshFile;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.http.MimeHeaders;

class SftpServletFile implements SshFile {
    private static final int SC_MULTI_STATUS = 207;
    private static final DateFormat HTTP_DATE_FORMAT =
        new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
    private static final String README_FILENAME = "README.txt";
    private static final URL README_FILE_URL =
        SftpServletFile.class.getResource(README_FILENAME);
    private static final int README_FILE_SIZE;
    static {
        try {
            // TODO does this need closing?
            README_FILE_SIZE = README_FILE_URL.
                openConnection().getContentLength();
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
    
    
    private final SftpProtocol protocol;
    private final String userName;
    private final int httpStatus;
    private final MimeHeaders httpHeaders;
    // TODO make these final
    private long contentLength;
    private Boolean isDirectory;
    private Boolean isFile;
    private List<Map<String,String>> sshFilesProps;
    
    /**
     * Submit a request to be serviced by Coyote.
     * 
     * @param path request path.
     * @param method request method.
     * @param headers request headers.
     * @param inputBuffer PUT/POST contents.
     * @param outputBuffer response contents.
     * @return response objects (containing header information).
     */
    private Response service(
            String method, Map<String,String> headers,
            InputBuffer inputBuffer, OutputBuffer outputBuffer) {
        Request request = new Request();
        request.setInputBuffer(inputBuffer);
        
        Response response = new Response();
        response.setOutputBuffer(outputBuffer);
        
        RequestInfo rp = request.getRequestProcessor();
        rp.setStage(org.apache.coyote.Constants.STAGE_PREPARE);
        request.scheme().setString("sftp");
        request.serverName().setString(protocol.getEndpoint().getHost());
        request.protocol().setString("SFTP");
        request.method().setString(method);
        request.requestURI().setString(getAbsolutePath());
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
            protocol.getAdapter().service(request, response);
        } catch (Exception e) {
            throw new RuntimeException(
                "An error occurred when " +
                userName + " requested " + path,
                e);
        }
        
        rp.setStage(org.apache.coyote.Constants.STAGE_ENDED);
        return response;
    }
    
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
        return service(
            method, Collections.<String,String>emptyMap(),
            inputBuffer, outputBuffer);
    }
    // TODO revisit/rewrite
    private Response processWebDav() {
        InputBuffer propFindBuf = new InputBuffer() {
            public int doRead(ByteChunk chunk, Request request)
                    throws IOException {
                // TODO move out
                String propFindBody =
                    "<?xml version=\"1.0\" encoding=\"utf-8\" ?>" +
                    "<D:propfind xmlns:D=\"DAV:\">" +
                        "<D:allprop/>" +
                    "</D:propfind>";
                int len = propFindBody.length();
                
                chunk.setBytes(
                    propFindBody.getBytes(chunk.getCharset()), 0, len);
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
        propFindHeaders.put("Depth", "1");
        Response response = service(
            "PROPFIND", propFindHeaders, propFindBuf, webDavBuf);
        // TODO hack since it's hard to get Spring to return 207
        int status = response.getStatus();
        if (status == SC_OK || status == SC_MULTI_STATUS) { // TODO consider throwing exception otherwise
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setValidating(true);
            WebDavSaxHandler handler = new WebDavSaxHandler();
            try {
                SAXParser parser = factory.newSAXParser();
                parser.parse(
                    new ByteArrayInputStream(
                    webDavChunk.getBuffer()), handler);
                List<Map<String,String>> parsedFilesProps =
                    handler.getFiles();
                for (Iterator<Map<String,String>> iter =
                        parsedFilesProps.iterator(); iter.hasNext();) {
                    Map<String,String> fileProps = iter.next();
                    
                    if (path.equals(new File(fileProps.get("path")))) {
                        iter.remove();
                        String len = fileProps.get("contentLength");
                        if (len != null) {
                            contentLength = Long.parseLong(len);
                        }
                        isDirectory = fileProps.containsKey("isDirectory");
                        isFile = fileProps.containsKey("isFile");
                    }
                }
                sshFilesProps =
                    Collections.unmodifiableList(parsedFilesProps);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        
        return response;
    }
    
    public SftpServletFile(
            SftpProtocol protocol, String path, String userName) {
        this.protocol = protocol;
        this.userName = userName;
        if (path == null || path.equals(".")) {
            this.path = new File("/");
        } else {
            this.path = new File(new File("/"), path);
        }
        
        Response response = null;
        try {
            response = processWebDav();
        } catch (Exception e) { // throw specific exception
            // TODO WTF was going on here?
        }
        int status = response != null ? response.getStatus() : -1;
        if (status == SC_OK || status == SC_MULTI_STATUS) {
            // TODO is this right? is it OK to return 207?
            httpStatus = status;
            httpHeaders = response.getMimeHeaders();
        } else {
            if (!path.endsWith("/")) {
                OutputBuffer outputBuffer = new OutputBuffer() {
                    public int doWrite(ByteChunk chunk, Response response)
                            throws IOException {
                        return chunk.getLength();
                    }
                };
                
                response = service(Constants.HEAD, null, outputBuffer);
                
                httpStatus = response.getStatus();
                contentLength = response.getContentLengthLong();
                httpHeaders = response.getMimeHeaders();
            } else {
                httpStatus = SC_NOT_FOUND;
                contentLength = 0;
                httpHeaders = new MimeHeaders();
            }
        }
    }
    
    private final File path;
    
    // @Override
    public String getAbsolutePath() {
        try {
            return path.getCanonicalPath();
        } catch (IOException e) {
            // TODO ensure that this never gets called (by calling in constructor and assigning to variable)
            throw new RuntimeException("bad path");
        }
    }
    
    // @Override
    public String getName() {
        return path.getName();
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
        throw new UnsupportedOperationException();
    }
    
    // @Override
    public boolean mkdir() {
        throw new UnsupportedOperationException();
    }
    
    // @Override
    public List<SshFile> listSshFiles() {
        List<SshFile> sshFiles = new ArrayList<SshFile>();
        String absolutePath = getAbsolutePath();
        
        sshFiles.add(
            new SftpServletFile(this.protocol, absolutePath + "/.", userName));
        sshFiles.add(
            new SftpServletFile(this.protocol, absolutePath + "/..", userName));
        if (sshFilesProps != null) {
            for (Map<String,String> fileProps : sshFilesProps) {
                sshFiles.add(
                    new SftpServletFile(this.protocol, fileProps.get("path"), userName));
            }
        } else {
            sshFiles.add(
                new SftpServletFile(
                    this.protocol, absolutePath + "/" + README_FILENAME, userName));
        }
        
        return Collections.unmodifiableList(sshFiles);
    }
    
    // @Override
    public boolean isWritable() {
        return false;
    }
    
    public boolean isExecutable() {
        return isDirectory();
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
        return isFile != null ?
            isFile :
            httpStatus == SC_OK || README_FILENAME.equals(getName());
    }
    
    // @Override
    public boolean isDirectory() {
        return isDirectory != null ? isDirectory : !isFile();
    }
    
    // @Override
    public void handleClose() throws IOException {
        // do nothing
    }
    
    // @Override
    public long getSize() {
        long size = 0;
        
        if (httpStatus == SC_OK || httpStatus == SC_MULTI_STATUS) {
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
        throw new UnsupportedOperationException("Pending");
    }
    
    public boolean create() throws IOException {
        return true;
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
        
        if (httpStatus == SC_OK || httpStatus == SC_MULTI_STATUS) {
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
            // TODO make sure this is getting closed.
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
    
    @Override
    public String toString() {
        return path.toString();
    }
}