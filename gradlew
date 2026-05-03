#!/bin/sh
# Gradle wrapper script for Unix-like systems.
# Generated for Project {Kid} — Gradle 8.12
#
# NOTE: gradle-wrapper.jar must be present in gradle/wrapper/ for this script to work.
# Run `gradle wrapper` from Android Studio terminal to generate it, or copy from any
# existing Android project's gradle/wrapper/gradle-wrapper.jar.

APP_NAME="Gradle"
APP_BASE_NAME=$(basename "$0")

# Determine the Java home to use
if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

# Resolve the script's own directory
PRG="$0"
while [ -h "$PRG" ] ; do
    ls=$(ls -ld "$PRG")
    link=$(expr "$ls" : '.*-> \(.*\)$')
    if expr "$link" : '/.*' > /dev/null; then
        PRG="$link"
    else
        PRG=$(dirname "$PRG")"/$link"
    fi
done
APP_HOME=$(dirname "$PRG")

exec "$JAVACMD" \
    -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" \
    org.gradle.wrapper.GradleWrapperMain "$@"
