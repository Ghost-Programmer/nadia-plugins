@echo off
setlocal enabledelayedexpansion

REM ============================================================================
REM Nadia Plugins Builder & Packager (Windows Batch)
REM ============================================================================
REM Default version is stored in gradle.properties (pluginsVersion / pluginApiVersion
REM and optional per-plugin <plugin-name>.version).
REM
REM Builds all PF4J plugin subprojects and collects final plugin JAR files into:
REM   dist\<plugin-id>-<version>.jar
REM
REM Usage:
REM   build.bat                         Build using versions in gradle.properties
REM   build.bat --version 1.2.0         Build all plugins as 1.2.0 (one-off override)
REM   build.bat --set-version 1.2.0     Save pluginsVersion=1.2.0 to gradle.properties and build
REM   build.bat --plugin <name> --set-version 1.3.0
REM                                     Save <name>.version=1.3.0 to gradle.properties and build
REM   build.bat --api-version 1.0.0     Override host nadia-plugin-api version
REM   build.bat --with-tests            Run unit & integration tests before packaging
REM   build.bat --install               Also install built JARs into ~/.nadia/plugins
REM ============================================================================

cd /d "%~dp0"

set "GRADLE_CMD=gradlew.bat"
if not exist "gradlew.bat" set "GRADLE_CMD=gradle"

set "RUN_TESTS=0"
set "INSTALL_PLUGINS=0"
set "TARGET_PLUGIN="
set "OVERRIDE_VER="
set "PERSIST_VER="
set "OVERRIDE_API_VER="
set "EXTRA_ARGS="

:parse_args
if "%~1"=="" goto args_done
if /i "%~1"=="--with-tests" goto opt_with_tests
if /i "%~1"=="--install" goto opt_install
if /i "%~1"=="--plugin" goto opt_plugin
if /i "%~1"=="--version" goto opt_version
if /i "%~1"=="--set-version" goto opt_set_version
if /i "%~1"=="--api-version" goto opt_api_version
set "EXTRA_ARGS=!EXTRA_ARGS! %1"
shift
goto parse_args

:opt_with_tests
set "RUN_TESTS=1"
shift
goto parse_args

:opt_install
set "INSTALL_PLUGINS=1"
shift
goto parse_args

:opt_plugin
if "%~2"=="" goto err_missing_val
set "TARGET_PLUGIN=%~2"
shift
shift
goto parse_args

:opt_version
if "%~2"=="" goto err_missing_val
set "OVERRIDE_VER=%~2"
shift
shift
goto parse_args

:opt_set_version
if "%~2"=="" goto err_missing_val
set "PERSIST_VER=%~2"
shift
shift
goto parse_args

:opt_api_version
if "%~2"=="" goto err_missing_val
set "OVERRIDE_API_VER=%~2"
shift
shift
goto parse_args

:err_missing_val
echo [Nadia Plugins Builder] ERROR: Option "%~1" requires an argument value.
exit /b 1

:args_done
if defined PERSIST_VER (
    set "TARGET_PROP_KEY=pluginsVersion"
    if defined TARGET_PLUGIN set "TARGET_PROP_KEY=!TARGET_PLUGIN!.version"
    call :update_gradle_prop "!TARGET_PROP_KEY!" "!PERSIST_VER!"
    if !ERRORLEVEL! neq 0 exit /b !ERRORLEVEL!
)

if defined OVERRIDE_VER (
    if defined TARGET_PLUGIN (
        set "EXTRA_ARGS=!EXTRA_ARGS! -P!TARGET_PLUGIN!.version=!OVERRIDE_VER!"
    ) else (
        set "EXTRA_ARGS=!EXTRA_ARGS! -PpluginsVersionOverride=!OVERRIDE_VER!"
    )
)
if defined OVERRIDE_API_VER set "EXTRA_ARGS=!EXTRA_ARGS! -PpluginApiVersion=!OVERRIDE_API_VER!"

set "EFFECTIVE_API_VER=1.0.0"
set "EFFECTIVE_VER=1.0.0"
if exist "gradle.properties" (
    for /f "usebackq tokens=1,* delims==" %%A in ("gradle.properties") do (
        if "%%A"=="pluginApiVersion" set "EFFECTIVE_API_VER=%%B"
        if "%%A"=="pluginsVersion" set "EFFECTIVE_VER=%%B"
    )
)
if defined OVERRIDE_API_VER set "EFFECTIVE_API_VER=!OVERRIDE_API_VER!"
if defined OVERRIDE_VER set "EFFECTIVE_VER=!OVERRIDE_VER!"
if defined PERSIST_VER set "EFFECTIVE_VER=!PERSIST_VER!"

set "M2_API_DIR=%USERPROFILE%\.m2\repository\com\nadia\nadia-plugin-api\!EFFECTIVE_API_VER!"
if not exist "!M2_API_DIR!\nadia-plugin-api-!EFFECTIVE_API_VER!.jar" (
    if exist "..\nadia\gradlew.bat" (
        echo [Nadia Plugins Builder] Publishing nadia-plugin-api:!EFFECTIVE_API_VER! to mavenLocal from ..\nadia...
        pushd "..\nadia"
        call gradlew.bat :nadia-plugin-api:publishToMavenLocal -PpluginApiVersion=!EFFECTIVE_API_VER!
        set "API_PUB_EXIT=!ERRORLEVEL!"
        popd
        if !API_PUB_EXIT! neq 0 (
            echo [Nadia Plugins Builder] ERROR: Failed to publish nadia-plugin-api ^(exit code !API_PUB_EXIT!^).
            exit /b !API_PUB_EXIT!
        )
    )
)

set "INSTALL_TASK="
if "!INSTALL_PLUGINS!"=="1" set "INSTALL_TASK=installPlugin"

echo ============================================================================
echo [Nadia Plugins Builder] Working Directory: %CD%
echo [Nadia Plugins Builder] Target Plugin Version: !EFFECTIVE_VER! ^(API v!EFFECTIVE_API_VER!^)
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
echo [Nadia Plugins Builder] Build Completed Successfully
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

:update_gradle_prop
set "PROP_KEY=%~1"
set "PROP_VAL=%~2"
echo [Nadia Plugins Builder] Updating %PROP_KEY%=%PROP_VAL% in gradle.properties...
powershell -NoProfile -Command "$p='gradle.properties'; $k=$env:PROP_KEY; $v=$env:PROP_VAL; $esc=[regex]::Escape($k); $c = if (Test-Path $p) { [IO.File]::ReadAllText($p) } else { '' }; if ($c -match ('(?m)^' + $esc + '=')) { $c = [regex]::Replace($c, ('(?m)^' + $esc + '=.*$'), ($k + '=' + $v)) } else { $c = $c.TrimEnd() + \"`r`n\" + $k + '=' + $v + \"`r`n\" }; [IO.File]::WriteAllText($p, $c, (New-Object Text.UTF8Encoding($false)))"
exit /b %ERRORLEVEL%
