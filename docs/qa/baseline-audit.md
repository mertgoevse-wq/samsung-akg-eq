# Sonora 0.2 — Baseline Audit

Audit-Datum: 2026-10-22  
Auditor: Sonora QA & Core Engineering Team  
Status: Bestätigt (Baseline erfasst und verifiziert)

---

## 1. Ausgangszustand Repository & Git

- **Ausgangscommit (HEAD):** `8aaef5eeb125171ec0fed9add93ca3759343eb06` (`8aaef5e`)  
  Commit-Nachricht: `docs: Add MIT license and update README`
- **Aktiver Branch:** `main`
- **Arbeitsstatus:** Clean vor Beginn der Arbeiten (nach Phase 1 Dokumentation: `?? docs/qa/`)
- **Vorhandene lokale Änderungen vor Audit:** Keine (`git status --short` war leer, `git diff --stat` leer)
- **Remote:** `origin https://github.com/mertgoevse-wq/samsung-akg-eq.git (fetch/push)`
- **Verifizierter reproduzierbarer Baseline-Stand:** Git-Tag `v0.1.0` auf Commit `0dc28187a9a085605857cd00e61c34439b372c61`, HEAD auf `8aaef5eeb125171ec0fed9add93ca3759343eb06`

---

## 2. Gelesene Projektanweisungen & Richtlinien

