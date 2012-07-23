/*
 * InvalidDavContentException.java
 *
 * Copyright 2011-2012 Jack Leow
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
package my.edu.clhs.tomcat.coyote;

/**
 * Indicates a semantic problem with the DAV response.
 * 
 * This usually means the DAV XML is valid, but the content does not
 * make sense (e.g., response for requested resource is absent,
 * response has lies outside the hierarchy of the requested resource).
 * 
 * @author Jack Leow
 */
class InvalidDavContentException extends DavProcessingException {
    private static final long serialVersionUID = 1L;
    
    private static final String DEFAULT_MESSAGE =
        "Resource responded with semantic errors in DAV XML";
    
    public InvalidDavContentException(String resourcePath) {
        super(DEFAULT_MESSAGE, resourcePath);
    }
}
