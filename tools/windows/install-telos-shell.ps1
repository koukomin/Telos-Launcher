# Optional per-user Telos Desktop shell (HKCU). Revert with -Revert.
# Prefers a packaged telos-desktop.exe (setup install or portable folder).
# Falls back to java -jar when only the UberJar is present.
param(
    [switch]$Revert,
    [string]$ExePath,
    [string]$JarPath,
    [string]$JavaExe = "java"
)

$ErrorActionPreference = "Stop"
$regPath = "HKCU:\Software\Microsoft\Windows NT\CurrentVersion\Winlogon"

if ($Revert) {
    Set-ItemProperty -Path $regPath -Name Shell -Value "explorer.exe"
    Write-Host "Restored explorer.exe shell. Sign out to apply."
    exit 0
}

$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$searchRoots = @($here, (Join-Path $here ".."), (Get-Location).Path)

if (-not $ExePath) {
    $exeCandidates = foreach ($root in $searchRoots) {
        Get-ChildItem -Path $root -Recurse -Filter "telos-desktop*.exe" -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -notmatch 'setup' }
    }
    $ExePath = ($exeCandidates | Select-Object -First 1).FullName
}

if ($ExePath -and (Test-Path $ExePath)) {
    $shell = "`"$ExePath`""
} else {
    if (-not $JarPath) {
        $candidates = foreach ($root in $searchRoots) {
            Get-ChildItem -Path $root -Filter "telos-desktop-windows*.jar" -ErrorAction SilentlyContinue
        }
        $JarPath = ($candidates | Select-Object -First 1).FullName
    }
    if (-not $JarPath -or -not (Test-Path $JarPath)) {
        Write-Error "Telos Desktop exe or jar not found. Pass -ExePath or -JarPath."
    }
    $shell = "`"$JavaExe`" -jar `"$JarPath`""
}

Write-Warning "This replaces your user shell at next sign-in. Press Ctrl+C to cancel."
Start-Sleep -Seconds 3
Set-ItemProperty -Path $regPath -Name Shell -Value $shell
Write-Host "Telos shell set to: $shell"
Write-Host "Sign out and sign in to start Telos Desktop. Use -Revert to restore explorer.exe."
