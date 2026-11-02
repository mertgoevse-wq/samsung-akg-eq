# Sonora 0.4 — Test-Report

**Stand:** 2026-11-02  
**Branch:** `feat/sonora-0.4-windows-continuation`  
**Zielgerät:** Samsung Galaxy A56 5G (SM-A566B), Android 15 (API 35)  
**Verantwortlich:** Agent E (Security & QA Engineer)

## 1. Zweck

Dieser Bericht dokumentiert alle im Rahmen von Sonora 0.4 durchgeführten (bzw.
durchführbaren) Test-Aktivitäten: laufende Befehle, Pass-/Fail-Zahlen, Logs sowie
offene Blocker. Er ist die verbindliche Basis für die Abnahme der
Test-Pyramide und für den Commit auf `feat/sonora-0.4-windows-continuation`.

## 2. Durchgeführte Befehle und Ergebnisse

Keine der Gradle-Test-Befehle konnte lokal ausgeführt werden (siehe Blocker,
Abschnitt 4). Die Zahlen unten sind daher **konzeptionelle Zählungen** (Anzahl
Testdateien und `@Test`-Annotationen), keine tatsächlich gemessenen
Pass-/Fail-Ergebnisse.

| Befehl | Ziel | Status | Ergebnis |
|--------|------|--------|----------|
| `./gradlew testDebugUnitTest` | JVM Unit-Tests | ❌ blockiert | Nicht ausführbar (Gradle Loopback, §4.1) |
| `./gradlew connectedDebugAndroidTest` | Instrumentation-Tests | ❌ blockiert | Kein Gerät/Emulator (§4.2) |
| `./gradlew lintDebug` | Lint | ❌ blockiert | Nicht ausführbar (§4.1) |
| `ktlint` / `detekt` (falls konfiguriert) | Statische Analyse | ❌ blockiert | Nicht ausführbar (§4.1) |

**Geloggte Ausgaben:** keine — Gradle bricht vor Test-Ausführung ab; es existieren
keine `build/reports/tests`- oder `build/test-results`-Artefakte lokal.

## 3. Test-Pyramide — Bestandsaufnahme

### 3.1 JVM Unit-Tests (app/src/test)

| Kategorie | Dateien | `@Test`-Methoden | Status |
|-----------|---------|------------------|--------|
| DSP (Biquad, EQ, Limiter, Preamp, Flanger, Compressor, Stereo-Crossfeed, Voice-Focus, FFT, DspSignal) | 11 | ~ | Konzeptuell |
| AI / Inferenz (LiteRt, ModelIntegrity, PcmPreprocessor) | 3 | ~ | Konzeptuell |
| Core (Engine, BandMapping, BlindTest, Diagnostics, EqModel, Interview, Limiter, MediaRule, PresetCatalog, ProfileSchema, SpatialApply, VolumeGuard, AudioRoutingManager, AiModule, HardwareCapabilityRepository) | 15 | ~ | Konzeptuell |
| **Gesamt JVM** | **29** | **218** | **0 lokal ausgeführt** |

### 3.2 Instrumentation-Tests (app/src/androidTest)

| Testdatei | `@Test`-Methoden | Zweck | Gerät erforderlich |
|-----------|------------------|-------|--------------------|
| `PermissionStateTest.kt` | ~ | RECORD_AUDIO deklariert, POST_NOTIFICATIONS deklariert, keine INTERNET/MediaProjection-Berechtigung, Mikrofon-Widerruf | Ja |
| `NavigationTest.kt` | ~ | MainActivity startet, SonoraTab-Katalog (6 Tabs) | Ja |
| `ViewModelTest.kt` | ~ | ViewModel-Defaults, Band-Clamp ±12, Band-Anzahl, Profil-Persistenz, Engine-Status, Mikrofon-Widerruf, Sound-Lab, Retry | Ja |
| `ModelLoadingTest.kt` | ~ | yamnet.tflite/yamnet_labels.txt gebündelt, LiteRT-Initialisierung, Top-K ohne NaN/Inf, leere Eingabe abgelehnt | Ja |
| `AudioServiceLifecycleTest.kt` | ~ | Dienst-Start/Stopp via ViewModel, SonoraService nicht exported, Engine-Release | Ja |
| `ai/inference/GoldenSampleTest.kt` (bestehend) | ~ | Instrumentierte YAMNet-Inferenz | Ja |
| **Gesamt Instrumentation** | **6** | **34** | **0 lokal ausgeführt** |

**Gesamtsumme:** 35 Testdateien, 252 `@Test`-Methoden (konzeptionell).

### 3.3 Abgedeckte vs. offene Bereiche

