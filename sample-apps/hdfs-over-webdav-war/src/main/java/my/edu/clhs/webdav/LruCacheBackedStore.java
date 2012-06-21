/*
 * LruCacheBackedStore.java
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
package my.edu.clhs.webdav;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.Principal;
import java.util.Collections;
import java.util.Date;
import java.util.Set;
import java.util.TreeSet;

import net.sf.webdav.ITransaction;
import net.sf.webdav.IWebdavStore;
import net.sf.webdav.StoredObject;
import net.sf.webdav.exceptions.AccessDeniedException;
import net.sf.webdav.exceptions.ObjectAlreadyExistsException;
import net.sf.webdav.exceptions.ObjectNotFoundException;
import net.sf.webdav.exceptions.WebdavException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.Weigher;

/**
 * An LRU cache based {@link IWebdavStore}.
 * 
 * @author Jack Leow
 */
public class LruCacheBackedStore implements IWebdavStore {
    private static final Logger log =
        LoggerFactory.getLogger(LruCacheBackedStore.class);
    
    private static class ExtendedStoredObject extends StoredObject {
        public ExtendedStoredObject(boolean folder) {
            super.setFolder(folder);
            
            Date now = new Date();
            super.setCreationDate(now);
            super.setLastModified(now);
            if (folder) {
                contentBytes = null;
                childrenNames = Collections.emptySet();
            } else {
                contentBytes = new byte[0];
                childrenNames = null;
            }
        }
        
        public ExtendedStoredObject(
                boolean folder, Date creationDate, Date lastModified,
                byte[] contentBytes, Set<String> childrenNames) {
            super.setFolder(folder);
            super.setCreationDate(creationDate);
            super.setLastModified(lastModified);
            this.contentBytes = contentBytes;
            if (contentBytes != null) {
                setResourceLength(contentBytes.length);
            }
            this.childrenNames = childrenNames != null ?
                Collections.unmodifiableSet(childrenNames) : null;
        }
        
        private final byte[] contentBytes;
        
        public InputStream getContent() {
            return new ByteArrayInputStream(contentBytes);
        }
        
        private final Set<String> childrenNames;
        
        public String[] getChildrenNames() {
            return childrenNames != null ?
                childrenNames.toArray(new String[childrenNames.size()]) :
                null;
        }
        
        public ExtendedStoredObject addChildName(String childName) {
            Set<String> childrenNames =
                new TreeSet<String>(this.childrenNames);
            childrenNames.add(childName);
            
            return new ExtendedStoredObject(
                isFolder(),
                getCreationDate(),
                getLastModified(),
                contentBytes,
                childrenNames);
        }
        
        @Override
        public void setFolder(boolean folder) {
            throw new UnsupportedOperationException(
                "ExtendedStoredObject.folder is immutable");
        }
        
        @Override
        public void setCreationDate(Date date) {
            throw new UnsupportedOperationException(
                "ExtendedStoredObject.creationDate is immutable");
        }
        
        @Override
        public void setLastModified(Date date) {
            throw new UnsupportedOperationException(
                "ExtendedStoredObject.lastModified is immutable");
        }
    }
    
    private final long MAX_RESOURCE_LENGTH;
    private final Cache<File,ExtendedStoredObject> cache;
    
    public LruCacheBackedStore(Long maxResourceLength, Long maxStoreSpace) {
        log.trace("new(" + maxResourceLength + ", " + maxStoreSpace + ")");
        if (maxResourceLength == null) {
            throw new NullPointerException(
                "maxResourceLength must be non-null");
        }
        if (maxStoreSpace == null) {
            throw new NullPointerException(
                "maxStoreSpace must be non-null");
        }
        if (maxResourceLength < 0) {
            throw new IllegalArgumentException(
                "maxResourceLength must be non-negative");
        }
        if (maxStoreSpace < 0) {
            throw new IllegalArgumentException(
                "maxStoreSpace must be non-negative");
        }
        MAX_RESOURCE_LENGTH = maxResourceLength;
        
        ExtendedStoredObject root = new ExtendedStoredObject(true);
        
        cache = CacheBuilder.newBuilder().
            maximumWeight(maxStoreSpace).
            weigher(
                new Weigher<File,ExtendedStoredObject>() {
                    @Override
                    public int weigh(
                            File file, ExtendedStoredObject storedObject) {
                        long length = storedObject.getResourceLength();
                        
                        return length < Integer.MAX_VALUE ?
                            (int)length : Integer.MAX_VALUE;
                    }
                }
            ).
            build();
        cache.put(new File("/"), root);
    }
    
    @Override
    public ITransaction begin(Principal principal) {
        log.trace("begin(...)");
        // No op
        return null;
    }
    
    @Override
    public void checkAuthentication(ITransaction transaction) {
        log.trace("checkAuthentication(...)");
        // No op
    }
    
