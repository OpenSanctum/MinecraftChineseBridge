@echo off
setlocal
set SCRIPT_DIR=%~dp0
python "%SCRIPT_DIR%build-release.py"
if errorlevel 1 exit /b %errorlevel%
