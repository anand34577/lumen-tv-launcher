@echo off
rem Saves a snapshot of the TV's current package/launcher/settings state into ..\backups
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0backup.ps1" %*
pause
