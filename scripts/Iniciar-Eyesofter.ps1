$ErrorActionPreference = 'Stop'

try {
    $raiz = Split-Path -Parent $PSScriptRoot
    $app = Join-Path $raiz 'release\EYESOFTER\EYESOFTER.exe'
    $marca = Join-Path $raiz 'release\EYESOFTER\app\VERSION_ACTUAL.txt'
    $rutaLogo = Join-Path $raiz 'src\main\resources\img\eye-logo.png'
    $temporal = Join-Path $raiz 'tmp'
    $salida = Join-Path $raiz 'release'

    Add-Type -AssemblyName System.Windows.Forms
    Add-Type -AssemblyName System.Drawing

    # --- CLON VISUAL DEL SPLASH DE JAVAFX (MODO KIOSCO) ---
    $ventana = New-Object System.Windows.Forms.Form
    $ventana.Text = 'EYESOFTER'
    $ventana.StartPosition = 'CenterScreen'
    $ventana.FormBorderStyle = 'None'
    $ventana.WindowState = 'Maximized'
    $ventana.BackColor = [System.Drawing.Color]::FromArgb(246, 248, 250)

    # 1. Logo
    $pictureBox = New-Object System.Windows.Forms.PictureBox
    $pictureBox.SizeMode = 'Zoom'
    $pictureBox.Size = New-Object System.Drawing.Size(300, 176)
    if (Test-Path $rutaLogo) {
        $bytes = [System.IO.File]::ReadAllBytes($rutaLogo)
        $ms = New-Object System.IO.MemoryStream(,$bytes)
        $pictureBox.Image = [System.Drawing.Image]::FromStream($ms)
    }

    # 2. Título (EYESOFTER)
    $titulo = New-Object System.Windows.Forms.Label
    $titulo.Text = 'EYESOFTER'
    $titulo.ForeColor = [System.Drawing.Color]::FromArgb(10, 25, 47)
    $titulo.Font = New-Object System.Drawing.Font('Segoe UI', 22, [System.Drawing.FontStyle]::Bold)
    $titulo.AutoSize = $true

    # 3. Subtítulo
    $subtitulo = New-Object System.Windows.Forms.Label
    $subtitulo.Text = 'Sistema de apoyo para tamizaje visual'
    $subtitulo.ForeColor = [System.Drawing.Color]::FromArgb(100, 110, 120)
    $subtitulo.Font = New-Object System.Drawing.Font('Segoe UI', 12)
    $subtitulo.AutoSize = $true

    # 4. Barra de Progreso
    $barra = New-Object System.Windows.Forms.ProgressBar
    $barra.Style = 'Marquee'
    $barra.MarqueeAnimationSpeed = 20
    $barra.Size = New-Object System.Drawing.Size(360, 12)

    # 5. Etiqueta de Estado
    $estado = New-Object System.Windows.Forms.Label
    $estado.Text = 'Verificando entorno local…'
    $estado.ForeColor = [System.Drawing.Color]::FromArgb(100, 110, 120)
    $estado.Font = New-Object System.Drawing.Font('Segoe UI', 11)
    $estado.AutoSize = $true

    $ventana.Controls.AddRange(@($pictureBox, $titulo, $subtitulo, $barra, $estado))
    $ventana.Show()

    [int]$cx = $ventana.ClientSize.Width / 2
    [int]$cy = $ventana.ClientSize.Height / 2

    $pictureBox.Location = New-Object System.Drawing.Point(($cx - 150), ($cy - 190))
    $titulo.Location = New-Object System.Drawing.Point(($cx - ($titulo.Width / 2)), ($cy + 5))
    $subtitulo.Location = New-Object System.Drawing.Point(($cx - ($subtitulo.Width / 2)), ($cy + 50))
    $barra.Location = New-Object System.Drawing.Point(($cx - 180), ($cy + 100))
    $estado.Location = New-Object System.Drawing.Point(($cx - ($estado.Width / 2)), ($cy + 130))
    [System.Windows.Forms.Application]::DoEvents()

    function MostrarEstado([string] $texto) {
        $estado.Text = $texto
        $estado.Location = New-Object System.Drawing.Point(($cx - ($estado.Width / 2)), ($cy + 130))
        [System.Windows.Forms.Application]::DoEvents()
    }

    # --- LÓGICA DE DETECCIÓN Y CIERRE DE PROCESOS ---
    $rutaProyecto = [IO.Path]::GetFullPath($raiz).TrimEnd('\')

    $consultaWMI = "SELECT ProcessId, Name, ExecutablePath, CommandLine FROM Win32_Process WHERE Name = 'EYESOFTER.exe' OR Name LIKE 'java%.exe'"
    $procesos = Get-CimInstance -Query $consultaWMI | Where-Object {
        ($_.Name -ieq 'EYESOFTER.exe' -and $_.ExecutablePath -like "$(Join-Path $raiz 'release\EYESOFTER')*") -or
                ($_.CommandLine -and $_.CommandLine.IndexOf($rutaProyecto, [StringComparison]::OrdinalIgnoreCase) -ge 0)
    }

    foreach ($procesoActivo in $procesos) {
        MostrarEstado "Cerrando una sesión anterior de EYESOFTER…"
        Stop-Process -Id $procesoActivo.ProcessId -Force -ErrorAction SilentlyContinue
    }
    if ($procesos) { Start-Sleep -Milliseconds 900 }

    # Detectar cambios en código
    $fuenteSrc = Get-ChildItem -LiteralPath (Join-Path $raiz 'src') -Recurse -File | Sort-Object LastWriteTime -Descending | Select-Object -First 1

    $fuenteTarget = $null
    if (Test-Path (Join-Path $raiz 'target')) {
        $fuenteTarget = Get-ChildItem -LiteralPath (Join-Path $raiz 'target') -Recurse -File | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    }

    $ultimaModificacion = $fuenteSrc.LastWriteTime
    if ($fuenteTarget -and $fuenteTarget.LastWriteTime -gt $ultimaModificacion) {
        $ultimaModificacion = $fuenteTarget.LastWriteTime
    }

    $requiereActualizacion = -not (Test-Path -LiteralPath $app) -or -not (Test-Path -LiteralPath (Join-Path $raiz 'release\EYESOFTER\javafx')) -or -not (Test-Path -LiteralPath $marca) -or ($ultimaModificacion -gt (Get-Item -LiteralPath $marca).LastWriteTime)

    if ($requiereActualizacion) {
        # --- REQUISITOS: Java, Maven y modelo de voz ---
        . (Join-Path $PSScriptRoot 'Herramientas.ps1')
        $faltaVosk = -not (Test-Path -LiteralPath (Join-Path $raiz 'modelo_vosk\vosk-model-small-es-0.42'))
        if (-not (Test-EyesofterRequisitos) -or $faltaVosk) {
            MostrarEstado 'Faltan componentes por instalar…'
            $ventana.TopMost = $false
            $respuesta = [System.Windows.Forms.MessageBox]::Show(
                "Para compilar EYESOFTER faltan componentes (Java, Maven o el modelo de voz).`n`n¿Instalarlos ahora? Windows pedirá permiso de administrador y se descargarán de internet.",
                'EYESOFTER - Requisitos', 'YesNo', 'Question')
            if ($respuesta -ne 'Yes') { throw 'Instalación cancelada. Ejecute INSTALAR_REQUISITOS.bat cuando quiera instalar los requisitos.' }
            MostrarEstado 'Instalando requisitos (ver la ventana de instalación)…'
            $instalador = Start-Process -FilePath 'powershell.exe' -Verb RunAs -Wait -PassThru -ArgumentList @(
                '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', "`"$(Join-Path $PSScriptRoot 'Instalar-Requisitos.ps1')`"", '-SinCompilar')
            if ($instalador.ExitCode -ne 0) { throw 'La instalación de requisitos falló. Ejecute INSTALAR_REQUISITOS.bat para ver el detalle.' }
            $env:Path = [Environment]::GetEnvironmentVariable('Path', 'Machine') + ';' + [Environment]::GetEnvironmentVariable('Path', 'User')
            if (-not (Test-EyesofterRequisitos)) { throw 'Siguen faltando Java o Maven. Ejecute INSTALAR_REQUISITOS.bat como administrador.' }
        }

        # --- SANEADOR AUTOMÁTICO ---
        # Si hay nueva versión, borramos cachés viejas antes de compilar para evitar conflictos
        MostrarEstado 'Limpiando versiones y cachés anteriores…'
        foreach ($carpeta in @($temporal, $salida)) {
            if (Test-Path $carpeta) {
                Remove-Item -LiteralPath $carpeta -Recurse -Force -ErrorAction SilentlyContinue
            }
        }
        New-Item -ItemType Directory -Force -Path $temporal, $salida | Out-Null

        MostrarEstado 'Actualizando aplicación y compilando componentes…'
        $registro = Join-Path $temporal 'empaquetado.log'
        $registroErr = Join-Path $temporal 'empaquetado-errores.log'
        $proceso = Start-Process -FilePath 'powershell.exe' -WindowStyle Hidden -PassThru `
            -RedirectStandardOutput $registro -RedirectStandardError $registroErr `
            -ArgumentList @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', "`"$(Join-Path $raiz 'EMPAQUETAR_EYESOFTER.ps1')`"")
        $null = $proceso.Handle   # sin esto ExitCode puede quedar vacío
        $pasos = @('Compilando código…', 'Empaquetando aplicación…', 'Copiando recursos clínicos…', 'Finalizando actualización…')
        $indice = 0
        while (-not $proceso.HasExited) {
            MostrarEstado $pasos[$indice % $pasos.Count]
            Start-Sleep -Milliseconds 1200
            $indice++
        }
        $proceso.WaitForExit()
        if ($proceso.ExitCode -ne 0) {
            $detalle = @()
            foreach ($r in @($registro, $registroErr)) { if (Test-Path $r) { $detalle += Get-Content -LiteralPath $r -Tail 25 } }
            throw ("Falló EMPAQUETAR_EYESOFTER.ps1. Registro completo en:`n$registro`n`n" + ($detalle -join "`n"))
        }
    }

    if (-not (Test-Path -LiteralPath $app)) { throw "No se encontró el ejecutable en: $app" }

    MostrarEstado 'Cargando recursos locales…'
    Start-Sleep -Milliseconds 350
    MostrarEstado 'Iniciando EYESOFTER…'

    # Windows (Control inteligente de aplicaciones / Smart App Control) bloquea EYESOFTER.exe
    # porque es un ejecutable recién compilado y sin firma digital. Por eso se arranca con
    # javaw.exe del JDK instalado (firmado por Microsoft) y solo si eso no es posible se usa el .exe.
    . (Join-Path $PSScriptRoot 'Herramientas.ps1')
    $carpetaApp = Split-Path -Parent $app
    $jdkLocal = Get-EyesofterJdk
    $javaw = if ($jdkLocal) { Join-Path $jdkLocal 'bin\javaw.exe' } else { $null }
    $modulosFx = Join-Path $carpetaApp 'javafx'
    $iniciado = $false
    if ($javaw -and (Test-Path -LiteralPath $javaw) -and (Test-Path -LiteralPath $modulosFx)) {
        try {
            $argumentos = @(
                '--module-path', "`"$modulosFx`"",
                '--add-modules', 'javafx.controls,javafx.fxml',
                '-Dfile.encoding=UTF-8',
                '-cp', "`"$(Join-Path $carpetaApp 'app\*')`"",
                'com.eyesofter.MainApp')
            Start-Process -FilePath $javaw -WorkingDirectory $carpetaApp -ArgumentList $argumentos
            $iniciado = $true
        } catch { $iniciado = $false }
    }
    if (-not $iniciado) {
        try {
            Start-Process -FilePath $app -WorkingDirectory $carpetaApp
        } catch {
            throw ("Windows bloqueó EYESOFTER.exe (Control inteligente de aplicaciones).`n`n" +
                   "Ejecute INSTALAR_REQUISITOS.bat para instalar Java y vuelva a intentarlo, o desactive el Control " +
                   "inteligente de aplicaciones en Seguridad de Windows.`n`nDetalle: " + $_.Exception.Message)
        }
    }

    $ventana.Close()
    $ventana.Dispose()

} catch {
    if ($null -ne $ventana) { $ventana.Hide() }
    Add-Type -AssemblyName System.Windows.Forms -ErrorAction SilentlyContinue
    [System.Windows.Forms.MessageBox]::Show($_.Exception.Message, 'Error en EYESOFTER', 'OK', 'Error') | Out-Null
}