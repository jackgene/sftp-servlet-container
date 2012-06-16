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

/**
 * {@link LruCacheBackedStore} specifications.
 * 
 * @author Jack Leow
 */
@RunWith(classOf[JUnitRunner])
class LruCacheBackedStoreSpecs extends WordSpec with MustMatchersForJUnit {
  "A LruCacheBackedStore" must {
    "complain when initialized without a maxResourceLength." is (pending)
    
    "complain when initialized without a maxStoreSpace." is (pending)
    
    "complain when initialized with a negative maxResourceLength." is (pending)
    
    "complain when initialized with a negative maxStoreSpace." is (pending)
    
    "allow a folder to be created." is (pending)
    
    "prevent the creation of a duplicate folder." is (pending)
    
    "prevent the creation of a folder over an existing resource." is (pending)
    
    "prevent the creation of an orphaned folder." is (pending)
    
    "allow a resource to be created." is (pending)
    
    "prevent the creation of a duplicate resource." is (pending)
    
    "prevent the creation of a resource over an existing folder." is (pending)
    
    "prevent the creation of an orphaned resource." is (pending)
    
    "allow resource contents to be read." is (pending)
    
    "complain when reading the contents of a folder." is (pending)
    
    "complain when reading the contents of a missing resource." is (pending)
    
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
    
    "return null when accessing missing resource/folder attributes." is (pending)
    
    "always have a root folder for attribute access." is (pending)
    
    "evict the least recently used resource when full." is (pending)
  }
}