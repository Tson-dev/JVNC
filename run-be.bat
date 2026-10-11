@echo off
rem ===========================================================================
rem  DHOPM - chay Backend (Manager CLI/API, module dhopm-cli)
rem  Su dung:  run-be.bat                      -> serve --tcp (cong 7079 + stdio)
rem            run-be.bat serve [--port 7079]  -> mo cong TCP cho FE
rem            run-be.bat serve                -> JSONL tren stdio (test/CI)
rem            run-be.bat info --json          -> chay 1 lenh roi thoat
rem            run-be.bat window 0.9 --minOcc 0.000001 --json
rem  Yeu cau: da build truoc bang  run.bat -DskipTests install
rem ===========================================================================
setlocal EnableDelayedExpansion
set "JAVA_HOME=C:\Program Files\Java\jdk-25.0.4.1"
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo [ERROR] Khong tim thay JDK 25 tai %JAVA_HOME%
    exit /b 1
)
set "PATH=%JAVA_HOME%\bin;%PATH%"

set "ROOT=%~dp0"
set "CP=%ROOT%implementation\dhopm-cli\target\classes;%ROOT%implementation\dhopm-common\target\classes;%ROOT%implementation\dhopm-v1-standard\target\classes;%ROOT%implementation\dhopm-v2-minocc\target\classes;%ROOT%implementation\dhopm-v3-optimized\target\classes"

if not exist "%ROOT%implementation\dhopm-cli\target\classes" (
    echo [ERROR] Chua build. Chay truoc:  run.bat -DskipTests install
    exit /b 1
)

set "ARGS=%*"
if "%ARGS%"=="" set "ARGS=serve --tcp"

echo [DHOPM] ManagerMain !ARGS!
java -cp "!CP!" dhopm.cli.ManagerMain !ARGS!
exit /b %ERRORLEVEL%
