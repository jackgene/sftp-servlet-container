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

import net.sf.webdav.ITransaction;
import net.sf.webdav.IWebdavStore;
import net.sf.webdav.StoredObject;
import net.sf.webdav.exceptions.AccessDeniedException;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;

/**
 * FilteredWebdavStore that filters and accepts only certain URIs to be
 * read and written to the underlying {@link IWebdavStore}.
 * 
 * The filter predicate is configurable, and resources rejected by the
 * predicate are stored in memory transiently if they are under a certain
 * size.
 * 
 * Resources that are rejected <em>and</em> exceeds the size limit will
 * cause {@link AccessDeniedException} to be thrown.
 *
 * @author Jack Leow
 */
public class FilteredWebdavStore implements IWebdavStore {
    /**
     * The default {@link #MAX_BANNED_FILE_SIZE} if none is specified.
     */
    public static final Long DEFAULT_MAX_BANNED_FILE_SIZE = 128*1024l;
    
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
    
    private final Long MAX_BANNED_FILE_SIZE;
    private final IWebdavStore delegate;
    private final Predicate<? super String> inclusionPredicate;
    
    public FilteredWebdavStore(
            IWebdavStore delegate, Predicate<? super String> inclusionPredicate,
            Long maxBannedFileSize) {
        if (delegate == null) {
            throw new NullPointerException(
                "delegate must be non-null.");
        }
        if (inclusionPredicate == null) {
            throw new NullPointerException(
                "inclusionPredicate must be non-null.");
        }
        if (maxBannedFileSize == null) {
            throw new NullPointerException(
                "maxBannedFileSize must be non-null.");
        }
        if (maxBannedFileSize < 0) {
            throw new IllegalArgumentException(
                "maxBannedFileSize must be positive.");
        }
        this.delegate = delegate;
        this.inclusionPredicate = inclusionPredicate;
        MAX_BANNED_FILE_SIZE = maxBannedFileSize;
    }
    
    public FilteredWebdavStore(IWebdavStore delegate) {
        this(delegate,
            Predicates.and(
                ImmutableSet.of(
                    LINUX_METADATA_FILE_EXCLUSIONS,
                    MACOS_METADATA_FILE_EXCLUSIONS,
                    WINDOWS_METADATA_FILE_EXCLUSIONS
                )
            ),
            DEFAULT_MAX_BANNED_FILE_SIZE
        );
    }
    
    @Override
    public ITransaction begin(Principal principal) {
        // TODO Auto-generated method stub
        return null;
    }
    
    @Override
    public void checkAuthentication(ITransaction transaction) {
        // TODO Auto-generated method stub
    
    }
    
    @Override
    public void commit(ITransaction transaction) {
        // TODO Auto-generated method stub
    
    }
    
    @Override
    public void rollback(ITransaction transaction) {
        // TODO Auto-generated method stub
    
    }
    
    @Override
    public void createFolder(ITransaction transaction, String folderUri) {
        // TODO Auto-generated method stub
    
    }
    
    @Override
    public void createResource(ITransaction transaction, String resourceUri) {
        // TODO Auto-generated method stub
    
    }
    
    @Override
    public InputStream getResourceContent(
            ITransaction transaction, String resourceUri) {
        // TODO Auto-generated method stub
        return null;
    }
    
    @Override
    public long setResourceContent(ITransaction transaction,
            String resourceUri, InputStream content, String contentType,
            String characterEncoding) {
        // TODO Auto-generated method stub
        return 0;
    }
    
    @Override
    public String[] getChildrenNames(
            ITransaction transaction, String folderUri) {
        // TODO Auto-generated method stub
        return null;
    }
    
    @Override
    public long getResourceLength(
            ITransaction transaction, String resourceUri) {
        // TODO Auto-generated method stub
        return 0;
    }
    
    @Override
    public void removeObject(ITransaction transaction, String uri) {
        // TODO Auto-generated method stub
    
    }
    
    @Override
    public StoredObject getStoredObject(ITransaction transaction, String uri) {
        // TODO Auto-generated method stub
        return null;
    }
}
