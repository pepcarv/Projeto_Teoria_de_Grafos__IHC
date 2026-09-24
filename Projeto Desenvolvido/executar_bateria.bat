:: BATERIA DE TESTES USANDO O ARQUIVO DE ENTRADA DE TXT (versão final)


:: -> usado para exemplificar as funcionalidade do simulador

:: Referencias para montar o bat:
:: - https://ss64.com/nt/chcp.html: Justifica o uso de 'chcp 65001' para forçar o 
::console do Windows a renderizar caracteres UTF-8, evitando falhas de acentuação nos logs.

:: - https://ss64.com/nt/syntax-args.html: Explica o uso da sintaxe '%~dp0', que garante 
::que os comandos 'cd' e de leitura encontrem os arquivos na mesma pasta em que o script está salvo.

:: - https://docs.oracle.com/en/java/javase/11/tools/javac.html: Documentação oficial consultada 
::para as flags de compilação e execução via linha de comando (como '-d out', '-cp' e '-Dfile.encoding').

:: - https://ss64.com/nt/syntax-redirection.html: Base para o redirecionamento de fluxo usado na 
::bateria de testes, explicando como injetar respostas de um arquivo ('< entrada_bateria.txt') e unificar 
::saída e erros em um log ('> resultados\log_bateria.txt 2>&1').

@echo off
setlocal
chcp 65001 >nul
cd /d "%~dp0"

echo ==========================================================
echo  BATERIA COMPLETA DE TESTES - SIMULADOR COVID-19 METRO/CPTM
echo ==========================================================

where javac >nul 2>nul
if errorlevel 1 (
    echo [ERRO] Java JDK nao encontrado. Instale o JDK 11 ou superior e adicione ao PATH.
    pause & exit /b 1
)
if not exist "demanda_unificada.csv" (
    echo [ERRO] demanda_unificada.csv nao encontrado em %~dp0
    pause & exit /b 1
)
if not exist "entrada_bateria.txt" (
    echo [ERRO] entrada_bateria.txt nao encontrado em %~dp0
    pause & exit /b 1
)

echo [1/3] Compilando...
if not exist out mkdir out
javac -encoding UTF-8 -d out *.java
if errorlevel 1 ( echo [ERRO] Falha na compilacao. & pause & exit /b 1 )

if not exist resultados mkdir resultados

echo [2/3] Executando a bateria (pode levar alguns minutos)...
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp out Main demanda_unificada.csv < entrada_bateria.txt > resultados\log_bateria.txt 2>&1
if errorlevel 1 ( echo [ERRO] A execucao falhou. Veja resultados\log_bateria.txt & pause & exit /b 1 )

echo [3/3] Concluido.
echo.
echo Resultados salvos em: %~dp0resultados
echo Log completo do terminal: resultados\log_bateria.txt
echo.
echo Experimentos executados:
echo   carga_dados, mapa_demanda, E1/E2 percurso (x3), E3/E6 disseminacao (x4),
echo   E4 comparacao mensal, E5 transmissibilidade, E2 protecao, ranking de estacoes
echo.
pause
endlocal
