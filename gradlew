#!/usr/bin/env sh
set -eu
DIR="$(CDPATH= cd -- "$(dirname "$0")" && pwd)"
if [ -n "${BUILD_ANDROID_JAVA_HOME:-}" ] && [ -x "${BUILD_ANDROID_JAVA_HOME}/bin/java" ]; then
  export JAVA_HOME="${BUILD_ANDROID_JAVA_HOME}"
  export PATH="${JAVA_HOME}/bin:$PATH"
fi
if [ -n "${BUILD_ANDROID_GRADLE_BIN:-}" ] && [ -x "${BUILD_ANDROID_GRADLE_BIN}" ]; then
  exec "${BUILD_ANDROID_GRADLE_BIN}" "$@"
fi
if [ -f "$DIR/gradle/wrapper/gradle-wrapper.jar" ]; then
  exec java -Dorg.gradle.appname=gradlew -classpath "$DIR/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
fi
echo "build-android: missing gradle wrapper launcher and BUILD_ANDROID_GRADLE_BIN is not set" >&2
exit 1
