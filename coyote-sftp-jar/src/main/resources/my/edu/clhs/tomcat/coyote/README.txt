This is a filesystem view of a Java EE web application, and has the following
characteristics:
- Servlet resource URIs are map to file paths.
- You can "cd" into any directory, even if they do not exist.
- You cannot list the actual contents of directories.
  - All directories appear to be empty except for this README.txt file.
    e.g., "ls /" shows only "README.txt" even though "/index.jsp" may be a
    valid resource
- You access servlet resources by using "get," e.g., "get /index.jsp"
- You can access resources in directories using the full path, or by "cd-ing"
  into the directories first. E.g., to access "/foo/bar.gif" you can:
  > get /foo/bar.gif
  or:
  > cd /foo
  > get bar.gif