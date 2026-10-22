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

if not defined API_BASE_URL (
    set "API_BASE_URL=https://cloud-native-data-analytics.onrender.com"
)

echo Connecting to Cloud Backend: %API_BASE_URL%
echo.

if not defined JAVA_HOME (
    if exist "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot" (
        set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
    )
)
if defined JAVA_HOME set "PATH=%JAVA_HOME%\bin;%PATH%"

if exist "%USERPROFILE%\.maven\apache-maven-3.9.6\bin" (
    set "PATH=%USERPROFILE%\.maven\apache-maven-3.9.6\bin;%PATH%"
)

where mvn >nul 2>&1
if %errorlevel% neq 0 (
    echo [!] Maven not found on PATH. Attempting automatic setup...
    powershell -NoProfile -ExecutionPolicy Bypass -Command ^
        "$mDir = Join-Path $env:USERPROFILE '.maven'; if (-not (Test-Path $mDir)) { New-Item -ItemType Directory -Path $mDir | Out-Null }; $zip = Join-Path $mDir 'maven.zip'; Invoke-WebRequest -Uri 'https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.zip' -OutFile $zip; Expand-Archive -Path $zip -DestinationPath $mDir -Force; Remove-Item $zip -Force"
    set "PATH=%USERPROFILE%\.maven\apache-maven-3.9.6\bin;%PATH%"
)

cd frontend
mvn javafx:run -DAPI_BASE_URL=!API_BASE_URL!
pause
