# Sonora

**Systemweiter Equalizer und persönliche Klangprofile für das Galaxy A56 — ohne Root, ohne Konto, ohne Netzwerk.**

![Version](https://img.shields.io/badge/version-0.1.0-C98B5E)
![Android](https://img.shields.io/badge/Android-12%2B-3DDC84)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF)
![Tests](https://img.shields.io/badge/Tests-58%20gr%C3%BCn-4C9A6A)

Sonora ersetzt die feste Samsung-Abstimmung durch einen Equalizer, den du selbst einstellst — oder durch ein Profil, das aus acht Alltagsfragen entsteht. Alles läuft auf dem Gerät. Die App hat keine Netzwerkberechtigung.

> **Status:** v0.1.0, Debug-Build zum Sideloaden. Zielgerät ist das Galaxy A56 5G mit den USB-C-Hörern EO-IC100. Kein Play-Store-Release.

---

## Was funktioniert — und was nicht

| Bereich | Status |
|---|---|
| 10-, 31- und 64-Band-Equalizer, −12 bis +12 dB | Funktioniert, soweit Android die Kontrolle des Systemausgangs erlaubt |
| Vorverstärkung mit Limiter gegen Übersteuern | Funktioniert |
| Presets, Profile speichern, Export/Import als JSON | Funktioniert |
| A/B-Vergleich, Interview-Profil, verblindeter Hörtest | Funktioniert |
| Raumklang-Modi über `Virtualizer` und `EnvironmentalReverb` | Geräteabhängig, wird in der App offen angezeigt |
| Umgebungsmessung über das Mikrofon | Misst nur den Pegel und ordnet ihn drei Stufen zu |
| Erkennung laufender Apps/Titel | Optional, braucht Benachrichtigungszugriff |
| Hörschutz-Erinnerung nach einer Stunde lautem Hören | Funktioniert, senkt die Lautstärke nie selbst |
| „KI"-Klangverbesserung | **Nicht enthalten.** Es lag keine Modelldatei im Paket; der Dienst verarbeitete Stille. Er wurde entfernt, statt eine Wirkung zu behaupten. |

### Die wichtige Einschränkung

Ohne Root darf eine App den globalen Audio-Mix nur steuern, wenn One UI es zulässt. Sonora prüft das beim Start und zeigt das Ergebnis unter **Jetzt → Systemausgang** und in **Mehr → Diagnose**:

- **„Klangänderung angefordert"** — Android hat die Effektkontrolle freigegeben.
- **„Gerät erlaubt keine Steuerung des Systemausgangs"** — Android verweigert den Zugriff. Die App speichert deine Kurve weiterhin, kann sie aber nicht anwenden. Es gibt keinen stillen Workaround; genau das zu verschweigen wäre der Fehler.

Raumklang hängt zusätzlich davon ab, ob das Gerät `Virtualizer` und `EnvironmentalReverb` bereitstellt. Mittenfokus und Dynamikkontrolle werden von Android nicht als getrennte Effekte angeboten und deshalb auch nicht behauptet.

---

## Installation auf dem Galaxy A56

Die fertige APK liegt auf dem Handy unter:

```
/storage/emulated/0/Download/Sonora-0.1.0-debug.apk
```

1. Öffne **Eigene Dateien** (Samsung) → **Downloads** → `Sonora-0.1.0-debug.apk`.
2. Tippe die Datei an. Android fragt nach der Erlaubnis, aus dieser Quelle zu installieren → **Zulassen**.
3. **Installieren** → **Öffnen**.
4. Im Onboarding: Benachrichtigungen erlauben, Mikrofon optional, Benachrichtigungszugriff optional. Ohne Mikrofon funktioniert alles außer der Umgebungsmessung.
5. Nach dem Start siehst du eine stille Benachrichtigung **„Sonora ist aktiv"**. Sie hält den Klangdienst am Leben und lässt sich über **Mehr → Hintergrund → Beenden** stoppen.

Die APK ist debug-signiert. Updates müssen deshalb mit derselben Signatur installiert werden; eine später signierte Release-Version verlangt eine Neuinstallation.

---

## Equalizer benutzen

**1. Prüfen, ob der Systemausgang mitspielt**

Öffne **Jetzt**. Die Karte **Systemausgang** zeigt den Zustand. Steht dort die Warnung, kann Sonora auf diesem Gerät nicht systemweit eingreifen — dann ist der Equalizer gespeichert, aber nicht hörbar.

**2. Kurve einstellen**

| Element | Bedeutung |
|---|---|
| **10 / 31 / 64+** | Anzahl der Regler. Die Kurve wird beim Wechsel interpoliert, nicht verworfen. |
| **Presets** | Fertige Kurven, sofort hörbar. |
| **Vorverstärkung** | Absenken, wenn laute Bässe übersteuern. Der Limiter begrenzt die Anhebung automatisch. |
| **Bänder** | Jeder Regler von −12 bis +12 dB in 0,5-dB-Schritten. |
| **A/B** | Vergleicht deine Kurve mit neutral, ohne sie zu verlieren. |
| **Zurücksetzen** | Setzt alle Bänder auf 0 dB. |
| **Profil speichern** | Legt die aktuelle Kurve unter einem Namen ab. |

Die Zeile unter den Reglern sagt, auf wie viele echte Gerätebänder deine Kurve abgebildet wird. Ein Gerät mit weniger Bändern bekommt trotzdem eine sinnvolle Näherung; jedes Geräteband wird nur einmal belegt, damit keine Anhebung doppelt wirkt.

**3. Profil dauerhaft nutzen**

Unter **Profile** aktivierst du ein gespeichertes Profil, exportierst es als JSON oder importierst eines. Das Format ist versioniert und wird beim Import migriert.

**4. Wenn du nicht weißt, wo du anfangen sollst**

- **Interview:** acht Fragen in Alltagssprache → Kurve mit Vorschau → übernehmen und speichern.
- **Verblindeter Test:** zehn Durchläufe, Sonora gegen neutral, Bewertung 1–10. Das Ergebnis ist die ehrlichste Rückmeldung, die diese App geben kann.

---

## Die fünf Bereiche

| Tab | Inhalt |
|---|---|
| **Jetzt** | Systemstatus, aktives Profil, Umgebung, Hörschutz, Schnellstart für Interview und Hörtest |
| **Klang** | Equalizer, Presets, Vorverstärkung, A/B, Profil speichern |
| **Raum** | Neun eigene Raumklang-Modi plus „Aus", gemeinsamer Stärke-Regler |
| **Profile** | Aktivieren, speichern, exportieren, importieren, löschen |
| **Mehr** | Berechtigungen, Diagnose, Inhaltserkennung, Hörschutz, Hintergrunddienst |

Die Raumnamen sind eigene Bezeichnungen. Dolby, DTS und Auro werden weder dekodiert noch als Fähigkeit dargestellt.

---

## Datenschutz

- Keine `INTERNET`-Berechtigung. Kein Konto, kein Telemetrieversand.
- Mikrofon nur für Pegelmessung; kein Mitschnitt, keine Speicherung.
- Profile und Diagnoseereignisse liegen lokal im App-Speicher.
- Benachrichtigungszugriff liest App-Name und Titel, nie den Ton. Abschaltbar.
- Die Hörschutz-Erinnerung ändert die Lautstärke nicht.

---

## Technik

| Pfad | Aufgabe |
|---|---|
| `audio/SonoraEngine.kt` | Android-Effekte: Equalizer, Virtualizer, Reverb, Limiter |
| `audio/MediaWatcher.kt` | Laufende Medien über `MediaSessionManager` erkennen |
| `audio/EnvironmentAnalyzer.kt` | Pegelmessung und Einordnung |
| `core/EqModel.kt` | Bandraster und Interpolation zwischen 10/31/64 Bändern |
| `core/BandMapping.kt` | Abbildung UI-Band → Geräteband ohne Doppelbelegung |
| `core/Limiter.kt` | Begrenzung der Anhebung |
| `core/ProfileSchema.kt` | Versioniertes JSON-Format mit Migration |
| `data/ProfileStore.kt` | Persistenz und Import/Export |
| `service/SonoraService.kt` | Vordergrunddienst, Hörschutz-Erinnerung |
| `ui/` | Compose-Oberfläche, Theme und Screens |

Kein Root, keine nativen Bibliotheken, keine Fremd-DSP. Die App nutzt ausschließlich die öffentlichen Android-Audioeffekte.

---

## Selbst bauen

Voraussetzungen: JDK 17, Android SDK mit Plattform 35 und Build-Tools 36.0.0, Gradle 8.14.5.

```bash
# SDK-Pfad einmalig eintragen (wird nicht committet)
echo "sdk.dir=$HOME/Android/Sdk" > local.properties

# Debug-APK bauen
gradle --no-daemon assembleDebug

# Unit-Tests (58)
gradle --no-daemon testDebugUnitTest
```

Ergebnis: `app/build/outputs/apk/debug/app-debug.apk`.

Die Konfiguration ist bewusst konservativ (`gradle.properties`: ein Worker, kein Daemon, 1536 MiB Heap), damit sie auch auf dem Telefon in Termux/PRoot durchläuft.

---

## Dokumentation

- [`docs/prd.md`](docs/prd.md) — Produktanforderungen, Umfang, Key Results
- [`docs/design/ux-spec.md`](docs/design/ux-spec.md) — Interaktions- und Zustandsregeln
- [`DESIGN.md`](DESIGN.md) — Farben, Typografie, Komponenten

---

## Lizenz

Lizenziert unter der [MIT-Lizenz](LICENSE).
