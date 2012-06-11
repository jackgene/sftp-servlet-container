/*
 * LinuxMetadataFileExclusionsSpecs.scala
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
 * @author Jack Leow
 */
@RunWith(classOf[JUnitRunner])
class LinuxMetadataFileExclusionsSpecs
    extends WordSpec with MustMatchersForJUnit {
  "A MacOSMetadataFileExclusion Predicate" must {
    val filter = FilteredWebdavStore.LINUX_METADATA_FILE_EXCLUSIONS
    
    // MacOS metadata files
    "accept a plain .DS_Store file uri" in {
      filter.apply(".DS_Store") must be (true)
    }
    "accept a plain ._.* file uri" in {
      filter.apply("._.file.txt") must be (true)
    }
    "accept a nested .DS_Store file uri" in {
      filter.apply("/Users/webdav/.DS_Store") must be (true)
    }
    "accept a nested ._.* file uri" in {
      filter.apply("/Users/webdav/._.file.txt") must be (true)
    }
    
    // Windows metadata files
    "accept a plain desktop.ini file uri" in {
      filter.apply("desktop.ini") must be (true)
    }
    "accept a plain Thumbs.db file uri" in {
      filter.apply("Thumbs.db") must be (true)
    }
    "accept a nested desktop.ini file uri" in {
      filter.apply("/Users/webdav/desktop.ini") must be (true)
    }
    "accept a nested Thumbs.db file uri" in {
      filter.apply("/Users/webdav/Thumbs.db") must be (true)
    }
    
    // Other files
    "accept a regular file uri" in {
      filter.apply("/Users/webdav/file.txt") must be (true)
    }
  }
}
