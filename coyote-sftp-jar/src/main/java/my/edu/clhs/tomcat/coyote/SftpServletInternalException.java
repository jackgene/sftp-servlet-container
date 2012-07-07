/*
 * SftpServletException.java
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
 * Indicates an internal problem with the SFTP Servlet subsystem.
 * 
 * Exceptions of this type should always be handled by the SFTP Servlet
 * subsystem. Do NOT use this exception (or any of its subclasses) to
 * report problems to the outside.
 * 
 * @author Jack Leow
 */
abstract class SftpServletInternalException extends Exception {
    private static final long serialVersionUID = 1L;
    
    public SftpServletInternalException(String msg) {
        super(msg);
    }
    
    public SftpServletInternalException(String msg, Throwable cause) {
        super(msg, cause);
    }
}