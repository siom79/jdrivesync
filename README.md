# jdrivesync

jdrivesync is a simple command line tool that synchronizes a local file system structure to your Google Drive (and back):

    java -jar jdrivesync-0.4.0-jar-with-dependencies.jar -l "/home/siom79"

The website is located at [http://siom79.github.io/jdrivesync/](http://siom79.github.io/jdrivesync).

## Development

If you want to contribute, you can fork the project into your github account, create a feature branch
and later submit your changes in form of a pull request.

### Coding styles

Please format your code according to the `Java Conventions` as they are provided by your IDE
([IntelliJ](https://confluence.jetbrains.com/display/IntelliJIDEA/Code+Style+and+Formatting),
[Eclipse](http://www.eclipseonetips.com/2009/12/13/automatically-format-and-cleanup-code-every-time-you-save/)) with the following changes:
* Line length: 180
* Newline: LF

### Build

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

### Version control

Version control follows the guidelines known as [gitflow](http://nvie.com/posts/a-successful-git-branching-model/). This means
basically that the master branch is always in a releasable state and that all work is done on feature branches that
are later on merged to the `develop` branch.

### Integration tests

Integration tests are written with [JUnit](http://junit.org/) and reside under `src/test/java`. The class names have
to start with `IT` in order to distinguish them from normal unit tests. To run the integration tests place a valid authentication
file under `src/test/resources/.jdrivesync` and execute them with (as they are not executed during a normal build):

```
mvn test -Pintegration-test
```

Please use a separate Google account for these integration tests as they are written to delete and to list all
files of the account.

### Continous Integration

[Travis CI](https://travis-ci.org) build: ![Build Status](https://travis-ci.org/siom79/jdrivesync.svg)
