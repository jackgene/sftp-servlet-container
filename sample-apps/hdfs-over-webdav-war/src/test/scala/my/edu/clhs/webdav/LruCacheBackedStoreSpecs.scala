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

import java.io.ByteArrayInputStream
import org.junit.runner.RunWith
import org.scalatest.WordSpec
import org.scalatest.junit.JUnitRunner
import org.scalatest.junit.MustMatchersForJUnit
import net.sf.webdav.exceptions.AccessDeniedException
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
  
  def testInstance: LruCacheBackedStore = {
    val instance = new LruCacheBackedStore(8L, 16L);
    instance.createFolder(null, "/folder/");
    instance.createResource(null, "/resource");
    
    return instance
  }
  "A properly initialized LruCacheBackedStore" must {
    val instance = testInstance
    
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
      val actualContentStream = instance.getResourceContent(null, testUri)
      
      // Verify
      actualContentStream.read() must equal (-1) // Empty content
      actualContentStream.close()
    }
    
    "complain when reading the contents of a folder." in {
      // Input
      val testUri = "/folder/"
      
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
    
    "allow content to be written to a resource." in {
      // Input
      val testUri = "/resource"
      val testContent = Array[Byte](1, 2, 3, 5, 7, 11, 13, 17)
      
      // Test
      val actualLength = instance.setResourceContent(
        null, testUri, new ByteArrayInputStream(testContent), null, null)
      
      // Verify
      actualLength must equal (8)
      val actualContentStream = instance.getResourceContent(null, testUri)
      testContent.toList.foreach { expectedValue =>
        actualContentStream.read() must equal (expectedValue)
      }
      actualContentStream.read() must equal (-1) // no extra data
      actualContentStream.close()
    }
    
    "prevent the writing of content to a folder." in {
      // Input
      val testUri = "/folder"
      val testContent = Array[Byte](1, 2, 3, 5, 7, 11, 13, 17)
      
      // Test & Verify
      evaluating {
        instance.setResourceContent(
          null, testUri, new ByteArrayInputStream(testContent), null, null)
      } must produce[WebdavException]
    }
    
    "prevent the writing of content to a non-existent resource." in {
      // Input
      val testUri = "/missing"
      val testContent = Array[Byte](1, 2, 3, 5, 7, 11, 13, 17)
      
      // Test & Verify
      evaluating {
        instance.setResourceContent(
          null, testUri, new ByteArrayInputStream(testContent), null, null)
      } must produce[ObjectNotFoundException]
    }
    
    "prevent the writing of content that is too long." in {
      // Input
      val testUri = "/resource"
      val testContent = Array[Byte](1, 2, 3, 5, 7, 11, 13, 17, 19)
      
      // Test & Verify
      evaluating {
        instance.setResourceContent(
          null, testUri, new ByteArrayInputStream(testContent), null, null)
      } must produce[AccessDeniedException]
    }
    
    "allow folder children names to be listed." in {
      // Input
      val testUri = "/folder/"
      
      // Set up
      instance.createResource(null, "/folder/folder/")
      instance.createResource(null, "/folder/resource")
      
      // Test & Verify
      val expectedNames = Array[String]("folder", "resource")
      instance.getChildrenNames(null, testUri) must equal (expectedNames)
    }
    
    "return an empty array when listing the chilren of an empty folder." in {
      // Input
      val testUri = "/folder/"
      
      // Set up
      val instance = testInstance
      
      // Test & Verify
      val expectedNames = Array[String]()
      instance.getChildrenNames(null, testUri) must equal (expectedNames)
    }
    
    "return null when listing the chilren of a resource." in {
      // Input
      val testUri = "/resource"
      
      // Test & Verify
      instance.getChildrenNames(null, testUri) must be (null)
    }
    
    "return null when listing the chilren of a missing folder." in {
      // Input
      val testUri = "/missing/"
      
      // Test & Verify
      instance.getChildrenNames(null, testUri) must be (null)
    }
    
    "always have a root folder for listing children." in {
      // Input
      val testUri = "/"
      
      // Set up
      val instance = testInstance
      
      // Test & Verify
      val expectedNames = Array[String]("folder", "resource")
      instance.getChildrenNames(null, testUri) must equal (expectedNames)
    }
    
    "allow resource length to be accessed." in {
      // Input
      val testUri = "/resource"
      val testContent = Array[Byte](1, 2, 3, 5, 7, 11, 13, 17)
      
      // Set up
      instance.setResourceContent(
        null, testUri, new ByteArrayInputStream(testContent), null, null)
      
      // Test & Verify
      instance.getResourceLength(null, testUri) must equal (8)
    }
    
    "allow folder length to be accessed." in {
      // Input
      val testUri = "/folder"
      
      // Test & Verify
      instance.getResourceLength(null, testUri) must equal (0)
    }
    
    // This doesn't make sense but mimmick's LocalFileSystemStore behavior
    "indicate that a missing resource/folder has zero length." in {
      // Input
      val testUri = "/missing"
      
      // Test & Verify
      instance.getResourceLength(null, testUri) must equal (0)
    }
    
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