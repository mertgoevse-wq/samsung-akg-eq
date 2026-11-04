# Sonora 0.5 — NPU/GPU Audio Enhancement PRD

## Problem Statement
Der Nutzer (Galaxy A56, Exynos 1580) möchte einen systemweiten Audio-Enhancer mit Echtzeit-Störgeräusch-Cancellation, 3D Audio/Surround-Simulation und NPU/GPU-Beschleunigung. Aktuell existieren nur CPU-basierte DSP-Filter und ein YAMNet-Klassifikator. NPU/GPU-Backends sind code-technisch vorhanden, aber nicht auf dem Gerät verifiziert.

## Key Hypothesis
Wir glauben, dass NPU-beschleunigte Audio-Enhancement-Modelle (Noise Suppression, Quality Upscaling) auf dem Exynos 1580 in Echtzeit laufen können und hörbar bessere Klangqualität liefern als reine CPU-DSP. Wir wissen es, wenn die Inferenzzeit < 10ms pro Frame beträgt und der Nutzer im Blindtest ≥8/10 bewertet.

## Must Have (v1)
1. Vollständiger Bauer-Crossfeed als Echtzeit-PCM-Prozessor (✅ bereits implementiert)
2. 9 Spatial Audio Modi mit Dolby/Atmos-Namen (✅ bereits implementiert, erweiterbar)
3. NPU-Backend-Verifikation auf Gerät (Code vorhanden, Test nötig)
4. GPU-Backend-Verifikation auf Gerät (Code vorhanden, Test nötig)
5. 432 Hz Tuning Toggle (neu)
6. Störgeräusch-Cancellation via NPU-Modell (neu, Modell nötig)
7. HRTF-basiertes 3D Audio (neu, Kopfmodell nötig)

## Nice to Have (v2)
- Echte Dolby Atmos Object-Based Rendering
- 21.1/5.1/7.1 Surround-Simulation
- Audio-Quality-Upscaling-Modell auf NPU
- Play Store Release

## Out of Scope
- Root-Zugriff oder Kernel-Modifikationen
- Echte Dolby-Lizenzierung (nur Simulation/Benennung)
- Cloud-basierte Verarbeitung

## Success Metrics
| Metric | Target | How Measured |
|--------|--------|--------------|
| NPU Inferenzzeit | < 10ms/Frame | SonoraLogger |
| GPU Inferenzzeit | < 15ms/Frame | SonoraLogger |
| CPU Inferenzzeit | < 50ms/Frame | SonoraLogger |
| Blindtest-Bewertung | ≥ 8/10 | BlindTest.kt |

## Implementation Phases
| # | Phase | Description | Status |
|---|-------|-------------|--------|
| 1 | NPU/GPU Backend-Verifikation | Code vorhanden, auf Gerät testen | pending |
| 2 | Bauer-Crossfeed Echtzeit | ✅ Implementiert (StereoCrossfeedDsp) | complete |
| 3 | 432 Hz Tuning | Toggle + Frequenz-Shift | pending |
| 4 | Störgeräusch-Cancellation | NPU-Modell + Pipeline | pending |
| 5 | HRTF 3D Audio | Kopfmodell-Integration | pending |
| 6 | Surround-Simulation | 2.1/3.1/5.1/7.1/21.1 | pending |
| 7 | NPU Audio-Quality-Modell | Upscaling auf NPU | pending |

*Generated: 2026-11-03*
*Status: DRAFT*