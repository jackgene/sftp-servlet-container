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

import java.io.InputStream;
import java.security.Principal;

import com.google.common.base.Predicate;

import net.sf.webdav.ITransaction;
import net.sf.webdav.IWebdavStore;
import net.sf.webdav.StoredObject;
import net.sf.webdav.exceptions.AccessDeniedException;

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
    private final Long MAX_BANNED_FILE_SIZE;
    private final IWebdavStore delegate;
    private final Predicate<String> filterPredicate;
    
    public FilteredWebdavStore(
            IWebdavStore delegate, Predicate<String> filterPredicate,
            Long maxBannedFileSize) {
        // TODO check for null
        this.delegate = delegate;
        this.filterPredicate = filterPredicate;
        MAX_BANNED_FILE_SIZE = maxBannedFileSize;
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
