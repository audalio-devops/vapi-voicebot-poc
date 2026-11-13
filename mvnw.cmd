@echo off
set "JAVA_HOME=C:\Java\jdk-25.0.2"
set "MAVEN_HOME=D:\Tools\apache-maven-3.6.3"

if not exist "%JAVA_HOME%\bin\java.exe" (
    echo JDK 25 was not found at "%JAVA_HOME%\bin\java.exe".
    exit /b 1
)

if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
    echo Maven 3.6.3 was not found at "%MAVEN_HOME%\bin\mvn.cmd".
    exit /b 1
)

call "%MAVEN_HOME%\bin\mvn.cmd" %*
exit /b %ERRORLEVEL%
