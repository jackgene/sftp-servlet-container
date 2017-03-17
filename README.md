A servlet container that uses SFTP instead of HTTP as its access protocol.

Overview
========

The SFTP Servlet Container presents filesystem view of a Java EE web application via the SFTP/SCP protocol.

It behaves in two different ways, depending on if the deployed web application supports the HTTP WebDAV extensions.

Without WebDAV support
----------------------

If the web application does not support WebDAV extensions (this is typical):

1. Files paths are mapped to Servlet resource URIs.
2. The following table describes how SFTP commands are translated into Servlet requests:

| SFTP command | Servlet request method |
|--------------|------------------------|
| get          | GET                    |
| put          | PUT                    |
| rm           | DELETE                 |
| rmdir        | DELETE                 |

(Commands are based on the OpenSSH SFTP client)

* Navigating into directories using "cd" always succeeds, even if the directory does not exist, or is inaccessible.
E.g., "cd /WEB-INF" succeeds even though you won't be able to access anything under WEB-INF:

```
sftp> cd /WEB-INF

sftp> get web.xml

Couldn't stat remote file: No such file or directory

File "/WEB-INF/web.xml" not found.
```
* All directories appear to be empty except for a WHERE_ARE_MY_FILES.txt.

E.g., If you list the files under root, you only see this file listed even though "/index.jsp" may in fact be a valid Servlet resource:

```
sftp> ls /

WHERE_ARE_MY_FILES.txt

sftp> get /index.jsp

Fetching /index.jsp to index.jsp

/index.jsp                                   100%  7779    7.6KB/s   00:00
```
* You can access resources in a directory using the full path, or by navigating into the directory using "cd" first.

E.g., To access "/foo/bar.gif" you can do:

```
sftp> get /foo/bar.gif
```
or:
```
sftp> cd /foo

sftp> get bar.gif
```
* If a path maps to a Servlet resource that is both a file and a directory (this is possible in web applications), you must append a slash (/) to it to navigate to it as a directory. You may access it as a file using the path directly or by appending a slash dot (/.) to it.

E.g., If /baz is both a file and a directory, this is how you would access its file contents:
```
sftp> get /baz
```
or:
```
sftp> get /baz/.
```

and this is how you would list its directory contents:
```
sftp> ls /baz/
```

One special case is the root directory, if it's both a file and directory, this is how you would access its file contents:
```
sftp> get /.
```

and this is how you would list its directory contents:
```
sftp> ls /
```

* The following commands are not supported:
  * chgrp
  * chmod
  * chown
  * ln
  * mkdir
  * rename

With WebDAV support
-------------------

If the web application does support WebDAV extensions, you will be able to access it more or less like a regular filesystem. Specifically in addition to the capabilities above:

1. You will be able to list the contents of directories using ls (this is translated into a WebDAV PROPFIND operation with Depth: 1).
2. You will be able to create directories using mkdir (this is translated into a WebDAV MKCOL operation).
3. However, you will NOT be able to navigate into arbitrary non-existent directories using "cd" (there is no need to).
4. The following commands are not supported:
  * chgrp
  * chmod
  * chown
  * ln
  * rename

Getting Started
===============

Installing SFTP Servlet Container is simply copying JAR files from the distribution package into Tomcat's lib folder, and adding the following lines to the `conf/server.xml` file:
```xml
<Connector port="2222"
           protocol="my.edu.clhs.tomcat.coyote.SftpProtocol"
           anonymousUsername="anonymous"
           sessionTimeout="600000" />
```

Please refer to INSTALL.txt in the distribution package for more details.
