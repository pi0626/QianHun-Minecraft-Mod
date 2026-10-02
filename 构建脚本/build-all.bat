@echo off
setlocal
set "TOOLS=%~dp0"

echo ############ STEP 1 : MC 1.21.1 ############
call "%TOOLS%build-1211.bat" nopause
set "RC1=%ERRORLEVEL%"

echo.
echo ############ STEP 2 : MC 26.2 ############
call "%TOOLS%build-262.bat" nopause
set "RC2=%ERRORLEVEL%"

echo.
echo ============================================
echo  1.21.1 exit code : %RC1%
echo  26.2   exit code : %RC2%
echo ============================================

if /i not "%~1"=="nopause" pause
endlocal
