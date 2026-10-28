# Sonora Neural Audio Engine

## Architekturübersicht

```
Audio Input (16 kHz mono PCM)
       │
       ▼
PcmPreprocessor → Anti-Aliasing → Normalisierung [-1, 1]
       │
       ▼
RingBuffer (überlappende Fenster: 975 ms Input)
       │
       ▼
LiteRtInferenceEngine (YAMNet, 521 Klassen)
       │
       ├──► CPU: CompiledModel.create(Accelerator.CPU)
       ├──► GPU: CompiledModel.create(Accelerator.GPU)
       │        (mit CPU-Fallback auf NPU-freien Geräten)
       └──► NPU: ❌ Nicht verfügbar auf Exynos 1580
       │
       ▼
ModelResult: top-5 labels + scores + inference time
```

## YAMNet Modell-Spezifikationen

| Parameter | Wert |
|-----------|------|
| Sample Rate | 16 kHz (mono) |
| Input Tensor | [16000] Float32 (1 Sekunde Rohwelligkeit) |
| Output Tensor | [1, 521] Float32 (Klassenscores) |
| Klassen | 521 (AudioSet) |
| Fenstergröße | 960 ms (96 Frames × 10 ms Hop) |
| Mel-Bins | 64 (125–7500 Hz, Hann-Fenster) |
| Modellgröße | ~14 MB (quantisiert int8) |

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

## Hardware-Unterstützung (Galaxy A56 / Exynos 1580)

| Backend | Verfügbar | Verifiziert | Status |
|---------|-----------|-------------|--------|
| CPU | ✅ Ja | ✅ Ja | Produktiv |
| GPU (Xclipse 540) | ✅ Ja | ⚠️ Laufzeit-Verifikation nötig | `initializeGpu()` testen |
| NPU | ❌ Nein | ❌ | Siehe [NPU_FEASIBILITY.md](NPU_FEASIBILITY.md) |

## Lizenzierung

- YAMNet: Apache 2.0 (TensorFlow Models) — https://github.com/tensorflow/models
- Labels: 521 Klassen aus AudioSet — https://storage.googleapis.com/audioset/yamnet/yamnet_class_map.csv
- LiteRT: Google AI Edge — https://developers.google.com/edge/litert/android
