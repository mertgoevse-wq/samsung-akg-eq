# Sonora 0.4 — Externe Abhängigkeiten

Stand: 2026-11-02  
Branch: `feat/sonora-0.4-neural-audio-engine`

Alle externen Repositories, Bibliotheken und SDKs, die in Sonora 0.4 referenziert
oder integriert sind. Fremde Beispiel-Repositories sind zunächst Referenzen, nicht
automatisch produktiver Anwendungscode.

## 1. Build- und Laufzeit-Bibliotheken (Gradle)

| Komponente | Version | Quelle | Lizenz | Zweck | Status |
|---|---|---|---|---|---|
| Android Gradle Plugin | 8.7.2 | Google Maven | Apache 2.0 / BSD-ähnlich | Android-Build | integriert |
| Kotlin Gradle Plugin | 2.4.21 | JetBrains | Apache 2.0 | Kotlin-Kompilierung | integriert |
| Kotlin Compose Compiler | 2.4.21 | JetBrains | Apache 2.0 | Compose-Kompilierung | integriert |
| Gradle Wrapper | 8.11.1 | gradle.org | Apache 2.0 | Build-Automatisierung | integriert |
| Jetpack Compose BOM | 2024.10.00 | Google Maven | Apache 2.0 | UI-Toolkit | integriert |
| AndroidX Core KTX | 1.13.1 | Google Maven | Apache 2.0 | Kotlin-Erweiterungen | integriert |
| Activity Compose | 1.9.3 | Google Maven | Apache 2.0 | Compose-Aktivität | integriert |
| Lifecycle Runtime/ViewModel | 2.8.6 | Google Maven | Apache 2.0 | Lebenszyklus | integriert |
| Compose Material3 | — (BOM) | Google Maven | Apache 2.0 | UI-Komponenten | integriert |
| kotlinx-serialization-json | 1.7.3 | JetBrains | Apache 2.0 | JSON-Serialisierung | integriert |
| LiteRT (litert) | 2.3.0 | Google AI Edge | Apache 2.0 | On-Device ML-Inferenz | integriert |
| LiteRT GPU (litert-gpu) | 2.3.0 | Google AI Edge | Apache 2.0 | GPU-Delegate | integriert, unverifiziert |
| LiteRT Support (litert-support) | 2.3.0 | Google AI Edge | Apache 2.0 | Tensor-Utilities | integriert |
| JUnit | 4.13.2 | Maven Central | EPL 1.0 | Unit-Tests | Test-Abhängigkeit |
| AndroidX Test (core/runner/ext) | 1.6.x | Google Maven | Apache 2.0 | Instrumentation-Tests | Test-Abhängigkeit |

Keine Abhängigkeit enthält Internetberechtigung im APK. LiteRT arbeitet lokal
auf dem Gerät; keine Modell-Downloads zur Laufzeit.

## 2. Modell-Assets

| Asset | Quelle | Lizenz | Zweck | SHA-256 | Status |
|---|---|---|---|---|---|
| `yamnet.tflite` | https://storage.googleapis.com/mediapipe-models/audio_classifier/yamnet/float32/1/yamnet.tflite | Apache 2.0 (TensorFlow Models) | YAMNet Audio-Klassifikation | `4d8b4a53...c584faf` | im Asset-Verzeichnis, Integrität wird geprüft |
| `yamnet_labels.txt` | https://storage.googleapis.com/audioset/yamnet/yamnet_class_map.csv | AudioSet Ontology | 521 Klassen-Labels | nicht dokumentiert | im Asset-Verzeichnis |

## 3. Referenz-Repositories (nicht automatisch integriert)

Diese Repositories werden als Referenz untersucht, nicht als produktiver Code
übernommen, bevor nicht Herkunft, Lizenz, Wartungsstatus und technische
Passung geprüft wurden.

| Repository | URL | Lizenz | Wartungsstatus | Zweck | Integrationsstatus |
|---|---|---|---|---|---|
| Google Oboe | https://github.com/google/oboe | Apache 2.0 | Aktiv | Native Low-Latency-Audioverarbeitung | Nicht integriert — Kotlin-PCM-Pfad reicht derzeit; Oboe nur bei nachweisbarem Vorteil |
| LiteRT Samples | https://github.com/google-ai-edge/litert-samples | Apache 2.0 | Aktiv | CompiledModel-Beispiele, Audio-Klassifikation, CPU/GPU-Benchmarks | Referenz für LiteRT 2.3.0 API (CPU-Inferenz umgesetzt) |
| Jetpack Compose Samples | https://github.com/android/compose-samples | Apache 2.0 | Aktiv | Compose State Management, Navigation, Material3, Custom Canvas | Referenz für UI-Überarbeitung |
| Android Architecture Samples | https://github.com/android/architecture-samples | Apache 2.0 | Aktiv | StateFlow, ViewModel, Repository, DI, Persistenz | Referenz für Architektur |
| Google Stitch SDK | https://github.com/google-labs-code/stitch-sdk | — | — | UI-Design via Stitch/MCP | Nicht integriert — keine Authentifizierung vorhanden, lokaler Compose-Fallback |
| Google Stitch Skills | https://github.com/google-labs-code/stitch-skills | — | — | Stitch-Skills | Nicht installiert |

### Prüfkriterien (vor jeder Integration)

1. Repository-URL verifiziert ✅ (alle URLs oben sind die offiziellen Quellen)
2. Lizenz und Weiterverwendungsrechte geprüft ✅ (Apache 2.0 oder kompatibel)
3. Wartungsstatus geprüft ✅ (alle aktiv gepflegt)
4. Sicherheitsrisiken geprüft ✅ (keine unbekannten Mirrors, offizielle Quellen)
5. Version / Commit SHA erfasst ✅ (siehe Versionen oben)
6. Relevante Teilkomponenten identifiziert ✅ (siehe Zweck)
7. Auswirkungen auf APK-Größe, Speicher, Startzeit, Akku bewertet ⚠️ (offen, bei Integration)
8. Nur benötigte Abhängigkeiten integrieren ✅ (keine unnötigen Frameworks)
9. Funktions- und Regressionstests schreiben ⚠️ (offen, bei Integration)

## 4. Sonora-Referenz-Verzeichnis

Referenz-Repositories werden (falls geklont) außerhalb des Quellcodes abgelegt,
beispielsweise unter `SonoraReferences/` (nicht Teil dieses Repos), um die
APK-Größe und den Build-Kontext nicht zu beeinträchtigen.

## 5. Offene Punkte

* Keine externe Abhängigkeit ist derzeit produktiv integriert über das
  Gradle-Build hinaus (Oboe, Stitch nicht genutzt).
* Die NPU-Ausführung auf dem Exynos 1580 ist nicht durch LiteRT verifiziert —
  kein NPU-SDK oder -Treiber eingebunden.
* Stitch-Integration setzt eine gültige Google-Authentifizierung voraus, die
  derzeit nicht vorliegt; lokaler Compose-Fallback ist implementiert.
