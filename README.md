# Sonora

**Personal sound profiles and equalizer for the Galaxy A56 — on-device, no root, no account.**

![Version](https://img.shields.io/badge/version-0.3.0-C98B5E)
![Android](https://img.shields.io/badge/Android-12%2B-3DDC84)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF)
![License](https://img.shields.io/badge/license-MIT-blue)

Sonora is a system-wide equalizer with 10/31/64+ bands, 14 presets, USB-C device detection, and local rule-based sound suggestions. Audio effects only work when Android and One UI actually allow them. The app has no network permission.

> **Status:** v0.3.0. Debug APK built, 83 unit tests passing. Real device test on the Galaxy A56 pending.

## What works — and what doesn't

| Area | Status |
|---|---|
| USB-C detection & routing | Detects connected outputs live via `AudioDeviceCallback`. Detected output ≠ guaranteed system-wide effect. |
| Equalizer (10, 31, 64+ bands) | Works where One UI allows Session 0 control. Status: requested / available / confirmed. |
| DSP coefficient calculation | Biquad coefficients computed in Kotlin on CPU. No NNAPI/NPU or GPU compute. |
| Preamp & limiter | Works. Dynamic headroom protection against clipping. |
| 14 studio presets & profiles | Fully implemented. |
| Profile management | Save, duplicate, rename, delete, versioned JSON export/import with schema validation. |
| Spatial audio (DSP) | Virtualizer and EnvironmentalReverb via Android audio effects. Crossfeed, voice focus, and DRC are described as mix parameters but are **not** implemented as separate real-time filters. HRTF honestly reported as unavailable. |
| Live waveform | Optional via Android `Visualizer`. Only after explicit opt-in and microphone permission. |
| Local sound suggestions | Offline via fixed rules in the `ai/` module. Not a learning model, no NPU/GPU inference. |
| Hearing protection & volume guard | Works. Reminds after one hour at high volume. Never lowers volume without consent. |
| Interview / AI assistant | Rule-based, not a neural model. Custom profiles as independent profiles. |

## Key limitation

Without root, an app can only control the global audio mix if One UI allows it. Sonora checks this at startup and shows the result under **Now → Sound engine** and in **More → Diagnostics**.

## Install on the Galaxy A56

The finished APK is at `/storage/emulated/0/Download/Sonora-0.3-debug.apk`.

1. Open **My Files** → **Downloads** → `Sonora-0.3-debug.apk`.
2. Tap the file → **Allow** (install from this source).
3. **Install** → **Open**.

## Build from source

Requirements: JDK 17, Android SDK (Platform 35, Build-Tools 36.0.0), Gradle 8.14.5.

```bash
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
gradle --no-daemon assembleDebug
gradle --no-daemon testDebugUnitTest
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

## Continue development on Raspberry Pi 400

This repo is optimized for development on the Galaxy A56 (Termux/PRoot/Debian). On a Raspberry Pi 400 (Linux/ARM64) the build works identically with JDK 17 + Android SDK + Gradle. Clone the repo and build with `gradle --no-daemon assembleDebug`.

## Documentation

- `docs/prd.md` — Product requirements
- `DESIGN.md` — Colors, typography, accessibility
- `docs/design/ux-spec.md` — UX specification
- `docs/qa/sonora-0.3-audit.md` — Audit baseline
- `docs/qa/sonora-0.2-report.md` — QA report v0.2

## Translations

This README is in English. Translations are available:

- [Deutsch (German)](docs/i18n/README.de.md)
- [Türkçe (Turkish)](docs/i18n/README.tr.md)
- [Español (Spanish)](docs/i18n/README.es.md)
- [Français (French)](docs/i18n/README.fr.md)
- [中文 (Chinese)](docs/i18n/README.zh.md)
- [日本語 (Japanese)](docs/i18n/README.ja.md)
- [한국어 (Korean)](docs/i18n/README.ko.md)
- [हिन्दी (Hindi)](docs/i18n/README.hi.md)

Want to add a translation? Open a PR with a new file in `docs/i18n/`.

## License & Privacy

- **License:** MIT
- **Privacy:** 100% on-device. No network permission, no telemetry, no account required, no audio streaming.
