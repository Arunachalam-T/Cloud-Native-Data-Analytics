@echo off
setlocal enabledelayedexpansion
title Cloud-Native Data Analytics Desktop App

echo ===================================================
echo  Starting Cloud-Native Data Analytics JavaFX UI
echo ===================================================

cd /d "%~dp0"

REM Read .env file if present
if exist .env (
    for /f "usebackq tokens=1,* delims==" %%A in (".env") do (
        set "line=%%A"
        if not "!line:~0,1!"=="#" (
            if not "%%A"=="" set "%%A=%%B"
        )
    )
)

if not defined JAVA_HOME (
    if exist "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot" (
        set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
    )
)
if defined JAVA_HOME set "PATH=%JAVA_HOME%\bin;%PATH%"
if exist "%USERPROFILE%\.maven\apache-maven-3.9.6\bin" (
    set "PATH=%USERPROFILE%\.maven\apache-maven-3.9.6\bin;%PATH%"
)

cd frontend
mvn javafx:run
pause
