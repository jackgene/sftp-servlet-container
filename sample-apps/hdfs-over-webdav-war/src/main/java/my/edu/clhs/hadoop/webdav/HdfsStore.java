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
import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import my.edu.clhs.webdav.FilteredWebdavStore;
import net.sf.webdav.ITransaction;
import net.sf.webdav.IWebdavStore;
import net.sf.webdav.StoredObject;
import net.sf.webdav.exceptions.AccessDeniedException;
import net.sf.webdav.exceptions.WebdavException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.security.AccessControlException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import com.google.common.collect.Lists;

/**
 * Hadoop Distribute File System to WebDAV adapter.
 * 
 * @author Jack Leow
 */
public class HdfsStore implements IWebdavStore {
    public static class Filtered extends FilteredWebdavStore {
        public Filtered(File root) {
            super(new HdfsStore(root));
        }
    }
    
    public static final String HDFS_ROOT_URI_JNDI_NAME = "hdfsRootUri";
    private static final Logger log =
        LoggerFactory.getLogger(HdfsStore.class);
    
    private final URI HDFS_URI;
    private final FileSystem guestFileSystem;
    
    public HdfsStore(File root) {
        try {
            final Context namingCtx =
                (Context)new InitialContext().lookup("java:comp/env");
            final String hdfsRootUri =
                (String)namingCtx.lookup(HDFS_ROOT_URI_JNDI_NAME);
            HDFS_URI = new URI(hdfsRootUri);
            guestFileSystem = FileSystem.get(HDFS_URI, EMPTY_CFG, "guest");
        } catch (NamingException e) {
            throw new RuntimeException(e);
        } catch (ClassCastException e) {
            throw new RuntimeException(
                HDFS_ROOT_URI_JNDI_NAME + " must be a java.lang.String", e);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
    
    private static final Configuration EMPTY_CFG = new Configuration();
    private class HdfsTransaction implements ITransaction {
        private final LoadingCache<String, FileSystem> fileSystems =
            CacheBuilder.newBuilder().
                maximumSize(16).
                removalListener(
                    new RemovalListener<String, FileSystem>() {
                        @Override
                        public void onRemoval(
                                RemovalNotification<String, FileSystem> evt) {
                            try {
                                evt.getValue().close();
                            } catch (IOException e) {
                                log.warn("Failed to close Filesystem", e);
                            }
                        }
                    }
                ).
                build(
                    CacheLoader.from(new Function<String, FileSystem>() {
                        
                        @Override
                        public FileSystem apply(String name) {
                            try {
                                return FileSystem.get(
                                    HDFS_URI, EMPTY_CFG, name);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    })
                );
        
        public HdfsTransaction(Principal principal) {
            this.principal = principal;
            try {
                this.fileSystem = principal != null ?
                    fileSystems.get(principal.getName()) : guestFileSystem;
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
        
        private final Principal principal;
        
        public Principal getPrincipal() {
            return principal;
        }
        
        private FileSystem fileSystem;
        
        public FileSystem getFileSystem() {
            return fileSystem;
        }
        
        private final List<String> operationHistory = new LinkedList<String>();
        
        public void pushOperation(String operation) {
            operationHistory.add(operation);
        }
        
        private final Map<InputStream, Throwable> inputStreams =
            new HashMap<InputStream, Throwable>();
        
        public void pushInputStream(InputStream is, Throwable stack) {
            inputStreams.put(is, stack);
        }
        
        private void closeUnclosedInputStreams() {
            for (Map.Entry<InputStream, Throwable> contentStackPair :
                    inputStreams.entrySet()) {
                Throwable stack = contentStackPair.getValue();
                
                try {
                    InputStream content = contentStackPair.getKey();
                    content.available();
                    // IOException not thrown, stream is still open.
                    log.warn("A client did not close an input stream.", stack);
                    content.close();
                } catch (IOException e) {
                    if (e.getMessage().contains("Stream closed")) {
                         // We want this.
                        log.trace(
                            "The input stream was closed by the client as " +
                            "desired.");
                    } else {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        
        public void handleCommit() {
            closeUnclosedInputStreams();
        }
        
        public void handleRollback() {
            if (log.isWarnEnabled()) {
                log.warn(
                    "Transaction rollbacks are not supported.\n" +
                    "The following operations will NOT be rolled back:\n" +
                    Joiner.on('\n').join(operationHistory));
            }
            closeUnclosedInputStreams();
        }
        
        @Override
        public String toString() {
            return "tx-" + System.identityHashCode(this);
        }
        
    }
    
    private Path resolvePath(String uri) {
        return new Path(uri == null || "".equals(uri) ? "/" : uri);
    }
    
    @Override
    public ITransaction begin(Principal principal) {
        ITransaction transaction = new HdfsTransaction(principal);
        log.trace("begin(" + principal + "): " + transaction);
        if (principal != null) {
            log.debug("Authenticating using principal: " + principal.getName());
        } else {
            log.debug("null principal, authenticating as guest.");
        }
        return transaction;
    }
    
    @Override
    public void checkAuthentication(ITransaction transaction) {
        log.trace("checkAuthentication(" + transaction + ")");
        // Do nothing.
    }
    
    public void checkWriteAccess(ITransaction transaction) {
        log.trace("checkWriteAccess(" + transaction + ")");
        if (transaction.getPrincipal() == null) {
            throw new AccessDeniedException("Must be authenticated to write.");
        }
    }
    
    @Override
    public void commit(ITransaction transaction) {
        log.trace("commit(" + transaction + ")");
        HdfsTransaction hdfsTx = (HdfsTransaction)transaction;
        
        hdfsTx.handleCommit();
    }
    
    @Override
    public void rollback(ITransaction transaction) {
        log.trace("rollback(" + transaction + ")");
        log.warn("Transaction is being rolled back.");
        HdfsTransaction hdfsTx = (HdfsTransaction)transaction;
        
        hdfsTx.handleRollback();
    }
    
    @Override
    public void createFolder(ITransaction transaction, String folderUri) {
        log.trace("createFolder(" + transaction + "," + folderUri + ")");
        checkWriteAccess(transaction);
        Path path = resolvePath(folderUri);
        HdfsTransaction hdfsTx = (HdfsTransaction)transaction;
        FileSystem hdfs = hdfsTx.getFileSystem();
        
        try {
            hdfs.mkdirs(path);
        } catch (AccessControlException e) {
            throw new AccessDeniedException(e);
        } catch (IOException e) {
            throw new WebdavException(e);
        }
        hdfsTx.pushOperation("createFolder(" + folderUri + ")");
    }
    
    @Override
    public void createResource(ITransaction transaction, String resourceUri) {
        log.trace("createResource(" + transaction + "," + resourceUri + ")");
        checkWriteAccess(transaction);
        Path path = resolvePath(resourceUri);
        HdfsTransaction hdfsTx = (HdfsTransaction)transaction;
        FileSystem hdfs = hdfsTx.getFileSystem();
        
        try {
            hdfs.createNewFile(path);
        } catch (AccessControlException e) {
            throw new AccessDeniedException(e);
        } catch (IOException e) {
            throw new WebdavException(e);
        }
        hdfsTx.pushOperation("createResource(" + resourceUri + ")");
    }
    
    public InputStream getResourceContent(
            ITransaction transaction, String uri) {
        log.trace("getResourceContent(" + transaction + "," + uri + ")");
        Path path = resolvePath(uri);
        HdfsTransaction hdfsTx = (HdfsTransaction)transaction;
        FileSystem hdfs = hdfsTx.getFileSystem();
        
        try {
            InputStream content = hdfs.open(path);
            hdfsTx.pushInputStream(content, new Throwable("uri=" + uri));
            
            return content;
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
        log.trace("setResourceContent(" +
            transaction + "," + resourceUri + ",...)");
        long numBytesWritten = 0;
        Path path = resolvePath(resourceUri);
        HdfsTransaction hdfsTx = (HdfsTransaction)transaction;
        FileSystem hdfs = hdfsTx.getFileSystem();
        
        FSDataOutputStream out = null;
        try {
            out = hdfs.create(path, true);
            
            int read;
            byte[] buf = new byte[64*1024];
            int counter = 0;
            while ((read = content.read(buf)) != -1) {
                out.write(buf, 0, read);
                numBytesWritten += read;
                if (log.isTraceEnabled()) {
                    if (++counter % 1024 == 0) {
                        log.trace("Writing file " + resourceUri + ":\n" +
                            "chunk # " + counter + "\n" +
                            "size of current chunk: " + read + "\n" +
                            "kilobytes written so far: " +
                            (numBytesWritten / 1024));
                    }
                }
            }
        } catch (AccessControlException e) {
            throw new AccessDeniedException(e);
        } catch (IOException e) {
            throw new WebdavException(e);
        } finally {
            try {
                if (out != null) out.close();
            } catch (IOException e) {
                log.warn("Failed to close output stream.", e);
            }
            try {
                content.close();
            } catch (IOException e) {
                log.warn("Failed to close input stream.", e);
            }
        }
        hdfsTx.pushOperation("setResourceContent(" + resourceUri + ")");
        
        return numBytesWritten;
    }
    
    @Override
    public String[] getChildrenNames(
            ITransaction transaction, String folderUri) {
        log.trace("getChildrenNames(" + transaction + "," + folderUri + ")");
        try {
            FileSystem fs = ((HdfsTransaction)transaction).getFileSystem();
            List<FileStatus> fileStatuses =
                Arrays.asList(fs.listStatus(resolvePath(folderUri)));
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
        log.trace("getResourceLength(" + transaction + "," + uri + ")");
        Path path = resolvePath(uri);
        FileSystem hdfs = ((HdfsTransaction)transaction).getFileSystem();
        
        try {
            FileStatus fileStatus = hdfs.getFileStatus(path);
            
            return fileStatus.getLen();
        } catch (IOException e) {
            throw new WebdavException(e);
        }
    }
    
    @Override
    public void removeObject(ITransaction transaction, String uri) {
        log.trace("removeObject(" + transaction + "," + uri + ")");
        checkWriteAccess(transaction);
        Path path = resolvePath(uri);
        HdfsTransaction hdfsTx = (HdfsTransaction)transaction;
        FileSystem hdfs = hdfsTx.getFileSystem();
        
        try {
            hdfs.delete(path, true);
        } catch (AccessControlException e) {
            throw new AccessDeniedException(e);
        } catch (IOException e) {
            throw new WebdavException(e);
        }
        hdfsTx.pushOperation("removeObject(" + uri + ")");
    }
    
    @Override
    public StoredObject getStoredObject(ITransaction transaction, String uri) {
        log.trace("getStoredObject(" + transaction + "," + uri + ")");
        StoredObject so = null;
        Path path = resolvePath(uri);
        FileSystem hdfs = ((HdfsTransaction)transaction).getFileSystem();
        
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
