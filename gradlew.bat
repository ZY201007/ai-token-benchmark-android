@rem Gradle Wrapper 启动脚本（Windows）
@echo off
set DIRNAME=%~dp0
set APP_HOME=%DIRNAME%

if defined JAVA_HOME (
    set JAVA_CMD=%JAVA_HOME%\bin\java.exe
) else (
    set JAVA_CMD=java
)

set CLASSPATH=%APP_HOME%gradle\wrapper\gradle-wrapper.jar
"%JAVA_CMD%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
