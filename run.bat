@echo off
rem ===========================================================================
rem  DHOPM - chay toan bo test mot lenh (mac dinh: mvn test)
rem  Su dung:  run.bat                     -> mvn test
rem            run.bat clean test          -> truyen goal cho Maven
rem            run.bat -q test             -> flag + goal
rem            run.bat -Dtest=ReaderTest   -> tu them goal "test"
rem ===========================================================================
setlocal EnableDelayedExpansion
set "JAVA_HOME=C:\Program Files\Java\jdk-25.0.4.1"
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo [ERROR] Khong tim thay JDK 25 tai %JAVA_HOME%
    exit /b 1
)
set "PATH=%JAVA_HOME%\bin;%PATH%"

set "ARGS="
set "HASGOAL="
for %%A in (%*) do (
    set "tok=%%~A"
    set "ARGS=!ARGS! %%A"
    if not "!tok:~0,1!"=="-" set "HASGOAL=1"
)
if not defined HASGOAL set "ARGS=!ARGS! test"

echo [DHOPM] JAVA_HOME=%JAVA_HOME%
echo [DHOPM] mvn!ARGS!
call mvn -f "%~dp0implementation\pom.xml"!ARGS!
set "RC=%ERRORLEVEL%"
if not "%RC%"=="0" echo [DHOPM] LOI - mvn tra ve ma %RC%
exit /b %RC%