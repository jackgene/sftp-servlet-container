/*
 * WebDavSaxHandler.java
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
package my.edu.clhs.tomcat.coyote;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * SAX handler that parses DAV:multistatus XML content into
 * {@link ServletResourceSshFile}s.
 * 
 * TODO could this be an inner class or a more generic SftpServletFileSystemView?
 * 
 * @author Jack Leow
 */
class WebDavSaxHandler extends DefaultHandler {
    private static final String NAMESPACE_URI = "DAV:";
    
    private final SftpServletFileSystemView fileSystemView;
    private final String pathToDiscard;
    
    public WebDavSaxHandler(
            SftpServletFileSystemView fileSystemView, String pathToDiscard) {
        this.fileSystemView = fileSystemView;
        this.pathToDiscard = pathToDiscard;
    }
    
    private List<ServletResourceSshFile> files;
    private ServletResourceSshFile.Builder fileBuilder;
    private StringBuilder charBuffer;
    
    private boolean shouldDiscard(String path) {
        return pathToDiscard != null &&
            new File(pathToDiscard).equals(new File(path));
    }
    
    public List<ServletResourceSshFile> getFiles() {
        return Collections.unmodifiableList(files);
    }
    
    private enum State {
        START {
            @Override
            State startElement(
                    WebDavSaxHandler context, String uri, String localName,
                    String qName, Attributes attributes) {
                if (NAMESPACE_URI.equals(uri) &&
                        "multistatus".equals(localName)) {
                    context.files = new ArrayList<ServletResourceSshFile>();
                    return MULTISTATUS;
                }
                return super.startElement(
                    context, uri, localName, qName, attributes);
            }
        },
        MULTISTATUS {
            @Override
            State startElement(
                    WebDavSaxHandler context, String uri, String localName,
                    String qName, Attributes attributes) {
                if (NAMESPACE_URI.equals(uri) &&
                        "response".equals(localName)) {
                    context.fileBuilder =
                        new ServletResourceSshFile.Builder(context.fileSystemView);
                    return RESPONSE;
                }
                return super.startElement(
                    context, uri, localName, qName, attributes);
            }
            
            @Override
            State endElement(
                    WebDavSaxHandler context, String uri, String localName,
                    String qName) {
                if (NAMESPACE_URI.equals(uri) &&
                        "multistatus".equals(localName)) {
                    return END;
                }
                return super.endElement(context, uri, localName, qName);
            }
        },
        RESPONSE {
            @Override
            State startElement(
                    WebDavSaxHandler context, String uri, String localName,
                    String qName, Attributes attributes) {
                if (NAMESPACE_URI.equals(uri)) {
                    if ("href".equals(localName)) {
                        context.charBuffer = new StringBuilder();
                        return HREF;
                    } else if ("propstat".equals(localName)) {
                        return PROPSTAT;
                    }
                }
                return super.startElement(
                    context, uri, localName, qName, attributes);
            }
            
            @Override
            State endElement(
                    WebDavSaxHandler context, String uri, String localName,
                    String qName) {
                if (NAMESPACE_URI.equals(uri) &&
                        "response".equals(localName)) {
                    context.files.add(context.fileBuilder.build());
                    return MULTISTATUS;
                }
                return super.endElement(context, uri, localName, qName);
            }
        },
        HREF {
            @Override
            State endElement(
                    WebDavSaxHandler context, String uri, String localName,
                    String qName) {
                if (NAMESPACE_URI.equals(uri) &&
                        "href".equals(localName)) {
                    String path = context.charBuffer.toString();
                    if (context.shouldDiscard(path)) {
                        return DISCARD;
                    }
                    context.fileBuilder.path(context.charBuffer.toString());
                    return RESPONSE;
                }
                return super.endElement(context, uri, localName, qName);
            }
            
            @Override
            State characters(
                    WebDavSaxHandler context, char[] ch, int start,
                    int length) {
                context.charBuffer.append(ch, start, length);
                return this;
            }
        },
        DISCARD {
            @Override
            State startElement(
                    WebDavSaxHandler context, String uri, String localName,
                    String qName, Attributes attributes) {
                // TODO revisit, check for known types.
                return this;
            }
            
            @Override
            State endElement(
                    WebDavSaxHandler context, String uri, String localName,
                    String qName) {
                if (NAMESPACE_URI.equals(uri)) {
                    if ("response".equals(localName)) {
                        return MULTISTATUS;
                    } else if ("multistatus".equals(localName)) {
                        // We've gone too far
                        return super.endElement(context, uri, localName, qName);
                    }
                }
                // TODO revisit, check for known types.
                return this;
            }
            
            @Override
            State characters(
                    WebDavSaxHandler context, char[] ch, int start,
                    int length) {
                return this;
            }
        },
        PROPSTAT {
            @Override
            State startElement(
                    WebDavSaxHandler context, String uri, String localName,
                    String qName, Attributes attributes) {
                if (NAMESPACE_URI.equals(uri)) {
                    if ("prop".equals(localName)) {
                        return PROP;
                    } else if ("status".equals(localName)) {
                        return PROPSTAT;
                    }
                }
                return super.startElement(
                    context, uri, localName, qName, attributes);
            }
            
            @Override
            State endElement(
                    WebDavSaxHandler context, String uri, String localName,
                    String qName) {
                if (NAMESPACE_URI.equals(uri) &&
                        "propstat".equals(localName)) {
                    return RESPONSE;
                }
                return this;
            }
            
            @Override
            State characters(
                    WebDavSaxHandler context, char[] ch, int start,
                    int length) {
                return this;
            }
        },
        PROP {
            @Override
            State startElement(
                    WebDavSaxHandler context, String uri, String localName,
                    String qName, Attributes attributes) {
                if (NAMESPACE_URI.equals(uri)) {
                    if ("getcontentlength".equals(localName)) {
                        context.charBuffer = new StringBuilder();
                        return GETCONTENTLENGTH;
                    } else if ("getlastmodified".equals(localName)) {
                        context.charBuffer = new StringBuilder();
                        return GETLASTMODIFIED;
                    } else if ("resourcetype".equals(localName)) {
                        context.charBuffer = new StringBuilder();
                        return RESOURCETYPE;
                    }
                }
                // TODO revisit, check for known types.
                return this;
            }
            
            @Override
            State endElement(
                    WebDavSaxHandler context, String uri, String localName,
                    String qName) {
                if (NAMESPACE_URI.equals(uri) && "prop".equals(localName)) {
                    return PROPSTAT;
                }
                return this;
            }
            
            @Override
            State characters(
                    WebDavSaxHandler context, char[] ch, int start,
                    int length) {
                return this;
            }
        },
        GETCONTENTLENGTH {
            @Override
            State endElement(
                    WebDavSaxHandler context, String uri, String localName,
                    String qName) {
                if (NAMESPACE_URI.equals(uri) &&
                        "getcontentlength".equals(localName)) {
                    context.fileBuilder.size(
                        Long.valueOf(context.charBuffer.toString()));
                    context.fileBuilder.isFile(true);
                    return PROP;
                }
                return super.endElement(context, uri, localName, qName);
            }
            
            @Override
            State characters(
                    WebDavSaxHandler context, char[] ch, int start,
                    int length) {
                context.charBuffer.append(ch, start, length);
                return this;
            }
        },
        GETLASTMODIFIED {
            @Override
            State endElement(
                    WebDavSaxHandler context, String uri, String localName,
                    String qName) {
                if (NAMESPACE_URI.equals(uri) &&
                        "getlastmodified".equals(localName)) {
                    context.fileBuilder.lastModifiedRfc1123(
                        context.charBuffer.toString());
                    return PROP;
                }
                return super.endElement(context, uri, localName, qName);
            }
            
            @Override
            State characters(
                    WebDavSaxHandler context, char[] ch, int start,
                    int length) {
                context.charBuffer.append(ch, start, length);
                return this;
            }
        },
        RESOURCETYPE {
            @Override
            State startElement(
                    WebDavSaxHandler context, String uri, String localName,
                    String qName, Attributes attributes) {
                return this;
            }
            
            @Override
            State endElement(
                    WebDavSaxHandler context, String uri, String localName,
                    String qName) {
                if (NAMESPACE_URI.equals(uri)) {
                    if ("collection".equals(localName)) {
                        context.fileBuilder.isDirectory(true);
                        return this;
                    } else if ("resourcetype".equals(localName)) {
                        return PROP;
                    }
                }
                return this;
            }
        },
        END;
        
        State startElement(
                WebDavSaxHandler context, String uri, String localName,
                String qName, Attributes attributes) {
            throw new IllegalStateException();
        }
        
        State endElement(
                WebDavSaxHandler context, String uri, String localName,
                String qName) {
            throw new IllegalStateException();
        }
        
        State characters(
                WebDavSaxHandler context, char[] ch, int start, int length) {
            if ("".equals(new String(ch, start, length).trim())) {
                return this;
            } else {
                throw new IllegalStateException();
            }
        }
    }
    
    private State current = State.START;
    
    @Override
    public void startElement(
            String uri, String localName, String qName, Attributes attributes)
            throws SAXException {
        current = current.startElement(this, uri, localName, qName, attributes);
    }
    
    @Override
    public void endElement(String uri, String localName, String qName)
            throws SAXException {
        current = current.endElement(this, uri, localName, qName);
    }
    
    @Override
    public void characters(char[] ch, int start, int length)
            throws SAXException {
        current = current.characters(this, ch, start, length);
    }
}
