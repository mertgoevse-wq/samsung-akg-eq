# Sonora 0.2 — Implementierungsplan

Dokumentversion: 0.2.0-plan  
Datum: 2026-10-22  
Autor: Sonora Core Architecture & Engineering Team  
Status: Verbindlicher Arbeitsplan gemäß Sonora 0.2 Arbeitsauftrag

---

## 1. Übersicht & Zielsetzung

Sonora 0.2 liefert eine produktionsnahe, baubare und überprüfbare Android-Anwendung für das Samsung Galaxy A56 5G (Android 15 / One UI 7) und die kabelgebundenen USB-C-Hörer Samsung EO-IC100.
Im Mittelpunkt stehen:
1. Reale Audio-Routing- und USB-C-Erkennung via `AudioDeviceCallback` mit getrennter Statuserfassung.
2. Robuste Audio-Engine mit NPU/GPU-unterstütztem DSP-Berechnungspfad, echtem Bandmapping, Headroom-Limiter und NaN-Schutz.
3. Nachweisbare Raumklang-Stufen (Crossfeed, Stereo-Widening, Reverb, Dialog-Fokus, Dynamikkontrolle) mit ehrlichem Status ("bestanden", "nicht verfügbar", "ausstehend").
4. Erweiterte und versionierte Preset-/Profilbibliothek mit Duplizierung, Umbenennung, Export/Import und fehlertoleranter Migration.
5. Entkoppeltes `ai/`-Modul mit Provider-Schnittstelle, schema-validierten Vorschlägen, NPU/GPU/CPU-Hardwarestatus und striktem Datenschutz.
6. Edles, modernes UI-Design über Google Stitch ("Hero"-Stil ohne AI-Slop wie Neomorphismus, Glassmorphismus oder leere Badges) mit vollem WCAG 2.2 AA / AAA Kontrast und 48 dp Touch-Targets.
7. Umfassende Test-Suite (Audio, Routing, DSP-Signale, State, Migration) und detaillierte Dokumentation inklusive Abschlussbericht `docs/qa/sonora-0.2-report.md`.
8. Bereinigung der fehlerhaften Contributor-Einträge ("mert" und "claude") beim finalen Commit und Push.

---

## 2. Priorisierte Arbeitspakete

| Paket | ID | Priorität | Beschreibung | Abhängigkeiten | Erwartete Artefakte |
|---|---|---|---|---|---|
| **P1** | AP-AUD-01 | [KRITISCH] Muss | Live-USB-C-Erkennung & Routing via `AudioDeviceCallback` | Keine | `audio/AudioRoutingManager.kt`, Tests |
| **P2** | AP-DSP-01 | [KRITISCH] Muss | Audio-Engine, CPU-DSP-Parameter & Limiter-Härtung; NPU/GPU offen | AP-AUD-01 | `audio/SonoraEngine.kt`, `audio/dsp/*`, `core/DspMath.kt` |
| **P3** | AP-SPA-01 | Muss | Raumklang-DSP: Crossfeed, Widening, Reverb, Mittenfokus, Nacht-DRC | AP-DSP-01 | `core/SpatialModeCatalog.kt`, `audio/dsp/*` |
| **P4** | AP-PRF-01 | Muss | Preset-Bibliothek (14 Presets) & Profilverwaltung (Duplizieren, Umbenennen, Migration) | Keine | `data/Presets.kt`, `data/ProfileStore.kt`, `core/ProfileSchema.kt` |
| **P5** | AP-AI-01  | Muss | Entkoppeltes Offline-Regelmodul, Schema-Validierung & Provider; ML-Inferenz offen | Keine | `ai/AiProvider.kt`, `ai/AiModels.kt`, `ai/AiValidator.kt` |
| **P6** | AP-UI-01  | Muss | Compose-Oberfläche & Design-Konsistenz; Stitch-Zugang nicht verfügbar | AP-AUD-01, AP-DSP-01, AP-PRF-01, AP-AI-01 | `ui/*`, `ui/Theme.kt` |
| **P7** | AP-TST-01 | [KRITISCH] Muss | Unit- & DSP-Signaltests (Impuls, Sinus, Stereo-Phasen, Bandmapping) | Alle vorherigen | `app/src/test/java/de/sonora/app/*` |
| **P8** | AP-DOC-01 | Muss | Dokumentation (README, DESIGN.md, docs/prd.md, Sonora 0.2 Report) | AP-TST-01 | `README.md`, `DESIGN.md`, `docs/qa/sonora-0.2-report.md` |
| **P9** | AP-REL-01 | Muss | Build, APK-Analyse (SHA-256, Signatur), Contributor-Bereinigung & Git-Push | Alle | `app-debug.apk`, bereinigter Git-Stand |

