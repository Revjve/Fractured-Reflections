@echo off
rem ============================================================================
rem  Fractured Reflections - one-click build (Windows)
rem  Double-click this file. The finished mod jar is copied to the dist folder.
rem ============================================================================
setlocal
cd /d "%~dp0"
title Fractured Reflections - build

where java >nul 2>nul
if errorlevel 1 (
    echo.
    echo  Java was not found. Install a JDK 17 or 21 ^(for example Eclipse Temurin^)
    echo  from https://adoptium.net and run this file again.
    echo.
    pause
    exit /b 1
)

echo.
echo  Building Fractured Reflections... the first build downloads Minecraft and
echo  the mod dependencies and can take several minutes.
echo.
call gradlew.bat dist
if errorlevel 1 (
    echo.
    echo  BUILD FAILED - scroll up for the first error.
    echo.
    pause
    exit /b 1
)

echo.
echo  ===========================================================
echo   Done! Your mod:
for %%f in (dist\*.jar) do echo     %cd%\%%f
echo.
echo   Put it in .minecraft\mods next to Fabric API ^(1.20.1^).
echo   Recommended: Immersive Portals, LambDynamicLights,
echo   Simple Voice Chat.
echo  ===========================================================
echo.
pause
