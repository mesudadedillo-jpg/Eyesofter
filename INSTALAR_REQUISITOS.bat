@echo off
setlocal EnableExtensions
cd /d "%~dp0"

rem --- Pedir permisos de administrador si no los tiene ---
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo Solicitando permisos de administrador...
    powershell.exe -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
    exit /b
)

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\Instalar-Requisitos.ps1"
if %errorlevel% neq 0 (
    echo.
    echo [ERROR] La instalacion no termino. Revise los mensajes de arriba.
) else (
    echo.
    echo Ya puede abrir INICIAR_EYESOFTER.bat
)
pause
