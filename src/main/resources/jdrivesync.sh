#!/bin/sh

# Setup the JVM
if [ "x$JAVA" = "x" ]; then
    if [ "x$JAVA_HOME" != "x" ]; then
        JAVA="$JAVA_HOME/bin/java"
    else
        JAVA="java"
    fi
fi

JAVA_OPTS="$JAVA_OPTS -XX:+UseCompressedOops"

if [ "$DEBUG_MODE" = "true" ]; then
    # Display our environment
    echo "========================================================================="
    echo "  JAVA: $JAVA"
    echo ""
    echo "  JAVA_OPTS: $JAVA_OPTS"
    echo "========================================================================="
fi

$JAVA $JAVA_OPTS -jar /opt/jdrivesync/jdrivesync.jar $@
