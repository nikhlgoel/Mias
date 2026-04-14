@echo off
REM {Kid} V4 - Gradle Wrapper Bootstrap (Windows)
REM Run this from project root if gradle-wrapper.jar is missing

setlocal enabledelayedexpansion

set GRADLE_VERSION=8.13
set WRAPPER_DIR=%CD%\gradle\wrapper
set WRAPPER_JAR=%WRAPPER_DIR%\gradle-wrapper.jar
set WRAPPER_URL=https://raw.githubusercontent.com/gradle/gradle/v8.12.0/gradle/wrapper/gradle-wrapper.jar

echo 🔧 Bootstrapping Gradle Wrapper (v%GRADLE_VERSION%)...
echo.

REM Check if curl is available
where curl >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo ❌ ERROR: curl not found. Please install Git for Windows or use WSL.
    exit /b 1
)

REM Create wrapper directory
if not exist "%WRAPPER_DIR%" mkdir "%WRAPPER_DIR%"

REM Download wrapper jar directly (fast + reliable)
echo 📥 Downloading gradle-wrapper.jar...
cd /d "%CD%"
curl -fL --retry 3 --retry-delay 2 -o "%WRAPPER_JAR%" "%WRAPPER_URL%"

if %ERRORLEVEL% NEQ 0 (
    echo ❌ Failed to download gradle-wrapper.jar
    exit /b 1
)

REM Verify
if exist "%WRAPPER_JAR%" (
    echo ✅ Gradle wrapper successfully bootstrapped!
    dir "%WRAPPER_JAR%"
) else (
    echo ❌ Failed to bootstrap gradle wrapper
    exit /b 1
)

echo.
echo 🚀 Next steps (in Command Prompt or PowerShell):
echo    cd %CD%
echo    gradlew.bat assembleDebug
echo.
echo 💡 IMPORTANT: Run commands from the PROJECT ROOT (%CD%), not gradle/ folder!
echo.
pause
