@echo off
setlocal
cd /d "%~dp0"
powershell.exe -NoProfile -STA -ExecutionPolicy Bypass -File "%~dp0install_local_firebase_config.ps1"
set "RESULT=%ERRORLEVEL%"
echo.
if not "%RESULT%"=="0" echo Configurazione interrotta. Non installare APK.
echo Premi un tasto per chiudere.
pause >nul
exit /b %RESULT%
