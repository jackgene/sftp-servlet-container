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
  val mockStore = mock[IWebdavStore]
  
  "A FilteredWebdavStore" must {
    "complain when initialized with no delegate" in {
      evaluating {
        new FilteredWebdavStore(null, Predicates.alwaysTrue(), 0l)
      } must produce [NullPointerException]
    }
    
    "complain when initialized with no inclusionPredicate" in {
      evaluating {
        new FilteredWebdavStore(mockStore, null, 0l)
      } must produce[NullPointerException]
    }
    
    "complain when initialized with no maxBannedFileSize" in {
      evaluating {
        new FilteredWebdavStore(mockStore, Predicates.alwaysTrue(), null)
      } must produce[NullPointerException]
    }
    
    "complain when initialized with a negative maxBannedFileSize value" in {
      evaluating {
        new FilteredWebdavStore(mockStore, Predicates.alwaysTrue(), -1)
      } must produce[IllegalArgumentException]
    }
  }
  
  "A FilteredWebdavStore" when {
    "properly initialized" must {
      val instance = new FilteredWebdavStore(mockStore)
      
      "delegate begin invocations" in {
        // Test input
        val testPrin = mock[Principal]
        val testTx = mock[ITransaction]
        val expectedTx = testTx
        
        // Expectations
        mockStore expects 'begin withArgs testPrin returning testTx
        
        // Test & verify
        instance.begin(testPrin) must be theSameInstanceAs (expectedTx)
      }
      
      "delegate checkAuthentication invocations" in {
        val testTx = mock[ITransaction]
        
        // Expectations
        mockStore expects 'checkAuthentication withArgs testTx
        
        // Test
        instance.checkAuthentication(testTx)
      }
      
      "delegate commit invocations" in {
        val testTx = mock[ITransaction]
        
        // Expectations
        mockStore expects 'commit withArgs testTx
        
        // Test
        instance.commit(testTx)
      }
      
      "delegate rollback invocations" in {
        val testTx = mock[ITransaction]
        
        // Expectations
        mockStore expects 'rollback withArgs testTx
        
        // Test
        instance.rollback(testTx)
      }
    }
    
    "initialized with the alwaysTrue inclusionPredicate" must {
      "delegate all file operations" is (pending)
    }
    
    "initialized with the alwaysFalse inclusionPredicate" must {
      "not delegate any file operation" is (pending)
    }
  }
}