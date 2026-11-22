$ErrorActionPreference = 'Stop'
Write-Host 'Compilando EYESOFTER...' -ForegroundColor Cyan

$raiz = $PSScriptRoot
. (Join-Path $raiz 'scripts\Herramientas.ps1')

# --- Java y Maven: se buscan en el equipo (ya no hay rutas fijas) ---
$jdk = Get-EyesofterJdk
if (-not $jdk) { throw 'No se encontro un JDK 17+ con jpackage. Ejecute INSTALAR_REQUISITOS.bat (como administrador).' }
$mvn = Get-EyesofterMaven
if (-not $mvn) { throw 'No se encontro Maven. Ejecute INSTALAR_REQUISITOS.bat (como administrador).' }

$env:JAVA_HOME = $jdk
$env:Path = "$jdk\bin;$env:Path"
$javac = Join-Path $jdk 'bin\javac.exe'
$jar = Join-Path $jdk 'bin\jar.exe'
$jpackage = Join-Path $jdk 'bin\jpackage.exe'
$java = Join-Path $jdk 'bin\java.exe'
Write-Host "JDK:   $jdk"
Write-Host "Maven: $mvn"

# --- Carpetas temporales y de salida ---
$temporal = Join-Path $raiz 'tmp\empaquetado-actual'
$clases = Join-Path $temporal 'classes'
$entrada = Join-Path $temporal 'input'
$modulosJavaFx = Join-Path $temporal 'javafx-modules'
$dependencias = Join-Path $temporal 'deps'
$salida = Join-Path $raiz 'release'

if (Test-Path $temporal) { Remove-Item -LiteralPath $temporal -Recurse -Force }
New-Item -ItemType Directory -Force -Path $clases, $entrada, $modulosJavaFx, $dependencias, $salida | Out-Null

# --- Dependencias: las versiones salen del pom.xml ---
# Antes se tomaban de una lista fija en .m2 (con OpenCV 4.5.1, que no trae FaceDetectorYN
# y hacia fallar la compilacion). Ahora Maven descarga y copia exactamente lo del pom.
Write-Host 'Resolviendo dependencias con Maven...' -ForegroundColor Yellow
& $mvn -B -q -f (Join-Path $raiz 'pom.xml') 'org.apache.maven.plugins:maven-dependency-plugin:3.6.1:copy-dependencies' '-DincludeScope=runtime' "-DoutputDirectory=$dependencias"
if ($LASTEXITCODE -ne 0) { throw 'Maven no pudo descargar las dependencias (revise la conexion a internet).' }

$jars = Get-ChildItem -LiteralPath $dependencias -Filter '*.jar'
$jarsJavaFx = @($jars | Where-Object { $_.Name -like 'javafx-*-win.jar' })
$jarsLibs = @($jars | Where-Object { $_.Name -notlike 'javafx-*' })
if ($jarsJavaFx.Count -lt 4) { throw "Faltan los modulos de JavaFX para Windows (se encontraron $($jarsJavaFx.Count))." }

$jarsJavaFx | Copy-Item -Destination $modulosJavaFx -Force
$jarsLibs | Copy-Item -Destination $entrada -Force

# --- Compilacion ---
Write-Host 'Compilando codigo fuente...' -ForegroundColor Yellow
$classpath = (@($jarsJavaFx) + @($jarsLibs) | ForEach-Object { $_.FullName }) -join ';'
$fuentes = Get-ChildItem (Join-Path $raiz 'src\main\java') -Recurse -Filter '*.java' | ForEach-Object { '"' + ($_.FullName -replace '\\', '/') + '"' }
$listaFuentes = Join-Path $temporal 'fuentes.txt'
[IO.File]::WriteAllLines($listaFuentes, $fuentes)

& $javac -proc:none -encoding UTF-8 --release 17 -cp $classpath -d $clases "@$listaFuentes"
if ($LASTEXITCODE -ne 0) { throw 'La compilacion fallo (ver errores arriba).' }

Copy-Item (Join-Path $raiz 'src\main\resources\*') $clases -Recurse -Force

