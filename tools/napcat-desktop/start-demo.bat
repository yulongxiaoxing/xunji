@echo off
setlocal
cd /d "%~dp0\..\.." || exit /b 1
"%USERPROFILE%\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe" "tools\napcat-desktop\desktop.py" start
pause
