# Sonora

**Persönliche Klangprofile und Equalizer für das Galaxy A56 — lokal auf dem Gerät, ohne Root und ohne Konto.**

![Version](https://img.shields.io/badge/version-0.2.0-C98B5E)
![Android](https://img.shields.io/badge/Android-12%2B-3DDC84)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF)

<a href="https://github.com/mertgoevse-wq/samsung-akg-eq"><strong>Quellcode auf GitHub ansehen</strong></a>

Sonora bietet einen Equalizer mit 10, 31 oder 64+ Bändern, 14 Presets, USB-C-Geräteerkennung und lokale, regelbasierte Klangvorschläge. Audioeffekte wirken nur, wenn Android und One UI sie tatsächlich freigeben. Die App besitzt keine Netzwerkberechtigung.

> **Status:** v0.2.0. Debug-APK gebaut, 83 Unit-Tests bestanden. Der echte Gerätetest auf dem Galaxy A56 steht noch aus.

---

## Was funktioniert — und was nicht

Sonora misst Umgebungslautstärke zur optionalen EQ-Anpassung. **Das ist keine aktive Geräuschunterdrückung (ANC)**: ANC erfordert passende Mikrofone und eine Echtzeit-Gegenphase im Audioausgang. Die App erstellt auch kein Audio-Routing durch sich selbst; sie nutzt Android-Audioeffekte, sofern das Gerät diese freigibt.

| Bereich | Status |
|---|---|
| **USB-C-Erkennung & Routing** | Erkennt angeschlossene Ausgänge live über `AudioDeviceCallback`. Ein erkannter Ausgang belegt nicht automatisch, dass der systemweite Effekt greift. |
| **Equalizer (10, 31, 64+ Bänder)** | Funktioniert, soweit One UI die Kontrolle von Session 0 erlaubt. Statustrennung: angefordert, technisch verfügbar, bestätigt. |
| **DSP-Parameterberechnung** | Filterkoeffizienten werden in Kotlin auf der CPU berechnet; NaN- und Infinity-Eingaben werden begrenzt. NNAPI/NPU- und GPU-Compute sind nicht eingebunden. Eine früher angezeigte feste Latenz war keine verlässliche Messung. |
| **Vorverstärkung & Limiter** | Funktioniert. Dynamischer Headroom-Schutz verhindert digitales Clipping. |
| **14 Studio-Presets & Profile** | Vollständig implementiert: Neutral, Basskontrolle, Kräftiger Bass mit Headroom, Stimme, Podcast, Klassik, Rock, Metal, Hip-Hop, Elektronisch, Kino, Bühne, Raumklang, Nacht. Inklusive Preamp, Herkunft, Unsicherheit und Grenzen. |
| **Profilverwaltung** | Speichern, Duplizieren, Umbenennen, Löschen, versionierter JSON-Export und fehlertoleranter Import mit Schema-Validierung. |
| **Raumklang-DSP** | Echte, mathematisch getrennte Stufen: Stereo-Widening (Hardware-Virtualizer), Bauer-Crossfeed (280 µs ITD), Raumhall & Reflexionen (`EnvironmentalReverb`), Sprachfokus und Nachtmodus-DRC. HRTF wird ehrlich als nicht verfügbar ausgewiesen mangels gerätespezifischem SOFA-Modell. |
| **Live-Wellenform** | Optional über Android `Visualizer` am Systemausgang; startet nur nach ausdrücklichem Einschalten und Mikrofonfreigabe. Android/OEMs können die Signalabfrage verweigern. |
| **Lokale Klangvorschläge** | Funktionieren offline über feste Regeln im `ai/`-Modul. Das ist kein lernendes Modell und verwendet keine NPU/GPU-Inferenz. Pegel und Headroom werden vor Übernahme geprüft. |
| **Hörschutz & Lautstärke-Wächter** | Funktioniert. Erinnert nach einer Stunde bei hohem Medienpegel. Senkt die Lautstärke niemals ungefragt. |
| **Compose-Oberfläche** | Dark-Anthrazit/Kupfer-Stil mit verständlicheren Statusangaben. Der hier verfügbare Lauf hat keinen Stitch-MCP-Zugang; ein neues Stitch-Design ist daher nicht Teil dieses Builds. |

### Die wichtige Einschränkung

Ohne Root darf eine App den globalen Audio-Mix nur steuern, wenn One UI es zulässt. Sonora prüft das beim Start und zeigt das Ergebnis unter **Jetzt → Klang-Engine** und in **Mehr → Diagnose**:

- **„Verarbeitung angefordert: Ja · Session 0: Bestätigt“** — Android hat die Effektkontrolle freigegeben.
- **„Nicht verfügbar: Das Gerät erlaubt keine Steuerung des Systemausgangs“** — Android verweigert den Zugriff auf Session 0. Die App speichert deine Kurve weiterhin, kann sie aber nicht global erzwingen. Es gibt keinen stillen Workaround; genau das offen zu benennen ist das Prinzip von Sonora.

---

## Designhinweis

Die Oberfläche ist native Jetpack Compose. In dieser Entwicklungsumgebung ist kein Google-Stitch-MCP-Server eingerichtet; das Redesign beansprucht daher keine Stitch-Generierung.

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

# Unit- & DSP-Signaltests ausführen
gradle --no-daemon testDebugUnitTest
```

Ergebnis: `app/build/outputs/apk/debug/app-debug.apk` (SHA-256: `3d15457406640d9184642d1d88e309c1acc2acf388b6dcde1f9209a75230bc56`).

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
