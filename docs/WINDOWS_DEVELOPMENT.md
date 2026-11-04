# Sonora 0.4 — Windows-Entwicklung

Dieses Dokument beschreibt, wie ein technisch wenig erfahrener Nutzer Sonora 0.4
unter Windows 11 baut, testet und auf dem Samsung Galaxy A56 installiert.

Voraussetzung: Ein frisch geklones Sonora-Repository.

## Schritt 0 — Repository klonen

```powershell
git clone https://github.com/mertgoevse-wq/samsung-akg-eq.git
cd samsung-akg-eq
git checkout feat/sonora-0.4-neural-audio-engine
```

Der Arbeitsbranch für diese Weiterentwicklung ist
`feat/sonora-0.4-windows-continuation`.

## Schritt 1 — Entwicklungsumgebung installieren

Führe das idempotente Setup-Skript aus (PowerShell als Administrator nicht nötig,
außer `winget` verlangt es):

```powershell
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
.\scripts\windows\setup-sonora.ps1
```

Das Skript installiert nur fehlende Komponenten und kann bei Unterbrechungen
erneut gestartet werden. Es schreibt `local.properties` (wird nicht in Git
eingecheckt) und setzt `JAVA_HOME`, `ANDROID_HOME`, `ANDROID_SDK_ROOT`.

Falls `sdkmanager` remote keine Paketlisten laden kann (lokales Netzwerk
blockiert), müssen die Android-SDK-Komponenten manuell über Android Studio
(SDK Manager) installiert werden:
* `platforms;android-35`
* `build-tools;36.0.0`
* `platform-tools` (enthält ADB)

## Schritt 2 — Installation prüfen

```powershell
.\scripts\windows\doctor-sonora.ps1
```

Alle Komponenten sollten `OK` melden. `WARN` bei optionalen Werkzeugen
(Node.js, Python) ist akzeptabel.

## Schritt 3 — Build

Beide APK-Varianten bauen:

```powershell
.\gradlew.bat assembleStandardDebug assembleParallelDebug --no-daemon
```

Ausgabe:
* `app/build/outputs/apk/standard/debug/Sonora-0.4-StandardDebug.apk`
* `app/build/outputs/apk/parallel/debug/Sonora-0.4-ParallelDebug.apk`

Einzeln:
```powershell
.\gradlew.bat assembleStandardDebug --no-daemon
.\gradlew.bat assembleParallelDebug --no-daemon
```

## Schritt 4 — Unit-Tests

```powershell
.\gradlew.bat testStandardDebugUnitTest testParallelDebugUnitTest --no-daemon
```

## Schritt 5 — Android Lint

```powershell
.\gradlew.bat lintStandardDebug lintParallelDebug --no-daemon
```

## Schritt 6 — Installation auf dem Galaxy A56

Nur die Parallel-Testvariante (`de.sonora.app.test`) installieren. Die
Standard-App (`de.sonora.app`) darf nicht überschrieben werden.

```powershell
adb devices -l
.\gradlew.bat assembleParallelDebug --no-daemon
adb install -r app/build/outputs/apk/parallel/debug/Sonora-0.4-ParallelDebug.apk
```

Die Installation gilt erst als erfolgreich, wenn ADB `Success` meldet.

## Bekannte Einschränkungen (dieser Stand)

* Der lokale Gradle-Build schlägt auf manchen Windows-Systemen mit
  `Unable to establish loopback connection` fehlen. Dies ist eine
  Umgebungs-/Netzwerk-Einstellung des Gast-Systems, kein Repo-Fehler.
* Android SDK `platforms;android-35`, `build-tools;36.0.0`,
  `platform-tools` müssen vor dem ersten Build installiert sein.
* CI war zuletzt fehlerhaft, weil generische `testDebugUnitTest`-
  Tasks mit den Product-Flavors mehrdeutig waren. Behoben via
  `.github/workflows/ci.yml` (Flavor-Matrix `standard` / `parallel`).

## Weitere Skripte

| Skript | Zweck |
|--------|-------|
| `scripts/windows/setup-sonora.ps1` | Installiert fehlende Entwicklungswerkzeuge |
| `scripts/windows/doctor-sonora.ps1` | Prüft Toolchain, SDK, Versionen, ADB, Speicher |
| `scripts/windows/test-sonora.ps1` | Führt Unit-Tests, Lint und Verifikationen aus |
| `scripts/windows/build-sonora.ps1` | Baut beide APK-Varianten und prüft Integrität |
| `scripts/windows/deploy-sonora.ps1` | Installiert nur die Parallel-Variante auf autorisiertem Gerät |
| `scripts/windows/collect-diagnostics.ps1` | Sammelt technische Logs und Benchmarks (ohne Geheimnisse) |
