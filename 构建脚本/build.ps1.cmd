@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build.ps1" %*
if /i not "%~1"=="nopause" pause
