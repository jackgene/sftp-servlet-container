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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.Principal;
import java.util.Date;

import net.sf.webdav.ITransaction;
import net.sf.webdav.IWebdavStore;
import net.sf.webdav.StoredObject;
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
        
        Date now = new Date();
        
        ExtendedStoredObject root = new ExtendedStoredObject();
        root.setFolder(true);
        root.setCreationDate(now);
        root.setLastModified(now);
        
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
        File parent = path.getParentFile();
        if (load(parent) == null) {
            throw new ObjectNotFoundException(
                parent.getPath() + " while attempting to create folder " +
                folderUri);
        }
        ExtendedStoredObject folder = new ExtendedStoredObject();
        folder.setFolder(true);
        store(path, folder);
    }
    
    @Override
    public void createResource(ITransaction transaction, String resourceUri) {
        log.trace("createResource(...," + resourceUri + ")");
        File path = toCanonicalFile(resourceUri);
        if (load(path) != null) {
            throw new ObjectAlreadyExistsException(resourceUri);
        }
        File parent = path.getParentFile();
        if (load(parent) == null) {
            throw new ObjectNotFoundException(
                parent.getPath() + " while attempting to create resource " +
                resourceUri);
        }
        ExtendedStoredObject resource = new ExtendedStoredObject();
        resource.setFolder(false);
        store(path, resource);
    }
    
    @Override
    public InputStream getResourceContent(
            ITransaction transaction, String resourceUri) {
        throw new UnsupportedOperationException("pending");
    }
    
    @Override
    public long setResourceContent(ITransaction transaction,
            String resourceUri, InputStream content, String contentType,
            String characterEncoding) {
        throw new UnsupportedOperationException("pending");
    }
    
    @Override
    public String[] getChildrenNames(ITransaction transaction, String folderUri) {
        throw new UnsupportedOperationException("pending");
    }
    
    @Override
    public long getResourceLength(ITransaction transaction, String resourceUri) {
        throw new UnsupportedOperationException("pending");
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