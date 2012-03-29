/**
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * TODO quick implementation for JPR demo. Revisit and rewrite.
 * 
 * @author Jack Leow
 */
class WebDavSaxHandler extends DefaultHandler {
    private static final String NAMESPACE_URI = "DAV:";
    
    private List<Map<String,String>> files;
    private Map<String,String> file;
    
    public List<Map<String,String>> getFiles() {
        return files;
    }
    
    private enum State {
        START {
            @Override
            State startElement(
                    WebDavSaxHandler context, String uri, String localName,
                    String qName, Attributes attributes) {
                if (NAMESPACE_URI.equals(uri) &&
                        "multistatus".equals(localName)) {
                    context.files = new ArrayList<Map<String,String>>();
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
                    context.file = new HashMap<String,String>();
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
                    context.files.add(context.file);
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
                    return RESPONSE;
                }
                return super.endElement(context, uri, localName, qName);
            }
            
            @Override
            State characters(
                    WebDavSaxHandler context, char[] ch, int start,
                    int length) {
                context.file.put("path", new String(ch, start, length).trim());
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
                    if ("creationdate".equals(localName)) {
                        return CREATIONDATE;
                    } else if ("getcontentlength".equals(localName)) {
                        return GETCONTENTLENGTH;
                    } else if ("getlastmodified".equals(localName)) {
                        return GETLASTMODIFIED;
                    } else if ("resourcetype".equals(localName)) {
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
        CREATIONDATE {
            @Override
            State endElement(
                    WebDavSaxHandler context, String uri, String localName,
                    String qName) {
                if (NAMESPACE_URI.equals(uri) &&
                        "creationdate".equals(localName)) {
                    return PROP;
                }
                return super.endElement(context, uri, localName, qName);
            }
            
            @Override
            State characters(
                    WebDavSaxHandler context, char[] ch, int start,
                    int length) {
                context.file.put(
                    "creationDate", new String(ch, start, length).trim());
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
                    context.file.put("isFile", "true");
                    return PROP;
                }
                return super.endElement(context, uri, localName, qName);
            }
            
            @Override
            State characters(
                    WebDavSaxHandler context, char[] ch, int start,
                    int length) {
                context.file.put(
                    "contentLength", new String(ch, start, length).trim());
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
                    return PROP;
                }
                return super.endElement(context, uri, localName, qName);
            }
            
            @Override
            State characters(
                    WebDavSaxHandler context, char[] ch, int start,
                    int length) {
                context.file.put(
                    "lastModified", new String(ch, start, length).trim());
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
                        context.file.put("isDirectory", "true");
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