    @Override
    public void commit(ITransaction transaction) {
        log.trace("commit(...)");
        // No op
    }
    
    @Override
    public void rollback(ITransaction transaction) {
        log.trace("rollback(...)");
        // No op
    }
    
    private File toCanonicalFile(String uri) {
        try {
            return new File(uri).getCanonicalFile();
        } catch (IOException e) {
            throw new WebdavException("bad URI");
        }
    }
    
    private ExtendedStoredObject load(File path) {
        return cache.getIfPresent(path);
    }
    
    private void store(File path, ExtendedStoredObject object) {
        cache.put(path, object);
    }
    
    @Override
    public void createFolder(ITransaction transaction, String folderUri) {
        log.trace("createFolder(...," + folderUri + ")");
        File path = toCanonicalFile(folderUri);
        if (load(path) != null) {
            throw new ObjectAlreadyExistsException(folderUri);
        }
        File parentPath = path.getParentFile();
        ExtendedStoredObject parent = load(parentPath);
        if (parent == null) {
            throw new ObjectNotFoundException(
                parentPath.getPath() + ": while attempting to create folder " +
                folderUri);
        } else if (!parent.isFolder()) {
            throw new WebdavException(
                parentPath.getPath() + " must be a folder: " +
                "while attempting to create folder " + folderUri);
        }
        ExtendedStoredObject folder = new ExtendedStoredObject(true);
        store(path, folder);
        store(parentPath, parent.addChildName(path.getName()));
    }
    
    @Override
    public void createResource(ITransaction transaction, String resourceUri) {
        log.trace("createResource(...," + resourceUri + ")");
        File path = toCanonicalFile(resourceUri);
        if (load(path) != null) {
            throw new ObjectAlreadyExistsException(resourceUri);
        }
        File parentPath = path.getParentFile();
        ExtendedStoredObject parent = load(parentPath);
        if (parent == null) {
            throw new ObjectNotFoundException(
                parentPath.getPath() + ": while attempting to create folder " +
                resourceUri);
        } else if (!parent.isFolder()) {
            throw new WebdavException(
                parentPath.getPath() + " must be a folder: " +
                "while attempting to create folder " + resourceUri);
        }
        ExtendedStoredObject resource = new ExtendedStoredObject(false);
        store(path, resource);
        store(parentPath, parent.addChildName(path.getName()));
    }
    
    @Override
    public InputStream getResourceContent(
            ITransaction transaction, String resourceUri) {
        File path = toCanonicalFile(resourceUri);
        ExtendedStoredObject resource = load(path);
        if (resource == null) {
            throw new ObjectNotFoundException(resourceUri);
        }
        if (resource.isFolder()) {
            throw new WebdavException(resourceUri);
        }
        
        return resource.getContent();
    }
    
    @Override
    public long setResourceContent(ITransaction transaction,
            String resourceUri, InputStream content, String contentType,
            String characterEncoding) {
        long len = 0;
        File path = toCanonicalFile(resourceUri);
        ExtendedStoredObject resource = load(path);
        if (resource == null) {
            throw new ObjectNotFoundException(resourceUri);
        }
        if (resource.isFolder()) {
            throw new WebdavException(resourceUri);
        }
        byte[] buf = new byte[64*1024];
        InputStream buffered = new BufferedInputStream(content);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try {
            int read = buffered.read(buf);
            while (read != -1) {
                len += read;
                if (len > MAX_RESOURCE_LENGTH) {
                    throw new AccessDeniedException(resourceUri +
                        " - resource length must be under " +
                        MAX_RESOURCE_LENGTH);
                }
                os.write(buf, 0, read);
                read = buffered.read(buf);
            }
            byte[] contentBytes = os.toByteArray();
            store(path,
                new ExtendedStoredObject(
                    resource.isFolder(),
                    resource.getCreationDate(),
                    new Date(),
                    contentBytes, null)
            );
        } catch (IOException e) {
            throw new WebdavException(e);
        } finally {
            try {
                buffered.close();
            } catch (IOException e) {
                // Ignore
            }
        }
        
        return len;
    }
    
    @Override
    public String[] getChildrenNames(ITransaction transaction, String folderUri) {
        final File path = toCanonicalFile(folderUri);
        final ExtendedStoredObject folder = load(path);
        
        return folder != null ? folder.getChildrenNames() : null;
    }
    
    @Override
    public long getResourceLength(ITransaction transaction, String resourceUri) {
        final File path = toCanonicalFile(resourceUri);
        final ExtendedStoredObject resource = load(path);
        
        return resource != null ? resource.getResourceLength() : 0;
    }
    
    @Override
    public void removeObject(ITransaction transaction, String uri) {
        throw new UnsupportedOperationException("pending");
    }
    
    @Override
    public StoredObject getStoredObject(ITransaction transaction, String uri) {
        return cache.getIfPresent(toCanonicalFile(uri));
    }
}