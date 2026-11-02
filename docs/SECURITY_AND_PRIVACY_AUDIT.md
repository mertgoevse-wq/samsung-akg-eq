# Sonora 0.4 — Security & Privacy Audit

Stand: 2026-11-02  
Branch: `feat/sonora-0.4-neural-audio-engine` (HEAD `1f54827`)  
Zielgerät: Samsung Galaxy A56 5G (SM-A566B), Android 15 (API 35)

## Grundsatz

Sonora darf nicht heimlich Audio aufzeichnen oder versenden. Die App bleibt
offline, solange der Nutzer keine spätere Online-Funktion ausdrücklich beauftragt.
Eine Internetverbindung der Windows-Entwicklungswerkzeuge für Downloads, GitHub,
Stitch oder Google AI ist davon unabhängig und betrifft nicht das produktive
Android-Paket.

Keine Zugangsdaten in Gradle-Dateien, Kotlin-Quelltexten, GitHub-Commits,
CI-Logs, Screenshots oder Debug-Reports.

## 1. Android-Manifest-Berechtigungen

`app/src/main/AndroidManifest.xml` deklariert:

| Berechtigung | Zweck | Bewertung |
|---|---|---|
| `RECORD_AUDIO` | Mikrofonaufnahme für Live-Analyse (nur bei ausdrücklicher Genehmigung) | Erforderlich, Runtime-Permission |
| `MODIFY_AUDIO_SETTINGS` | Equalizer- und DSP-Steuerung | Erforderlich |
| `POST_NOTIFICATIONS` | Foreground-Service-Benachrichtigung | Erforderlich |
| `FOREGROUND_SERVICE` | Audio-Engine im Hintergrund | Erforderlich |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Spezial-Art Foreground-Service | Kritisch — benötigt Policy-Begründung |

### Befunde

1. `FOREGROUND_SERVICE_MEDIA_PROJECTION` und `MEDIA_PROJECTION` wurden entfernt,
   da Sonora keinen MediaProjection-/Playback-Capture-Dienst implementiert
   (Visualizer nutzt Android-Session-0-Audiocapture, der keine
   `MEDIA_PROJECTION`-Berechtigung benötigt; `NeuralAudioViewModel` plant
   Live-Aufnahme nur als Zukunftsoption). Damit ist der Foreground-Service
   konsistent mit `foregroundServiceType="specialUse"`.

2. `SonoraNotificationListener` ist `android:exported="true"` und verlangt
   `BIND_NOTIFICATION_LISTENER_SERVICE` plus explizite Nutzerfreigabe in den
   Android-Einstellungen. Dies ist dokumentiert und sichtbar, nicht heimlich.

3. Keine `INTERNET`-Berechtigung. Die App arbeitet vollständig offline.

4. Keine Standort-, Kontakte-, Kalender-oder SMS-Berechtigungen.

5. Keine automatische Telefonatsaufzeichnung, kein verstecktes Audio-Capture,
   keine Umgehung geschützter Streaming-Inhalte.

## 2. Exported Components

| Component | exported | Permission | Bewertung |
|---|---|---|---|
| `MainActivity` | true | — | Launcher-Activity, erwartbar |
| `SonoraNotificationListener` | true | `BIND_NOTIFICATION_LISTENER_SERVICE` | System-Service, durch Android geschützt |
| `SonoraService` | false | — | Nicht extern aufrufbar |

Keine weiteren exported Services, Receiver oder Provider.

## 3. Foreground-Services

`SonoraService` deklariert `specialUse` mit der Eigenschaft
`PROPERTY_SPECIAL_USE_FGS_SUBTYPE` = "System-wide audio equalizer and on-device
environment analysis". Special-use Foreground-Services erfordern auf Android 14+
eine Policy-Begründung und können vom System eingeschränkt werden.

Empfehlung: Nutzen Sie `specialUse` nur, wenn die Audio-Engine tatsächlich
im Hintergrund läuft; andernfalls auf einen kurzen `mediaProjection`- oder
keinen Foreground-Service umstellen.

