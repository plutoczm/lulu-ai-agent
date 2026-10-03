@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0status-dev.ps1"
echo.
pause
endlocal
