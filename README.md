#jdrivesync#

jdrivesync is a simple command line tool that synchronizes a local file system structure to your Google Drive (and back):

    java -jar jdrivesync-0.2.1-jar-with-dependencies.jar -l "/home/siom79"

##Motivation##
Having a backup of your documents and pictures at some remote site is valuable. Google Drive
comes with a good cost/performance ratio and provides a feature-rich web interface to search and browse files.

Google also offers a client application that allows you to synchronize your files to the cloud. But it has a few limitations:
* It is only available for Windows/MacOS (and not for Linux).
* You can only use it with one Google account.
* Only synchronizes one local directory.
* Synchronizes all files to the root of your Google Drive.
* No overview of which files are synchronized.
* No explicit direction of synchronization (up or down).

jdrivesync is written in Java (JRE >= 1.8 is required) and therefore runs on all platforms for which a Java Runtime Environment (JRE) exists.
You can use jdrivesync with more than one Google account and synchronize more than one directory. Even partial synchronization to an existing
folder on your Google Drive is possible (e.g. push all your files under /home/user/projects/xy to /backups/xy).

##Features##

###Scalability###
In contrast to other clients jdrivesync does not download the complete list of files before starting the synchronization. Instead each local directory
is synchronized with its corresponding directory on Google Drive. This approach scales better for large sets of files (>10.000 files) in terms of memory
consumption and initial download rate. Beyond that it also means that after restarting the tool no download of the complete file list is necessary.

###Multi-User###
The credentials can be passed to the application, hence you can use jdrivesync with more than one user account.

###Partial Sync###
While the Google client always synchronizes the complete folder hierarchy, jdrivesync allows you to specify a specific local and remote directory
that should be synchronized. Hence you can synchronize only parts of your file collection or manage multiple copies of your files with the same account
(e.g. /backup1, /backup2, etc.).

###Up or down synchronization###
When you start jdrivesync you specify if you want to upload or download files. This enables you to use parts of your file
collection on Google Drive as archive as you can delete them locally after successful synchronization and download them later when necessary.

###Reporting###
jdrivesync can create an HTML file that reports the actions taken in the last run.

###No application-specific metadata###
jdrivesync just uses the last modification timestamp and/or the MD5 checksum to determine whether a file has changed.
It does not utilize any application-specific metadata. Hence you can start working with an already existing backup that has been created by another client.

##Limitations##

Unfortunately the current version of the Google Drive API has a server-side bug regarding resumable uploades that take longer than the expiration time of
the access token (see for example [here](http://stackoverflow.com/questions/23789284/resumable-upload-error-401) or [here](https://code.google.com/p/google-api-python-client/issues/detail?id=231)).
Until this bug is fixed, you can use the command line option --max-file-size to exclude files that are too large to be uploaded within one hour. 

##Usage##

The following options can be passed on the command line:

    -h,--help
            Prints this help.
    -l,--local-dir <local-dir>
            Provides the local directory that should be synchronized.
    -r,--remote-dir <remote-dir>
            Provides the remote directory that should be synchronized.
    -a,--authentication-file <auth-file>
            Use given authentication file instead of default one (.jdrivesync).
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

Hence a simple upload synchronization of your file collection under /home/user/documents will be done with:

    java -jar jdrivesync-0.2.1-jar-with-dependencies.jar -u -l "/home/user/documents"

When you start jdrivesync for the first time it will print an authentication URL. You will have to point your browser
to this URL, login with your Google account and grant jdrivesync the requested privileges. After having clicked on
'Accept' you will be redirected to a new page that displays an authentication token. Copy this token to
the command line and press Enter.

Now jdrivesync will create a file called .jdrivesync in your current working directory. This is your authentication file.
As long as jdrivesync will find a valid authentication file in your current working directory, it will use it. When you
want to use an alternative file, you can specify that on the command line:

     java -jar jdrivesync-0.2.1-jar-with-dependencies.jar -u -l "/home/user/documents" -a ".myfile.properties"

You can also exclude certain files from being uploaded/downloaded. Just create a text document that contains file name
patterns and provide the path to this file using the option -i:

    java -jar jdrivesync-0.2.1-jar-with-dependencies.jar -u -l "/home/user/documents" -i .jdrivesyncignore
    
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

##Downloads##
The latest version can be downloaded from the [release page](https://github.com/siom79/jdrivesync/releases).
