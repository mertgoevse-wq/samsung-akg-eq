# Sonora Neural Audio Engine

## Architekturübersicht

```
Audio Input (16 kHz mono PCM)
       │
       ▼
PcmPreprocessor → Mono-Konvertierung → Resampling → [-1,1] Clamping
       │
       ▼
RingBuffer (überlappende Fenster: 975 ms Input)
       │
       ▼
LiteRtInferenceEngine (YAMNet, 521 Klassen)
       │
       ├──► CPU: CompiledModel.create(Accelerator.CPU) — immer verfügbar, verifiziert
       ├──► GPU: CompiledModel.create(Accelerator.GPU)
       │        (Laufzeit-Verifikation via echte Inferenz; CPU-Fallback auf NPU-freien Geräten)
       └──► NPU: ✅ Verfügbar auf Exynos 1580 — siehe NPU_FEASIBILITY.md
       │
       ▼
ModelResult: top-5 labels + scores + inference time + backend
```

## YAMNet Modell-Spezifikationen (Float32)

| Parameter | Wert |
|-----------|------|
| Sample Rate | 16 kHz (mono) |
| Input Tensor | [15600] Float32 (975 ms Rohwelligkeit bei 16 kHz) |
| Output Tensor | [1, 521] Float32 (Klassenscores) |
| Klassen | 521 (AudioSet Ontology) |
| Fenstergröße | 960 ms (96 Frames × 10 ms Hop) |
| Mel-Bins | 64 (125–7500 Hz, Hann-Fenster) |
| Modellgröße | ~4 MB (Float32, unquantisiert) |

## Modell-Integrität & Herkunft

- **Quelle:** MediaPipe Models Gallery — MediaPipe Audio Classification
- **Download-URL:** `https://storage.googleapis.com/mediapipe-models/audio_classifier/yamnet/float32/1/yamnet.tflite`
- **Lizenz:** Apache 2.0 (TensorFlow Models)
- **Labels-Quelle:** `https://storage.googleapis.com/audioset/yamnet/yamnet_class_map.csv`
- **SHA-256 (Float32 v1):** `4d8b4a53282dc83ef04e3e7dbc4fbc98082e34e44ed798e16c3a0cdd4c584faf`

### Verifikation

```kotlin
// Model file integrity check
val expectedSha256 = "4d8b4a53282dc83ef04e3e7dbc4fbc98082e34e44ed798e16c3a0cdd4c584faf"
val digest = MessageDigest.getInstance("SHA-256")
val actualHash = digest.digest(modelBytes).joinToString("") { "%02x".format(it) }
require(actualHash == expectedSha256) { "Model integrity check failed" }

// Tensor contract validation
val inputBuffers = model.createInputBuffers()
val outputBuffers = model.createOutputBuffers()
require(inputBuffers[0].size() == 15600) { "Expected 15600 input samples" }
require(outputBuffers[0].size() == 521) { "Expected 521 output classes" }
```

## LiteRT 2.3.0 API (verifiziert)

```kotlin
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel

val model = CompiledModel.create(
    assetManager,
    "yamnet.tflite",
    CompiledModel.Options(Accelerator.CPU),
    null
)

val inputBuffers = model.createInputBuffers()
val outputBuffers = model.createOutputBuffers()

inputBuffers[0].writeFloat(inputFloatArray)   // FloatArray, size 15600
model.run(inputBuffers, outputBuffers)

val output = outputBuffers[0].readFloat()      // FloatArray, size 521

inputBuffers.forEach { it.close() }
outputBuffers.forEach { it.close() }
model.close()
```

Quelle: https://github.com/google-ai-edge/litert-samples

## Eingabevertrag (Input Contract)

- **Sample Rate:** 16 kHz (muss vor Resampling validiert werden)
- **Kanäle:** Mono (Stereo muss vorab gemischt werden)
- **Amplitude:** Float32 in [-1.0, 1.0] — Peak-Normalisierung wird NICHT angewendet;
  stattdessen Clamping, um Signal-Dynamik zu erhalten
- **Länge:** Mindestens 15600 Samples (~975 ms); kürzere Signale werden mit Nullen aufgefüllt
- **NaN/Infinity:** Eingaben dürfen keine NaN- oder Infinity-Werte enthalten

## Ausgabevertrag (Output Contract)

- **Form:** Float32-Array der Länge 521 (logits/Sigmoid-Wahrscheinlichkeiten)
- **Bereich:** Werte liegen typischerweise in [0.0, 1.0] (Sigmoid-Aktivierung)
- **Validierung:** Alle Werte müssen endlich und nicht-NaN sein
- **Top-K:** Die 5 Klassen mit höchsten Scores werden zurückgegeben

## Hardware-Unterstützung (Galaxy A56 / Exynos 1580)

| Backend | Verfügbar | Verifiziert | Status |
|---------|-----------|-------------|--------|
| CPU | ✅ Ja | ✅ Ja (132 Tests + Golden Sample) | Produktiv |
| GPU (Xclipse 540) | ✅ Ja | ⚠️ Laufzeit-Verifikation nötig | `initializeGpu()` führt echte Inferenz aus |
| NPU | ✅ Ja | ⚠️ Laufzeit-Verifikation nötig | Siehe [NPU_FEASIBILITY.md](NPU_FEASIBILITY.md) |

## Wärmeaufrung (Warm-up)

Nach der Initialisierung wird eine Inferenz mit einem Stille-Eingabefenster (alle Nullen)
ausgeführt, um:
1. Lazy Initialisierung der DSP-Beschleuniger zu veranlassen
2. Erste Inferenzzeitmessung zu ermöglichen
3. Thread-Pool-Initialisierung zu veranlassen

## Thread-Sicherheit

- Inferenz läuft auf einem dedizierten Hintergrundthread
- TensorBuffers werden nach jedem Inferenzlauf geschlossen
- CPU-Afälligkeitsprüfung: `initializeCpu()` ist idempotent
- UI-Thread ist von Inferenz ausgeschlossen

## Lizenzierung

- YAMNet: Apache 2.0 (TensorFlow Models) — https://github.com/tensorflow/models
- Labels: 521 Klassen aus AudioSet — https://storage.googleapis.com/audioset/yamnet/yamnet_class_map.csv
- LiteRT: Google AI Edge — https://developers.google.com/edge/litert/android
