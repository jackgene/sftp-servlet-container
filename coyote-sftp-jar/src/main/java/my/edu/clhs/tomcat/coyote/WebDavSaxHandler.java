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
import java.io.IOError;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * SAX handler that parses DAV:multistatus XML content into
 * {@link WebDAVServletResourceSshFile}s.
 * 
 * @author Jack Leow
 */
class WebDavSaxHandler extends DefaultHandler {
    private static final String NAMESPACE_URI = "DAV:";
    private static final String FALLBACK_URI_ENCODING = "iso-8859-1";
    private static final Log log = LogFactory.getLog(WebDavSaxHandler.class);
    
    private final SftpServletFileSystemView fileSystemView;
    private final String pathToDiscard;
    private final String uriEncoding;
    
    public WebDavSaxHandler(
            SftpServletFileSystemView fileSystemView, String pathToDiscard,
            String uriEncoding) {
        this.fileSystemView = fileSystemView;
        this.pathToDiscard = pathToDiscard;
        this.uriEncoding = uriEncoding;
    }
    
    private List<WebDAVServletResourceSshFile> files;
    private WebDAVServletResourceSshFile.Builder fileBuilder;
    private StringBuilder charBuffer;
    
    private boolean shouldDiscard(String path) {
        return pathToDiscard != null &&
            new File(pathToDiscard).equals(new File(path));
    }
    
    public List<WebDAVServletResourceSshFile> getFiles() {
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
                    context.files = new ArrayList<WebDAVServletResourceSshFile>();
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
                        new WebDAVServletResourceSshFile.Builder(
                        context.fileSystemView);
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
            private final String PLUS_URLENCODED = "%2B";
            @Override
            State endElement(
                    WebDavSaxHandler context, String uri, String localName,
                    String qName) {
                if (NAMESPACE_URI.equals(uri) &&
                        "href".equals(localName)) {
                    String href = context.charBuffer.toString().
                        replace("+", PLUS_URLENCODED);
                    try {
                        href = URLDecoder.decode(href, context.uriEncoding);
                    } catch (UnsupportedEncodingException e) {
                        log.warn(
                            "\"URIEncoding\" (" + context.uriEncoding +
                            ") not supported. Falling back to " +
                            FALLBACK_URI_ENCODING
                        );
                        try {
                            href = URLDecoder.decode(
                                href, FALLBACK_URI_ENCODING
                            );
                        } catch (UnsupportedEncodingException e1) {
                            // This should never happen
                            throw new IOError(e1);
                        }
                    }
                    if (context.shouldDiscard(href)) {
                        return DISCARD;
                    }
                    context.fileBuilder.path(href);
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
                if (NAMESPACE_URI.equals(uri) && "response".equals(localName)) {
                    // We should not see the start of another response
                    return super.startElement(
                        context, uri, localName, qName, attributes);
                }
                // Discard any other start elements
                return this;
            }
            
            @Override
            State endElement(
                    WebDavSaxHandler context, String uri, String localName,
                    String qName) {
                if (NAMESPACE_URI.equals(uri) && "response".equals(localName)) {
                    return MULTISTATUS;
                }
                // Discard any other end elements
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
                    } else if ("prop".equals(localName)) {
                        // We should not see the start of another prop
                        return super.startElement(
                            context, uri, localName, qName, attributes);
                    }
                }
                // discard any other start elements
                // prop can contain just about anything
                return this;
            }
            
            @Override
            State endElement(
                    WebDavSaxHandler context, String uri, String localName,
                    String qName) {
                if (NAMESPACE_URI.equals(uri) && "prop".equals(localName)) {
                    return PROPSTAT;
                }
                // discard any other end elements
                // prop can contain just about anything
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
                if (NAMESPACE_URI.equals(uri)) {
                    if ("collection".equals(localName) ||
                            "principal".equals(localName)) {
                        return this;
                    }
                }
                return super.startElement(
                    context, uri, localName, qName, attributes);
            }
            
            @Override
            State endElement(
                    WebDavSaxHandler context, String uri, String localName,
                    String qName) {
                if (NAMESPACE_URI.equals(uri)) {
                    if ("collection".equals(localName)) {
                        context.fileBuilder.isDirectory(true);
                        return this;
                    } else if ("principal".equals(localName)) {
                        return this;
                    } else if ("resourcetype".equals(localName)) {
                        return PROP;
                    }
                }
                return super.endElement(context, uri, localName, qName);
            }
        },
        END;
        
        State startElement(
                WebDavSaxHandler context, String uri, String localName,
                String qName, Attributes attributes) {
            throw new IllegalStateException(
                String.format(
                    "State (%s) does not expect start of element \"%s\"",
                    this, localName
                )
            );
        }
        
        State endElement(
                WebDavSaxHandler context, String uri, String localName,
                String qName) {
            throw new IllegalStateException(
                String.format(
                    "State (%s) does not expect end of element \"%s\"",
                    this, localName
                )
            );
        }
        
        State characters(
                WebDavSaxHandler context, char[] ch, int start, int length) {
            if ("".equals(new String(ch, start, length).trim())) {
                return this;
            } else {
                throw new IllegalStateException(
                    String.format(
                        "State (%s) does not expect characters", this
                    )
                );
            }
        }
    }
    
    private Locator locator;
    
    @Override
    public void setDocumentLocator(Locator locator) {
        this.locator = locator;
    }
    
    private State current = State.START;
    
    @Override
    public void startElement(
            String uri, String localName, String qName, Attributes attributes)
            throws SAXException {
        try {
            current = current.
                startElement(this, uri, localName, qName, attributes);
        } catch (IllegalStateException e) {
            throw new SAXParseException(
                "Error parsing DAV response", locator, e);
        }
    }
    
    @Override
    public void endElement(String uri, String localName, String qName)
            throws SAXException {
        try {
            current = current.endElement(this, uri, localName, qName);
        } catch (IllegalStateException e) {
            throw new SAXParseException(
                "Error parsing DAV response", locator, e);
        }
    }
    
    @Override
    public void characters(char[] ch, int start, int length)
            throws SAXException {
        try {
            current = current.characters(this, ch, start, length);
        } catch (IllegalStateException e) {
            throw new SAXParseException(
                "Error parsing DAV response", locator, e);
        }
    }
    
    @Override
    public void endDocument() throws SAXException {
        if (current != State.END) {
            throw new SAXParseException(
                "Error parsing DAV response", locator);
        }
    }
}
