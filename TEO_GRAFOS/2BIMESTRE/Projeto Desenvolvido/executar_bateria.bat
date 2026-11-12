@echo off
setlocal
chcp 65001 >nul
cd /d "%~dp0"

echo ==========================================================
echo   BATERIA DE TESTES - REDE E SIMULACOES VIGIMETRO JAVA
echo ==========================================================

where javac >nul 2>nul
if errorlevel 1 (
    echo [ERRO] JDK 11 ou superior nao encontrado no PATH.
    pause
    exit /b 1
)

if not exist "out" mkdir "out"
if not exist "resultados" mkdir "resultados"

javac -encoding UTF-8 -d out Main.java MalhaMetroferroviaria.java ModeloEpidemiologico.java TestesRede.java
if errorlevel 1 (
    echo [ERRO] Falha na compilacao.
    pause
    exit /b 1
)

echo [1/2] Validando dados, caminhos, formulas e simulacao...
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp out TestesRede .
if errorlevel 1 (
    echo [ERRO] Verificacoes da rede falharam.
    pause
    exit /b 1
)

echo [2/2] Executando o menu com entrada predeterminada...
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp out Main demanda_unificada.csv < entrada_bateria.txt > resultados\log_bateria.txt 2>&1
if errorlevel 1 (
    echo [ERRO] Erro de execucao. Verifique resultados\log_bateria.txt
    pause
    exit /b 1
)

findstr /C:"Erro:" "resultados\log_bateria.txt" >nul
if not errorlevel 1 (
    echo [ERRO] O menu registrou um erro. Verifique resultados\log_bateria.txt
    pause
    exit /b 1
)

set "ETAPAS=0"
for /f %%N in ('findstr /C:"Arquivos salvos em:" "resultados\log_bateria.txt" ^| find /C ":"') do set "ETAPAS=%%N"
if not "%ETAPAS%"=="7" (
    echo [ERRO] Nem todos os experimentos foram concluidos. Etapas concluidas: %ETAPAS% de 7.
    pause
    exit /b 1
)

echo.
echo Verificacoes e simulacoes concluidas. Resultados em: %~dp0resultados
echo Log do menu: resultados\log_bateria.txt
pause
endlocal
