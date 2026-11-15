@echo off
rem Starts RuneLite with the Compass Only plugin loaded, using the JDK unpacked next to this folder.
cd /d "%~dp0"
set "JAVA_HOME=%~dp0..\jdk\jdk-11.0.33+10"
if not exist "%JAVA_HOME%\bin\java.exe" (
	echo Could not find the JDK at %JAVA_HOME%
	echo Install a Java 11+ JDK and set JAVA_HOME, or edit this file to point at it.
	pause
	exit /b 1
)
call gradlew.bat run
pause
