@echo off
rem Full undo: re-enables everything, restores the stock launcher and uninstalls Lumen.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0restore.ps1" -RemoveLumen %*
pause
