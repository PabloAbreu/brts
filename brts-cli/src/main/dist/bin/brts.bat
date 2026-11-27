@echo off
setlocal

for %%I in ("%~dp0..") do set "INSTALL_ROOT=%%~fI"
set "LAUNCHER_PATH=%~f0"
set "CONFIG_EXAMPLE=%INSTALL_ROOT%\etc\brts.conf.example"
set "CONFIG_FILE=%INSTALL_ROOT%\etc\brts.conf"
set "ASSETS_DIR=%INSTALL_ROOT%\assets"
set "CLI_JAR="
for %%I in ("%INSTALL_ROOT%\lib\brts-cli-*.jar") do if exist "%%~fI" set "CLI_JAR=%%~fI"

if defined JAVA_HOME (
    set "JAVA_COMMAND=%JAVA_HOME%\bin\java.exe"
    if not exist "%JAVA_HOME%\bin\java.exe" (
        >&2 echo BRTS requires Java 21 or newer, but JAVA_HOME does not contain bin\java.exe: %JAVA_HOME%
        exit /b 1
    )
) else (
    where java.exe >nul 2>nul
    if errorlevel 1 (
        >&2 echo BRTS requires Java 21 or newer, but java.exe was not found on PATH.
        exit /b 1
    )
    set "JAVA_COMMAND=java.exe"
)

"%JAVA_COMMAND%" -version >nul 2>&1
if errorlevel 1 (
    >&2 echo Unable to determine the Java version using: %JAVA_COMMAND%
    exit /b 1
)

set "JAVA_VERSION="
for /f "tokens=1,2,3" %%A in ('"%JAVA_COMMAND%" -version 2^>^&1') do if not defined JAVA_VERSION if /i "%%B"=="version" (set "JAVA_VERSION=%%~C") else set "JAVA_VERSION=%%~B"
if not defined JAVA_VERSION (
    >&2 echo Unable to parse the Java version reported by: %JAVA_COMMAND%
    exit /b 1
)

for /f "tokens=1,2 delims=.-+_" %%A in ("%JAVA_VERSION%") do if "%%A"=="1" (set "JAVA_MAJOR=%%B") else set "JAVA_MAJOR=%%A"
if not defined JAVA_MAJOR (
    >&2 echo Unable to parse the Java version from: %JAVA_VERSION%
    exit /b 1
)
if %JAVA_MAJOR% LSS 21 (
    >&2 echo BRTS requires Java 21 or newer; detected Java %JAVA_MAJOR% using: %JAVA_COMMAND%
    exit /b 1
)

if not exist "%CONFIG_FILE%" (
    if not exist "%CONFIG_EXAMPLE%" (
        >&2 echo BRTS configuration example not found: %CONFIG_EXAMPLE%
        exit /b 1
    )
    copy /Y "%CONFIG_EXAMPLE%" "%CONFIG_FILE%" >nul
    if errorlevel 1 (
        >&2 echo Unable to create BRTS configuration file: %CONFIG_FILE%
        exit /b 1
    )
)

if not exist "%CLI_JAR%" (
    >&2 echo Platform-specific BRTS CLI jar not found in: %INSTALL_ROOT%\lib
    exit /b 1
)

"%JAVA_COMMAND%" "-Dbrts.command=%LAUNCHER_PATH%" "-Dbrts.conf=%CONFIG_FILE%" "-Dbrts.assets.dir=%ASSETS_DIR%" -jar "%CLI_JAR%" %*
exit /b %ERRORLEVEL%
