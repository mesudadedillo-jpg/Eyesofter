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

    $requiereActualizacion = -not (Test-Path -LiteralPath $app) -or -not (Test-Path -LiteralPath $marca) -or ($ultimaModificacion -gt (Get-Item -LiteralPath $marca).LastWriteTime)

    if ($requiereActualizacion) {
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
        $proceso = Start-Process -FilePath 'powershell.exe' -ArgumentList @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', (Join-Path $raiz 'EMPAQUETAR_EYESOFTER.ps1')) -PassThru
        $pasos = @('Compilando código…', 'Empaquetando aplicación…', 'Copiando recursos clínicos…', 'Finalizando actualización…')
        $indice = 0
        while (-not $proceso.HasExited) {
            MostrarEstado $pasos[$indice % $pasos.Count]
            Start-Sleep -Milliseconds 1200
            $indice++
        }
        if ($proceso.ExitCode -ne 0) { throw 'Falló el script EMPAQUETAR_EYESOFTER.ps1. Revisa los errores de compilación.' }
    }

    if (-not (Test-Path -LiteralPath $app)) { throw "No se encontró el ejecutable en: $app" }

    MostrarEstado 'Cargando recursos locales…'
    Start-Sleep -Milliseconds 350
    MostrarEstado 'Iniciando EYESOFTER…'

    Start-Process -FilePath $app -WorkingDirectory (Split-Path -Parent $app)

    $ventana.Close()
    $ventana.Dispose()

} catch {
    if ($null -ne $ventana) { $ventana.Hide() }
    Add-Type -AssemblyName System.Windows.Forms -ErrorAction SilentlyContinue
    [System.Windows.Forms.MessageBox]::Show($_.Exception.ToString(), 'Error Crítico en EYESOFTER', 'OK', 'Error') | Out-Null
}