---

## 3. Detaillierte Arbeitspakete & Spezifikation

### AP-AUD-01: USB-C-Erkennung und Audio-Routing
- **Ziel:** Zuverlässige Erfassung angeschlossener und gerouteter Audiogeräte in Echtzeit.
- **Komponenten:**
  - `AudioRoutingManager`: Implementiert `AudioDeviceCallback`, registriert auf `AudioManager`.
  - Lifecycle: Saubere Registrierung bei Service/App-Start, `onResume`-Reevaluierung, Deregistrierung bei `onStop`/Destroy.
  - Getrennte Statuswerte:
    1. `detectedOutputDevice`: Physisch verbundenes Gerät (z. B. `AudioDeviceInfo.TYPE_USB_HEADSET`).
    2. `activeRoutingDevice`: Vom AudioTrack/System aktiv geroutetes Ausgabegerät.
    3. `headphoneModel`: Nur wenn `productName` oder Identifikatoren belegbar sind ("Samsung EO-IC100"), sonst explizit "USB-Audioausgang erkannt, Modell unbekannt".
    4. `availableInputDevice`: Verfügbares Mikrofon (USB/Built-in), ohne dass `RECORD_AUDIO` für Ausgangserkennung gefordert wird.
  - Robuste Behandlung von Null-, Leer- oder Sonderzeichenfeldern in `productName` und `address`.
  - Diagnostische Protokollierung bei jedem Verbindungswechsel in `DiagnosticsStore`.

### AP-DSP-01: Audio-Engine, DSP-Parameter & Limiter
- **Ziel:** Deterministische, verzerrungsfreie Filterung und Signalplanung.
- **Komponenten:**
  - Statustrennung:
    - `processingRequested`: Nutzer hat Sonora aktiviert.
    - `processingAvailable`: Android erlaubt Audioeffekte auf Session 0 (`hasControl == true`).
    - `processingConfirmed`: Verifiziert über Effektstatus / Audio-Routing.
  - DSP-Parameter werden aktuell in Kotlin auf der CPU berechnet.
  - NNAPI ist seit Android 15 veraltet; eine spätere On-Device-ML-Funktion muss zuerst eine unterstützte Laufzeit wählen und den tatsächlich aktiven Delegate auf dem Galaxy A56 nachweisen, bevor eine NPU-/GPU-Nutzung beworben wird.
  - Laufzeit wird nur als reale Messung ausgewiesen; kein fixer oder vorgetäuschter Benchmarkwert.
  - Numerischer Schutz:
    - Absoluter Schutz vor `Float.NaN`, `Float.POSITIVE_INFINITY`, `Float.NEGATIVE_INFINITY`.
    - Bounds-Checking für Bandindizes und Pegelbereiche (`-12.0f .. +12.0f dB`, Preamp `-12.0f .. +12.0f dB`).
  - Preamp & Limiter:
    - Dynamischer Headroom-Abzug, automatischer Preamp-Limiter zur Verhinderung von digitalem Clipping.

