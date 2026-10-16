@echo off
rem Fractured Reflections - decompiled Minecraft sources for IntelliJ (Windows). Run once after importing into IntelliJ.
setlocal
cd /d "%~dp0.."
title Fractured Reflections - decompiled Minecraft sources for IntelliJ
call gradlew.bat genSources
if errorlevel 1 pause
