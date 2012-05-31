This is a filesystem view of a Java EE web application.

Early versions of this software has the following characteristics:

- You can "cd" into any directory, even if they do not exist, or are
  inaccessible. E.g., "cd /WEB-INF" succeeds even though you won't be able to
  access anything under WEB-INF.

- You cannot list the actual contents of directories. All directories appear to
  be empty except for this README.txt file. E.g., if you list the contents of
  root, you only see "README.txt" even though "/index.jsp" may in fact be a
  valid resource:
   sftp> ls /
   README.txt   
   sftp> get /index.jsp
   Fetching /index.jsp to index.jsp
   /index.jsp                                   100%  7779    7.6KB/s   00:00

- Servlet resource URIs are mapped to file paths.

- You access servlet resources by using "get," e.g., "get /index.jsp"

- You can access resources in directories using the full path, or by "cd-ing"
  into the directories first. E.g., to access "/foo/bar.gif" you can:
   sftp> get /foo/bar.gif
  or:
   sftp> cd /foo
   sftp> get bar.gif

- You cannot put (upload) files.