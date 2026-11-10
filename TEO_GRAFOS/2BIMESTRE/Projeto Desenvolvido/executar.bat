@echo off
setlocal
chcp 65001 >nul
cd /d "%~dp0"

where javac >nul 2>nul
if errorlevel 1 (
    echo [ERRO] JDK nao encontrado. Instale Java 11 ou superior e inclua javac no PATH.
    pause
    exit /b 1
)

if not exist "demanda_unificada.csv" goto dados_ausentes
if not exist "topologia_extra.csv" goto dados_ausentes
if not exist "conexoes.csv" goto dados_ausentes

if not exist "out" mkdir "out"
echo Compilando o simulador...
javac -encoding UTF-8 -d out Main.java MalhaMetroferroviaria.java ModeloEpidemiologico.java TestesRede.java
if errorlevel 1 (
    echo [ERRO] Nao foi possivel compilar.
    pause
    exit /b 1
)

echo.
echo Iniciando menu do simulador...
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstdin.encoding=UTF-8 -cp out Main demanda_unificada.csv
set "RETORNO=%errorlevel%"
echo.
pause
exit /b %RETORNO%

:dados_ausentes
echo [ERRO] Os tres CSVs devem estar ao lado dos arquivos Java:
echo   demanda_unificada.csv
echo   topologia_extra.csv
echo   conexoes.csv
pause
exit /b 1
