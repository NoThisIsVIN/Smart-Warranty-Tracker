<#
.SYNOPSIS
    Builds a Windows release of Smart Warranty Tracker with its own Java runtime, so users don't need Java installed.

.DESCRIPTION
    -Type app-image (default)  A ready-to-run folder, zipped to target\dist\SmartWarrantyTracker-<version>-windows.zip.
                               Needs only a JDK 21+.
    -Type exe | msi            A Windows installer. Also needs the WiX Toolset (https://wixtoolset.org) on PATH.

.EXAMPLE
    .\packaging\package-windows.ps1
    .\packaging\package-windows.ps1 -Type exe
#>
param(
    [ValidateSet('app-image', 'exe', 'msi')]
    [string]$Type = 'app-image'
)

$ErrorActionPreference = 'Stop'

# Runs a native tool. Windows PowerShell 5.1 treats a tool's stderr warnings as errors under 'Stop',
# so relax that for the call and rely on the exit code instead.
function Invoke-Tool([string]$Exe, [string[]]$Arguments, [string]$Failure) {
    $ErrorActionPreference = 'Continue'
    & $Exe @Arguments 2>&1 | ForEach-Object { "$_" }
    if ($LASTEXITCODE -ne 0) { throw $Failure }
}

$root = Split-Path $PSScriptRoot -Parent
Set-Location $root

# Keep the same ID across releases so a new installer upgrades the old install instead of adding a second copy.
$upgradeUuid = 'd6e40437-4cfa-40d1-8978-7fd0e839abf6'
$appName = 'SmartWarrantyTracker'

$version = ([xml](Get-Content pom.xml)).project.version
if ($version -notmatch '^\d+\.\d+\.\d+$') {
    throw "pom.xml version '$version' must be a plain MAJOR.MINOR.PATCH for a release (no -SNAPSHOT)."
}

$jpackage = (Get-Command jpackage -ErrorAction SilentlyContinue).Source
if (-not $jpackage) {
    $ErrorActionPreference = 'Continue'
    $javaHome = (& java -XshowSettings:properties -version 2>&1 | ForEach-Object { "$_" } | Select-String 'java.home\s*=\s*(.+)$').Matches[0].Groups[1].Value.Trim()
    $jpackage = Join-Path $javaHome 'bin\jpackage.exe'
    $ErrorActionPreference = 'Stop'
    if (-not (Test-Path $jpackage)) { throw 'jpackage not found. Install JDK 21+ and put its bin folder on PATH.' }
}

# Clear the previous package first. A running copy of the app, or OneDrive/antivirus scanning the new
# files, can hold them open for a few seconds, which otherwise makes 'mvn clean' fail.
$dest = Join-Path $root 'target\dist'
for ($try = 1; Test-Path $dest; $try++) {
    try {
        Remove-Item $dest -Recurse -Force -ErrorAction Stop
    } catch {
        if ($try -ge 10) {
            throw "Could not delete $dest. Close Smart Warranty Tracker if it is running (or pause OneDrive sync) and try again."
        }
        Start-Sleep -Seconds 1
    }
}

Write-Host "Building $appName $version ($Type)..."
Invoke-Tool 'mvn' @('-B', '-q', 'clean', 'package') 'Maven build failed.'

# jpackage bundles everything in --input, so give it only the app jar.
$inputDir = Join-Path $root 'target\jpackage-input'
New-Item -ItemType Directory -Force $inputDir, $dest | Out-Null
$jar = "smart-warranty-tracker-$version.jar"
Copy-Item (Join-Path 'target' $jar) $inputDir

$jpackageArgs = @(
    '--type', $Type,
    '--name', $appName,
    '--app-version', $version,
    '--vendor', 'Vinesh Reddy',
    '--description', 'Keep track of product warranties and receipts.',
    '--copyright', 'Copyright (c) 2026 Vinesh Reddy. MIT License.',
    '--input', $inputDir,
    '--main-jar', $jar,
    '--dest', $dest
)
if ($Type -ne 'app-image') {
    $jpackageArgs += @(
        '--license-file', 'LICENSE',
        '--win-upgrade-uuid', $upgradeUuid,
        '--win-per-user-install',
        '--win-dir-chooser',
        '--win-menu',
        '--win-shortcut'
    )
}

Invoke-Tool $jpackage $jpackageArgs 'jpackage failed.'

if ($Type -eq 'app-image') {
    $zip = Join-Path $dest "$appName-$version-windows.zip"
    Remove-Item $zip -ErrorAction SilentlyContinue
    Copy-Item LICENSE (Join-Path $dest $appName)
    Compress-Archive -Path (Join-Path $dest $appName) -DestinationPath $zip
    Write-Host "Done: $zip"
} else {
    Write-Host "Done: installer in $dest"
}
