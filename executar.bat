@echo off
setlocal
chcp 65001 >nul
cd /d "%~dp0"

where javac >nul 2>nul
if errorlevel 1 (
    echo [ERRO] Java JDK nao encontrado. Instale o JDK 11 ou superior e adicione ao PATH.
    pause
    exit /b 1
)

if not exist "demanda_unificada.csv" (
    echo [ERRO] demanda_unificada.csv nao encontrado em %~dp0
    pause
    exit /b 1
)

echo Compilando...
javac -encoding UTF-8 -d out *.java
if errorlevel 1 (
    echo [ERRO] Falha na compilacao.
    pause
    exit /b 1
)

java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstdin.encoding=UTF-8 -cp out Main demanda_unificada.csv

echo.
pause
endlocal
