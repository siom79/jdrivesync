#jdrivesync#

jdrivesync is a simple command line tool that synchronizes a local file system structure to your Google Drive (and back):

    java -jar jdrivesync-0.3.0-jar-with-dependencies.jar -l "/home/siom79"

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

##Installation##

###deb package###

There is a debian package available. It can be installed using the following command:

    sudo dpkg -i jdrivesync_0.3.0_all.deb

Please note that jdrivesync requires openjdk-8-jre. How to install openjdk in version 8 for Ubuntu <14.10 is explained
[here](http://askubuntu.com/questions/464755/how-to-install-openjdk-8-on-14-04-lts).

After successful installation you can start jdrivesync with:

    /opt/jdrivesync/jdrivesync.sh

##Usage##

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
```

Hence a simple upload synchronization of your file collection under /home/user/documents will be done with:

    java -jar jdrivesync-0.3.0-jar-with-dependencies.jar -u -l "/home/user/documents"

When you start jdrivesync for the first time it will print an authentication URL. You will have to point your browser
to this URL, login with your Google account and grant jdrivesync the requested privileges. After having clicked on
'Accept' you will be redirected to a new page that displays an authentication token. Copy this token to
the command line and press Enter.

Now jdrivesync will create a file called .jdrivesync in your home directory. This is your authentication file.
As long as jdrivesync will find a valid authentication file in your home directory, it will use it. When you
want to use an alternative file, you can specify that on the command line:

     java -jar jdrivesync-0.3.0-jar-with-dependencies.jar -u -l "/home/user/documents" -a "my-auth-file.properties"

You can also exclude certain files from being uploaded/downloaded. Just create a text document that contains file name
patterns and provide the path to this file using the option -i:

    java -jar jdrivesync-0.3.0-jar-with-dependencies.jar -u -l "/home/user/documents" -i .jdrivesyncignore

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

##Development##

If you want to contribute, you can fork the project into your github account, create a feature branch
and later submit your changes in form of a pull request.

###Coding styles###

Please format your code according to the `Java Conventions` as they are provided by your IDE
([IntelliJ](https://confluence.jetbrains.com/display/IntelliJIDEA/Code+Style+and+Formatting),
[Eclipse](http://www.eclipseonetips.com/2009/12/13/automatically-format-and-cleanup-code-every-time-you-save/)) with the following changes:
* Line length: 180
* Newline: LF

###Build###

The sources are build using the [JDK](http://www.oracle.com/technetwork/java/javase/downloads/index.html) (>= 1.8)
and [maven](https://maven.apache.org/) (>= 3.2).
To build jdrivesync from scratch execute these commands:

```
git clone https://github.com/siom79/jdrivesync.git
cd jdrivesync
mvn install
```

After successful compilation you can execute jdrivesync with this command:

```
java -jar target/jdrivesync-0.3.0-jar-with-dependencies.jar
```

The deb package is created with the maven plugin [jdeb](https://github.com/tcurdt/jdeb) during the build and is located in the `target`
directory.

###Version control###

Version control follows the guidelines known as [gitflow](http://nvie.com/posts/a-successful-git-branching-model/). This means
basically that the master branch is always in a releasable state and that all work is done on feature branches that
are later on merged to the `develop` branch.

###Integration tests###

Integration tests are written with [JUnit](http://junit.org/) and reside under `src/test/java`. The class names have
to start with `IT` in order to distinguish them from normal unit tests. To run the integration tests place a valid authentication
file under `src/test/resources/.jdrivesync` and execute them with (as they are not executed during a normal build):

```
mvn test -Pintegration-test
```

Please use a separate Google account for these integration tests as they are written to delete and to list all
files of the account.

###Continous Integration###

[Travis CI](https://travis-ci.org) build: ![Build Status](https://travis-ci.org/siom79/jdrivesync.svg)
