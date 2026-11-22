# Funciones compartidas por INSTALAR_REQUISITOS, EMPAQUETAR e INICIAR.
# Localizan el JDK y Maven sin depender de una ruta fija (antes estaba amarrado a
# "C:\Program Files\Microsoft\jdk-21.0.8.9-hotspot", que no existe en otros equipos
# ni despues de actualizar Java).

$script:RaizEyesofter = Split-Path -Parent $PSScriptRoot
$script:CarpetaHerramientas = Join-Path $script:RaizEyesofter 'herramientas'

function Get-VersionMayorJdk([string] $rutaJdk) {
    $release = Join-Path $rutaJdk 'release'
    if (Test-Path -LiteralPath $release) {
        $linea = Select-String -LiteralPath $release -Pattern '^JAVA_VERSION="?([0-9]+)' | Select-Object -First 1
        if ($linea) { return [int]$linea.Matches[0].Groups[1].Value }
    }
    $nombre = Split-Path -Leaf $rutaJdk
    if ($nombre -match '(?:jdk|java)[-_]?(\d+)') { return [int]$Matches[1] }
    return 0
}

function Get-EyesofterJdk {
    $candidatos = New-Object System.Collections.Generic.List[string]
    if ($env:JAVA_HOME) { $candidatos.Add($env:JAVA_HOME.Trim('"')) }

    $javac = Get-Command javac.exe -ErrorAction SilentlyContinue
    if ($javac) { $candidatos.Add((Split-Path -Parent (Split-Path -Parent $javac.Source))) }

    $bases = @(
        (Join-Path $env:ProgramFiles 'Microsoft'),
        (Join-Path $env:ProgramFiles 'Eclipse Adoptium'),
        (Join-Path $env:ProgramFiles 'Java'),
        (Join-Path $env:ProgramFiles 'Zulu'),
        (Join-Path $env:ProgramFiles 'Amazon Corretto'),
        (Join-Path $env:ProgramFiles 'BellSoft'),
        (Join-Path $env:ProgramFiles 'OpenJDK'),
        (Join-Path $script:CarpetaHerramientas 'jdk')
    )
    if ($env:LOCALAPPDATA) { $bases += (Join-Path $env:LOCALAPPDATA 'Programs\Eclipse Adoptium') }
    foreach ($base in $bases) {
        if (Test-Path -LiteralPath $base) {
            Get-ChildItem -LiteralPath $base -Directory -ErrorAction SilentlyContinue | ForEach-Object { $candidatos.Add($_.FullName) }
        }
    }

    $mejor = $null
    $mejorVersion = 0
    foreach ($ruta in ($candidatos | Select-Object -Unique)) {
        if (-not (Test-Path -LiteralPath (Join-Path $ruta 'bin\javac.exe'))) { continue }
        if (-not (Test-Path -LiteralPath (Join-Path $ruta 'bin\jpackage.exe'))) { continue }
        $version = Get-VersionMayorJdk $ruta
        if ($version -ge 17 -and $version -gt $mejorVersion) {
            $mejor = $ruta
            $mejorVersion = $version
        }
    }
    return $mejor
}

function Get-EyesofterMaven {
    $comando = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($comando) { return $comando.Source }

    foreach ($variable in @($env:MAVEN_HOME, $env:M2_HOME)) {
        if ($variable) {
            $ruta = Join-Path $variable.Trim('"') 'bin\mvn.cmd'
            if (Test-Path -LiteralPath $ruta) { return $ruta }
        }
    }

    if (Test-Path -LiteralPath $script:CarpetaHerramientas) {
        $local = Get-ChildItem -LiteralPath $script:CarpetaHerramientas -Directory -Filter 'apache-maven-*' -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending |
            ForEach-Object { Join-Path $_.FullName 'bin\mvn.cmd' } |
            Where-Object { Test-Path -LiteralPath $_ } |
            Select-Object -First 1
        if ($local) { return $local }
    }
    return $null
}

function Test-EyesofterRequisitos {
    return [bool](Get-EyesofterJdk) -and [bool](Get-EyesofterMaven)
}
