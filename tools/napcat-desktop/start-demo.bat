@echo off
setlocal
cd /d "%~dp0\..\.." || exit /b 1
where py.exe >nul 2>&1
if not errorlevel 1 (
    py -3 "tools\napcat-desktop\desktop.py" start %*
) else (
    where python.exe >nul 2>&1
    if not errorlevel 1 (
        python "tools\napcat-desktop\desktop.py" start %*
    ) else (
        if exist "%USERPROFILE%\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe" (
            "%USERPROFILE%\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe" "tools\napcat-desktop\desktop.py" start %*
        ) else (
            echo Python 3 is required. Install Python and enable its PATH option, then run this file again.
        )
    )
)
pause
