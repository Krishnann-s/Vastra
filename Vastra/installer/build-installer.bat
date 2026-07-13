@echo off
setlocal

REM ============================================================
REM  Builds a Windows installer (Vastra-1.0.exe) for the app.
REM  Run this from the Vastra project folder (or double-click it
REM  from inside the installer\ folder - it finds the project
REM  root either way).
REM
REM  Requirements on THIS machine (the one building the installer):
REM    1. JDK 17+          (java -version to check)
REM    2. Maven            (mvn -version to check)
REM    3. WiX Toolset v3.x (jpackage needs this on Windows to
REM                          produce a .exe/.msi installer)
REM       Download: https://wixtoolset.org/releases/
REM       After installing, make sure candle.exe and light.exe
REM       are on your PATH (the WiX installer usually does this
REM       for you - restart your terminal after installing WiX
REM       if the build below can't find them).
REM
REM  The resulting installer does NOT require Java to already be
REM  installed on the machine it's installed on - a private Java
REM  runtime is bundled inside it automatically by jpackage.
REM ============================================================

cd /d "%~dp0.."

echo.
echo === Step 1/2: Building the application with Maven ===
call mvn clean package
if errorlevel 1 (
    echo.
    echo Maven build failed - fix the error above before continuing.
    exit /b 1
)

echo.
echo === Step 2/2: Building the Windows installer with jpackage ===

jpackage ^
  --type exe ^
  --name Vastra ^
  --app-version 1.0 ^
  --vendor "Vastra" ^
  --description "Billing and shop management for Vastra" ^
  --input target ^
  --main-jar Vastra-1.0.jar ^
  --main-class com.vastra.MainApp ^
  --icon installer\vastra.ico ^
  --dest target\installer ^
  --win-shortcut ^
  --win-menu ^
  --win-per-user-install

if errorlevel 1 (
    echo.
    echo jpackage failed - see the error above.
    echo Most common cause: WiX Toolset isn't installed, or candle.exe/light.exe
    echo aren't on your PATH. See the notes at the top of this script.
    exit /b 1
)

echo.
echo ============================================================
echo  Done. Installer created at:
echo    target\installer\Vastra-1.0.exe
echo.
echo  Copy that one file to any Windows PC and double-click it to
echo  install Vastra - no separate Java install needed there.
echo ============================================================

endlocal
