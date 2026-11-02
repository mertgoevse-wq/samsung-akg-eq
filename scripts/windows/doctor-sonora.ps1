# =============================================================================
# scripts/windows/doctor-sonora.ps1
#
# Diagnostik-Skript fuer die Sonora 0.4 Windows-Toolchain.
# Prueft:
#   - Betriebssystem, CPU, RAM, freier Speicher
#   - Java (JDK 17), JAVA_HOME
#   - Android SDK (Platform android-35, Build-Tools 36.0.0, Platform-Tools/ADB)
#   - Gradle Wrapper, Gradle-Tasks
#   - Git, GitHub CLI, Auth-Status
#   - Node.js, Python (optional)
#   - ADB-Geratestatus (falls Geraat verbunden)
#
# Ausgabe: OK / WARN / FAIL pro Komponente.
#
# Usage (PowerShell):
#   .\scripts\windows\doctor-sonora.ps1
# =============================================================================
[CmdletBinding()]
param()

$ErrorActionPreference = "Continue"
$ProgressPreference = "SilentlyContinue"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$RepoRoot  = Resolve-Path (Join-Path $ScriptDir "..\..\..")

$pass = 0
$warn = 0
$fail = 0

function Test-Command($name) {
    return (Get-Command $name -ErrorAction SilentlyContinue) -ne $null
}

function Result($component, $status, $detail) {
    $color = switch ($status) {
        "OK"   { "Green" }
        "WARN" { "Yellow" }
        "FAIL" { "Red" }
        default { "Gray" }
    }
    Write-Host ("{0,-12} [{1}] {2}" -f $component, $status, $detail) -ForegroundColor $color
    switch ($status) {
        "OK"   { $script:pass++ }
        "WARN" { $script:warn++ }
        "FAIL" { $script:fail++ }
    }
}

Write-Host "=============================================================" -ForegroundColor Cyan
Write-Host " Sonora 0.4 — Doctor" -ForegroundColor Cyan
Write-Host " Repo: $RepoRoot" -ForegroundColor Cyan
Write-Host "=============================================================" -ForegroundColor Cyan
Write-Host ""

# ---------------------------------------------------------------------------
# System
# ---------------------------------------------------------------------------
Write-Host "--- System ---" -ForegroundColor Gray
$os = Get-CimInstance Win32_OperatingSystem
$cpu = Get-CimInstance Win32_Processor | Select-Object -First 1
$memGB = [math]::Round($os.TotalVisibleMemorySize / 1MB, 1)
$freeGB = [math]::Round($os.FreePhysicalMemory / 1MB, 1)
Result "OS" "OK" "$($os.Caption) ($($os.OSArchitecture))"
Result "CPU" "OK" "$($cpu.Name) — $(($cpu.NumberOfLogicalProcessors)) Threads"
Result "RAM" "OK" "${memGB} GB total, ${freeGB GB frei}"
if ($memGB -lt 8) { Result "RAM" "WARN" "Unter 8 GB RAM — Gradle-Worker bereits auf 1 begrenzt." }

$drive = Get-CimInstance Win32_LogicalDisk -Filter "DeviceID='C:'" | Select-Object -First 1
if ($drive) {
    $free = [math]::Round($drive.FreeSpace / 1GB, 1)
    Result "Disk C:" "OK" "${free} GB frei"
    if ($free -lt 10) { Result "Disk C:" "WARN" "Weniger als 10 GB freier Speicher." }
}

Write-Host ""

# ---------------------------------------------------------------------------
# Java / JDK
# ---------------------------------------------------------------------------
Write-Host "--- Java / JDK ---" -ForegroundColor Gray
$javaHome = [Environment]::GetEnvironmentVariable("JAVA_HOME", "Machine")
if (-not $javaHome) { $javaHome = [Environment]::GetEnvironmentVariable("JAVA_HOME", "Process") }
if ($javaHome -and (Test-Path (Join-Path $javaHome "bin\java.exe"))) {
    $javaVer = & (Join-Path $javaHome "bin\java.exe") -version 2>&1 | Select-Object -First 1
    Result "JAVA_HOME" "OK" "$javaHome — $javaVer"
} else {
    Result "JAVA_HOME" "FAIL" "Nicht gesetzt oder JDK nicht gefunden"
}
if (Test-Command java) {
    Result "java (PATH)" "OK" "$(java -version 2>&1 | Select-Object -First 1)"
} else {
    Result "java (PATH)" "FAIL" "java nicht im PATH"
}

Write-Host ""

# ---------------------------------------------------------------------------
# Android SDK
# ---------------------------------------------------------------------------
Write-Host "--- Android SDK ---" -ForegroundColor Gray
$androidHome = [Environment]::GetEnvironmentVariable("ANDROID_HOME", "Machine")
if (-not $androidHome) { $androidHome = [Environment]::GetEnvironmentVariable("ANDROID_HOME", "Process") }
if (-not $androidHome) { $androidHome = "$env:LOCALAPPDATA\Android\Sdk" }
Result "ANDROID_HOME" (if (Test-Path $androidHome) { "OK" } else { "WARN" }) $androidHome

