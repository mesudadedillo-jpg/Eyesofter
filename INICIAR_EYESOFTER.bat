@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"
title EYESOFTER

rem ------------------------------------------------------------------
rem  Arranca EYESOFTER con java.exe del JDK (firmado por Microsoft).
rem  No usa EYESOFTER.exe porque Windows (Control inteligente de
rem  aplicaciones) bloquea ejecutables recien compilados sin firma.
rem ------------------------------------------------------------------

set "APPDIR=%~dp0release\EYESOFTER"

rem --- 1. Buscar un JDK 17+ ---
set "JDK="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JDK=%JAVA_HOME%"
if not defined JDK for /d %%D in ("%ProgramFiles%\Microsoft\jdk-*") do if exist "%%D\bin\java.exe" set "JDK=%%D"
if not defined JDK for /d %%D in ("%ProgramFiles%\Eclipse Adoptium\jdk-*") do if exist "%%D\bin\java.exe" set "JDK=%%D"
if not defined JDK for /d %%D in ("%ProgramFiles%\Java\jdk-*") do if exist "%%D\bin\java.exe" set "JDK=%%D"
if not defined JDK (
    echo [ERROR] No se encontro Java. Ejecute primero INSTALAR_REQUISITOS.bat
    pause
    exit /b 1
)
echo Java: %JDK%

rem --- 2. Compilar si falta algo de la aplicacion ---
set "COMPILAR="
if not exist "%APPDIR%\app\eyesofter-app.jar" set "COMPILAR=1"
if not exist "%APPDIR%\javafx" set "COMPILAR=1"
if "%~1"=="/compilar" set "COMPILAR=1"
rem Si el codigo fuente es mas nuevo que la ultima compilacion, se recompila.
if not defined COMPILAR (
    powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$m='%APPDIR%\app\VERSION_ACTUAL.txt'; if(-not (Test-Path $m)){exit 1}; $u=(Get-ChildItem '%~dp0src' -Recurse -File | Sort-Object LastWriteTime -Descending | Select-Object -First 1).LastWriteTime; if($u -gt (Get-Item $m).LastWriteTime){exit 1}; exit 0"
    if errorlevel 1 set "COMPILAR=1"
)
if defined COMPILAR (
    echo Compilando EYESOFTER, espere...
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0EMPAQUETAR_EYESOFTER.ps1"
    if errorlevel 1 (
        echo.
        echo [ERROR] La compilacion fallo. Revise los mensajes de arriba.
        pause
        exit /b 1
    )
)

rem --- 3. Iniciar ---
echo Iniciando EYESOFTER... (no cierre esta ventana mientras use la aplicacion)
cd /d "%APPDIR%"
"%JDK%\bin\java.exe" --module-path "%APPDIR%\javafx" --add-modules javafx.controls,javafx.fxml -Dfile.encoding=UTF-8 -cp "%APPDIR%\app\*" com.eyesofter.MainApp
if errorlevel 1 (
    echo.
    echo [ERROR] EYESOFTER se cerro con un error. Copie los mensajes de arriba.
    pause
)