| Bereich | Abdeckung | Status |
|---------|-----------|--------|
| Berechtigungs-Manifest (RECORD_AUDIO, POST_NOTIFICATIONS, keine INTERNET/MediaProjection) | `PermissionStateTest` | Konzeptionell |
| Navigation (SonoraTab, MainActivity) | `NavigationTest` | Konzeptionell |
| ViewModel (Defaults, Master, Bands, Profile, Engine-Status, Mikrofon-Widerruf, Sound-Lab, Retry) | `ViewModelTest` | Konzeptionell |
| Modell-Assets & LiteRT (yamnet, 521 Labels, Top-K, NaN/Inf, leere Eingabe) | `ModelLoadingTest` | Konzeptionell |
| Audio-Service-Lebenszyklus (Start/Stopp, nicht exported, Engine-Release) | `AudioServiceLifecycleTest` | Konzeptionell |
| YAMNet-Inferenz (bestehend) | `GoldenSampleTest` | Konzeptionell |
| DSP-Einheiten (Biquad, EQ, Limiter, Preamp, Flanger, Compressor, Stereo-Crossfeed, Voice-Focus, FFT) | JVM Unit-Tests | Konzeptionell |
| Core-Logik (Engine, Profile, BlindTest, Interview, Spatial, Diagnostics, Presets, Audio-Routing, Hardware, Media) | JVM Unit-Tests | Konzeptionell |
| UI / Compose-Screens (EqualizerScreen, ProfilesScreen, SettingsScreen, SpatialScreen, BlindTestScreen, InterviewScreen, AiAdvisorDialog) | Keine eigenen UI-Tests | ⚠️ Lücke (siehe §5) |
| SoundLabEngine | Keine eigene Testdatei | ⚠️ Lücke (siehe §5) |
| NeuralAudioViewModel / NeuralAudioScreen | Keine UI-Tests | ⚠️ Lücke (siehe §5) |

### 3.4 Keine gemockten Werte als echte Messungen

Kein Test ersetzt eine echte Messung durch einen festcodierten Wert:
- LiteRT-Inferenz liefert echte Top-5-Labels/Scores (keine hartekodierten Klassen).
- Engine-Status wird von `SonoraEngine.initialize()` ehrlich als `Active`/`Unavailable`/`Inactive` gemeldet, niemals als verifiziert behauptet.
- Mikrofon-Widerruf prüft den tatsächlichen `checkSelfPermission`-Zustand auf dem Gerät.
- Modell-Integrität prüft die SHA-256 der Asset-Datei gegen den dokumentierten Hash.

## 4. Blocker

### 4.1 Lokale Gradle-Schleife (Loopback) — Build kann nicht ausgeführt werden

Die Gradle-Ausführung schlägt lokal unter Windows fehl (Loopback / kein
Android-SDK in der Entwicklungsumgebung). Siehe
`docs/audit/SONORA_0_4_WINDOWS_BASELINE.md`, Sektion Build-Blocker.

**Folge:** Keine JVM Unit-Tests, keine Lint-/Statische-Analyse-Ausführung, keine
Test-Berichte, keine Pass-/Fail-Zahlen lokal messbar.

**Erforderlich zur Auflösung:** Ausführung auf einer Maschine mit installiertem
Android SDK / Gradle (CI oder Entwickler-Windows mit SDK) oder ein
Emulator/Gerät für Instrumentation-Tests.

### 4.2 Kein Gerät / Emulator für Instrumentation-Tests

Alle 6 Instrumentation-Testdateien (34 `@Test`-Methoden) benötigen ein
Android-Gerät oder Emulator (API 35, Samsung Galaxy A56 5G Zielgerät). Keines
ist lokal verfügbar.

**Folge:** `connectedDebugAndroidTest` kann nicht laufen; alle
Instrumentation-Tests stehen unter „pending“.

**Erforderlich zur Auflösung:** Gerät/Emulator anschließen und
`connectedDebugAndroidTest` ausführen.

### 4.3 GPU/NPU nicht verifiziert

Gemäß Auftrag wird GPU/NPU **nicht** als verifiziert markiert, solange Agent C
keine Laufzeit-Beweise liefert. Siehe `docs/EXTERNAL_DEPENDENCIES.md` (LiteRT
GPU „integriert, unverifiziert“; NPU auf Exynos 1580 nicht durch LiteRT
verifiziert).

## 5. Offene Lücken und Empfehlungen

1. **CI muss flavor-spezifische Gradle-Test-Tasks und einen Lint-Schritt laufen**
   lassen (siehe `docs/audit/SONORA_0_4_WINDOWS_BASELINE.md`). Bis dahin bleiben
   alle 252 `@Test`-Methoden konzeptionell.
2. **UI/Compose-Screen-Tests ergänzen:** EqualizerScreen, ProfilesScreen,
   SettingsScreen, SpatialScreen, BlindTestScreen, InterviewScreen,
   AiAdvisorDialog haben keine eigenen Instrumentation-Tests.
3. **SoundLabEngine und NeuralAudioViewModel/NeuralAudioScreen** benötigen eigene
   Testabdeckung.
4. **Blocker §4.1 und §4.2 auflösen**, bevor der Release als „getestet“
   gekennzeichnet wird.

## 6. Signatur

Agent E (Security & QA) — Bericht erstellt am 2026-11-02 auf Basis des
Quellstandes auf `feat/sonora-0.4-windows-continuation`. Keine lokal
ausgeführten Testergebnisse wegen der Blocker in Abschnitt 4.
