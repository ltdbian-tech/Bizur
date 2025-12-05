@echo off
setlocal

set JAVA_CMD=java
if defined JAVA_HOME set JAVA_CMD=%JAVA_HOME%\bin\java.exe

set CLASSPATH=%~dp0\gradle\wrapper\gradle-wrapper.jar

"%JAVA_CMD%" -Xmx64m -Xms64m -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
