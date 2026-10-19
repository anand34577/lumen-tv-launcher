@echo off
rem Puts the TV back exactly as it was before Lumen + debloat (keeps Lumen installed but inactive).
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0restore.ps1" %*
pause
