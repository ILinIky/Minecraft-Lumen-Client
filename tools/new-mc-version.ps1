# Lumen Client - neue Minecraft-Version anlegen (baut nichts).
#
# Beispiel:
#   powershell -ExecutionPolicy Bypass -File tools\new-mc-version.ps1 -To 1.21.8 -Forge 58.0.0
#
# Legt versions\<To>.properties an und kopiert den versionsabhaengigen Code src\mc\<From>\
# nach src\mc\<To>\. Danach mit "Lumen_Bauen_und_Installieren.bat -Mc <To> -NoInstall" testen.
# Die passende Forge-Version steht auf https://files.minecraftforge.net
param(
    [Parameter(Mandatory = $true)] [string] $To,
    [Parameter(Mandatory = $true)] [string] $Forge,
    [string] $From,
    [string] $Mappings,
    [string] $Java,
    [string] $EventBus,
    [switch] $SetDefault
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path $PSScriptRoot -Parent
$Latin1 = [Text.Encoding]::GetEncoding('ISO-8859-1')

function Read-Properties([string] $Path) {
    $result = @{}
    foreach ($line in [IO.File]::ReadAllLines($Path, $Latin1)) {
        if ($line -match '^\s*([A-Za-z0-9_.]+)\s*=\s*(.*?)\s*$') { $result[$Matches[1]] = $Matches[2] }
    }
    return $result
}

function Set-Property([string] $Text, [string] $Key, [string] $Value) {
    return [regex]::Replace($Text, "(?m)^$([regex]::Escape($Key))=.*$", "$Key=$Value")
}

try {
    $gradleProperties = Join-Path $ProjectRoot 'gradle.properties'
    if (-not $From) { $From = (Read-Properties $gradleProperties)['mc_version'] }
    foreach ($value in @($To, $From)) {
        if ($value -notmatch '^\d+(\.\d+){1,3}[A-Za-z0-9.-]*$') { throw "Ungueltige Minecraft-Version: $value" }
    }
    if ($Forge -notmatch '^\d+(\.\d+){1,3}$') { throw "Ungueltige Forge-Version: $Forge (z.B. 58.0.0)" }

    $sourceFile = Join-Path $ProjectRoot "versions\$From.properties"
    $targetFile = Join-Path $ProjectRoot "versions\$To.properties"
    $sourceCode = Join-Path $ProjectRoot "src\mc\$From"
    $targetCode = Join-Path $ProjectRoot "src\mc\$To"
    if (-not (Test-Path -LiteralPath $sourceFile)) { throw "Vorlage fehlt: versions\$From.properties" }
    if (-not (Test-Path -LiteralPath $sourceCode)) { throw "Vorlage fehlt: src\mc\$From" }
    if (Test-Path -LiteralPath $targetFile) { throw "versions\$To.properties gibt es schon." }
    if (Test-Path -LiteralPath $targetCode) { throw "src\mc\$To gibt es schon." }

    $template = Read-Properties $sourceFile
    if (-not $Mappings) { $Mappings = $To }
    if (-not $Java) { $Java = $template['java_version'] }
    if (-not $EventBus) { $EventBus = $template['eventbus_validator_version'] }

    # Einstellungen: Vorlage uebernehmen, nur die versionsabhaengigen Werte ersetzen.
    $text = [IO.File]::ReadAllText($sourceFile, $Latin1)
    $text = $text -replace [regex]::Escape("Minecraft $From"), "Minecraft $To"
    $text = $text -replace [regex]::Escape("-Mc $From"), "-Mc $To"
    $text = Set-Property $text 'minecraft_version' $To
    # Genau diese Version: eine JAR je Minecraft-Version.
    $text = Set-Property $text 'minecraft_version_range' "[$To]"
    $text = Set-Property $text 'forge_version' $Forge
    $text = Set-Property $text 'mapping_version' $Mappings
    $text = Set-Property $text 'java_version' $Java
    $text = Set-Property $text 'eventbus_validator_version' $EventBus
    [IO.File]::WriteAllText($targetFile, $text, $Latin1)

    # Versionsabhaengigen Code als Ausgangspunkt kopieren.
    Copy-Item -LiteralPath $sourceCode -Destination $targetCode -Recurse

    if ($SetDefault) {
        $properties = [IO.File]::ReadAllText($gradleProperties, $Latin1)
        [IO.File]::WriteAllText($gradleProperties, (Set-Property $properties 'mc_version' $To), $Latin1)
    }

    Write-Host ''
    Write-Host "  Minecraft $To angelegt (Vorlage: $From)" -ForegroundColor Green
    Write-Host "    versions\$To.properties   Forge $Forge, Mappings $Mappings, Java $Java" -ForegroundColor Gray
    Write-Host "    src\mc\$To\                Mixins, Tasten, Render-Zustaende (Kopie von $From)" -ForegroundColor Gray
    if ($SetDefault) { Write-Host "    gradle.properties          mc_version=$To (neue Standard-Version)" -ForegroundColor Gray }
    Write-Host ''
    Write-Host '  Naechste Schritte (siehe docs\NEUE_MINECRAFT_VERSION.md):' -ForegroundColor White
    Write-Host "    1. Im Forge-MDK von $To pruefen: Java-, EventBus- und ForgeGradle-Version" -ForegroundColor Gray
    Write-Host "    2. Testbuild:  Lumen_Bauen_und_Installieren.bat -Mc $To -NoInstall" -ForegroundColor Gray
    Write-Host "    3. Fehler zuerst in src\mc\$To\ beheben (Mixins, ClientKeybinds, compat\RenderTypes)" -ForegroundColor Gray
    Write-Host '    4. Im Spiel pruefen: Menue, HUD, Minimap, ESP, Bauplan-Vorschau, Teleport' -ForegroundColor Gray
    exit 0
} catch {
    Write-Host ''
    Write-Host ('  FEHLER: ' + $_.Exception.Message) -ForegroundColor Red
    exit 1
}
