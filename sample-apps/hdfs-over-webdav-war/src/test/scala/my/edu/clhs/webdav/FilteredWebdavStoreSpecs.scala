/*
 * FilteredWebdavStoreSpecs.scala
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
import java.io.InputStream
import java.security.Principal
import com.google.common.base.Predicates
import net.sf.webdav.ITransaction
import net.sf.webdav.IWebdavStore
import net.sf.webdav.StoredObject
import org.junit.runner.RunWith
import org.scalamock.ProxyMockFactory
import org.scalamock.scalatest.MockFactory
import org.scalatest.junit.JUnitRunner
import org.scalatest.junit.MustMatchersForJUnit
import org.scalatest.WordSpec

/**
 * {@link FilteredWebdavStore} specifications.
 * 
 * @author Jack Leow
 */
@RunWith(classOf[JUnitRunner])
class FilteredWebdavStoreSpecs extends WordSpec
    with MustMatchersForJUnit with MockFactory with ProxyMockFactory {
  "A FilteredWebdavStore" must {
    val mockStore = mock[IWebdavStore]
    
    "complain when initialized with no inclusion predicate." in {
      evaluating {
        new FilteredWebdavStore(null, mockStore, mockStore)
      } must produce[NullPointerException]
    }
    
    "complain when initialized with no primary store." in {
      evaluating {
        new FilteredWebdavStore(Predicates.alwaysTrue(), null, mockStore)
      } must produce [NullPointerException]
    }
    
    "complain when initialized with no rejection store." in {
      evaluating {
        new FilteredWebdavStore(
            Predicates.alwaysTrue(), mockStore, null)
      } must produce[NullPointerException]
    }
  }
  
  "A FilteredWebdavStore," when {
    val mockTransaction = mock[ITransaction]
    
    "properly initialized," must {
      val mockPrimaryStore = mock[IWebdavStore]
      val mockRejectionStore = mock[IWebdavStore]
      val instance = new FilteredWebdavStore(
        Predicates.alwaysTrue(), mockPrimaryStore, mockRejectionStore)
      
      "delegate begin invocations to the primary store." in {
        // Test input
        val testPrin = mock[Principal]
        val expectedTx = mockTransaction
        
        // Expectations
        mockPrimaryStore expects 'begin withArgs (
          testPrin) returning mockTransaction
        
        // Test & verify
        instance.begin(testPrin) must be theSameInstanceAs (expectedTx)
      }
      
      "delegate checkAuthentication invocations to the primary store." in {
        // Expectations
        mockPrimaryStore expects 'checkAuthentication withArgs mockTransaction
        
        // Test
        instance.checkAuthentication(mockTransaction)
      }
      
      "delegate commit invocations to the primary store." in {
        // Expectations
        mockPrimaryStore expects 'commit withArgs mockTransaction
        
        // Test
        instance.commit(mockTransaction)
      }
      
      "delegate rollback invocations to the primary store." in {
        // Expectations
        mockPrimaryStore expects 'rollback withArgs mockTransaction
        
        // Test
        instance.rollback(mockTransaction)
      }
      
      "have null childrenNames if both its stores have null childrenNames." in {
        val testUri = "/tmp"
        
        // Expectations
        mockPrimaryStore expects 'getChildrenNames withArgs (
          mockTransaction, testUri
        ) returning null
        mockRejectionStore expects 'getChildrenNames withArgs (
          mockTransaction, testUri
        ) returning null
        
        // Test & Verify
        instance.getChildrenNames(mockTransaction, testUri) must be (null)
      }
      
      "include childrenNames from its primaryStore." in {
        val testUri = "/tmp"
        val testFile = "file"
        
        // Expectations
        mockPrimaryStore expects 'getChildrenNames withArgs (
          mockTransaction, testUri
        ) returning Array(testFile)
        mockRejectionStore expects 'getChildrenNames withArgs (
          mockTransaction, testUri
        ) returning null
        mockRejectionStore expects 'getStoredObject anyNumberOfTimes;
        mockRejectionStore expects 'createFolder anyNumberOfTimes
        
        // Test & Verify
        val expectedNames = List(testFile)
        instance.getChildrenNames(
          mockTransaction, testUri).toList.sorted must equal (expectedNames)
      }
      
      "include childrenNames from its rejectionStore." in {
        val testUri = "/tmp"
        val testFile = "file"
        
        // Expectations
        mockPrimaryStore expects 'getChildrenNames withArgs (
          mockTransaction, testUri
        ) returning null
        mockRejectionStore expects 'getChildrenNames withArgs (
          mockTransaction, testUri
        ) returning Array(testFile)
        
        // Test & Verify
        val expectedNames = List(testFile)
        instance.getChildrenNames(
          mockTransaction, testUri).toList.sorted must equal (expectedNames)
      }
      
      "include unique childrenNames from both its stores." in {
        val testUri = "/tmp"
        val testFile0 = "file0"
        val testFile1 = "file1"
        val testFile2 = "file2"
        
        // Expectations
        mockPrimaryStore expects 'getChildrenNames withArgs (
          mockTransaction, testUri
        ) returning Array(testFile0, testFile1)
        mockRejectionStore expects 'getChildrenNames withArgs (
          mockTransaction, testUri
        ) returning Array(testFile0, testFile2)
        
        // Test & Verify
        val expectedNames = List(testFile0, testFile1, testFile2)
        instance.getChildrenNames(
          mockTransaction, testUri).toList.sorted must equal (expectedNames)
      }
    }
    
    "initialized with an always-true inclusion predicate," must {
      val mockPrimaryStore = mock[IWebdavStore]
      val mockRejectionStore = mock[IWebdavStore]
      val instance = new FilteredWebdavStore(
          Predicates.alwaysTrue(), mockPrimaryStore, mockRejectionStore)
      
      "delegate createFolder invocations to the primary store." in {
        val testUri = "/tmp"
        
        // Expectations
        mockPrimaryStore expects 'createFolder withArgs (
          mockTransaction, testUri)
        mockRejectionStore expects 'createFolder withArgs (
          mockTransaction, testUri)
        
        // Test
        instance.createFolder(mockTransaction, testUri)
      }
      
      "delegate createResource invocations to the primary store." in {
        val testUri = "/tmp/file"
        
        // Expectations
        mockPrimaryStore expects 'createResource withArgs (
          mockTransaction, testUri)
        mockRejectionStore expects 'createResource never
        
        // Test
        instance.createResource(mockTransaction, testUri)
      }
      
      "delegate getResourceContent invocations to the primary store." in {
        val testUri = "/tmp/file"
        val testInputStream = new ByteArrayInputStream(new Array[Byte](0))
        
        // Expectations
        mockPrimaryStore expects 'getResourceContent withArgs (
          mockTransaction, testUri
        ) returning testInputStream
        mockRejectionStore expects 'getResourceContent never
        
        // Test
        val actualInputStream =
          instance.getResourceContent(mockTransaction, testUri)
        
        // Verify
        val expectedInputStream = testInputStream
        (actualInputStream) must equal (expectedInputStream)
      }
      
      "delegate setResourceContent invocations to the primary store." in {
        val testUri = "/tmp/file"
        val testInputStream = new ByteArrayInputStream(new Array[Byte](0))
        val testContentType = "text/plain"
        val testEncoding = "UTF-8"
        val testLength = 42l
        
        // Expectations
        mockPrimaryStore expects 'setResourceContent withArgs (
          mockTransaction, testUri, testInputStream,
          testContentType, testEncoding
        ) returning testLength
        mockRejectionStore expects 'setResourceContent never
        
        // Test & Verify
        val expectedLength = testLength
        instance.setResourceContent(
          mockTransaction, testUri, testInputStream,
          testContentType, testEncoding
        ) must equal (expectedLength)
      }
      
      "delegate getResourceLength invocations to the primary store." in {
        val testUri = "/tmp/file"
        val testLength = 42l
        
        // Expectations
        mockPrimaryStore expects 'getResourceLength withArgs (
          mockTransaction, testUri
        ) returning testLength
        mockRejectionStore expects 'getResourceLength never
        
        // Test & Verify
        val expectedLength = testLength
        instance.getResourceLength(
          mockTransaction, testUri) must equal (expectedLength)
      }
      
      "delegate removeObject invocations to the primary store." in {
        val testUri = "/tmp/file"
        
        // Expectations
        mockPrimaryStore expects 'removeObject withArgs (
          mockTransaction, testUri)
        mockRejectionStore expects 'removeObject never
        
        // Test
        instance.removeObject(mockTransaction, testUri)
      }
      
      "delegate getStoredObject invocations to the primary store." in {
        val testUri = "/tmp/file"
        val mockStoredObject = new StoredObject()
        
        // Expectations
        mockPrimaryStore expects 'getStoredObject withArgs (
          mockTransaction, testUri
        ) returning mockStoredObject
        mockRejectionStore expects 'getStoredObject never
        
        // Test
        val actualStoredObject =
          instance.getStoredObject(mockTransaction, testUri)
        
        // Verify
        val expectedStoredObject = mockStoredObject
        (actualStoredObject) must be theSameInstanceAs (expectedStoredObject)
      }
    }
    
    "initialized with an always-false inclusion predicate," must {
      val mockPrimaryStore = mock[IWebdavStore]
      val mockRejectionStore = mock[IWebdavStore]
      val instance = new FilteredWebdavStore(
        Predicates.alwaysFalse(), mockPrimaryStore, mockRejectionStore)
      
      "delegate createFolder invocations to the rejection store." in {
        val testUri = "/tmp"
        
        // Expectations
        mockRejectionStore expects 'createFolder withArgs (
          mockTransaction, testUri)
        mockPrimaryStore expects 'createFolder never
        
        // Test
        instance.createFolder(mockTransaction, testUri)
      }
      
      "delegate createResource invocations to the rejection store." in {
        val testUri = "/tmp"
        
        // Expectations
        mockRejectionStore expects 'createResource withArgs (
          mockTransaction, testUri)
        mockPrimaryStore expects 'createResource never
        
        // Test
        instance.createResource(mockTransaction, testUri)
      }
      
      "delegate getResourceContent invocations to the rejection store." in {
        val testUri = "/tmp/file"
        val mockInputStream = new ByteArrayInputStream(new Array[Byte](0))
        
        // Expectations
        mockRejectionStore expects 'getResourceContent withArgs (
          mockTransaction, testUri
        ) returning mockInputStream
        mockPrimaryStore expects 'getResourceContent never
        
        // Test
        val actualInputStream =
          instance.getResourceContent(mockTransaction, testUri)
        
        // Verify
        val expectedInputStream = mockInputStream
        (actualInputStream) must equal (expectedInputStream)
      }
      
      "delegate setResourceContent invocations to the rejection store." in {
        val testUri = "/tmp/file"
        val testInputStream = new ByteArrayInputStream(new Array[Byte](0))
        val testContentType = "text/plain"
        val testEncoding = "UTF-8"
        val testLength = 42l
        
        // Expectations
        mockRejectionStore expects 'setResourceContent withArgs (
          mockTransaction, testUri, testInputStream,
          testContentType, testEncoding
        ) returning testLength
        mockPrimaryStore expects 'setResourceContent never
        
        // Test & Verify
        val expectedLength = testLength
        instance.setResourceContent(
          mockTransaction, testUri, testInputStream,
          testContentType, testEncoding
        ) must equal (expectedLength)
      }
      
      "delegate getResourceLength invocations to the rejection store." in {
        val testUri = "/tmp/file"
        val testLength = 42l
        
        // Expectations
        mockRejectionStore expects 'getResourceLength withArgs (
          mockTransaction, testUri
        ) returning testLength
        mockPrimaryStore expects 'getResourceLength never
        
        // Test & Verify
        val expectedLength = testLength
        instance.getResourceLength(
          mockTransaction, testUri) must equal (expectedLength)
      }
      
      "delegate removeObject invocations to the rejection store." in {
        val testUri = "/tmp/file"
        
        // Expectations
        mockRejectionStore expects 'removeObject withArgs (
          mockTransaction, testUri)
        mockPrimaryStore expects 'removeObject never
        
        // Test
        instance.removeObject(mockTransaction, testUri)
      }
      
      "delegate getStoredObject invocations to the rejection store." in {
        val testUri = "/tmp/file"
        val mockStoredObject = new StoredObject()
        
        // Expectations
        mockRejectionStore expects 'getStoredObject withArgs (
          mockTransaction, testUri
        ) returning mockStoredObject
        mockPrimaryStore expects 'getStoredObject never
        
        // Test
        val actualStoredObject =
          instance.getStoredObject(mockTransaction, testUri)
        
        // Verify
        val expectedStoredObject = mockStoredObject
        (actualStoredObject) must be theSameInstanceAs (expectedStoredObject)
      }
    }
  }
}