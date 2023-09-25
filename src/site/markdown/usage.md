# Usage

The following options can be passed on the command line:

```
-h,--help
	Prints this help.
-l,--local-dir <local-dir>
	Provides the local directory that should be synchronized.
-r,--remote-dir <remote-dir>
	Provides the remote directory that should be synchronized.
-a,--authentication-file <auth-file>
	Use given authentication file instead of default one (~/.jdrivesync).
--dry-run
	Simulates all data manipulating operations (dry run).
--delete
	Deletes all files instead of moving them to trash.
-c,--checksum
	Use MD5 checksum instead of last modification timestamp of file.
-i,--ignore-file <ignore-file>
	Provides a file with newline separated file and/or path name patterns that should be ignored.
-u,--up
	Synchronization is performed from the local to the remote site (default).
-d,--down
	Synchronization is performed from the remote to the local site.
--html-report
	Creates an HTML report of the synchronization.
-m,--max-file-size <maxFileSize>
	Provides the maximum file size in MB.
--http-chunk-size
	The size of a chunk in MB used for chunked uploads (default: 10MB).
--network-number-of-retries
	The number of times how often a request is retried (default: 3).
--network-sleep-between-retries
	The number of seconds to sleep between retries (default: 10).
-v,--verbose
	Verbose output
--log-file <log-file>
	The location for the log file.
--no-delete
	Do not delete files.
--doc application/vnd.oasis.opendocument.text
	Google doc export/import format (default:Open Office doc).
--sheet application/x-vnd.oasis.opendocument.spreadsheet
	Google sheet export/import format (default:Open Office sheet).
--slides application/vnd.oasis.opendocument.presentation
	Google slides export/import format (default:Open Office presentation).
--drowing image/jpeg
	Google drowing export/import format (default:JPEG).
```

Hence a simple upload synchronization of your file collection under /home/user/documents will be done with:

    java -jar jdrivesync-0.4.2-jar-with-dependencies.jar -u -l "/home/user/documents"

When you start jdrivesync for the first time it will print an authentication URL. You will have to point your browser
to this URL, login with your Google account and grant jdrivesync the requested privileges. After having clicked on
'Accept' you will be redirected to a new page that displays an authentication token. Copy this token to
the command line and press Enter.

Now jdrivesync will create a file called .jdrivesync in your home directory. This is your authentication file.
As long as jdrivesync will find a valid authentication file in your home directory, it will use it. When you
want to use an alternative file, you can specify that on the command line:

     java -jar jdrivesync-0.4.2-jar-with-dependencies.jar -u -l "/home/user/documents" -a "my-auth-file.properties"

You can also exclude certain files from being uploaded/downloaded. Just create a text document that contains file name
patterns and provide the path to this file using the option -i:

    java -jar jdrivesync-0.4.2-jar-with-dependencies.jar -u -l "/home/user/documents" -i .jdrivesyncignore

The patterns should follow these rules:

* Blank lines are ignored.
* Leading and trailing spaces are ignored.
* If the line starts with # it is treated as comment. If the first character should be #, use \\#.
* All patterns are seen relative from the root of your local directory (example: doc/git.html).
* Use / as path separator (and not the backslash).
* Use \* as wildcard for a file name. The pattern doc/*.html ignores all files ending with .html inside doc.
* If the pattern starts with \*\*/ (e.g. \*\*/foo) it will match all files or folders named foo inside the file hierarchy.
* If the pattern ends with /\*\* (e.g. foo/\*\*) it will match all files and folders below the directory foo.
* If the pattern contains /\*\*/ (e.g. foo/\*\*/xy) it will expand to zero or more directories that are in between foo and xy.
