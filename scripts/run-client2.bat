@echo off
rem Fractured Reflections - Client 2 (Fractured) (Windows). Includes Immersive Portals, LambDynamicLights, Simple Voice Chat.
setlocal
cd /d "%~dp0.."
title Fractured Reflections - Client 2 (Fractured)
call gradlew.bat runClient2
if errorlevel 1 pause
