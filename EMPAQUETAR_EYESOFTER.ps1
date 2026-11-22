$ErrorActionPreference = 'Stop'
Write-Host 'Compilando EYESOFTER…' -ForegroundColor Cyan

$raiz = $PSScriptRoot
$jdk = Join-Path $env:ProgramFiles 'Microsoft\jdk-21.0.8.9-hotspot\bin'
$javac = Join-Path $jdk 'javac.exe'
$jar = Join-Path $jdk 'jar.exe'
$jpackage = Join-Path $jdk 'jpackage.exe'

# Verificar herramientas de Java
foreach ($herramienta in @($javac, $jar, $jpackage)) {
    if (-not (Test-Path $herramienta)) { throw "No se encontró: $herramienta" }
}

# Configuración de carpetas temporales y de salida
$temporal = Join-Path $raiz 'tmp\empaquetado-actual'
$clases = Join-Path $temporal 'classes'
$entrada = Join-Path $temporal 'input'
$modulosJavaFx = Join-Path $temporal 'javafx-modules'
$salida = Join-Path $raiz 'release'

if (Test-Path $temporal) { Remove-Item -LiteralPath $temporal -Recurse -Force }
New-Item -ItemType Directory -Force -Path $clases, $entrada, $modulosJavaFx | Out-Null

# Dependencias del proyecto
$dependencias = @(
    "$env:USERPROFILE\.m2\repository\org\openjfx\javafx-base\17.0.2\javafx-base-17.0.2-win.jar",
    "$env:USERPROFILE\.m2\repository\org\openjfx\javafx-controls\17.0.2\javafx-controls-17.0.2-win.jar",
    "$env:USERPROFILE\.m2\repository\org\openjfx\javafx-fxml\17.0.2\javafx-fxml-17.0.2-win.jar",
    "$env:USERPROFILE\.m2\repository\org\openjfx\javafx-graphics\17.0.2\javafx-graphics-17.0.2-win.jar",
    "$env:USERPROFILE\.m2\repository\com\google\code\gson\gson\2.10.1\gson-2.10.1.jar",
    "$env:USERPROFILE\.m2\repository\org\openpnp\opencv\4.5.1-2\opencv-4.5.1-2.jar",
    "$env:USERPROFILE\.m2\repository\net\java\dev\jna\jna\5.14.0\jna-5.14.0.jar",
    "$env:USERPROFILE\.m2\repository\com\alphacephei\vosk\0.3.32\vosk-0.3.32.jar",
    "$env:USERPROFILE\.m2\repository\org\xerial\sqlite-jdbc\3.42.0.0\sqlite-jdbc-3.42.0.0.jar",
    "$env:USERPROFILE\.m2\repository\org\apache\pdfbox\pdfbox\2.0.27\pdfbox-2.0.27.jar",
    "$env:USERPROFILE\.m2\repository\org\apache\pdfbox\fontbox\2.0.27\fontbox-2.0.27.jar",
    "$env:USERPROFILE\.m2\repository\commons-logging\commons-logging\1.2\commons-logging-1.2.jar"
)

foreach ($archivo in $dependencias) {
    if (-not (Test-Path $archivo)) { throw "Dependencia no encontrada: $archivo" }
}

$javafx = $dependencias | Where-Object { $_ -match 'javafx-' }
Copy-Item $javafx $modulosJavaFx -Force

Write-Host 'Compilando código fuente...' -ForegroundColor Yellow
$archivosJava = Get-ChildItem (Join-Path $raiz 'src\main\java') -Recurse -Filter '*.java' | Select-Object -ExpandProperty FullName
& $javac -proc:none -encoding UTF-8 -cp ($dependencias -join ';') -d $clases $archivosJava
if ($LASTEXITCODE -ne 0) { throw 'La compilación falló.' }

Copy-Item (Join-Path $raiz 'src\main\resources\*') $clases -Recurse -Force

Write-Host 'Creando archivo JAR y vinculando librerías externas...' -ForegroundColor Yellow
$libsNormales = $dependencias | Where-Object { $_ -notmatch 'javafx-' }
Copy-Item $libsNormales $entrada

# --- INICIO DE LA CORRECCIÓN MAGISTRAL (EL MANIFEST) ---
$nombresLibs = ($libsNormales | Split-Path -Leaf) -join ' '
$manifestPath = Join-Path $temporal 'MANIFEST.MF'
$manifestContenido = "Manifest-Version: 1.0`r`nMain-Class: com.eyesofter.MainApp`r`nClass-Path: $nombresLibs`r`n"
Set-Content -Path $manifestPath -Value $manifestContenido -Encoding Ascii

# Usamos el manifest recién creado para compilar el JAR
& $jar --create --file (Join-Path $entrada 'eyesofter-app.jar') --manifest $manifestPath -C $clases .
if ($LASTEXITCODE -ne 0) { throw 'No se pudo crear el JAR.' }
# --- FIN DE LA CORRECCIÓN ---

if (Test-Path (Join-Path $salida 'EYESOFTER')) {
    Remove-Item -LiteralPath (Join-Path $salida 'EYESOFTER') -Recurse -Force
}

Write-Host 'Empaquetando aplicación y recursos con jpackage…' -ForegroundColor Green
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
    '--add-modules', 'javafx.controls,javafx.fxml,java.sql,jdk.unsupported,java.desktop,java.logging',
    '--java-options', '-Deyesofter.home=$APPDIR'
)

if (Test-Path $iconoIco) {
    $argsJpackage += '--icon'
    $argsJpackage += $iconoIco
}

& $jpackage $argsJpackage
if ($LASTEXITCODE -ne 0) { throw 'jpackage no pudo crear la aplicación.' }

Write-Host 'Transfiriendo recursos clínicos y de IA...' -ForegroundColor Yellow
$directorioExe = Join-Path $salida 'EYESOFTER'
$directorioApp = Join-Path $salida 'EYESOFTER\app'

# SOLO modelos. NUNCA se empaquetan la base clínica (eyesofter_local.db), la agenda, la
# configuración (eyesofter_config.properties, que guarda el token del equipo) ni .env:
# quien instalara la aplicación recibiría los datos de los alumnos de este equipo.
# Cada instalación empieza vacía y se vincula con el portal desde Configuración.
$recursosExternos = @('modelo_opencv', 'modelo_tts', 'modelo_vosk')

foreach ($recurso in $recursosExternos) {
    $origen = Join-Path $raiz $recurso
    if (Test-Path $origen) { Copy-Item $origen $directorioExe -Recurse -Force }
}

# Por si quedaron de un empaquetado anterior: la distribución no debe llevar datos.
foreach ($sensible in @('eyesofter_local.db', 'eyesofter_local.db.antes-de-migracion.bak', 'citas_agenda.json', 'eyesofter_config.properties', '.env', 'eyesofter_export.sql')) {
    foreach ($carpeta in @($directorioExe, $directorioApp)) {
        $ruta = Join-Path $carpeta $sensible
        if (Test-Path $ruta) { Remove-Item -LiteralPath $ruta -Force }
    }
}

$marca = Join-Path $directorioApp 'VERSION_ACTUAL.txt'
Set-Content -LiteralPath $marca -Value (Get-Date -Format o) -Encoding UTF8

Write-Host "=========================================" -ForegroundColor Green
Write-Host " LISTO: $(Join-Path $salida 'EYESOFTER\EYESOFTER.exe')" -ForegroundColor Green
Write-Host "=========================================" -ForegroundColor Green