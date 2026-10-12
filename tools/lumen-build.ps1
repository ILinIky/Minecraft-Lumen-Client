# Lumen Client - Bauen, testen und installieren.
# Aufruf ueber Lumen_Bauen_und_Installieren.bat. Mit -NoInstall wird nur gebaut und getestet
# (keine Installation, Build-Nummer bleibt unveraendert).
param([switch] $NoInstall)

$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path $PSScriptRoot -Parent
$KeepBackups = 10
try { $Host.UI.RawUI.WindowTitle = 'Lumen Client - Build & Install' } catch { }

# ---------------------------------------------------------------- Ausgabe

function Write-Banner {
    Write-Host ''
    Write-Host '   _      _    _ __  __ ______ _   _ ' -ForegroundColor Magenta
    Write-Host '  | |    | |  | |  \/  |  ____| \ | |' -ForegroundColor Magenta
    Write-Host '  | |    | |  | | \  / | |__  |  \| |' -ForegroundColor DarkMagenta
    Write-Host '  | |    | |  | | |\/| |  __| | . ` |' -ForegroundColor DarkMagenta
    Write-Host '  | |____| |__| | |  | | |____| |\  |' -ForegroundColor Cyan
    Write-Host '  |______|\____/|_|  |_|______|_| \_|' -ForegroundColor Cyan
    Write-Host '  C L I E N T   -   Build & Install' -ForegroundColor DarkGray
    Write-Host ''
}

function Write-Step([int] $Number, [string] $Text) {
    Write-Host ''
    Write-Host ("  [{0}/4] " -f $Number) -ForegroundColor Magenta -NoNewline
    Write-Host $Text -ForegroundColor White
}

function Write-Ok([string] $Text) {
    Write-Host '        OK   ' -ForegroundColor Green -NoNewline
    Write-Host $Text -ForegroundColor Gray
}

function Write-Detail([string] $Label, [string] $Value) {
    Write-Host ('        {0,-11}' -f $Label) -ForegroundColor DarkGray -NoNewline
    Write-Host $Value -ForegroundColor Gray
}

function Write-Warn([string] $Text) {
    Write-Host '        !    ' -ForegroundColor Yellow -NoNewline
    Write-Host $Text -ForegroundColor Yellow
}

function Format-Size([long] $Bytes) {
    if ($Bytes -ge 1MB) { return ('{0:N1} MB' -f ($Bytes / 1MB)) }
    return ('{0:N0} KB' -f ($Bytes / 1KB))
}

# ---------------------------------------------------------------- Hilfen

$Latin1 = [Text.Encoding]::GetEncoding('ISO-8859-1')

function Read-GradleProperties([string] $Path) {
    $result = @{}
    foreach ($line in [IO.File]::ReadAllLines($Path, $Latin1)) {
        if ($line -match '^\s*([A-Za-z0-9_.]+)\s*=\s*(.*?)\s*$') { $result[$Matches[1]] = $Matches[2] }
    }
    return $result
}

function Test-Jdk21([string] $JdkPath) {
    if (-not $JdkPath) { return $false }
    if (-not (Test-Path -LiteralPath (Join-Path $JdkPath 'bin/java.exe'))) { return $false }
    if (-not (Test-Path -LiteralPath (Join-Path $JdkPath 'bin/javac.exe'))) { return $false }
    $releaseFile = Join-Path $JdkPath 'release'
    return (Test-Path -LiteralPath $releaseFile) -and
        ((Get-Content -LiteralPath $releaseFile -Raw) -match '(?m)^JAVA_VERSION="21(?:\.|\")')
}

