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

import java.io.InputStream;
import java.net.URI;
import java.security.Principal;

import net.sf.webdav.ITransaction;
import net.sf.webdav.IWebdavStore;
import net.sf.webdav.StoredObject;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.Weigher;

/**
 * An LRU cache based {@link IWebdavStore}.
 * 
 * @author Jack Leow
 */
public class LruCacheBackedStore implements IWebdavStore {
    private static class ExtendedStoredObject extends StoredObject {
    }
    
    private final long MAX_RESOURCE_LENGTH;
    private final Cache<URI,ExtendedStoredObject> cache;
    
    public LruCacheBackedStore(Long maxResourceLength, Long maxStoreSpace) {
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
        cache = CacheBuilder.newBuilder().
            maximumWeight(maxStoreSpace).
            weigher(
                new Weigher<URI,ExtendedStoredObject>() {
                    @Override
                    public int weigh(
                            URI uri, ExtendedStoredObject storedObject) {
                        long length = storedObject.getResourceLength();
                        
                        return length < Integer.MAX_VALUE ?
                            (int)length : Integer.MAX_VALUE;
                    }
                }
            ).
            build();
    }
    
    @Override
    public ITransaction begin(Principal principal) {
        throw new UnsupportedOperationException("pending");
    }
    
    @Override
    public void checkAuthentication(ITransaction transaction) {
        throw new UnsupportedOperationException("pending");
    }
    
    @Override
    public void commit(ITransaction transaction) {
        throw new UnsupportedOperationException("pending");
    }
    
    @Override
    public void rollback(ITransaction transaction) {
        throw new UnsupportedOperationException("pending");
    }
    
    @Override
    public void createFolder(ITransaction transaction, String folderUri) {
        throw new UnsupportedOperationException("pending");
    }
    
    @Override
    public void createResource(ITransaction transaction, String resourceUri) {
        throw new UnsupportedOperationException("pending");
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
        throw new UnsupportedOperationException("pending");
    }
}