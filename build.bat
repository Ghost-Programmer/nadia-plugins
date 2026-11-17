@echo off
setlocal enabledelayedexpansion

REM ============================================================================
REM Nadia Plugins Builder & Packager (Windows Batch)
REM ============================================================================
REM Builds all PF4J plugin subprojects and collects final plugin JAR files into:
REM   dist\*.jar
REM
REM Usage:
REM   build.bat                Build all final plugin JARs into dist\ (skips tests)
REM   build.bat --with-tests   Run unit & integration tests before packaging
REM   build.bat --install      Also copy built plugin JARs into ~/.nadia/plugins
REM ============================================================================

cd /d "%~dp0"

set "GRADLE_CMD=gradlew.bat"
if not exist "gradlew.bat" (
    set "GRADLE_CMD=gradle"
)

set "RUN_TESTS=0"
set "INSTALL_PLUGINS=0"
set "EXTRA_ARGS="
:parse_args
if "%~1"=="" goto :args_done
if /i "%~1"=="--with-tests" (
    set "RUN_TESTS=1"
) else if /i "%~1"=="--install" (
    set "INSTALL_PLUGINS=1"
) else (
    set "EXTRA_ARGS=!EXTRA_ARGS! %1"
)
shift
goto :parse_args
:args_done

REM Ensure com.nadia:nadia-plugin-api is published to mavenLocal if ..\nadia is present
set "M2_API_DIR=%USERPROFILE%\.m2\repository\com\nadia\nadia-plugin-api\1.0.0"
if not exist "%M2_API_DIR%\nadia-plugin-api-1.0.0.jar" (
    if exist "..\nadia\gradlew.bat" (
        echo [Nadia Plugins Builder] Publishing nadia-plugin-api to mavenLocal from ..\nadia...
        pushd "..\nadia"
        call gradlew.bat :nadia-plugin-api:publishToMavenLocal
        set "API_PUB_EXIT=!ERRORLEVEL!"
        popd
        if !API_PUB_EXIT! neq 0 (
            echo [Nadia Plugins Builder] ERROR: Failed to publish nadia-plugin-api ^(exit code !API_PUB_EXIT!^).
            exit /b !API_PUB_EXIT!
        )
    )
)

set "INSTALL_TASK="
if "!INSTALL_PLUGINS!"=="1" (
    set "INSTALL_TASK=installPlugin"
)

echo ============================================================================
echo [Nadia Plugins Builder] Working Directory: %CD%
if "!RUN_TESTS!"=="1" (
    echo [Nadia Plugins Builder] Building all plugin JARs ^(with test suite^)...
    echo ============================================================================
    call "%GRADLE_CMD%" clean check packagePlugins !INSTALL_TASK! !EXTRA_ARGS!
) else (
    echo [Nadia Plugins Builder] Building all plugin JARs ^(skipping tests^)...
    echo ============================================================================
    call "%GRADLE_CMD%" clean packagePlugins !INSTALL_TASK! -x test !EXTRA_ARGS!
)

if !ERRORLEVEL! neq 0 (
    echo.
    echo [Nadia Plugins Builder] ERROR: Plugin build failed with exit code !ERRORLEVEL!.
    exit /b !ERRORLEVEL!
)

echo.
echo ============================================================================
echo [Nadia Plugins Builder] Build Completed Successfully!
echo ============================================================================
for %%F in ("dist\*.jar") do (
    if exist "%%~fF" (
        echo   Plugin JAR: %%~fF ^(%%~zF bytes^)
    )
)
if "!INSTALL_PLUGINS!"=="1" (
    echo   Installed to: %USERPROFILE%\.nadia\plugins
)
echo ============================================================================
exit /b 0

