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

##Contact##

If you want to report an issue or a feature request you can use the issue tracker at [github](https://github.com/siom79/jdrivesync/issues).