1. `~/.claude/CLAUDE.md`: Globale Anweisungen für Galaxy A56 / Debian PRoot aarch64, Resourcensicherheit (Single Worker, heap cap 1536 MiB), Secret-Scanning, Tool-Routing, Google Stitch Vorgaben.
2. `README.md`: Sonora v0.1.0 Status, Funktionsumfang, Einschränkungen (Session 0 Audio-Effekt, Rootless, fehlende Modelldateien bereinigt).
3. `DESIGN.md`: Visuelle Identität, Farbpalette (#0E1013, #171A1F, #232830, #C98B5E, #E3B591, #F2EDE7, #9AA0A6, #E0A458), Typografie, WCAG 4.5:1 / 3:1 Regeln, Komponentenanforderungen.
4. `docs/prd.md`: PRD v0.1 (Problemstellung, Key Results, US-001 bis US-011, NFR-001 bis NFR-012, Lösungskomponenten, Risiken).
5. Nutzer-Arbeitsauftrag Sonora 0.2: 8-Phasen-Modell, Google Stitch Hero-UI Redesign, NPU/GPU-DSP-Nutzung, Entfernung falscher Contributors ("mert" und "claude") beim Commit/Push.

---

## 3. Build- und Testergebnisse der Baseline

### 3.1 Unit-Tests
- **Befehl:** `gradle --no-daemon testDebugUnitTest --rerun-tasks`
- **Exit-Code:** `0` (Erfolgreich)
- **Laufzeit:** 2m 5s (auf A56 PRoot aarch64 Container)
- **Ergebnis:** 11 Testsuiten, **58 Tests ausgeführt, 58 bestanden, 0 Fehler, 0 übersprungen**
  - `de.sonora.app.core.DiagnosticsTest`: 4 Tests (bestanden)
  - `de.sonora.app.core.BlindTestTest`: 5 Tests (bestanden)
  - `de.sonora.app.core.EngineBridgeTest`: 3 Tests (bestanden)
  - `de.sonora.app.core.BandMappingTest`: 4 Tests (bestanden)
  - `de.sonora.app.core.EqModelTest`: 6 Tests (bestanden)
  - `de.sonora.app.core.LimiterTest`: 4 Tests (bestanden)
  - `de.sonora.app.core.MediaRuleTest`: 3 Tests (bestanden)
  - `de.sonora.app.core.ProfileSchemaTest`: 5 Tests (bestanden)
  - `de.sonora.app.core.SpatialApplyTest`: 11 Tests (bestanden)
  - `de.sonora.app.core.VolumeGuardTest`: 9 Tests (bestanden)
  - `de.sonora.app.core.InterviewTest`: 4 Tests (bestanden)

### 3.2 Debug-Build
- **Befehl:** `gradle --no-daemon assembleDebug`
- **Exit-Code:** `0` (Erfolgreich)
- **Laufzeit:** 1m 14s
- **Output-Artefakt:** `app/build/outputs/apk/debug/app-debug.apk`

---

## 4. APK- und Binäranalyse der Baseline

### 4.1 APK-Identifikation
- **Dateipfad:** `app/build/outputs/apk/debug/app-debug.apk`
- **Dateigröße:** 25.324.893 Bytes (24,15 MiB)
- **SHA-256 Digest:** `35ff9eee0a331944a66768361821164122bbebb7235005d05d0ffcd2f98de3f2`
- **Identisch mit Archiv:** `Eigene Dateien/Sonora-0.1.0-debug.apk` (identischer Hash)
- **Vergleich mit Altbestand `Sonora-AI-Enhanced.apk`:**
  - Dateigröße alt: 49.323.045 Bytes (47,04 MiB)
  - SHA-256 alt: `5a008203cf5cc8630ed8c5744469390c2facc1d652f69c3bc3cd6e4949d6c793`
  - Befund: Der frühere Stand enthielt ungenutzten TFLite-Ballast ohne Inferenzmodell, der bereits in v0.1.0 bereinigt wurde.

### 4.2 Manifest & Berechtigungen
- **Paketname:** `de.sonora.app`
- **Version:** `versionCode=1`, `versionName="0.1.0"`
- **Target SDK / Min SDK:** `minSdkVersion=31`, `targetSdkVersion=35`, `compileSdkVersion=35`
- **Deklarierte Berechtigungen:**
  - `android.permission.RECORD_AUDIO` (Pegelmessung für Umgebungserkennung)
  - `android.permission.MODIFY_AUDIO_SETTINGS` (Effektsteuerung)
  - `android.permission.POST_NOTIFICATIONS` (Dienstbenachrichtigung / Hörschutz)
  - `android.permission.FOREGROUND_SERVICE` (Hintergrundausführung)
  - `android.permission.FOREGROUND_SERVICE_SPECIAL_USE` (Typ specialUse mit Subtyp)
  - `de.sonora.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (Sicherheitsgrenze)
- **Befund Berechtigungen:** Keine Internetberechtigung (`android.permission.INTERNET` fehlt vollständig). Absolut lokaler Betrieb.

### 4.3 Signatur & Keystore
- **Prüfwerkzeug:** `apksigner verify --verbose --print-certs`
- **Ergebnis:** Signiert mit Debug-Keystore (`C=US, O=Android, CN=Android Debug`)
- **Scheme:** APK Signature Scheme v2 verifiziert (`v1=false`, `v2=true`, `v3=false`, `v4=false`)
- **Zertifikat SHA-256:** `aa48b17b4879423fe072f5f5a6b0cfd94273378f073d39125d7a713f29ae99b4`

### 4.4 Bibliotheken & Native Binaries
- **Native Shared Objects (`.so`):**
  - `lib/arm64-v8a/libandroidx.graphics.path.so`
  - `lib/armeabi-v7a/libandroidx.graphics.path.so`
  - `lib/x86/libandroidx.graphics.path.so`
  - `lib/x86_64/libandroidx.graphics.path.so`
- **Befund Bibliotheken:** Keine proprietären oder verdeckten Audio-Codecs. Reines AndroidX Graphics Path für Vektorzeichenoperationen.

### 4.5 Modell-Dateien & Inferenzcode
- **Vorhandene `.tflite` / `.onnx` / `.bin` Modelldateien:** Keine im aktuellen Repository.
- **Befund:** Inferenzcode in v0.1.0 war deaktiviert/entfernt. In v0.2 wird eine modulare `ai/`-Architektur mit echter Hardware-Beschleunigung (`HardwareAccelerator`), Schema-Validierung und lokalem Heuristik-/Regel-Provider aufgebaut.

### 4.6 Video- & Bildschirmaufnahmen
- **Prüfbefehl:** `find . -name "*.mp4" -o -name "*.mkv" -o -name "*.webm" -o -name "*.png" -o -name "*.jpg"`
- **Ergebnis:** Keine Mediendateien im Quellverzeichnis vorhanden.

---

## 5. Werkzeug-Inventar

| Werkzeug | Status | Pfad / Version | Bemerkung |
|---|---|---|---|
| `gradle` | Verfügbar | `/usr/local/bin/gradle` (v8.14.5) | Single Worker, no daemon konfiguriert |
| `aapt` | Verfügbar | `/usr/bin/aapt` | Dump- und Parsing-fähig |
| `aapt2` | Verfügbar | `/usr/bin/aapt2` / `/opt/android-aapt2-arm64/aapt2` | Native aarch64 Override aktiv |
| `apkanalyzer` | Verfügbar | `/home/mert/Android/Sdk/cmdline-tools/latest/bin/apkanalyzer` | Manifest- und APK-Analyse |
| `apksigner` | Verfügbar | `/home/mert/Android/Sdk/build-tools/36.0.0/apksigner` | Signaturprüfung v1/v2 |
| `ffprobe` | Verfügbar | `/usr/bin/ffprobe` | Audio/Video-Stream-Analyse |
| `ffmpeg` | Verfügbar | `/usr/bin/ffmpeg` | Audio/Video-Konvertierung |
| `adb` | Verfügbar | `adb version 1.0.41` | Server tcp:5037 gestartet, aktuell kein physisches Gerät angebunden |
| `apktool` | **Nicht verfügbar** | `apktool not found` (Exit 1) | Nicht im Container installiert |
| `jadx` | **Nicht verfügbar** | `jadx not found` (Exit 1) | Nicht im Container installiert |
| `Google Stitch (MCP)` | Verfügbar | Tools `mcp__stitch__*` | Zur Generierung und Synchronisation von Screens |

---

## 6. Identifizierte Risiken & Lücken in v0.1.0

1. **Audio-Routing & USB-C:** Es gab keinen `AudioDeviceCallback`. Der Kopfhörerstatus wurde nicht dynamisch überwacht; `EO-IC100` wurde in der Dokumentation erwähnt, aber in der App gab es keine echte Modellunterscheidung.
2. **Audio-Engine Statustrennung:** In v0.1.0 existierte nur `EngineStatus.Active` oder `Unavailable`. Die geforderte Trennung zwischen *angefordert*, *technisch verfügbar* und *bestätigt* fehlte.
3. **Raumklang-Differenzierung:** Crossfeed und Stereobreite nutzten denselben simplen Virtualizer-Strength-Regler; Mittenfokus und Dynamikkontrolle waren nicht als eigene DSP-Stufen ausgeprägt.
4. **Preset-Vollständigkeit:** Es existierten nur wenige Basis-Presets. Die 14 geforderten Presets (mit Herkunft, Unsicherheit und Headroom-Regeln) waren unvollständig.
5. **KI-Modul:** War nach der Bereinigung der fehlerhaften TFLite-Dienste in v0.1.0 komplett abwesend.
6. **UI & Design:** Das v0.1.0 UI war ein simpler Entwurf ohne Google Stitch Ausarbeitung und ohne vollständige Hero-Ästhetik.
7. **Contributors:** Im Git-Log stehen "Mert", "Mert Zet" und "Co-Authored-By: Claude Code", die vor dem Release bereinigt werden müssen.
