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
echo   Cloudflared opcional: sem ele, o site abre localmente.
echo ================================================================
echo.
"%PYTHON_EXE%" %PYTHON_ARGS% -c "import sys;sys.exit(0 if sys.version_info >= (3,10) else 1)"
if errorlevel 1 (
  echo [ERRO] Necessario Python 3.10 ou superior.
  pause
  exit /b 1
)
rem Verifica se o Cloudflared esta instalado, sem exigir instalacao.
set "SERVIDOR_ARGS=--sem-tunel"
where cloudflared >nul 2>&1
if not errorlevel 1 set "SERVIDOR_ARGS="
if exist "%~dp0cloudflared.exe" set "SERVIDOR_ARGS="
if exist "%~dp0cloudflared" set "SERVIDOR_ARGS="
if exist "%LOCALAPPDATA%\Microsoft\WinGet\Links\cloudflared.exe" set "SERVIDOR_ARGS="

if defined SERVIDOR_ARGS (
  echo [MODO LOCAL] Cloudflared nao encontrado.
  echo [ACESSO] http://localhost:8000
) else (
  echo [MODO PUBLICO] Cloudflared encontrado.
  echo [ACESSO LOCAL] http://localhost:8000
)
echo.
"%PYTHON_EXE%" %PYTHON_ARGS% -u "%~dp0servidor.py" %SERVIDOR_ARGS%
echo.
echo Servidor encerrado. Pressione qualquer tecla para fechar.
pause >nul
endlocal