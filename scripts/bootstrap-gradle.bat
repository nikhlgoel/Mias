@echo off
REM {Kid} V4 - Gradle Wrapper Bootstrap (Windows)
REM Run this from project root if gradle-wrapper.jar is missing

setlocal enabledelayedexpansion

set GRADLE_VERSION=8.12
set WRAPPER_DIR=%CD%\gradle\wrapper

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

REM Download gradle
echo 📥 Downloading Gradle %GRADLE_VERSION%...
cd /d "%CD%"
curl -fsSL -o gradle-%GRADLE_VERSION%-bin.zip https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip

if %ERRORLEVEL% NEQ 0 (
    echo ❌ Failed to download Gradle
    exit /b 1
)

REM Extract using tar (built-in on Windows 10+)
echo 📦 Extracting wrapper files...
tar -xf gradle-%GRADLE_VERSION%-bin.zip

if %ERRORLEVEL% NEQ 0 (
    echo ❌ Failed to extract Gradle
    exit /b 1
)

REM Copy wrapper jar
copy /Y "gradle-%GRADLE_VERSION%\lib\gradle-wrapper.jar" "%WRAPPER_DIR%\gradle-wrapper.jar"

REM Cleanup
rmdir /S /Q "gradle-%GRADLE_VERSION%"
del gradle-%GRADLE_VERSION%-bin.zip

REM Verify
if exist "%WRAPPER_DIR%\gradle-wrapper.jar" (
    echo ✅ Gradle wrapper successfully bootstrapped!
    dir "%WRAPPER_DIR%\gradle-wrapper.jar"
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
