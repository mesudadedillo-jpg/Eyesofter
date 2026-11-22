# Instala lo necesario para compilar y ejecutar EYESOFTER en Windows.
# Se ejecuta desde INSTALAR_REQUISITOS.bat, que lo abre como administrador.
#   1. JDK 21 (Microsoft OpenJDK) si no hay un JDK 17+ con jpackage.
#   2. Apache Maven en la carpeta "herramientas" del proyecto si no esta instalado.
#   3. Modelo de voz Vosk en espanol (reconocimiento de voz offline).
#   4. Piper TTS + voz es_MX (opcional: si falla se usa la voz de Windows).
#   5. Descarga las librerias del pom.xml y compila la aplicacion.

param([switch] $SinCompilar)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'   # Invoke-WebRequest es muchisimo mas rapido sin barra
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

. (Join-Path $PSScriptRoot 'Herramientas.ps1')
$raiz = $script:RaizEyesofter
$herramientas = $script:CarpetaHerramientas
$descargas = Join-Path $herramientas 'descargas'
New-Item -ItemType Directory -Force -Path $herramientas, $descargas | Out-Null

function Paso([string] $texto) { Write-Host ''; Write-Host "==> $texto" -ForegroundColor Cyan }
function Ok([string] $texto) { Write-Host "    OK: $texto" -ForegroundColor Green }
function Aviso([string] $texto) { Write-Host "    AVISO: $texto" -ForegroundColor Yellow }

function Descargar([string] $url, [string] $destino) {
    if ((Test-Path -LiteralPath $destino) -and (Get-Item -LiteralPath $destino).Length -gt 0) { return }
    Write-Host "    Descargando $url"
    $parcial = "$destino.parcial"
    Invoke-WebRequest -Uri $url -OutFile $parcial -UseBasicParsing
    Move-Item -LiteralPath $parcial -Destination $destino -Force
}

function Actualizar-PathSesion {
    $maquina = [Environment]::GetEnvironmentVariable('Path', 'Machine')
    $usuario = [Environment]::GetEnvironmentVariable('Path', 'User')
    $env:Path = "$maquina;$usuario"
    $jh = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Machine')
    if ($jh) { $env:JAVA_HOME = $jh }
}

$esAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
Write-Host '============================================' -ForegroundColor Green
Write-Host ' EYESOFTER - Instalacion de requisitos' -ForegroundColor Green
Write-Host '============================================' -ForegroundColor Green
Write-Host " Carpeta del proyecto: $raiz"
if (-not $esAdmin) { Aviso 'No se esta ejecutando como administrador; si falta Java, la instalacion puede fallar.' }

