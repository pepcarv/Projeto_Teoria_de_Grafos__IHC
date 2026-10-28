@echo off
setlocal
cd /d "%~dp0"
chcp 65001 >nul
set "PYTHONUTF8=1"
set "PYTHONIOENCODING=utf-8"
set "PYTHON_EXE="
set "PYTHON_ARGS="
if exist "%LOCALAPPDATA%\Programs\Python\Python310\python.exe" set "PYTHON_EXE=%LOCALAPPDATA%\Programs\Python\Python310\python.exe"
if not defined PYTHON_EXE if exist "%LOCALAPPDATA%\Programs\Python\Python313\python.exe" set "PYTHON_EXE=%LOCALAPPDATA%\Programs\Python\Python313\python.exe"
if not defined PYTHON_EXE if exist "%LOCALAPPDATA%\Programs\Python\Python312\python.exe" set "PYTHON_EXE=%LOCALAPPDATA%\Programs\Python\Python312\python.exe"
if not defined PYTHON_EXE if exist "%LOCALAPPDATA%\Programs\Python\Python311\python.exe" set "PYTHON_EXE=%LOCALAPPDATA%\Programs\Python\Python311\python.exe"
if not defined PYTHON_EXE (
  where py >nul 2>&1
  if not errorlevel 1 (
    set "PYTHON_EXE=py"
    set "PYTHON_ARGS=-3"
  )
)
if not defined PYTHON_EXE (
  where python >nul 2>&1
  if not errorlevel 1 set "PYTHON_EXE=python"
)
if not defined PYTHON_EXE (
  echo [ERRO] Python nao encontrado. Instale Python 3.10 ou superior.
  pause
  exit /b 1
)
echo.
echo ================================================================
echo   VIGIMETRO
echo   O navegador sera aberto automaticamente.
echo   O link publico e o QR Code ficam disponiveis apos conectar.
echo ================================================================
echo.
"%PYTHON_EXE%" %PYTHON_ARGS% -c "import sys;sys.exit(0 if sys.version_info >= (3,10) else 1)"
if errorlevel 1 (
  echo [ERRO] Necessario Python 3.10 ou superior.
  pause
  exit /b 1
)
"%PYTHON_EXE%" %PYTHON_ARGS% -u "%~dp0servidor.py"
echo.
echo Servidor encerrado. Pressione qualquer tecla para fechar.
pause >nul
endlocal
