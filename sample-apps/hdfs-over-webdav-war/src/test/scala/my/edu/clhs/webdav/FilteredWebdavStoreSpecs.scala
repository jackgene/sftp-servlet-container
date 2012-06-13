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

import com.google.common.base.Predicates
import net.sf.webdav.IWebdavStore
import org.junit.runner.RunWith
import org.scalamock.ProxyMockFactory
import org.scalamock.scalatest.MockFactory
import org.scalatest.junit.JUnitRunner
import org.scalatest.junit.MustMatchersForJUnit
import org.scalatest.WordSpec
import net.sf.webdav.ITransaction
import java.security.Principal

/**
 * @author Jack Leow
 */
@RunWith(classOf[JUnitRunner])
class FilteredWebdavStoreSpecs extends WordSpec
    with MustMatchersForJUnit with MockFactory with ProxyMockFactory {
  "A FilteredWebdavStore" must {
    val mockStore = mock[IWebdavStore]
    
    "complain when initialized with no inclusionPredicate" in {
      evaluating {
        new FilteredWebdavStore(null, mockStore, mockStore)
      } must produce[NullPointerException]
    }
    
    "complain when initialized with no primaryStore" in {
      evaluating {
        new FilteredWebdavStore(Predicates.alwaysTrue(), null, mockStore)
      } must produce [NullPointerException]
    }
    
    "complain when initialized with no rejectionStore" in {
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
      
      "delegate begin invocations to the primaryStore" in {
        // Test input
        val testPrin = mock[Principal]
        val expectedTx = mockTransaction
        
        // Expectations
        mockStore expects 'begin withArgs testPrin returning mockTransaction
        
        // Test & verify
        instance.begin(testPrin) must be theSameInstanceAs (expectedTx)
      }
      
      "delegate checkAuthentication invocations to the primaryStore" in {
        // Expectations
        mockStore expects 'checkAuthentication withArgs mockTransaction
        
        // Test
        instance.checkAuthentication(mockTransaction)
      }
      
      "delegate commit invocations to the primaryStore" in {
        // Expectations
        mockStore expects 'commit withArgs mockTransaction
        
        // Test
        instance.commit(mockTransaction)
      }
      
      "delegate rollback invocations to the primaryStore" in {
        // Expectations
        mockStore expects 'rollback withArgs mockTransaction
        
        // Test
        instance.rollback(mockTransaction)
      }
    }
    
    "initialized with the alwaysTrue inclusionPredicate" must {
      "delegate createFolder invocations to the primaryStore" is (pending)
      "delegate all file operations" is (pending)
    }
    
    "initialized with the alwaysFalse inclusionPredicate" must {
      "not delegate createFolder invocations to the rejectionStore" is (pending)
      "not delegate any file operation" is (pending)
    }
  }
}