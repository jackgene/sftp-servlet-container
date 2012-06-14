/*
 * FilteredWebdavStore.java
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
import java.io.InputStream;
import java.security.Principal;
import java.util.HashSet;
import java.util.Set;

import net.sf.webdav.ITransaction;
import net.sf.webdav.IWebdavStore;
import net.sf.webdav.LocalFileSystemStore;
import net.sf.webdav.StoredObject;
import net.sf.webdav.exceptions.AccessDeniedException;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;

/**
 * FilteredWebdavStore that filters and accepts only certain URIs to be
 * read and written to the primary underlying {@link IWebdavStore}.
 * 
 * The filter predicate is configurable, and resources rejected by the
 * predicate stored in a secondary, rejection {@link IWebdavStore} if
 * one is configured.
 * 
 * If no rejection store is configured, write operations to rejected
 * URIs will result in an {@link AccessDeniedException}.
 *
 * @author Jack Leow
 */
public class FilteredWebdavStore implements IWebdavStore {
    /**
     * Filter predicate that excludes Linux metadata files.
     * Since Linux does not create these files, this filter does not
     * exclude anything.
     */
    public static final Predicate<String> LINUX_METADATA_FILE_EXCLUSIONS =
        Predicates.alwaysTrue();
    
    /**
     * Filter predicate that excludes MacOS metadata files such as
     * .DS_Store and the ._.* files.
     */
    public static final Predicate<String> MACOS_METADATA_FILE_EXCLUSIONS =
        new Predicate<String>() {
            @Override
            public boolean apply(String uri) {
                String file = new File(uri).getName();
                
                return !file.equals(".DS_Store") && !file.startsWith("._.");
            }
        };
    
    /**
     * Filter predicate that excludes Windows metadata files such as
     * desktop.ini and Thumbs.db
     */
    public static final Predicate<String> WINDOWS_METADATA_FILE_EXCLUSIONS =
        new Predicate<String>() {
            @Override
            public boolean apply(String uri) {
                String file = new File(uri).getName();
                
                return !file.equals("desktop.ini") && !file.equals("Thumbs.db");
            }
        };
    
    private final IWebdavStore primaryStore;
    private final IWebdavStore rejectionStore;
    private final Predicate<? super String> inclusionPredicate;
    
    /**
     * Creates a new FilteredWebdavStore.
     * 
     * @param inclusionPredicate URIs that this predicate matches will
     *  be stored in the <tt>primaryStore</tt>; other URIs will be
     *  stored in the <tt>rejectionStore</tt>.
     * @param primaryStore the primary underlying store.
     * @param rejectionStore the rejection store.
     */
    public FilteredWebdavStore(
            Predicate<? super String> inclusionPredicate,
            IWebdavStore primaryStore,
            IWebdavStore rejectionStore) {
        if (inclusionPredicate == null) {
            throw new NullPointerException(
                "inclusionPredicate must be non-null.");
        }
        if (primaryStore == null) {
            throw new NullPointerException(
                "primaryStore must be non-null.");
        }
        if (rejectionStore == null) {
            throw new NullPointerException(
                "rejectionStore must be non-null.");
        }
        this.primaryStore = primaryStore;
        this.inclusionPredicate = inclusionPredicate;
        this.rejectionStore = rejectionStore;
    }
    
    private static IWebdavStore defaultRejectionStore() {
        File storeDir = new File(
            System.getProperty("java.io.tmpdir"),
            "FilteredWebdavStoreRejects");
        
        if (!storeDir.exists()) {
            storeDir.mkdirs();
        }
        
        return new LocalFileSystemStore(storeDir);
    }
    
    public FilteredWebdavStore(IWebdavStore primaryStore) {
        this(
            Predicates.and(
                ImmutableSet.of(
                    LINUX_METADATA_FILE_EXCLUSIONS,
                    MACOS_METADATA_FILE_EXCLUSIONS,
                    WINDOWS_METADATA_FILE_EXCLUSIONS
                )
            ),
            primaryStore,
            defaultRejectionStore()
        );
    }
    
    @Override
    public ITransaction begin(Principal principal) {
        return primaryStore.begin(principal);
    }
    
    @Override
    public void checkAuthentication(ITransaction transaction) {
        primaryStore.checkAuthentication(transaction);
    }
    
    @Override
    public void commit(ITransaction transaction) {
        primaryStore.commit(transaction);
    
    }
    
    @Override
    public void rollback(ITransaction transaction) {
        primaryStore.rollback(transaction);
    
    }
    
    private boolean acceptsUri(String uri) {
        return inclusionPredicate.apply(uri);
    }
    
    @Override
    public void createFolder(ITransaction transaction, String folderUri) {
        if (acceptsUri(folderUri)) {
            primaryStore.createFolder(transaction, folderUri);
        } else {
            rejectionStore.createFolder(transaction, folderUri);
        }
    
    }
    
    @Override
    public void createResource(ITransaction transaction, String resourceUri) {
        if (acceptsUri(resourceUri)) {
            primaryStore.createResource(transaction, resourceUri);
        } else {
            rejectionStore.createResource(transaction, resourceUri);
        }
    }
    
    @Override
    public InputStream getResourceContent(
            ITransaction transaction, String resourceUri) {
        return acceptsUri(resourceUri) ?
            primaryStore.getResourceContent(transaction, resourceUri) :
            rejectionStore.getResourceContent(transaction, resourceUri);
    }
    
    @Override
    public long setResourceContent(ITransaction transaction,
            String resourceUri, InputStream content, String contentType,
            String characterEncoding) {
        return acceptsUri(resourceUri) ?
            primaryStore.setResourceContent(
                transaction, resourceUri, content,
                contentType, characterEncoding) :
            rejectionStore.setResourceContent(
                transaction, resourceUri, content,
                contentType, characterEncoding);
    }
    
    @Override
    public String[] getChildrenNames(
            ITransaction transaction, String folderUri) {
        Set<String> combinedNames = new HashSet<String>();
        
        String[] names;
        names = primaryStore.getChildrenNames(transaction, folderUri);
        for (String name : names) {
            combinedNames.add(name);
        }
        names = rejectionStore.getChildrenNames(transaction, folderUri);
        for (String name : names) {
            combinedNames.add(name);
        }
        
        return combinedNames.toArray(new String[combinedNames.size()]);
    }
    
    @Override
    public long getResourceLength(
            ITransaction transaction, String resourceUri) {
        return acceptsUri(resourceUri) ?
            primaryStore.getResourceLength(transaction, resourceUri) :
            rejectionStore.getResourceLength(transaction, resourceUri);
    }
    
    @Override
    public void removeObject(ITransaction transaction, String uri) {
        if (acceptsUri(uri)) {
            primaryStore.removeObject(transaction, uri);
        } else {
            rejectionStore.removeObject(transaction, uri);
        }
    }
    
    @Override
    public StoredObject getStoredObject(ITransaction transaction, String uri) {
        return acceptsUri(uri) ?
            primaryStore.getStoredObject(transaction, uri) :
            rejectionStore.getStoredObject(transaction, uri);
    }
}