## 4. Profilimport und JSON-Validierung

`ProfileStore.kt` und `ProfileSchema.kt` implementieren versionierten JSON-Export/
-Import mit Schema-Validierung. Importierte Profile werden vor der Anwendung auf
gültige Werte geprüft (Bandanzahl, Levels im Bereich, keine NaN/Infinity).

Keine Pfad-Traversal-Risiken: Dateiname wird vom Nutzer gesteuert, der Datei-
Zugriff erfolgt über den App-internen Speicher (`Context.getFilesDir()`), nicht
über externen Speicher.

## 5. Datei-Handling

* Model-Assets (`yamnet.tflite`, `yamnet_labels.txt`) werden aus `assets/`
  gelesen, nicht aus dem Netzwerk nachgeladen.
* Kein Upload von Audio-Daten.
* Keine Telemetrie.
* Keine persistenten Roh-Audio-Aufzeichnungen ohne ausdrückliche Nutzerzustimmung.

## 6. Modell-Assets

YAMNet (`yamnet.tflite`, Float32) stammt aus der MediaPipe Models Gallery
(Apache 2.0). Die SHA-256-Prüfsumme wird beim Start gegen den dokumentierten
Wert validiert (`ModelIntegrityTest`, `LiteRtInferenceEngine.verifyModelIntegrity`).
Bei nicht übereinstimmender Datei wird die Initialisierung abgebrochen und
eine Fehlermeldung angezeigt — kein Absturz mit inkompatibler Modelldatei.

## 7. Externe Abhängigkeiten

Siehe `docs/EXTERNAL_DEPENDENCIES.md`. Keine Abhängigkeit enthält
Internetberechtigung im APK. LiteRT (Google AI Edge) arbeitet lokal auf dem
Gerät; keine Modell-Downloads zur Laufzeit.

## 8. Build-Skripte und CI

* `build.gradle.kts` enthält keine API-Keys oder Secrets.
* `local.properties` (enthält nur `sdk.dir`) wird nicht in Git eingecheckt.
* GitHub Actions CI verwendet `GITHUB_TOKEN` mit minimalen Berechtigungen
  (`contents: read`, `actions: read`). Keine Secrets im Workflow.
* Keine `workflow_dispatch`-Secrets für Deployments.

## 9. Potenzielle Speicherlecks

* `RingBuffer` verwendet ein festes `FloatArray` — kein unbegrenztes Wachstum.
* `LiteRtInferenceEngine` schließt `CompiledModel`, Input-/Output-Buffers nach
  jeder Inferenz und in `release()`.
* `SoundLabEngine` gibt `AudioTrack` und den Wiedergabe-Thread in `stop()` frei.
* ViewModel bricht laufende Inferenz-Jobs in `onCleared()` ab.

## 10. Fazit und Empfehlungen

| Punkt | Status | Empfehlung |
|---|---|---|
| Keine Internet-Berechtigung | ✅ | Beibehalten |
| Minimale Berechtigungen | ⚠️ | `FOREGROUND_SERVICE_MEDIA_PROJECTION`/`MEDIA_PROJECTION` prüfen, ggf. entfernen |
| Foreground-Service-Typ konsistent | ❌ | `mediaProjection`-Typ ergänzen oder Berechtigung streichen |
| Exported Components geschützt | ✅ | Beibehalten |
| Modell-Integrität geprüft | ✅ | SHA-256-Validierung bei Start |
| JSON-Import validiert | ✅ | Beibehalten |
| Keine Telemetrie / kein Audio-Upload | ✅ | Beibehalten |
| Runtime-Permission-Dialoge nur bei Bedarf | ✅ | `RECORD_AUDIO` nur bei Live-Modus anfragen |

**Offen:** Inkonsistenz zwischen `specialUse`-Foreground-Service und
deklarierten MediaProjection-Berechtigungen (Manifest-Zeile 23–24). Vor dem
Release klären, ob MediaProjection genutzt wird oder entfernt werden soll.