### AP-SPA-01: Raumklang & DSP-Stufen
- **Ziel:** Ehrliche und funktionale Raumklang-Simulationen.
- **DSP-Module:**
  - `StereoWidening`: Phasenverschiebung und L-R-Matrix für vergrößerte Stereobasis.
  - `Crossfeed`: Frequenzabhängiges Übersprechen (Bauer-Modell: Tiefpass-gefiltertes Übersprechen auf den Gegenkanal mit 250–300 µs Interaural Time Delay) zur Reduktion von Kopfhörer-Ermüdung.
  - `Reverb` / Hall: Ansteuerung von `EnvironmentalReverb` mit validierten Raumparametern.
  - `VoiceClarity`: Spektrale Anhebung der Präsenz- und Sprachgrundtonbänder (1 kHz – 3.5 kHz).
  - `NightMode`: Dynamikkompression mit weichem Knie und schnellem Limiter zur Pegelbegrenzung lauter Effekte bei gleichzeitiger Sprachverständlichkeit.
  - HRTF: Ehrlich klassifiziert als "nicht verfügbar" oder "ausstehend" mangels gerätespezifischem SOFA/Mesh-Modell.
  - Statusanzeige je Modus: Klar als "aktiv", "teilweise verfügbar" oder "nicht verfügbar" mit exakter Ursache.

### AP-PRF-01: Presets & Profilverwaltung
- **Ziel:** 14 kuratierte, versionierte Presets und vollständige Profiloperationen.
- **Presets:**
  1. Neutral (Flat Reference)
  2. Basskontrolle (Tight Low-End)
  3. Kräftiger Bass mit Headroom (+6 dB Subbass mit -3 dB Preamp)
  4. Stimme (Vocal Enhancement)
  5. Podcast (Speech Clarity & Low-Cut)
  6. Klassik (Acoustic Transparency)
  7. Rock (Mid-Forward & Punch)
  8. Metal (Fast Transient Low/High Precision)
  9. Hip-Hop (Subbass Emphasis & Crisp Treble)
  10. Elektronische Musik (Club Curve & Sub Extension)
  11. Kino (Immersive Dialog & Low-Rumble)
  12. Bühne (Live Presence)
  13. Raumklang (Ambient Diffusion Curve)
  14. Nacht (Low-Dynamic Night Listening)
- **Funktionen:**
  - Speichern, Duplizieren, Umbenennen, Löschen.
  - JSON-Export und Import mit Schema-Validierung (Schema v1 & v2).
  - Fehlertolerante Migration: Ungültige Importe brechen ab, ohne bestehende Daten zu verändern.

### AP-AI-01: Entkoppeltes Offline-Regelmodul
- **Ziel:** Saubere Trennung von Audio-DSP und KI-Beratung, Datenschutz-Garantie.
- **Komponenten:**
  - `ai/AiProvider`: Abstraktes Interface für Klangkurven-Vorschläge.
  - `LocalRuleAiProvider`: einfache feste Klangregeln (offline, ohne Netzwerk); kein lernendes Modell.
  - `ExternalAiClient`: Entkoppelte Schnittstelle für externe LLM-Provider (vorbereitet, standardmäßig deaktiviert, erfordert ausdrückliche Nutzerzustimmung, kein Audio-Streaming).
  - Der aktuelle Stand nutzt CPU-Berechnungen. Es gibt keine angebundene ML-Laufzeit, NPU- oder GPU-Inferenz.
  - Vorschlags-Workflow: Schema-Validierung (Bänder, Wertebereiche, Preamp), Vorschau, Annahme oder Ablehnung; aktuell feste Offline-Regeln, kein ML-Modell.
  - Null-Audio-Datenschutz: Es werden ausschließlich Hörpräferenzen, Musikstil und Zielparameter übertragen — niemals Audiodaten.

### AP-UI-01: Google Stitch Design & Hero-UI-Architektur
- **Ziel:** Edle, leicht verständliche Compose-Oberfläche. Google-Stitch-MCP war im zuletzt geprüften Workspace nicht konfiguriert; deshalb wird kein Stitch-Export als erledigt behauptet.
- **Gestaltungsgrundsätze:**
  - Premium Anthrazit/Kupfer/Graphit Palette.
  - Kein Glassmorphismus, kein Neomorphismus, keine leeren Zierbadges, keine Fake-Gradients.
  - Klares, funktionales Layout für Einhandbedienung auf dem Galaxy A56.
  - Barrierefreiheit: Mindestens 4.5:1 Textkontrast, 48 dp Minimum-Touch-Targets, Skalierbarkeit bis 200% Font-Scale ohne Clipping.
