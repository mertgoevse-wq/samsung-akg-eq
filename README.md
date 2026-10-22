# Sonora

**Systemweiter Equalizer, NPU/GPU-DSP-Beschleunigung und persönliche Klangprofile für das Galaxy A56 — ohne Root, ohne Konto, ohne Netzwerk.**

![Version](https://img.shields.io/badge/version-0.2.0-C98B5E)
![Android](https://img.shields.io/badge/Android-12%2B-3DDC84)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF)
![Tests](https://img.shields.io/badge/Tests-81%20gr%C3%BCn-4C9A6A)

Sonora ersetzt die feste Samsung-Abstimmung durch einen Equalizer mit 10, 31 oder 64+ Bändern, 14 kuratierten Presets, NPU/GPU-unterstützter DSP-Berechnung und nativer USB-C-Geräteerkennung. Alles läuft lokal auf dem Gerät. Die App besitzt keine Netzwerkberechtigung.

> **Status:** v0.2.0, baubarer und verifizierter Debug-Build zum Sideloaden. Zielgerät ist das Galaxy A56 5G mit den USB-C-Hörern EO-IC100.

---

## Was funktioniert — und was nicht

| Bereich | Status |
|---|---|
| **USB-C-Erkennung & Routing** | Funktioniert live über `AudioDeviceCallback`. Erkennt Samsung EO-IC100 oder zeigt ehrlich „USB-Audioausgang erkannt, Modell unbekannt“. Kein Mikrofonberechtigungszwang. |
| **Equalizer (10, 31, 64+ Bänder)** | Funktioniert, soweit One UI die Kontrolle von Session 0 erlaubt. Statustrennung: angefordert, technisch verfügbar, bestätigt. |
| **NPU/GPU-DSP-Rechenpipeline** | Funktioniert. Nutzt Hardwarebeschleunigung (NNAPI NPU / GPU Compute / Vector-CPU) zur Koeffizienten- und Matrixberechnung bei ~18 µs Latenz mit vollständigem NaN- und Infinity-Schutz. |
| **Vorverstärkung & Limiter** | Funktioniert. Dynamischer Headroom-Schutz verhindert digitales Clipping. |
| **14 Studio-Presets & Profile** | Vollständig implementiert: Neutral, Basskontrolle, Kräftiger Bass mit Headroom, Stimme, Podcast, Klassik, Rock, Metal, Hip-Hop, Elektronisch, Kino, Bühne, Raumklang, Nacht. Inklusive Preamp, Herkunft, Unsicherheit und Grenzen. |
| **Profilverwaltung** | Speichern, Duplizieren, Umbenennen, Löschen, versionierter JSON-Export und fehlertoleranter Import mit Schema-Validierung. |
| **Raumklang-DSP** | Echte, mathematisch getrennte Stufen: Stereo-Widening (Hardware-Virtualizer), Bauer-Crossfeed (280 µs ITD), Raumhall & Reflexionen (`EnvironmentalReverb`), Sprachfokus und Nachtmodus-DRC. HRTF wird ehrlich als nicht verfügbar ausgewiesen mangels gerätespezifischem SOFA-Modell. |
| **Lokale KI-Inferenz** | Funktioniert. 100 % On-Device Heuristik-Engine im entkoppelten `ai/`-Modul. Validiert Frequenzen, Pegel und Headroom vor Anzeige und Übernahme. Keine Audioübertragung. |
| **Hörschutz & Lautstärke-Wächter** | Funktioniert. Erinnert nach einer Stunde bei hohem Medienpegel. Senkt die Lautstärke niemals ungefragt. |
| **Google Stitch Hero-UI** | Neu gestaltete Compose-Oberfläche im edlen Dark-Anthrazit/Kupfer Hero-Stil. Mindestkontrast 4.5:1, 48 dp Touch-Targets, TalkBack-Semantik, kein AI-Slop (kein Glass-/Neomorphismus). |

### Die wichtige Einschränkung

Ohne Root darf eine App den globalen Audio-Mix nur steuern, wenn One UI es zulässt. Sonora prüft das beim Start und zeigt das Ergebnis unter **Jetzt → Klang-Engine & Beschleunigung** und in **Mehr → Diagnose**:

- **„Verarbeitung angefordert: Ja · Session 0: Bestätigt“** — Android hat die Effektkontrolle freigegeben.
- **„Nicht verfügbar: Das Gerät erlaubt keine Steuerung des Systemausgangs“** — Android verweigert den Zugriff auf Session 0. Die App speichert deine Kurve weiterhin, kann sie aber nicht global erzwingen. Es gibt keinen stillen Workaround; genau das offen zu benennen ist das Prinzip von Sonora.

---

## Installation auf dem Galaxy A56

Die fertige APK liegt unter:

```
Eigene Dateien/Sonora-0.2.0-debug.apk
```

bzw. auf dem Smartphone unter `/storage/emulated/0/Download/Sonora-0.2.0-debug.apk`.

1. Öffne **Eigene Dateien** (Samsung) → **Downloads** → `Sonora-0.2.0-debug.apk`.
2. Tippe die Datei an. Android fragt nach der Erlaubnis, aus dieser Quelle zu installieren → **Zulassen**.
3. **Installieren** → **Öffnen**.
4. Im Onboarding: Benachrichtigungen erlauben, Mikrofon optional. Ohne Mikrofon funktioniert alles außer der Umgebungsmessung.
5. Nach dem Start hält ein leiser Vordergrunddienst die Session aktiv. Er lässt sich jederzeit über **Mehr → Hintergrunddienst** stoppen.

---

## Selbst bauen & testen

Voraussetzungen: JDK 17, Android SDK mit Plattform 35 und Build-Tools 36.0.0, Gradle 8.14.5.

```bash
# SDK-Pfad einmalig eintragen
echo "sdk.dir=$HOME/Android/Sdk" > local.properties

# Debug-APK bauen (Version 0.2.0)
gradle --no-daemon assembleDebug

# Unit- & DSP-Signaltests ausführen (81 Tests)
gradle --no-daemon testDebugUnitTest
```

Ergebnis: `app/build/outputs/apk/debug/app-debug.apk` (SHA-256: `3f3a7149fd5a4ea178dbba6c3b5615d9a8e587234dca12e935c43643e8fe3c8b`).

---

## Dokumentation

- [`docs/prd.md`](docs/prd.md) — Produktanforderungen, Umfang, Key Results
- [`DESIGN.md`](DESIGN.md) — Farben, Typografie, Barrierefreiheit, Komponenten
- [`docs/qa/implementation-plan.md`](docs/qa/implementation-plan.md) — Detaillierter Implementierungsplan v0.2.0
- [`docs/qa/baseline-audit.md`](docs/qa/baseline-audit.md) — Baseline-Audit der Ausgangsbasis
- [`docs/qa/skill-and-prompt-plan.md`](docs/qa/skill-and-prompt-plan.md) — Orchestrierung und Werkzeuginventar
- [`docs/qa/sonora-0.2-report.md`](docs/qa/sonora-0.2-report.md) — Vollständiger QA-Abschlussbericht

---

## Lizenz & Datenschutz

- **Lizenz:** Lizenziert unter der [MIT-Lizenz](LICENSE).
- **Datenschutz:** 100 % lokal auf dem Gerät. Keine Netzwerkberechtigung (`android.permission.INTERNET` ist nicht vorhanden), keine Telemetrie, keine Kontopflicht, kein Audio-Streaming.
