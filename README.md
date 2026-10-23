# Sonora

**Persönliche Klangprofile und Equalizer für das Galaxy A56 — lokal auf dem Gerät, ohne Root und ohne Konto.**

![Version](https://img.shields.io/badge/version-0.3.0-C98B5E)
![Android](https://img.shields.io/badge/Android-12%2B-3DDC84)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF)

Sonora ist ein systemweiter Equalizer mit 10/31/64+ Bändern, 14 Presets, USB-C-Geräteerkennung und lokalen, regelbasierten Klangvorschlägen. Audioeffekte wirken nur, wenn Android und One UI sie tatsächlich freigeben. Die App hat keine Netzwerkberechtigung.

> **Status:** v0.3.0. Debug-APK gebaut, 83 Unit-Tests bestanden. Echter Gerätetest auf dem Galaxy A56 ausstehend.

## Was funktioniert — und was nicht

| Bereich | Status |
|---|---|
| USB-C-Erkennung & Routing | Erkennt angeschlossene Ausgänge live über `AudioDeviceCallback`. Erkannter Ausgang ≠ garantierte systemweite Wirkung. |
| Equalizer (10, 31, 64+ Bänder) | Funktioniert, soweit One UI Session 0 erlaubt. Status: angefordert / verfügbar / bestätigt. |
| DSP-Parameterberechnung | Filterkoeffizienten in Kotlin auf der CPU. Kein NNAPI/NPU oder GPU-Compute. |
| Vorverstärkung & Limiter | Funktioniert. Dynamischer Headroom-Schutz gegen Clipping. |
| 14 Studio-Presets & Profile | Vollständig implementiert. |
| Profilverwaltung | Speichern, Duplizieren, Umbenennen, Löschen, JSON-Export/Import mit Schema-Validierung. |
| Raumklang-DSP | Virtualizer und EnvironmentalReverb über Android-Audioeffekte. Crossfeed, Sprachfokus und DRC sind als Mix-Parameter beschrieben, aber **nicht** als separate Echtzeitfilter implementiert. HRTF ehrlich als nicht verfügbar ausgewiesen. |
| Live-Wellenform | Optional über Android `Visualizer`. Nur nach ausdrücklichem Einschalten und Mikrofonfreigabe. |
| Lokale Klangvorschläge | Offline über feste Regeln im `ai/`-Modul. Kein lernendes Modell, keine NPU/GPU-Inferenz. |
| Hörschutz & Lautstärke-Wächter | Funktioniert. Erinnert nach einer Stunde bei hohem Pegel. Senkt nie ungefragt. |
| Interview / KI-Assistent | Regelbasiert, nicht neuronales Modell. Custom-Profile als eigenständige Profile. |

## Wichtige Einschränkung

Ohne Root darf eine App den globalen Audio-Mix nur steuern, wenn One UI es zulässt. Sonora prüft das beim Start und zeigt das Ergebnis unter **Jetzt → Klang-Engine** und in **Mehr → Diagnose**.

## Installation auf dem Galaxy A56

Die fertige APK liegt unter `/storage/emulated/0/Download/Sonora-0.3-debug.apk`.

1. Öffne **Eigene Dateien** → **Downloads** → `Sonora-0.3-debug.apk`.
2. Tippe die Datei an → **Zulassen** (aus dieser Quelle installieren).
3. **Installieren** → **Öffnen**.

## Selbst bauen

Voraussetzungen: JDK 17, Android SDK (Platform 35, Build-Tools 36.0.0), Gradle 8.14.5.

```bash
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
gradle --no-daemon assembleDebug
gradle --no-daemon testDebugUnitTest
```

Ergebnis: `app/build/outputs/apk/debug/app-debug.apk`

## Weiterarbeiten am Raspberry Pi 400

Dieses Repo ist für Entwicklung am Galaxy A56 (Termux/PRoot/Debian) optimiert. Am Raspberry Pi 400 (Linux/ARM64) funktioniert der Build mit JDK 17 + Android SDK + Gradle identisch. Klone das Repo und baue mit `gradle --no-daemon assembleDebug`.

## Dokumentation

- `docs/prd.md` — Produktanforderungen
- `DESIGN.md` — Farben, Typografie, Barrierefreiheit
- `docs/design/ux-spec.md` — UX-Spezifikation
- `docs/qa/sonora-0.3-audit.md` — Audit-Ausgangsstand
- `docs/qa/sonora-0.2-report.md` — QA-Abschlussbericht v0.2

## Lizenz & Datenschutz

- **Lizenz:** MIT
- **Datenschutz:** 100 % lokal. Keine Netzwerkberechtigung, keine Telemetrie, keine Kontopflicht, kein Audio-Streaming.
