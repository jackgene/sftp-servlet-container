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
    instance.setResourceContent(
      null, "/resource",
      new ByteArrayInputStream(Array[Byte](1, 2, 3)),
      null, null)
    
    return instance
  }
  "A properly initialized LruCacheBackedStore" must {
    "allow a folder to be created." in {
      // Input
      val testUri = "/another-folder/"
      
      // Set up
      val instance = testInstance
      
      // Test
      instance.createFolder(null, testUri)
      
      // Verify
      instance.getStoredObject(null, testUri) must not be (null)
    }
    
    "prevent the creation of a duplicate folder." in {
      // Input
      val testUri = "/folder/"
      
      // Set up
      val instance = testInstance
      
      // Test & Verify
      evaluating {
        instance.createFolder(null, testUri)
      } must produce[ObjectAlreadyExistsException]
    }
    
    "prevent the creation of a folder over an existing resource." in {
      // Input
      val testUri = "/resource"
      
      // Set up
      val instance = testInstance
      
      // Test & Verify
      evaluating {
        instance.createFolder(null, testUri)
      } must produce[ObjectAlreadyExistsException]
    }
    
    "prevent the creation of an orphaned folder." in {
      // Input
      val testUri = "/missing/folder/"
      
      // Set up
      val instance = testInstance
      
      // Test & Verify
      evaluating {
        instance.createFolder(null, testUri)
      } must produce[ObjectNotFoundException]
    }
    
    "allow a resource to be created." in {
      // Input
      val testUri = "/another-resource"
      
      // Set up
      val instance = testInstance
      
      // Test
      instance.createResource(null, testUri)
      
      // Verify
      instance.getStoredObject(null, testUri) must not be (null)
    }
    
    "prevent the creation of a duplicate resource." in {
      // Input
      val testUri = "/resource"
      
      // Set up
      val instance = testInstance
      
      // Test & Verify
      evaluating {
        instance.createResource(null, testUri)
      } must produce[ObjectAlreadyExistsException]
    }
    
    "prevent the creation of a resource over an existing folder." in {
      // Input
      val testUri = "/folder"
      
      // Set up
      val instance = testInstance
      
      // Test & Verify
      evaluating {
        instance.createResource(null, testUri)
      } must produce[ObjectAlreadyExistsException]
    }
    
    "prevent the creation of an orphaned resource." in {
      // Input
      val testUri = "/missing/resource"
      
      // Set up
      val instance = testInstance
      
      // Test & Verify
      evaluating {
        instance.createResource(null, testUri)
      } must produce[ObjectNotFoundException]
    }
    
    "allow resource contents to be read." in {
      // Input
      val testUri = "/resource"
      
      // Set up
      val instance = testInstance
      
      // Test
      val actualContentStream = instance.getResourceContent(null, testUri)
      
      // Verify
      List[Byte](1, 2, 3, -1) foreach { expectedValue =>
        actualContentStream.read() must equal (expectedValue)
      }
      actualContentStream.close()
    }
    
    "complain when reading the contents of a folder." in {
      // Input
      val testUri = "/folder/"
      
      // Set up
      val instance = testInstance
      
      // Test & Verify
      evaluating {
        instance.getResourceContent(null, testUri)
      } must produce[WebdavException]
    }
    
    "complain when reading the contents of a missing resource." in {
      // Input
      val testUri = "/missing"
      
      // Set up
      val instance = testInstance
      
      // Test & Verify
      evaluating {
        instance.getResourceContent(null, testUri)
      } must produce[ObjectNotFoundException]
    }
    
    "allow content to be written to a resource." in {
      // Input
      val testUri = "/resource"
      val testContent = Array[Byte](1, 2, 3, 5, 7, 11, 13, 17)
      
      // Set up
      val instance = testInstance
      
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
      
      // Set up
      val instance = testInstance
      
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
      
      // Set up
      val instance = testInstance
      
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
      
      // Set up
      val instance = testInstance
      
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
      val instance = testInstance
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
      
      // Set up
      val instance = testInstance
      
      // Test & Verify
      instance.getChildrenNames(null, testUri) must be (null)
    }
    
    "return null when listing the chilren of a missing folder." in {
      // Input
      val testUri = "/missing/"
      
      // Set up
      val instance = testInstance
      
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
      
      // Set up
      val instance = testInstance
      
      // Test & Verify
      instance.getResourceLength(null, testUri) must equal (3)
    }
    
    "allow folder length to be accessed." in {
      // Input
      val testUri = "/folder"
      
      // Set up
      val instance = testInstance
      
      // Test & Verify
      instance.getResourceLength(null, testUri) must equal (0)
    }
    
    // This doesn't make sense but mimmick's LocalFileSystemStore behavior
    "indicate that a missing resource/folder has zero length." in {
      // Input
      val testUri = "/missing"
      
      // Set up
      val instance = testInstance
      
      // Test & Verify
      instance.getResourceLength(null, testUri) must equal (0)
    }
    
    "allow a resource to be removed." in {
      // Input
      val testUri = "/resource"
      
      // Set up
      val instance = testInstance
      
      // Test
      instance.removeObject(null, testUri)
      
      // Verify
      instance.getStoredObject(null, testUri) must be (null)
    }
    
    "allow a folder to be removed." in {
      // Input
      val testUri = "/folder/"
      
      // Set up
      val instance = testInstance
      
      // Test
      instance.removeObject(null, testUri)
      
      // Verify
      instance.getStoredObject(null, testUri) must be (null)
    }
    
    "prevent the removal of the root folder." in {
      // Input
      val testUri = "/"
      
      // Set up
      val instance = testInstance
      
      // Test & Verify
      evaluating {
        instance.removeObject(null, testUri)
      } must produce[AccessDeniedException]
    }
    
    "prevent the removal of a folder with children." in {
      // Input
      val testUri = "/folder"
      
       // Set up
      val instance = testInstance
      instance.createResource(null, "/folder/resource")
      
     // Test & Verify
      evaluating {
        instance.removeObject(null, testUri)
      } must produce[AccessDeniedException]
    }
    
    "prevent the removal of a missing resource/folder." in {
      // Input
      val testUri = "/missing"
      
      // Set up
      val instance = testInstance
      
      // Test & Verify
      evaluating {
        instance.removeObject(null, testUri)
      } must produce[ObjectNotFoundException]
    }
    
    "allow resource attributes to be accessed." in {
      // Input
      val testUri = "/resource"
      
       // Set up
      val instance = testInstance
      
      // Test
      val actualObject = instance.getStoredObject(null, testUri)
      
      // Verify
      actualObject must not be (null)
      actualObject.isFolder must be (false)
      actualObject.getResourceLength must equal (3)
      actualObject.getCreationDate must not be (null)
      actualObject.getLastModified must not be (null)
    }
    
    "allow folder attributes to be accessed." in {
      // Input
      val testUri = "/folder/"
      
       // Set up
      val instance = testInstance
      
      // Test
      val actualObject = instance.getStoredObject(null, testUri)
      
      // Verify
      actualObject must not be (null)
      actualObject.isFolder must be (true)
      actualObject.getResourceLength must equal (0)
      actualObject.getCreationDate must not be (null)
      actualObject.getLastModified must not be (null)
    }
    
    "allow resource attributes to be accessed by an equivalent URI." in {
      // Input
      val testUri = "/./folder/../resource"
      
       // Set up
      val instance = testInstance
      
      // Test
      val actualObject = instance.getStoredObject(null, testUri)
      
      // Verify
      actualObject must not be (null)
      actualObject.isFolder must be (false)
      actualObject.getResourceLength must equal (3)
      actualObject.getCreationDate must not be (null)
      actualObject.getLastModified must not be (null)
    }
    
    "allow folder attributes to be accessed by an equivalent URI." in {
      // Input
      val testUri = "/./folder/../folder/"
      
       // Set up
      val instance = testInstance
      
      // Test
      val actualObject = instance.getStoredObject(null, testUri)
      
      // Verify
      actualObject must not be (null)
      actualObject.isFolder must be (true)
      actualObject.getResourceLength must equal (0)
      actualObject.getCreationDate must not be (null)
      actualObject.getLastModified must not be (null)
    }
    
    "return null when accessing missing resource/folder attributes." in {
      // Input
      val testUri = "/missing"
      
       // Set up
      val instance = testInstance
      
      // Test
      val actualObject = instance.getStoredObject(null, testUri)
      
      // Verify
      actualObject must be (null)
    }
    
    "always have a root folder for attribute access." in {
      // Input
      val testUri = "/"
      
       // Set up
      val instance = testInstance
      
      // Test
      val actualObject = instance.getStoredObject(null, testUri)
      
      // Verify
      actualObject must not be (null)
      actualObject.isFolder must be (true)
      actualObject.getResourceLength must equal (0)
      actualObject.getCreationDate must not be (null)
      actualObject.getLastModified must not be (null)
    }
    
    "evict the least recently used resource when full." in {
      // Input
      val testContent = Array[Byte](1, 2, 3, 5, 7, 11, 13, 17)
      
      // Set up
      val instance = testInstance
      
      // Test
      instance.createResource(null, "/resource1")
      instance.setResourceContent(
        null, "/resource1", new ByteArrayInputStream(testContent), null, null)
      instance.createResource(null, "/resource2")
      instance.setResourceContent(
        null, "/resource2", new ByteArrayInputStream(testContent), null, null)
      
      // Verify
      instance.getStoredObject(null, "/resource") must be (null)
      instance.getStoredObject(null, "/resource1") must not be (null)
      instance.getStoredObject(null, "/resource2") must not be (null)
    }
  }
}