# ---------------------------------------------------------------- 1. JDK
Paso 'Java (JDK 17 o superior con jpackage)'
$jdk = Get-EyesofterJdk
if (-not $jdk) {
    $instalado = $false
    $winget = Get-Command winget.exe -ErrorAction SilentlyContinue
    if ($winget) {
        Write-Host '    Instalando Microsoft OpenJDK 21 con winget...'
        & $winget.Source install --id Microsoft.OpenJDK.21 -e --silent --accept-package-agreements --accept-source-agreements --scope machine
        Actualizar-PathSesion
        $jdk = Get-EyesofterJdk
        $instalado = [bool]$jdk
    }
    if (-not $instalado) {
        Write-Host '    Descargando instalador MSI de Microsoft OpenJDK 21...'
        $msi = Join-Path $descargas 'microsoft-jdk-21-windows-x64.msi'
        Descargar 'https://aka.ms/download-jdk/microsoft-jdk-21-windows-x64.msi' $msi
        $p = Start-Process msiexec.exe -ArgumentList @('/i', "`"$msi`"", '/quiet', '/norestart', 'ADDLOCAL=FeatureMain,FeatureEnvironment,FeatureJarFileRunWith,FeatureJavaHome') -Wait -PassThru
        if ($p.ExitCode -ne 0 -and $p.ExitCode -ne 3010) { throw "El instalador de Java termino con codigo $($p.ExitCode). Ejecute este script como administrador." }
        Actualizar-PathSesion
        $jdk = Get-EyesofterJdk
    }
    if (-not $jdk) { throw 'No se pudo instalar Java. Instale manualmente "Microsoft Build of OpenJDK 21" y vuelva a ejecutar.' }
}
Ok "JDK en $jdk (version $(Get-VersionMayorJdk $jdk))"
$env:JAVA_HOME = $jdk
$env:Path = "$jdk\bin;$env:Path"

# ---------------------------------------------------------------- 2. Maven
Paso 'Apache Maven'
$mvn = Get-EyesofterMaven
if (-not $mvn) {
    $versionMaven = '3.9.9'
    $zip = Join-Path $descargas "apache-maven-$versionMaven-bin.zip"
    $urls = @(
        "https://dlcdn.apache.org/maven/maven-3/$versionMaven/binaries/apache-maven-$versionMaven-bin.zip",
        "https://archive.apache.org/dist/maven/maven-3/$versionMaven/binaries/apache-maven-$versionMaven-bin.zip"
    )
    $ok = $false
    foreach ($u in $urls) {
        try { Descargar $u $zip; $ok = $true; break } catch { Aviso "No se pudo descargar de $u" }
    }
    if (-not $ok) { throw 'No se pudo descargar Maven. Revise la conexion a internet.' }
    Expand-Archive -LiteralPath $zip -DestinationPath $herramientas -Force
    $mvn = Get-EyesofterMaven
    if (-not $mvn) { throw 'Maven se descargo pero no se encontro mvn.cmd.' }
}
Ok "Maven en $mvn"

# ---------------------------------------------------------------- 3. Vosk
Paso 'Modelo de reconocimiento de voz (Vosk, espanol)'
$carpetaVosk = Join-Path $raiz 'modelo_vosk'
$modeloVosk = Join-Path $carpetaVosk 'vosk-model-small-es-0.42'
if (-not (Test-Path -LiteralPath (Join-Path $modeloVosk 'am'))) {
    try {
        New-Item -ItemType Directory -Force -Path $carpetaVosk | Out-Null
        $zip = Join-Path $descargas 'vosk-model-small-es-0.42.zip'
        Descargar 'https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip' $zip
        Expand-Archive -LiteralPath $zip -DestinationPath $carpetaVosk -Force
        Ok "Modelo en $modeloVosk"
    } catch {
        Aviso "No se pudo descargar el modelo de voz ($($_.Exception.Message)). La app funcionara en modo manual (teclado)."
    }
} else { Ok 'Ya estaba instalado.' }

# ---------------------------------------------------------------- 4. Piper (opcional)
Paso 'Voz guia neuronal (Piper, opcional)'
$carpetaTts = Join-Path $raiz 'modelo_tts'
$piperExe = Join-Path $carpetaTts 'piper.exe'
$vozOnnx = Join-Path $carpetaTts 'es_MX-claude-high.onnx'
try {
    New-Item -ItemType Directory -Force -Path $carpetaTts | Out-Null
    if (-not (Test-Path -LiteralPath $piperExe)) {
        $zip = Join-Path $descargas 'piper_windows_amd64.zip'
        Descargar 'https://github.com/rhasspy/piper/releases/download/2023.11.14-2/piper_windows_amd64.zip' $zip
        $tmpPiper = Join-Path $descargas 'piper_extraido'
        if (Test-Path -LiteralPath $tmpPiper) { Remove-Item -LiteralPath $tmpPiper -Recurse -Force }
        Expand-Archive -LiteralPath $zip -DestinationPath $tmpPiper -Force
        $origen = Join-Path $tmpPiper 'piper'
        if (-not (Test-Path -LiteralPath $origen)) { $origen = $tmpPiper }
        Copy-Item -Path (Join-Path $origen '*') -Destination $carpetaTts -Recurse -Force
    }
    if (-not (Test-Path -LiteralPath $vozOnnx)) {
        $base = 'https://huggingface.co/rhasspy/piper-voices/resolve/main/es/es_MX/claude/high'
        Descargar "$base/es_MX-claude-high.onnx.json" "$vozOnnx.json"
        Descargar "$base/es_MX-claude-high.onnx" $vozOnnx
    }
    Ok "Piper en $carpetaTts"
} catch {
    Aviso "No se pudo instalar Piper ($($_.Exception.Message)). Se usara la voz de Windows."
}

# ---------------------------------------------------------------- 5. Dependencias y compilacion
Paso 'Descargando librerias del proyecto (pom.xml)'
& $mvn -B -q -f (Join-Path $raiz 'pom.xml') 'org.apache.maven.plugins:maven-dependency-plugin:3.6.1:resolve'
if ($LASTEXITCODE -ne 0) { throw 'Maven no pudo descargar las dependencias del pom.xml.' }
Ok 'Librerias listas.'

if (-not $SinCompilar) {
    Paso 'Compilando y empaquetando EYESOFTER'
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $raiz 'EMPAQUETAR_EYESOFTER.ps1')
    if ($LASTEXITCODE -ne 0) { throw 'El empaquetado fallo. Revise los mensajes de arriba.' }
}

Write-Host ''
Write-Host '============================================' -ForegroundColor Green
Write-Host ' Todo listo. Abra INICIAR_EYESOFTER.bat' -ForegroundColor Green
Write-Host '============================================' -ForegroundColor Green
