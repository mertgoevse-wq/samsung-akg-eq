# =============================================================================
# scripts/windows/setup-sonora.ps1
#
# Idempotente Windows-Entwicklungsumgebung für Sonora 0.4.
#
# Prueft und installiert bei Bedarf:
#   - Git for Windows
#   - GitHub CLI (gh)
#   - JDK 17 (Temurin via Eclipse Adoptium)
#   - Android SDK Command-Line Tools, Platform android-35,
#     Build-Tools 36.0.0, Platform-Tools (ADB)
#   - Gradle Wrapper (projektintern, keine globale Gradle-Installation)
#   - Node.js LTS (optional, fuer Stitch-Tools)
#   - Python 3 (optional, fuer Diagnostik)
#
# Verfuegbare lokale Konfigurationsdateien werden geschrieben, aber NICHT
# in Git eingecheckt (local.properties, JAVA_HOME/ANDROID_HOME nur fuer
# diese Sitzung oder dauerhaft via setx, falls gewuenscht).
#
# Fuer Administratorrechte nur dann gefragt, wenn winget eine
# erweiterte Installation benoetigt.
#
# Usage (PowerShell):
#   Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
#   .\scripts\windows\setup-sonora.ps1
# =============================================================================
[CmdletBinding()]
param(
    [switch]$Force,
    [switch]$AcceptLicenses,
    [string]$AndroidSdkRoot = "$env:LOCALAPPDATA\Android\Sdk",
    [string]$JavaHome       = "$env:ProgramFiles\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$RepoRoot  = Resolve-Path (Join-Path $ScriptDir "..\..\..")

Write-Host "=============================================================" -ForegroundColor Cyan
Write-Host " Sonora 0.4 — Windows Setup" -ForegroundColor Cyan
Write-Host " Repo: $RepoRoot" -ForegroundColor Cyan
Write-Host "=============================================================" -ForegroundColor Cyan

# ---------------------------------------------------------------------------
# Helfer
# ---------------------------------------------------------------------------
function Test-Command($name) {
    return (Get-Command $name -ErrorAction SilentlyContinue) -ne $null
}

function Test-WingetPackage($id) {
    $pkg = winget show "$id" 2>$null
    return $null -ne $pkg
}

function Invoke-WingetInstall($id, $name) {
    Write-Host "Installiere $name ($id) ..." -ForegroundColor Yellow
    $args = @("install", "$id", "--silent", "--accept-package-agreements", "--accept-source-agreements")
    if ($AcceptLicenses) { $args += @("--force") }
    $proc = Start-Process -FilePath "winget.exe" -ArgumentList $args -Wait -PassThru -NoNewWindow
    if ($proc.ExitCode -ne 0) {
        Write-Warning "Installation von $name beendet mit Exit-Code $($proc.ExitCode)"
    } else {
        Write-Host "$name installiert." -ForegroundColor Green
    }
}

function Set-EnvVarDurable($name, $value) {
    [Environment]::SetEnvironmentVariable($name, $value, "Machine")
    Write-Host "  Umgebungsvariable dauerhaft gesetzt: $name = $value" -ForegroundColor Gray
}

# ---------------------------------------------------------------------------
# 1. winget vorhanden?
# ---------------------------------------------------------------------------
if (-not (Test-Command winget)) {
    Write-Error "winget (Windows Package Manager) ist nicht installiert. Bitte zuerst installieren."
    exit 1
}
Write-Host "winget vorhanden: $(winget --version)" -ForegroundColor Green

# ---------------------------------------------------------------------------
# 2. Git for Windows
# ---------------------------------------------------------------------------
if (-not (Test-Command git)) {
    Invoke-WingetInstall "Git.Git" "Git for Windows"
} else {
    Write-Host "Git vorhanden: $(git --version)" -ForegroundColor Green
}

# ---------------------------------------------------------------------------
# 3. GitHub CLI
# ---------------------------------------------------------------------------
if (-not (Test-Command gh)) {
    Invoke-WingetInstall "GitHub.cli" "GitHub CLI (gh)"
} else {
    Write-Host "GitHub CLI vorhanden: $(gh --version | Select-Object -First 1)" -ForegroundColor Green
}

# ---------------------------------------------------------------------------
# 4. JDK 17 (Temurin)
# ---------------------------------------------------------------------------
$jdkFound = $false
if (Test-Path $JavaHome) {
    $jdkFound = $true
    Write-Host "JDK 17 vorhanden unter $JavaHome" -ForegroundColor Green
}
if (-not $jdkFound) {
    Invoke-WingetInstall "EclipseAdoptium.Temurin.17.JDK" "JDK 17 (Temurin)"
    if (-not (Test-Path $JavaHome)) {
        Write-Warning "JDK 17 nicht unter $JavaHome gefunden — bitte JAVA_HOME manuell prüfen."
    }
}
Set-EnvVarDurable "JAVA_HOME" $JavaHome

# ---------------------------------------------------------------------------
# 5. Android SDK
# ---------------------------------------------------------------------------
$cmdlineToolsDir = Join-Path $AndroidSdkRoot "cmdline-tools"
$latestCmdline   = Join-Path $cmdlineToolsDir "latest"
$sdkmanager      = Join-Path $latestCmdline "bin" "sdkmanager.bat"

if (-not (Test-Path $sdkmanager)) {
    Write-Host "Android SDK Command-Line Tools fehlen — bitte manuell via Android Studio SDK Manager installieren oder winget-Paket 'Google.AndroidSDKCommandlineTools' verwenden." -ForegroundColor Yellow
} else {
    Write-Host "sdkmanager vorhanden: $sdkmanager" -ForegroundColor Green
}

$platformDir      = Join-Path $AndroidSdkRoot "platforms" "android-35"
$buildToolsDir    = Join-Path $AndroidSdkRoot "build-tools" "36.0.0"
$platformToolsDir = Join-Path $AndroidSdkRoot "platform-tools"

$missing = @()
if (-not (Test-Path $platformDir))      { $missing += "platforms;android-35" }
if (-not (Test-Path $buildToolsDir))    { $missing += "build-tools;36.0.0" }
if (-not (Test-Path $platformToolsDir)) { $missing += "platform-tools" }

if ($missing.Count -gt 0) {
    Write-Host "Fehlende Android SDK-Komponenten: $($missing -join ', ')" -ForegroundColor Yellow
    if (Test-Path $sdkmanager) {
        foreach ($comp in $missing) {
            Write-Host "Installiere $comp via sdkmanager ..." -ForegroundColor Yellow
            $args = @("sdkmanager", $comp)
            if ($AcceptLicenses) { $args += @("--licenses") }
            & $sdkmanager $args 2>$null
        }
    } else {
        Write-Warning "sdkmanager nicht verfügbar — SDK-Komponenten muessen manuell installiert werden."
    }
} else {
    Write-Host "Alle Android SDK-Komponenten vorhanden." -ForegroundColor Green
}

Set-EnvVarDurable "ANDROID_HOME" $AndroidSdkRoot
Set-EnvVarDurable "ANDROID_SDK_ROOT" $AndroidSdkRoot

# ---------------------------------------------------------------------------
# 6. Gradle Wrapper (projektintern, keine globale Installation)
# ---------------------------------------------------------------------------
$gradlew = Join-Path $RepoRoot "gradlew"
if (Test-Path $gradlew) {
    Write-Host "Gradle Wrapper vorhanden: $gradlew" -ForegroundColor Green
} else {
    Write-Warning "gradlew nicht im Repo gefunden."
}

# ---------------------------------------------------------------------------
# 7. Node.js LTS (optional)
# ---------------------------------------------------------------------------
if (-not (Test-Command node)) {
    Write-Host "Node.js nicht gefunden — optional fuer Stitch-Tools, kann uebersprungen werden." -ForegroundColor Gray
} else {
    Write-Host "Node.js vorhanden: $(node --version)" -ForegroundColor Green
}

# ---------------------------------------------------------------------------
# 8. Python 3 (optional)
# ---------------------------------------------------------------------------
if (-not (Test-Command python) -and -not (Test-Command python3)) {
    Write-Host "Python 3 nicht gefunden — optional fuer Diagnostik." -ForegroundColor Gray
} else {
    $py = if (Test-Command python3) { "python3" } else { "python" }
    Write-Host "Python vorhanden: $py — $(& $py --version 2>&1)" -ForegroundColor Green
}

# ---------------------------------------------------------------------------
# 9. local.properties schreiben (wird NICHT in Git eingecheckt)
# ---------------------------------------------------------------------------
$localProperties = Join-Path $RepoRoot "local.properties"
$propsContent = "sdk.dir=$AndroidSdkRoot`n"
try {
    Set-Content -Path $localProperties -Value $propsContent -Encoding UTF8
    Write-Host "local.properties geschrieben: $localProperties" -ForegroundColor Green
} catch {
    Write-Warning "Konnte local.properties nicht schreiben: $_"
}

# ---------------------------------------------------------------------------
# 10. Abschlusszusammenfassung
# ---------------------------------------------------------------------------
Write-Host "=============================================================" -ForegroundColor Cyan
Write-Host " Setup abgeschlossen." -ForegroundColor Cyan
Write-Host " Prufe die Installation mit: .\scripts\windows\doctor-sonora.ps1" -ForegroundColor Cyan
Write-Host " Baue das Projekt mit:       cd $RepoRoot; .\gradlew.bat tasks" -ForegroundColor Cyan
Write-Host "=============================================================" -ForegroundColor Cyan