$sdkmanager = Join-Path $androidHome "cmdline-tools\latest\bin\sdkmanager.bat"
if (Test-Path $sdkmanager) {
    Result "sdkmanager" "OK" $sdkmanager
} else {
    Result "sdkmanager" "FAIL" "cmdline-tools/latest/bin/sdkmanager.bat nicht gefunden"
}

foreach ($comp in @("platforms\android-35", "build-tools\36.0.0", "platform-tools")) {
    $p = Join-Path $androidHome $comp
    if (Test-Path $p) { Result "SDK $comp" "OK" "vorhanden" }
    else { Result "SDK $comp" "FAIL" "fehlt" }
}

if (Test-Command adb) {
    Result "adb (PATH)" "OK" "$(adb version 2>&1 | Select-Object -First 1)"
} else {
    $adbPath = Join-Path $androidHome "platform-tools\adb.exe"
    if (Test-Path $adbPath) { Result "adb" "OK" "$adbPath (nicht im PATH)" }
    else { Result "adb" "WARN" "nicht im PATH und nicht im SDK" }
}

Write-Host ""

# ---------------------------------------------------------------------------
# Gradle
# ---------------------------------------------------------------------------
Write-Host "--- Gradle ---" -ForegroundColor Gray
$gradlew = Join-Path $RepoRoot "gradlew"
if (Test-Path $gradlew) {
    Result "gradlew" "OK" $gradlew
} else {
    Result "gradlew" "FAIL" "nicht im Repo"
}

$wrapperProps = Join-Path $RepoRoot "gradle\wrapper\gradle-wrapper.properties"
if (Test-Path $wrapperProps) {
    $url = Select-String -Path $wrapperProps -Pattern "distributionUrl" | Select-Object -ExpandProperty Line | ForEach-Object { $_ -replace ".*=", "" }
    Result "Gradle Wrapper" "OK" $url
} else {
    Result "Gradle Wrapper" "FAIL" "gradle-wrapper.properties nicht gefunden"
}

# ---------------------------------------------------------------------------
# Git / GitHub CLI
# ---------------------------------------------------------------------------
Write-Host "--- Git / GitHub ---" -ForegroundColor Gray
if (Test-Command git) { Result "git" "OK" "$(git --version)" } else { Result "git" "FAIL" "nicht installiert" }
if (Test-Command gh) {
    $auth = gh auth status 2>&1 | Select-Object -First 1
    Result "gh" "OK" "$auth"
} else {
    Result "gh" "FAIL" "GitHub CLI nicht installiert"
}

$branch = git -C $RepoRoot branch --show-current 2>$null
Result "Branch" "OK" $branch

Write-Host ""

# ---------------------------------------------------------------------------
# Node.js / Python (optional)
# ---------------------------------------------------------------------------
Write-Host "--- Optionale Werkzeuge ---" -ForegroundColor Gray
if (Test-Command node) { Result "node" "OK" "$(node --version)" } else { Result "node" "WARN" "nicht installiert (optional)" }
$py = if (Test-Command python3) { "python3" } elseif (Test-Command python) { "python" } else { $null }
if ($py) { Result "python" "OK" "$py — $(& $py --version 2>&1 | Select-Object -First 1)" }
else { Result "python" "WARN" "nicht installiert (optional)" }

Write-Host ""

# ---------------------------------------------------------------------------
# ADB-Geraet
# ---------------------------------------------------------------------------
Write-Host "--- ADB-Geraet (falls verbunden) ---" -ForegroundColor Gray
if (Test-Command adb) {
    $devices = adb devices -l 2>$null
    if ($devices -match "device\s+") {
        Result "adb devices" "OK" "Geraat verbunden"
        $devices | Select-Object -Skip 1 | ForEach-Object { Write-Host "  $_" -ForegroundColor Gray }
    } else {
        Result "adb devices" "WARN" "Kein Geraat verbunden (USB-Debugging prüfen)"
    }
} else {
    Result "adb devices" "WARN" "adb nicht verfuegbar"
}

Write-Host ""
Write-Host "=============================================================" -ForegroundColor Cyan
Write-Host " Ergebnis: $pass OK, $warn WARN, $fail FAIL" -ForegroundColor Cyan
if ($fail -gt 0) {
    Write-Host " Es wurden Fehler festgestellt — Setup erneut ausfuehren und fehlende Komponenten installieren." -ForegroundColor Red
    exit 1
} elseif ($warn -gt 0) {
    Write-Host " Warnungen vorhanden, aber Build sollte moeglich sein." -ForegroundColor Yellow
} else {
    Write-Host " Alle Komponenten OK." -ForegroundColor Green
}
Write-Host "=============================================================" -ForegroundColor Cyan
