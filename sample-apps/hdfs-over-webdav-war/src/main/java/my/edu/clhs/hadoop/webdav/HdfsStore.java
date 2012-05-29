/*
 * HdfsStore.java
 *
 * Copyright 2012 Jack Leow
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
package my.edu.clhs.hadoop.webdav;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import net.sf.webdav.ITransaction;
import net.sf.webdav.IWebdavStore;
import net.sf.webdav.StoredObject;
import net.sf.webdav.exceptions.AccessDeniedException;
import net.sf.webdav.exceptions.WebdavException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.security.AccessControlException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.collect.Lists;

/**
 * Hadoop Distribute File System to WebDAV adapter.
 * 
 * @author Jack Leow
 */
public class HdfsStore implements IWebdavStore {
    public static final String HDFS_ROOT_URI_JNDI_NAME = "hdfsRootUri";
    private static final Logger log =
        LoggerFactory.getLogger(HdfsStore.class);
    
    private final URI HDFS_URI;
    private final FileSystem hdfs;
    
    public HdfsStore(File root) {
        try {
            final Context namingCtx =
                (Context)new InitialContext().lookup("java:comp/env");
            final String hdfsRootUri =
                (String)namingCtx.lookup(HDFS_ROOT_URI_JNDI_NAME);
            HDFS_URI = new URI(hdfsRootUri);
        } catch (NamingException e) {
            throw new RuntimeException(e);
        } catch (ClassCastException e) {
            throw new RuntimeException(
                HDFS_ROOT_URI_JNDI_NAME + " must be a java.lang.String", e);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        
        // TODO consider moving this to begin(), one per principal.
        try {
            hdfs = FileSystem.get(HDFS_URI, new Configuration());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    private static class HdfsSession implements ITransaction {
        public HdfsSession(Principal principal, FileSystem fileSystem) {
            this.principal = principal;
            this.fileSystem =fileSystem;
        }
        
        private final Principal principal;
        
        public Principal getPrincipal() {
            return principal;
        }
        
        private final FileSystem fileSystem;
        
        public FileSystem getFileSystem() {
            return fileSystem;
        }
        
        private List<Path> createdObjects = new LinkedList<Path>();
        
        public void addCreatedObject(Path uri) {
            createdObjects.add(0, uri);
        }
        
        private Map<Path,OutputStream> outputStreams =
            new HashMap<Path,OutputStream>();
        
        public OutputStream getOutputStream(Path path) {
            return outputStreams.get(path);
        }
        
        public void putOutputStream(Path path, OutputStream outputStream) {
            outputStreams.put(path, outputStream);
        }
        
        public void handleCommit() {
            for (OutputStream out : outputStreams.values()) {
                try {
                    out.close();
                } catch (IOException e) {
                    // TODO log warning
                }
            }
        }
        
        public void handleRollback() {
            for (OutputStream out : outputStreams.values()) {
                try {
                    out.close();
                } catch (IOException e) {
                    // TODO log warning
                }
            }
            for (Path uri : createdObjects) {
                try {
                    fileSystem.delete(uri, true);
                } catch(IOException e) {
                    // TODO Log warning.
                }
            }
        }
    }
    
    @Override
    public ITransaction begin(Principal principal) {
        if (principal != null)  {
            System.err.println("Hello " + principal.getName() + "-" + System.identityHashCode(principal) + "!");
        }
        ITransaction transaction = new HdfsSession(
            principal, hdfs
        );
        log.debug("begin()\t\t" + transaction.hashCode());
        return transaction;
    }
    
    @Override
    public void checkAuthentication(ITransaction transaction) {
        log.debug("checkAuthentication()\t\t" + transaction.hashCode());
        // Do nothing.
    }
    
    @Override
    public void commit(ITransaction transaction) {
        log.debug("commit()\t\t" + transaction.hashCode());
        HdfsSession session = (HdfsSession)transaction;
        
        session.handleCommit();
    }
    
    @Override
    public void rollback(ITransaction transaction) {
        log.debug("rollback()\t\t" + transaction.hashCode());
        HdfsSession session = (HdfsSession)transaction;
        
        session.handleRollback();
    }
    
    @Override
    public void createFolder(ITransaction transaction, String folderUri) {
        log.debug("createFolder()\t" + folderUri + "\t" + transaction.hashCode());
        Path path = new Path(folderUri);
        HdfsSession session = (HdfsSession)transaction;
        FileSystem hdfs = session.getFileSystem();
        
        session.addCreatedObject(path);
        try {
            hdfs.mkdirs(path);
        } catch (AccessControlException e) {
            throw new AccessDeniedException(e);
        } catch (IOException e) {
            throw new WebdavException(e);
        }
    }
    
    @Override
    public void createResource(ITransaction transaction, String resourceUri) {
        log.debug("createResource()\t" + resourceUri + "\t" + transaction.hashCode());
        Path path = new Path(resourceUri);
        HdfsSession session = (HdfsSession)transaction;
        FileSystem hdfs = session.getFileSystem();
        
        session.addCreatedObject(path);
        try {
            OutputStream out = hdfs.create(path);
            session.putOutputStream(path, out);
        } catch (AccessControlException e) {
            throw new AccessDeniedException(e);
        } catch (IOException e) {
            throw new WebdavException(e);
        }
    }
    
    public InputStream getResourceContent(
            ITransaction transaction, String uri) {
        log.debug("getResourceContent()\t" + uri + "\t" + transaction.hashCode());
        Path path = new Path(uri);
        FileSystem hdfs = ((HdfsSession)transaction).getFileSystem();
        
        try {
            return hdfs.open(path);
        } catch (AccessControlException e) {
            throw new AccessDeniedException(e);
        } catch (IOException e) {
            throw new WebdavException(e);
        }
    }
    
    @Override
    public long setResourceContent(
            ITransaction transaction, String resourceUri,
            InputStream content, String contentType, String characterEncoding) {
        log.debug("setResourceContent()\t" + resourceUri + "\t" + transaction.hashCode());
        long numBytesWritten = 0;
        Path path = new Path(resourceUri);
        HdfsSession session = (HdfsSession)transaction;
        FileSystem hdfs = session.getFileSystem();
        
        OutputStream out = session.getOutputStream(path);
        try {
            if (out == null) {
                out = hdfs.create(path, true);
            }
            
            int read;
            byte[] buf = new byte[64*1024];

            while ((read = content.read(buf, 0, buf.length)) != -1) {
                out.write(buf, 0, read);
                numBytesWritten += read;
            }
        } catch (AccessControlException e) {
            throw new AccessDeniedException(e);
        } catch (Exception e) {
            throw new WebdavException(e);
        } finally {
            try {
                if (out != null) out.close();
            } catch (IOException e) {
                // oh well
            }
        }
        
        return numBytesWritten;
    }
    
    @Override
    public String[] getChildrenNames(
            ITransaction transaction, String folderUri) {
        log.debug("getChildrenNames()\t" + folderUri + "\t" + transaction.hashCode());
        try {
            FileSystem fs = ((HdfsSession)transaction).getFileSystem();
            List<FileStatus> fileStatuses =
                Arrays.asList(fs.listStatus(new Path(folderUri)));
            List<String> names = Lists.transform(fileStatuses,
                new Function<FileStatus, String>() {
                    @Override
                    public String apply(FileStatus in) {
                        return in.getPath().getName();
                    }
                }
            );
            return names.toArray(new String[names.size()]);
        } catch (IOException e) {
            throw new WebdavException(e);
        }
    }
    
    @Override
    public long getResourceLength(ITransaction transaction, String uri) {
        log.debug("getResourceLength()\t" + uri + "\t" + transaction.hashCode());
        Path path = new Path(uri);
        FileSystem hdfs = ((HdfsSession)transaction).getFileSystem();
        
        try {
            FileStatus fileStatus = hdfs.getFileStatus(path);
            
            return fileStatus.getLen();
        } catch (IOException e) {
            throw new WebdavException(e);
        }
    }
    
    @Override
    public void removeObject(ITransaction transaction, String uri) {
        log.debug("removeObject()\t" + uri + "\t" + transaction.hashCode());
        Path path = new Path(uri);
        FileSystem hdfs = ((HdfsSession)transaction).getFileSystem();
        
        try {
            hdfs.delete(path, true);
        } catch (AccessControlException e) {
            throw new AccessDeniedException(e);
        } catch (IOException e) {
            throw new WebdavException(e);
        }
    }
    
    @Override
    public StoredObject getStoredObject(ITransaction transaction, String uri) {
        log.debug("getStoredObject()\t" + uri + "\t" + transaction.hashCode());
        StoredObject so = null;
        Path path = new Path(uri);
        FileSystem hdfs = ((HdfsSession)transaction).getFileSystem();
        
        try {
            if (hdfs.exists(path)) {
                FileStatus fileStatus = hdfs.getFileStatus(path);
                
                so = new StoredObject();
                so.setFolder(fileStatus.isDir());
                so.setLastModified(new Date(fileStatus.getModificationTime()));
                so.setCreationDate(new Date(fileStatus.getModificationTime()));
                so.setResourceLength(fileStatus.getLen());
            }
        } catch (IOException e) {
            throw new WebdavException(e);
        }
        
        return so;
    }
}
