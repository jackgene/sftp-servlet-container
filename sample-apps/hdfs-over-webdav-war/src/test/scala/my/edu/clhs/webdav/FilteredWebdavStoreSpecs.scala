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
import net.sf.webdav.IWebdavStore
import org.junit.runner.RunWith
import org.scalamock.ProxyMockFactory
import org.scalamock.scalatest.MockFactory
import org.scalatest.junit.JUnitRunner
import org.scalatest.junit.MustMatchersForJUnit
import org.scalatest.WordSpec
import net.sf.webdav.ITransaction

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
    
    "complain when initialized with no inclusion predicate" in {
      evaluating {
        new FilteredWebdavStore(null, mockStore, mockStore)
      } must produce[NullPointerException]
    }
    
    "complain when initialized with no primary store" in {
      evaluating {
        new FilteredWebdavStore(Predicates.alwaysTrue(), null, mockStore)
      } must produce [NullPointerException]
    }
    
    "complain when initialized with no rejection store" in {
      evaluating {
        new FilteredWebdavStore(
            Predicates.alwaysTrue(), mockStore, null)
      } must produce[NullPointerException]
    }
  }
  
  "A FilteredWebdavStore" when {
    val mockTransaction = mock[ITransaction]
    
    "properly initialized" must {
      val mockStore = mock[IWebdavStore]
      val instance = new FilteredWebdavStore(mockStore)
      
      "delegate begin invocations to the primary store" in {
        // Test input
        val testPrin = mock[Principal]
        val expectedTx = mockTransaction
        
        // Expectations
        mockStore expects 'begin withArgs testPrin returning mockTransaction
        
        // Test & verify
        instance.begin(testPrin) must be theSameInstanceAs (expectedTx)
      }
      
      "delegate checkAuthentication invocations to the primary store" in {
        // Expectations
        mockStore expects 'checkAuthentication withArgs mockTransaction
        
        // Test
        instance.checkAuthentication(mockTransaction)
      }
      
      "delegate commit invocations to the primary store" in {
        // Expectations
        mockStore expects 'commit withArgs mockTransaction
        
        // Test
        instance.commit(mockTransaction)
      }
      
      "delegate rollback invocations to the primary store" in {
        // Expectations
        mockStore expects 'rollback withArgs mockTransaction
        
        // Test
        instance.rollback(mockTransaction)
      }
    }
    
    "initialized with an always true inclusion predicate" must {
      val mockPrimaryStore = mock[IWebdavStore]
      val mockRejectionStore = mock[IWebdavStore]
      val instance = new FilteredWebdavStore(
          Predicates.alwaysTrue(), mockPrimaryStore, mockRejectionStore)
      
      "delegate createFolder invocations to the primary store" in {
        val testUri = "/tmp"
        
        // Expectations
        mockPrimaryStore expects 'createFolder withArgs (
          mockTransaction, testUri)
        mockRejectionStore expects 'createFolder never
        
        // Test
        instance.createFolder(mockTransaction, testUri)
      }
      
      "delegate createResource invocations to the primary store" in {
        val testUri = "/tmp/file"
        
        // Expectations
        mockPrimaryStore expects 'createResource withArgs (
          mockTransaction, testUri)
        mockRejectionStore expects 'createResource never
        
        // Test
        instance.createResource(mockTransaction, testUri)
      }
      
      "delegate getResourceContent invocations to the primary store" in {
        val testUri = "/tmp/file"
        val mockInputStream = new ByteArrayInputStream(new Array[Byte](0))
        
        // Expectations
        mockPrimaryStore expects 'getResourceContent withArgs (
          mockTransaction, testUri
        ) returning mockInputStream
        mockRejectionStore expects 'getResourceContent never
        
        // Test
        val actualInputStream =
          instance.getResourceContent(mockTransaction, testUri)
        
        // Verify
        val expectedInputStream = mockInputStream
        (actualInputStream) must equal (expectedInputStream)
      }
      
      "delegate setResourceContent invocations to the primary store" is (pending)
      "delegate getChildrenNames invocations to both stores" is (pending)
      "delegate getResourceLength invocations to the primary store" is (pending)
      "delegate removeObject invocations to the primary store" is (pending)
      "delegate getStoredObject invocations to the primary store" is (pending)
    }
    
    "initialized with an always false inclusion predicate" must {
      val mockPrimaryStore = mock[IWebdavStore]
      val mockRejectionStore = mock[IWebdavStore]
      val instance = new FilteredWebdavStore(
          Predicates.alwaysFalse(), mockPrimaryStore, mockRejectionStore)
      
      "delegate createFolder invocations to the rejection store" in {
        val testUri = "/tmp"
        
        // Expectations
        mockRejectionStore expects 'createFolder withArgs (
          mockTransaction, testUri)
        mockPrimaryStore expects 'createFolder never
        
        // Test
        instance.createFolder(mockTransaction, testUri)
      }
      
      "delegate createResource invocations to the rejection store" in {
        val testUri = "/tmp"
        
        // Expectations
        mockRejectionStore expects 'createResource withArgs (
          mockTransaction, testUri)
        mockPrimaryStore expects 'createResource never
        
        // Test
        instance.createResource(mockTransaction, testUri)
      }
      
      "delegate getResourceContent invocations to the rejection store" in {
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
      
      "delegate setResourceContent invocations to the rejection store" is (pending)
      "delegate getChildrenNames invocations to both stores" is (pending)
      "delegate getResourceLength invocations to the rejection store" is (pending)
      "delegate removeObject invocations to the rejection store" is (pending)
      "delegate getStoredObject invocations to the rejection store" is (pending)
    }
  }
}