/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package my.edu.clhs.sshd.server.command;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.sshd.server.Command;
import org.apache.sshd.server.CommandFactory;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.SshFile;
import org.apache.sshd.server.command.ScpCommand;

/**
 * {@link ScpCommand} subclass that will attempt file writes even under
 * non-existent directories.
 * 
 * @author Jack Leow
 */
public class ServletScpCommand extends ScpCommand {
    public static class Factory implements CommandFactory {
        private String concatenateWithSpace(String[] args, int from) {
            StringBuilder sb = new StringBuilder();
        
            for (int i = from; i < args.length; i++) {
                sb.append(args[i] + " ");
            }
            
            return sb.toString().trim().replaceAll("\"", "");
        }
        
        private String[] splitCommandString(String command) {
            if (!command.trim().startsWith("scp")) {
                throw new IllegalArgumentException(
                    "Unknown command, does not begin with 'scp'");
            }
        
            String[] args = command.split(" ");
            List<String> parts = new ArrayList<String>();
            parts.add(args[0]);
            for (int i = 1; i < args.length; i++) {
                if (!args[i].trim().startsWith("-")) {
                    parts.add(concatenateWithSpace(args, i));
                    break;
                } else {
                    parts.add(args[i]);
                }
            }
            return parts.toArray(new String[parts.size()]);
        }
        
        /**
         * Parses a command string and verifies that the basic syntax is
         * correct. If parsing fails the responsibility is delegated to
         * the configured {@link CommandFactory} instance; if one exist.
         *
         * @param command command to parse 
         * @return configured {@link Command} instance
         * @throws IllegalArgumentException
         */
        public Command createCommand(final String command) {
            try {
                return new ServletScpCommand(splitCommandString(command));
            } catch (IllegalArgumentException iae) {
                return new Command() {
                    private ExitCallback exitCallback;
                    
                    @Override
                    public void start(Environment env)
                            throws IOException {
                        log.warn(
                            "Silently ignoring non-SCP command: " +
                            command
                        );
                        exitCallback.onExit(0);
                    }
                    
                    @Override
                    public void setOutputStream(OutputStream out) {
                    }
                    
                    @Override
                    public void setInputStream(InputStream in) {
                    }
                    
                    @Override
                    public void setExitCallback(ExitCallback callback) {
                        this.exitCallback = callback;
                    }
                    
                    @Override
                    public void setErrorStream(OutputStream err) {}
                    
                    @Override
                    public void destroy() {}
                };
            }
        }
    }
    
    public ServletScpCommand(String[] args) {
        super(args);
    }
    
    // Copy of superclass implementation, except for file determination
    @Override
    protected void writeFile(String header, SshFile path) throws IOException {
        if (log.isDebugEnabled()) {
            log.debug("Writing file {}", path);
        }
        if (!header.startsWith("C")) {
            throw new IOException(
                "Expected a C message but got '" + header + "'");
        }
        
        //String perms = header.substring(1, 5);
        long length =
            Long.parseLong(header.substring(6, header.indexOf(' ', 6)));
        String name = header.substring(header.indexOf(' ', 6) + 1);
        
        SshFile file;
        if (path.doesExist() && path.isDirectory()) {
            file = root.getFile(path, name);
        } else if (path.doesExist() && path.isFile()) {
            file = path;
        } else if (!path.doesExist()
                /* && path.getParentFile().doesExist()
                 * && path.getParentFile().isDirectory()*/) {
            file = path;
        } else {
            throw new IOException("Can not write to " + path);
        }
        if (file.doesExist() && !file.isWritable()) {
            throw new IOException("Can not write to file: " + file);
        }
        OutputStream os = file.createOutputStream(0);
        try {
            ack();
            
            byte[] buffer = new byte[8192];
            while (length > 0) {
                int len = (int) Math.min(length, buffer.length);
                len = in.read(buffer, 0, len);
                if (len <= 0) {
                    throw new IOException("End of stream reached");
                }
                os.write(buffer, 0, len);
                length -= len;
            }
        } finally {
            os.close();
        }
        
        ack();
        readAck(false);
    }
}