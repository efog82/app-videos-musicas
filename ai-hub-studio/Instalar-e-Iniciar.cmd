@echo off
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0server\install-windows.ps1" %*
if errorlevel 1 echo O servidor nao iniciou. Consulte o diagnostico acima.
pause
