@echo off
setlocal
set DIR=%~dp0
if defined BUILD_ANDROID_JAVA_HOME (
  set JAVA_HOME=%BUILD_ANDROID_JAVA_HOME%
  set PATH=%JAVA_HOME%\bin;%PATH%
)
if defined BUILD_ANDROID_GRADLE_BIN if exist "%BUILD_ANDROID_GRADLE_BIN%" (
  call "%BUILD_ANDROID_GRADLE_BIN%" %*
  exit /b %ERRORLEVEL%
)
if exist "%DIR%gradle\wrapper\gradle-wrapper.jar" (
  java -Dorg.gradle.appname=gradlew -classpath "%DIR%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
  exit /b %ERRORLEVEL%
)
echo build-android: missing gradle wrapper launcher and BUILD_ANDROID_GRADLE_BIN is not set 1>&2
exit /b 1
