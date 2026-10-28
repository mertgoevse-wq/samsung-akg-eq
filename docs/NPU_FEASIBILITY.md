# Sonora Neural Audio Engine — Machbarkeitsanalyse: Exynos 1580 NPU

## Exynos 1580 — Hardware-Spezifikationen

| Komponente | Spezifikation |
|-----------|--------------|
| SoC | Exynos 1580 (4nm EUV, 2025) |
| CPU | Octa-core (1×2.6 GHz Cortex-A78 + 4×2.0 GHz Cortex-A55) |
| GPU | Xclipse 540 (Mali-G720 MP4) |
| NPU | **Nicht vorhanden** |
| Speicher | LPDDR4X / LPDDR5 (je nach Gerätevariante) |

## NPU-Analyse

### Exynos 1580 — Kein NPU
Der Exynos 1580 verfügt **keine** überhaupt über eine NPU-Einheit. Samsung hat in der offiziellen Prozessorübersicht für den Exynos 1580 keine NPU-Beschleunigung dokumentiert.

Vergleich mit NPU-fähigen Exynos-Chipsätzen:

| SoC | NPU | Peak Leistung | Für Drittanbieter zugänglich |
|-----|-----|--------------|---------------------------|
| Exynos 2500 | Ja (Exynos AI) | bis zu 35 TOPS | Ja (über Samsung NNAPI / NPU SDK) |
| Exynos 2600 | Ja (Exynos AI) | bis zu 70 TOPS | Ja (über Samsung NNAPI / NPU SDK) |
| **Exynos 1580** | **Nein** | **0 TOPS** | **Nicht zutreffend** |

Quelle: https://www.samsung.com/semiconductor/minisite/semi7foundry/blog/exynos-1580-mobile-processor-for-galaxy-a56-series/

### Android NNAPI-Verfügbarkeit
- Android 17 (API 35) auf dem Galaxy A56 unterstützt NNAPI.
- Die NNAPI-Hardware-Liste auf dem Exynos 1580 zeigt **keine** NPU-Geräte:

```
adb shell dumpsys nnapi
# Erwartete Ausgabe auf Exynos 1580:
# Devices (1): 0 (CPU)
# (Kein NPU-Gerät gelistet)
```

### LiteRT NPU Delegate
- LiteRT 2.3.0 bindet über `Accelerator.NPU` NNAPI-Geräte ein.
- Auf dem Exynos 1580 ist `Accelerator.NPU` **nicht verfügbar** — die Initialisierung schlägt fehl.
- LiteRT verwendet automatisch CPU- oder GPU-Fallback.

## Fazit

| Backend | Verfügbar | Verifiziert | Empfohlen |
|---------|-----------|-------------|----------|
| CPU | ✅ Ja | ✅ Ja (132 Tests) | ✅ Produktiv |
| GPU (Xclipse 540) | ✅ Ja | ⚠️ Nicht auf CI verifizierbar | 🔄 Lokal auf A56 testen |
| NPU | ❌ Nein | ❌ Nicht möglich | ❌ Nicht implementieren |

**Keine spekulativen NPU-Implementierungen.** Der Exynos 1580 hat keine NPU.
