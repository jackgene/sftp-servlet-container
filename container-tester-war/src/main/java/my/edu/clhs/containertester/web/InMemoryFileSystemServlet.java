/**
 * InMemoryFileSystemServlet.java
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
package my.edu.clhs.containertester.web;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author Jack Leow
 */
public class InMemoryFileSystemServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    
    private final Map<String,byte[]> fileSystem = new HashMap<String,byte[]>();
    
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        final String pathInfo = req.getPathInfo();
        
        if (fileSystem.containsKey(pathInfo)) {
            resp.getOutputStream().write(fileSystem.get(pathInfo));
        } else {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "File Not Found");
        }
    }
    
    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        final InputStream fromUser =
            new BufferedInputStream(req.getInputStream());
        final ByteArrayOutputStream toFile = new ByteArrayOutputStream();
        
        try {
            final byte[] buf = new byte[4096];
            
            for (int read = fromUser.read(buf);
                    read > -1;
                    read = fromUser.read(buf)) {
                toFile.write(buf, 0, read);
            }
            fileSystem.put(req.getPathInfo(), toFile.toByteArray());
        } finally {
            toFile.close();
            fromUser.close();
        }
    }
    
    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        final String pathInfo = req.getPathInfo();
        
        if (fileSystem.containsKey(pathInfo)) {
            fileSystem.remove(pathInfo);
        } else {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "File Not Found");
        }
    }
}