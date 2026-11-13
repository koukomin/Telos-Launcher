# Optional per-user Telos Desktop shell (HKCU). Revert with -Revert.
param(
    [switch]$Revert,
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

if (-not $JarPath) {
    $here = Split-Path -Parent $MyInvocation.MyCommand.Path
    $candidates = Get-ChildItem -Path $here, (Join-Path $here "..") -Filter "telos-desktop-windows*.jar" -ErrorAction SilentlyContinue
    $JarPath = ($candidates | Select-Object -First 1).FullName
}

if (-not $JarPath -or -not (Test-Path $JarPath)) {
    Write-Error "Telos Desktop jar not found. Pass -JarPath."
}

$shell = "`"$JavaExe`" -jar `"$JarPath`""
Write-Warning "This replaces your user shell at next sign-in. Press Ctrl+C to cancel."
Start-Sleep -Seconds 3
Set-ItemProperty -Path $regPath -Name Shell -Value $shell
Write-Host "Telos shell set to: $shell"
Write-Host "Sign out and sign in to start Telos Desktop. Use -Revert to restore explorer.exe."
