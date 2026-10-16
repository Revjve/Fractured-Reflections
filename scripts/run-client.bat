@echo off
rem Fractured Reflections - Client 1 (Survivor) (Windows). Includes Immersive Portals, LambDynamicLights, Simple Voice Chat.
setlocal
cd /d "%~dp0.."
title Fractured Reflections - Client 1 (Survivor)
call gradlew.bat runClient
if errorlevel 1 pause
