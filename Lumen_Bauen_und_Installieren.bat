@echo off
rem Lumen Client - baut die Mod, fuehrt die Tests aus und installiert sie in .minecraft\mods.
rem Optional: "Lumen_Bauen_und_Installieren.bat -NoInstall" baut nur (Testbuild).
setlocal
title Lumen Client - Build ^& Install
cd /d "%~dp0"
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\lumen-build.ps1" %*
set "LUMEN_RESULT=%ERRORLEVEL%"
echo.
pause
exit /b %LUMEN_RESULT%
