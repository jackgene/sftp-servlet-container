/*
 * LruCacheBackedStoreSpecs.scala
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
package my.edu.clhs.webdav

import org.junit.runner.RunWith
import org.scalatest.WordSpec
import org.scalatest.junit.JUnitRunner
import org.scalatest.junit.MustMatchersForJUnit
import net.sf.webdav.exceptions.ObjectAlreadyExistsException
import net.sf.webdav.exceptions.ObjectNotFoundException
import net.sf.webdav.exceptions.WebdavException

/**
 * {@link LruCacheBackedStore} specifications.
 * 
 * @author Jack Leow
 */
@RunWith(classOf[JUnitRunner])
class LruCacheBackedStoreSpecs extends WordSpec with MustMatchersForJUnit {
  "A LruCacheBackedStore" must {
    "complain when created without a maxResourceLength." in {
      evaluating {
        new LruCacheBackedStore(null, 0L)
      } must produce[NullPointerException]
    }
    
    "complain when created without a maxStoreSpace." in {
      evaluating {
        new LruCacheBackedStore(0L, null)
      } must produce[NullPointerException]
    }
    
    "complain when created with a negative maxResourceLength." in {
      evaluating {
        new LruCacheBackedStore(-1L, 0L)
      } must produce[IllegalArgumentException]
    }
    
    "complain when created with a negative maxStoreSpace." in {
      evaluating {
        new LruCacheBackedStore(0L, -1L)
      } must produce[IllegalArgumentException]
    }
  }
  
  "A properly initialized LruCacheBackedStore" must {
    val instance = new LruCacheBackedStore(2L, 4L);
    instance.createFolder(null, "/folder/");
    instance.createResource(null, "/resource");
    
    "allow a folder to be created." in {
      // Input
      val testUri = "/another-folder/"
      
      // Test
      instance.createFolder(null, testUri)
      
      // Verify
      instance.getStoredObject(null, testUri) must not be (null)
    }
    
    "prevent the creation of a duplicate folder." in {
      // Input
      val testUri = "/folder/"
      
      // Test & Verify
      evaluating {
        instance.createFolder(null, testUri)
      } must produce[ObjectAlreadyExistsException]
    }
    
    "prevent the creation of a folder over an existing resource." in {
      // Input
      val testUri = "/resource"
      
      // Test & Verify
      evaluating {
        instance.createFolder(null, testUri)
      } must produce[ObjectAlreadyExistsException]
    }
    
    "prevent the creation of an orphaned folder." in {
      // Input
      val testUri = "/missing/folder/"
      
      // Test & Verify
      evaluating {
        instance.createFolder(null, testUri)
      } must produce[ObjectNotFoundException]
    }
    
    "allow a resource to be created." in {
      // Input
      val testUri = "/another-resource"
      
      // Test
      instance.createResource(null, testUri)
      
      // Verify
      instance.getStoredObject(null, testUri) must not be (null)
    }
    
    "prevent the creation of a duplicate resource." in {
      // Input
      val testUri = "/resource"
      
      // Test & Verify
      evaluating {
        instance.createResource(null, testUri)
      } must produce[ObjectAlreadyExistsException]
    }
    
    "prevent the creation of a resource over an existing folder." in {
      // Input
      val testUri = "/folder"
      
      // Test & Verify
      evaluating {
        instance.createResource(null, testUri)
      } must produce[ObjectAlreadyExistsException]
    }
    
    "prevent the creation of an orphaned resource." in {
      // Input
      val testUri = "/missing/resource"
      
      // Test & Verify
      evaluating {
        instance.createResource(null, testUri)
      } must produce[ObjectNotFoundException]
    }
    
    "allow resource contents to be read." in {
      // Input
      val testUri = "/resource"
      
      // Test
      val actualContent = instance.getResourceContent(null, testUri);
      
      // Verify
      actualContent.read() must equal (-1) // Empty content
    }
    
    "complain when reading the contents of a folder." in {
      // Input
      val testUri = "/folder"
      
      // Test & Verify
      evaluating {
        instance.getResourceContent(null, testUri)
      } must produce[WebdavException]
    }
    
    "complain when reading the contents of a missing resource." in {
      // Input
      val testUri = "/missing"
      
      // Test & Verify
      evaluating {
        instance.getResourceContent(null, testUri)
      } must produce[ObjectNotFoundException]
    }
    
    "allow content to be written to a resource." is (pending)
    
    "prevent the writing of content to a non-existent resource." is (pending)
    
    "prevent the writing of content that is too long." is (pending)
    
    "allow folder children names to be listed." is (pending)
    
    "return an empty array when listing the chilren of an empty folder." is (pending)
    
    "return null when listing the chilren of a resource." is (pending)
    
    "return null when listing the chilren of a missing folder." is (pending)
    
    "always have a root folder for listing children." is (pending)
    
    "allow resource length to be accessed." is (pending)
    
    "allow folder legnth to be accessed." is (pending)
    
    "indicate that a missing resource/folder has zero length." is (pending)
    
    "allow a resource to be removed." is (pending)
    
    "allow a folder to be removed." is (pending)
    
    "prevent the removal of the root folder." is (pending)
    
    "prevent the removal of a folder with children." is (pending)
    
    "prevent the removal of a missing resource/folder." is (pending)
    
    "allow resource attributes to be accessed." is (pending)
    
    "allow folder attributes to be accessed." is (pending)
    
    "allow resource attributes to be accessed by an equivalent URI." is (pending)
    
    "allow folder attributes to be accessed by an equivalent URI." is (pending)
    
    "return null when accessing missing resource/folder attributes." is (pending)
    
    "always have a root folder for attribute access." is (pending)
    
    "evict the least recently used resource when full." is (pending)
  }
}