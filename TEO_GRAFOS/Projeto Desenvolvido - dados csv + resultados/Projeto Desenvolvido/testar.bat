@echo off
setlocal
chcp 65001 >nul
cd /d "%~dp0"

where javac >nul 2>nul
if errorlevel 1 (
    echo [ERRO] JDK 11 ou superior nao encontrado no PATH.
    pause
    exit /b 1
)

if not exist "out" mkdir "out"
javac -encoding UTF-8 -d out Main.java MalhaMetroferroviaria.java ModeloEpidemiologico.java TestesRede.java
if errorlevel 1 (
    echo [ERRO] Falha ao compilar.
    pause
    exit /b 1
)

java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp out TestesRede .
set "RETORNO=%errorlevel%"
echo.
pause
exit /b %RETORNO%