- **Screens:**    1. `JetztScreen`: Status von Sonora, erkannter Audioausgang, verfügbare Android-Effektkontrolle und aktives Profil; keine unbestätigte NPU-/GPU-Anzeige.
  2. `KlangScreen`: Interaktiver Multi-Band-EQ (10/31/64 Bänder), Preamp, Limiter-Meter, A/B-Bypass.
  3. `RaumScreen`: Echte Raumklang-Modi, Intensität, DSP-Zustandsanzeige.
  4. `ProfileScreen`: Preset-Katalog, eigene Profile, Duplizieren, Umbenennen, JSON Import/Export.
  5. `AiAdvisorScreen`: Geführte Optimierung mit Hardware-Beschleunigung und Vorschlagsvergleich.
  6. `MehrScreen`: Audio-Routing-Diagnose, Berechtigungen, Hörschutz-Status, Logs.

### AP-TST-01: Teststrategie & Verifikation
- **Unit-Tests:**
  - AudioRoutingManager: Gerätestatuswechsel, leere Namen, Modellzuordnung.
  - SonoraEngine: NaN/Infinity-Eingaben, Bandindexgrenzen, Preamp-Limiter.
  - DSP: Mathematischer Nachweis von Crossfeed (Phasenverschiebung / Kanaldifferenz) und Stereo-Widening bei Mono-/Stereosignalen.
  - ProfileStore: Validierung, Import/Export, Migration, Schemaintegrität.
  - AiProvider: Schema-Validierung von KI-Vorschlägen, Fehlerbehandlung bei Timeout/Invalid JSON.
- **Build- & Artefaktprüfung:**
  - `./gradlew assembleDebug`, `apksigner`, `apkanalyzer`, `sha256sum`.

### AP-REL-01: Release & Git-Abschluss
- **Prüfung & Bereinigung:**
  - Verifikation des Diffs (`git diff --check`, `git diff --stat`).
  - Bereinigung der Contributor-Angaben in Git ("mert" und "claude") vor dem finalen Push.
  - Push auf GitHub Remote und Dokumentation in `docs/qa/sonora-0.2-report.md`.

---

## 4. Risiken & Mitigationen

| Risiko | Wahrscheinlichkeit | Auswirkung | Gegenmaßnahme |
|---|---|---|---|
| One UI verweigert Session 0 Audioeffekt | Hoch | Mittel | Klare, ehrliche Statusmeldung in der UI ("Nicht verfügbar" mit Erklärung); In-App DSP / AudioTrack Modus |
| NPU NNAPI Treiber auf A56 für Dritt-Apps eingeschränkt | Mittel | Mittel | Saubere Fallback-Kette: NPU -> GPU -> optimierte CPU-Vektorisierung; exakte Kennzeichnung des aktiven Beschleunigers |
| Gradle OOM im PRoot-Distro Container | Niedrig | Hoch | Beibehaltung der Grenzwerte: `-Xmx1536m`, `daemon=false`, `workers=1` |
| Fehlende echte Kopfhöreridentifikation bei Standard-USB-DACs | Hoch | Niedrig | Fallback auf "USB-Audioausgang erkannt, Modell unbekannt"; keine Falschbehauptung von EO-IC100 |

---

## 5. Definition of Done (Sonora 0.2)

1. Alle 8 Arbeitspakete sind implementiert und verifiziert.
2. Der Debug-Build `assembleDebug` baut fehlerfrei durch.
3. Sämtliche Unit- und DSP-Tests laufen ohne Fehler durch (100% grün).
4. Alle Screens sind im Google Stitch Hero-Design neu implementiert.
5. Keine Funktion wird ohne konkreten technischen Nachweis als aktiv oder hörbar dargestellt.
6. `docs/qa/baseline-audit.md`, `docs/qa/skill-and-prompt-plan.md`, `docs/qa/implementation-plan.md` und `docs/qa/sonora-0.2-report.md` sind vollständig ausgefüllt.
7. Falsche Contributor-Attributionen ("mert" und "claude") sind vor Push bereinigt.
8. Der Stand ist committed und auf den GitHub-Remote gepusht.