function Find-Jdk21 {
    $candidates = @(
        $env:JAVA_HOME,
        [Environment]::GetEnvironmentVariable('JAVA_HOME', 'User'),
        [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Machine')
    )
    $javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($javaCommand) { $candidates += Split-Path (Split-Path $javaCommand.Source -Parent) -Parent }
    foreach ($root in @(
            (Join-Path $env:ProgramFiles 'Eclipse Adoptium'),
            (Join-Path $env:ProgramFiles 'Java'),
            (Join-Path $env:ProgramFiles 'Microsoft'),
            (Join-Path $env:USERPROFILE '.jdks'))) {
        if (Test-Path -LiteralPath $root) {
            $candidates += Get-ChildItem -LiteralPath $root -Directory | Select-Object -ExpandProperty FullName
        }
    }
    return $candidates | Where-Object { Test-Jdk21 $_ } | Select-Object -First 1
}

# Gradle schreibt waehrend des Builds in die Datei; Get-Content scheitert dann an der Sperre.
function Read-SharedLog([string] $Path) {
    try {
        $stream = [IO.File]::Open($Path, 'Open', 'Read', 'ReadWrite')
        try { return (New-Object IO.StreamReader($stream)).ReadToEnd() -split "`r?`n" }
        finally { $stream.Dispose() }
    } catch { return @() }
}

function Get-RunningMinecraft {
    return @(Get-Process -Name javaw, java -ErrorAction SilentlyContinue |
        Where-Object { $_.MainWindowTitle -match 'Minecraft' })
}

function Move-ToBackup([string] $File, [string] $BackupDirectory) {
    $name = (Get-Date -Format 'yyyyMMdd-HHmmss') + '_' + [IO.Path]::GetFileName($File)
    $target = Join-Path $BackupDirectory $name
    if (Test-Path -LiteralPath $target) {
        $target = Join-Path $BackupDirectory ([guid]::NewGuid().ToString('N').Substring(0, 8) + '_' + $name)
    }
    [IO.File]::Move($File, $target)
}

# ---------------------------------------------------------------- Ablauf

$stopwatch = [Diagnostics.Stopwatch]::StartNew()
try {
    Write-Banner
    $propertiesFile = Join-Path $ProjectRoot 'gradle.properties'
    $properties = Read-GradleProperties $propertiesFile
    $minecraftVersion = $properties['minecraft_version']
    $forgeVersion = $properties['forge_version']
    $fileName = $properties['mod_file_name']
    $buildText = $properties['mod_build']
    if ($fileName -notmatch '^[A-Za-z0-9_-]{1,64}$') { throw 'mod_file_name in gradle.properties ist ungueltig.' }
    if ($buildText -notmatch '^\d{1,6}$') { throw 'mod_build in gradle.properties muss eine ganze Zahl sein.' }
    $build = [int] $buildText
    $version = '{0}_v{1}' -f $minecraftVersion, $build

    # [1/4] Java -------------------------------------------------------------
    Write-Step 1 'Java 21 suchen'
    $jdk = Find-Jdk21
    if (-not $jdk) { throw 'Kein Java JDK 21 gefunden. Installiere Temurin JDK 21 oder setze JAVA_HOME auf dessen Ordner.' }
    $env:JAVA_HOME = $jdk
    $env:PATH = (Join-Path $jdk 'bin') + ';' + $env:PATH
    Write-Ok $jdk

    # [2/4] Version ----------------------------------------------------------
    Write-Step 2 'Version festlegen'
    Write-Ok ("Lumen Client {0}  (Minecraft {1}, Forge {2})" -f $version, $minecraftVersion, $forgeVersion)
    if ($NoInstall) { Write-Warn 'Testbuild: keine Installation, die Build-Nummer bleibt gleich.' }

    # [3/4] Bauen ------------------------------------------------------------
    Write-Step 3 'Bauen und testen'
    $buildDirectory = Join-Path $ProjectRoot 'build'
    New-Item -ItemType Directory -Path $buildDirectory -Force | Out-Null
    $log = Join-Path $buildDirectory 'lumen-build.log'
    $errorLog = Join-Path $buildDirectory 'lumen-build-errors.log'
    $gradlew = Join-Path $ProjectRoot 'gradlew.bat'
    # cmd /c ""pfad" args": aeussere Anfuehrungszeichen entfernt cmd selbst, der Pfad darf Leerzeichen enthalten.
    $gradleArguments = '/d /c ""{0}" -p "{1}" build --console=plain -Pmod_build={2}"' -f $gradlew, $ProjectRoot, $build
    $process = Start-Process -FilePath 'cmd.exe' -ArgumentList $gradleArguments -WorkingDirectory $ProjectRoot `
        -NoNewWindow -PassThru -RedirectStandardOutput $log -RedirectStandardError $errorLog
    $null = $process.Handle   # sonst liefert Windows PowerShell keinen Exit-Code
    $buildTimer = [Diagnostics.Stopwatch]::StartNew()
    while (-not $process.HasExited) {
        $task = 'Gradle startet'
        $last = Read-SharedLog $log | Where-Object { $_ -like '> Task :*' } | Select-Object -Last 1
        if ($last) { $task = ($last -replace '^> Task :', '') -replace '\s.*$', '' }
        $status = ('        {0,4} s   {1}' -f [int] $buildTimer.Elapsed.TotalSeconds, $task)
        Write-Host ("`r" + $status.PadRight(72)) -ForegroundColor DarkGray -NoNewline
        Start-Sleep -Milliseconds 400
    }
    $process.WaitForExit()
    Write-Host ("`r" + ''.PadRight(72) + "`r") -NoNewline
    $output = @()
    if (Test-Path -LiteralPath $log) { $output = @(Get-Content -LiteralPath $log) }

    if ($process.ExitCode -ne 0) {
        Write-Host '        FEHLER  Der Build ist fehlgeschlagen.' -ForegroundColor Red
        $errors = @()
        if (Test-Path -LiteralPath $errorLog) { $errors = @(Get-Content -LiteralPath $errorLog) }
        $relevant = @($output + $errors | Where-Object {
            $_ -match 'error:|Fehler:|FAILED|What went wrong|AssertionError|Exception|^\s+\^|\.java:\d+' })
        if ($relevant.Count -eq 0) { $relevant = @($output + $errors | Where-Object { $_.Trim() }) }
        foreach ($line in ($relevant | Select-Object -Last 25)) { Write-Host ('        ' + $line) -ForegroundColor DarkYellow }
        Write-Detail 'Protokoll:' $log
        throw 'Build fehlgeschlagen. Es wurde nichts installiert.'
    }
    foreach ($line in ($output | Where-Object { $_ -match '(checks|assertions) passed' })) { Write-Ok $line.Trim() }
    $jarName = '{0}-{1}.jar' -f $fileName, $version
    $builtJar = Join-Path $buildDirectory "libs/$jarName"
    if (-not (Test-Path -LiteralPath $builtJar -PathType Leaf)) { throw "Build-JAR fehlt: $builtJar" }
    $jarSize = (Get-Item -LiteralPath $builtJar).Length
    Write-Ok ("{0}  ({1}, {2} s)" -f $jarName, (Format-Size $jarSize), [int] $buildTimer.Elapsed.TotalSeconds)

    if ($NoInstall) {
        Write-Host ''
        Write-Host "  Testbuild fertig: $builtJar" -ForegroundColor Green
        exit 0
    }

    # [4/4] Installieren -----------------------------------------------------
    Write-Step 4 'Installieren'
    if (-not $env:APPDATA) { throw 'APPDATA ist nicht gesetzt.' }
    $minecraftDirectory = Join-Path $env:APPDATA '.minecraft'
    $modsDirectory = Join-Path $minecraftDirectory 'mods'
    $backupDirectory = Join-Path $minecraftDirectory 'mod-backups\lumen'
    New-Item -ItemType Directory -Path $modsDirectory -Force | Out-Null
    New-Item -ItemType Directory -Path $backupDirectory -Force | Out-Null

    while ((Get-RunningMinecraft).Count -gt 0) {
        Write-Warn 'Minecraft laeuft noch. Die Mod-Datei kann erst danach ersetzt werden.'
        $answer = Read-Host '        Minecraft schliessen und Enter druecken (A = abbrechen)'
        if ($answer -match '^[aA]') { throw "Abgebrochen. Die fertige JAR liegt unter $builtJar" }
    }

    # Archiv aller Versionen im Projekt, z.B. zum Weitergeben.
    $releaseDirectory = Join-Path $ProjectRoot 'releases'
    New-Item -ItemType Directory -Path $releaseDirectory -Force | Out-Null
    Copy-Item -LiteralPath $builtJar -Destination (Join-Path $releaseDirectory $jarName) -Force

    # Erst neben die alte Version legen (.tmp wird von Forge ignoriert), dann tauschen.
    $stagedJar = Join-Path $modsDirectory ('.lumen-' + [guid]::NewGuid().ToString('N') + '.tmp')
    Copy-Item -LiteralPath $builtJar -Destination $stagedJar
    $replaced = 0
    try {
        # Alle bisherigen Lumen-Versionen, auch die alte "examplemod.jar" des frueheren Skripts.
        $previous = @(Get-ChildItem -LiteralPath $modsDirectory -File |
            Where-Object { $_.Name -like "$fileName-*.jar" -or $_.Name -eq 'examplemod.jar' })
        foreach ($old in $previous) {
            try {
                Move-ToBackup $old.FullName $backupDirectory
                $replaced++
            } catch {
                throw ("{0} ist gesperrt. Laeuft Minecraft noch?" -f $old.Name)
            }
        }
        $installedJar = Join-Path $modsDirectory $jarName
        [IO.File]::Move($stagedJar, $installedJar)
    } finally {
        if (Test-Path -LiteralPath $stagedJar) { [IO.File]::Delete($stagedJar) }
    }

    # Nur die neuesten Sicherungen behalten.
    Get-ChildItem -LiteralPath $backupDirectory -Filter '*.jar' -File |
        Sort-Object LastWriteTime -Descending | Select-Object -Skip $KeepBackups |
        ForEach-Object { Remove-Item -LiteralPath $_.FullName }

    Write-Ok ("{0} -> mods" -f $jarName)
    if ($replaced -gt 0) { Write-Ok ("{0} aeltere Version(en) gesichert in mod-backups\lumen" -f $replaced) }

    # Naechste Nummer erst nach erfolgreicher Installation vergeben.
    $text = [IO.File]::ReadAllText($propertiesFile, $Latin1)
    $text = [regex]::Replace($text, '(?m)^mod_build=\d+', ('mod_build={0}' -f ($build + 1)))
    [IO.File]::WriteAllText($propertiesFile, $text, $Latin1)

    Write-Host ''
    Write-Host '  +----------------------------------------------------------+' -ForegroundColor Green
    Write-Host ('  |  {0,-56}|' -f ("Lumen Client $version ist installiert")) -ForegroundColor Green
    Write-Host '  +----------------------------------------------------------+' -ForegroundColor Green
    Write-Detail 'Datei:' ("{0}  ({1})" -f $jarName, (Format-Size $jarSize))
    Write-Detail 'Ordner:' $modsDirectory
    Write-Detail 'Archiv:' (Join-Path 'releases' $jarName)
    Write-Detail 'Dauer:' ("{0} s" -f [int] $stopwatch.Elapsed.TotalSeconds)
    Write-Detail 'Naechste:' ('{0}_v{1}' -f $minecraftVersion, ($build + 1))
    Write-Host ''
    Write-Host ("  Starte jetzt Minecraft {0} mit Forge {1}." -f $minecraftVersion, $forgeVersion) -ForegroundColor White
    exit 0
} catch {
    Write-Host ''
    Write-Host ('  FEHLER: ' + $_.Exception.Message) -ForegroundColor Red
    exit 1
}
