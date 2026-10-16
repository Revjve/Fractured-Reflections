@echo off
rem Fractured Reflections - Dev Server (Windows). Includes Immersive Portals, LambDynamicLights, Simple Voice Chat.
setlocal
cd /d "%~dp0.."
title Fractured Reflections - Dev Server
call gradlew.bat help -q >nul 2>nul
if not exist "run-server\eula.txt" (
    echo.
    echo  The dev server needs the Minecraft EULA accepted: read https://aka.ms/MinecraftEULA
    echo  and set accept_minecraft_eula=true in gradle.properties, then run this again.
    echo.
    pause
    exit /b 1
)
call gradlew.bat runServer
if errorlevel 1 pause
