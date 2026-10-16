<#
.SYNOPSIS
    PackHub offline internal-consistency check: compile common + fabric + neoforge against API stubs.

.DESCRIPTION
    No network and no Minecraft dependency required. This proves the project's own code is
    free of coding errors; it does NOT replace `gradlew build` (real API validation happens in CI).

    Notes:
      * The API stubs mirror the Minecraft 26.3 shapes we rely on.
      * Loader-specific networking classes (FabricNetwork / NeoForgeNetwork) are EXCLUDED here:
        their Fabric/NeoForge network APIs cannot be represented by hand-written stubs, so they
        are validated by the GitHub Actions build instead.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\tools\verify-offline.ps1 -Jdk25 "$env:APPDATA\.minecraft\runtime\java-runtime-epsilon"
#>
param(
    [Parameter(Mandatory = $true)][string]$Jdk25,
    [string]$GsonJar = "",
    [string]$OutputDir = ""
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

$javac = Join-Path $Jdk25 'bin\javac.exe'
if (-not (Test-Path $javac)) {
    throw "javac not found: $javac (pass -Jdk25 <JDK 25 dir>)"
}

if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $OutputDir = Join-Path $root 'build\offline-check'
}
if (Test-Path $OutputDir) { Remove-Item $OutputDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

$sources = @()
$sources += Get-ChildItem (Join-Path $root 'tools\api-stubs') -Recurse -Filter *.java | ForEach-Object { $_.FullName }
$sources += Get-ChildItem (Join-Path $root 'common\src\main\java') -Recurse -Filter *.java | ForEach-Object { $_.FullName }
$sources += Get-ChildItem (Join-Path $root 'fabric\src\main\java') -Recurse -Filter *.java | ForEach-Object { $_.FullName }
$sources += Get-ChildItem (Join-Path $root 'neoforge\src\main\java') -Recurse -Filter *.java | ForEach-Object { $_.FullName }

# Only the real loader network implementations are skipped (their Fabric/NeoForge APIs cannot be
# represented by stubs). tools/api-stubs contains stand-ins with the same class names for local use.
$excludedPaths = @(
    'fabric\src\main\java\com\packhub\fabric\FabricNetwork.java',
    'neoforge\src\main\java\com\packhub\neoforge\NeoForgeNetwork.java'
)
$sources = $sources | Where-Object {
    $full = $_
    -not ($excludedPaths | Where-Object { $full.EndsWith($_) })
}

$argFile = Join-Path $OutputDir 'sources.txt'
[System.IO.File]::WriteAllLines($argFile, $sources, (New-Object System.Text.UTF8Encoding($false)))

$classpath = @()
if ([string]::IsNullOrWhiteSpace($GsonJar)) {
    $bundled = Get-ChildItem (Join-Path $root 'tools\lib') -Filter 'gson-*.jar' -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($bundled) { $GsonJar = $bundled.FullName }
}
if (-not [string]::IsNullOrWhiteSpace($GsonJar)) { $classpath += $GsonJar }

Write-Host ("Compiling {0} source files (JDK 25 + API stubs)..." -f $sources.Count)

$output = & $javac -encoding UTF-8 -nowarn -d $OutputDir -cp ($classpath -join ';') "@$argFile" 2>&1 | Out-String
$diagnostics = ($output -split "`r?`n") | Where-Object { $_ -match '\.java:\d+:' }

Write-Host ("Diagnostics: {0}  (expected 0)" -f $diagnostics.Count)
if ($diagnostics.Count -gt 0) {
    $diagnostics | Select-Object -First 40 | ForEach-Object { Write-Host $_ }
    exit 1
}

$classes = (Get-ChildItem $OutputDir -Recurse -Filter *.class | Measure-Object).Count
Write-Host ("PASS: {0} class files produced." -f $classes)
exit 0
