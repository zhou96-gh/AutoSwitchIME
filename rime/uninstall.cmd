@echo off
chcp 65001 >nul
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0Install-AutoSwitchIME.ps1" -Uninstall
set "scriptExitCode=%ERRORLEVEL%"
echo.
pause
exit /b %scriptExitCode%