# --- JAR con Class-Path (manifest con lineas de maximo 72 bytes) ---
Write-Host 'Creando archivo JAR...' -ForegroundColor Yellow
function Formatear-LineaManifest([string] $linea) {
    if ($linea.Length -le 70) { return $linea }
    $partes = New-Object System.Collections.Generic.List[string]
    $partes.Add($linea.Substring(0, 70))
    $resto = $linea.Substring(70)
    while ($resto.Length -gt 0) {
        $n = [Math]::Min(69, $resto.Length)
        $partes.Add(' ' + $resto.Substring(0, $n))
        $resto = $resto.Substring($n)
    }
    return ($partes -join "`r`n")
}
$nombresLibs = ($jarsLibs | ForEach-Object { $_.Name }) -join ' '
$manifestPath = Join-Path $temporal 'MANIFEST.MF'
$manifest = "Manifest-Version: 1.0`r`nMain-Class: com.eyesofter.MainApp`r`n" + (Formatear-LineaManifest "Class-Path: $nombresLibs") + "`r`n"
[IO.File]::WriteAllText($manifestPath, $manifest, [Text.Encoding]::ASCII)

& $jar --create --file (Join-Path $entrada 'eyesofter-app.jar') --manifest $manifestPath -C $clases .
if ($LASTEXITCODE -ne 0) { throw 'No se pudo crear el JAR.' }

# --- Imagen de aplicacion con jpackage ---
if (Test-Path (Join-Path $salida 'EYESOFTER')) {
    Remove-Item -LiteralPath (Join-Path $salida 'EYESOFTER') -Recurse -Force
}

# Modulos del runtime. Antes faltaba java.net.http (envio de tamizajes al portal) y
# jdk.crypto.ec (HTTPS). Solo se agregan los que existan en este JDK.
$deseados = @('java.base', 'java.desktop', 'java.logging', 'java.sql', 'java.net.http', 'java.naming',
              'java.xml', 'java.prefs', 'java.management', 'java.scripting', 'jdk.unsupported',
              'jdk.crypto.ec', 'jdk.crypto.cryptoki', 'jdk.localedata', 'jdk.charsets', 'jdk.zipfs')
$disponibles = (& $java --list-modules) | ForEach-Object { ($_ -split '@')[0].Trim() }
$modulos = @($deseados | Where-Object { $disponibles -contains $_ }) + @('javafx.controls', 'javafx.fxml')

Write-Host 'Empaquetando aplicacion con jpackage...' -ForegroundColor Green
$iconoIco = Join-Path $raiz 'src\main\resources\img\eyesofter.ico'
$argsJpackage = @(
    '--type', 'app-image',
    '--name', 'EYESOFTER',
    '--app-version', '1.0.0',
    '--input', $entrada,
    '--main-jar', 'eyesofter-app.jar',
    '--main-class', 'com.eyesofter.MainApp',
    '--dest', $salida,
    '--module-path', $modulosJavaFx,
    '--add-modules', ($modulos -join ','),
    '--java-options', '-Dfile.encoding=UTF-8'
)
if (Test-Path $iconoIco) { $argsJpackage += @('--icon', $iconoIco) }

& $jpackage $argsJpackage
if ($LASTEXITCODE -ne 0) { throw 'jpackage no pudo crear la aplicacion.' }

# --- Modelos (nunca datos clinicos) ---
Write-Host 'Copiando modelos de camara y voz...' -ForegroundColor Yellow
$directorioExe = Join-Path $salida 'EYESOFTER'
$directorioApp = Join-Path $directorioExe 'app'

# SOLO modelos. NUNCA se empaquetan la base clinica (eyesofter_local.db), la agenda, la
# configuracion (eyesofter_config.properties, que guarda el token del equipo) ni .env.
foreach ($recurso in @('modelo_opencv', 'modelo_tts', 'modelo_vosk')) {
    $origen = Join-Path $raiz $recurso
    if (Test-Path $origen) { Copy-Item $origen $directorioExe -Recurse -Force }
    else { Write-Host "  (no existe $recurso; ejecute INSTALAR_REQUISITOS.bat para descargarlo)" -ForegroundColor DarkYellow }
}

foreach ($sensible in @('eyesofter_local.db', 'eyesofter_local.db.antes-de-migracion.bak', 'citas_agenda.json', 'eyesofter_config.properties', '.env', 'eyesofter_export.sql')) {
    foreach ($carpeta in @($directorioExe, $directorioApp)) {
        $ruta = Join-Path $carpeta $sensible
        if (Test-Path $ruta) { Remove-Item -LiteralPath $ruta -Force }
    }
}

$marca = Join-Path $directorioApp 'VERSION_ACTUAL.txt'
Set-Content -LiteralPath $marca -Value (Get-Date -Format o) -Encoding UTF8

Write-Host '=========================================' -ForegroundColor Green
Write-Host " LISTO: $(Join-Path $directorioExe 'EYESOFTER.exe')" -ForegroundColor Green
Write-Host '=========================================' -ForegroundColor Green
