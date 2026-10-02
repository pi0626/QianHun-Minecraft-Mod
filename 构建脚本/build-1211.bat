@echo off
setlocal
set "TOOLS=%~dp0"
set "ROOT=%TOOLS%.."
set "JAVA_HOME=%TOOLS%jdk\jdk-21.0.12.1+1"

if not exist "%JAVA_HOME%\bin\java.exe" (
    echo [ERROR] JDK not found: %JAVA_HOME%
    goto :end
)

set "PATH=%JAVA_HOME%\bin;%PATH%"
echo ============================================
echo  Qianhun mod - MC 1.21.1 / Fabric
echo  JDK    : %JAVA_HOME%
echo  Gradle : %TOOLS%gradle-9.5.1
echo  Project: %ROOT%\qianhun_1211
echo ============================================
echo.

cd /d "%ROOT%\qianhun_1211"
call "%TOOLS%gradle-9.5.1\bin\gradle.bat" build --console=plain
set "RC=%ERRORLEVEL%"

echo.
if "%RC%"=="0" (
    echo [DONE] Build OK. Output: qianhun_1211\build\libs\
) else (
    echo [FAIL] exit code %RC%
)

:end
if /i not "%~1"=="nopause" pause
endlocal
