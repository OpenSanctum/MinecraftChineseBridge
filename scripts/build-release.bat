@echo off
setlocal
set SCRIPT_DIR=%~dp0
set SCRIPT_PY=%SCRIPT_DIR%build-release.py

where py >nul 2>nul
if %errorlevel%==0 (
	py -3 "%SCRIPT_PY%" %*
	exit /b %errorlevel%
)

where python >nul 2>nul
if %errorlevel%==0 (
	python "%SCRIPT_PY%" %*
	exit /b %errorlevel%
)

echo [build-release] Python not found. Install Python 3 or use py launcher.
exit /b 1
