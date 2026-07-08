#!/bin/sh
: "${JAVA_HOME:=/usr/lib/jvm/temurin-21-jdk-amd64}"
CLASSPATH="$(dirname "$0")/gradle/wrapper/gradle-wrapper.jar"
exec "$JAVA_HOME/bin/java" -cp "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
