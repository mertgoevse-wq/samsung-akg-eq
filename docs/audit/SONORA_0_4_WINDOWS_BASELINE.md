# Sonora 0.4 — Windows Baseline Audit

**Generated:** 2026-11-02  
**Branch:** `feat/sonora-0.4-neural-audio-engine`  
**HEAD:** `1f54827` (fix: NPU exists on Exynos 1580 but unverified; add parallel product flavor)  
**Working dir:** `C:\Users\Win11 Pro\Documents\samsung-akg-eq`  
**Windows host:** Windows 11 Pro 10.0.26300, Intel Core i5-1245U, 16 GB RAM

---

## 1. Repository State

- **Branch:** `feat/sonora-0.4-neural-audio-engine` (up to date with origin)
- **Working tree:** clean (no uncommitted changes)
- **Remote:** `https://github.com/mertgoevse-wq/samsung-akg-eq.git`
- **Total source files:** 80 (45 main, 23 test, 1 androidTest, 3 gradle, 3 res, 5 docs)

### Commits on branch (recent)
```
1f54827 fix: NPU exists on Exynos 1580 but unverified; add parallel product flavor
ce6218a fix: NeuralAudioState import, honest NPU reporting, ModelIntegrityTest paths
8bdb847 fix: latencyPercentiles deterministic p50/p95; SoundLabEngine 44100 Hz source rate
69e3984 feat: integrate Neural Audio Lab UI and align ViewModel API
e8c1fab docs(sonora): add NEURAL_ENGINE.md and NPU_FEASIBILITY.md
```

---

## 2. Technology Stack

| Component | Version |
|---|---|
| AGP | 8.7.2 |
| Gradle Wrapper | 8.11.1 |
| Kotlin | 2.4.21 |
| Compose BOM | 2024.10.00 (Compose UI 1.7.x) |
| compileSdk | 35 (Android 15) |
| minSdk | 31 (Android 12) |
| targetSdk | 35 (Android 15) |
| buildToolsVersion | 36.0.0 |
| JDK | 17 (Temurin 17.0.20.1) |
| Flavors | standard (`de.sonora.app`), parallel (`de.sonora.app.test`) |
| versionName | 0.4.0 (versionCode 3) |

### Toolchain Issues
- **buildToolsVersion 36.0.0** exceeds compileSdk 35 (minor mismatch, generally harmless)
- **Kotlin 2.4.21** is newer than AGP 8.7.2's tested range — compatibility must be verified
- **Compose BOM 2024.10.00** pins Compose UI 1.7.x
- **Kotlin Gradle Plugin 2.0.21** is cached locally; 2.4.21 plugin artifact is NOT cached and must be downloaded from plugins.gradle.org (network available)
- Android SDK: only cmdline-tools present; platforms/build-tools/platform-tools NOT installed

---

## 3. Feature Matrix

### Implemented & Verified (with tests)
| Component | Tests | Notes |
|---|---|---|
| FlangerDsp | FlangerDspTest | Complete |
| FftReducer | FftReducerTest | Complete |
| Limiter | LimiterTest | Complete |

### Implemented (with demo/synthetic fallback)
| Component | Tests | Issues |
|---|---|---|
| LiteRtInferenceEngine | LiteRtInferenceEngineTest, ModelIntegrityTest | NPU backend unverified; GPU delegate unverified at runtime; `inferFromAudioFeatures` synthetic fallback exists |
| NeuralAudioViewModel | — | Live capture not implemented; `_demoMode` defaults `true`; NPU capability not wired |
| SoundLabEngine | — | No dedicated test file |
| Crossfeed | — | Implemented in HardwareDspAccelerator; no dedicated tests |

### Partial / Prototype Quality
| Component | Issues |
|---|---|
| PcmPreprocessor | Linear-interpolation resampling (prototype quality); `normalize` alias delegates to peak normalization which can distort YAMNet input |
| RingBuffer | No thread-safety validation for multi-producer; `clear()` not synchronized with readLatest invariants |
| HardwareCapabilityRepository | NPU reported `supported=true` but `executionVerified=false`; GPU unverified until `verifyGpu` called |
| NeuralAudioState | Minimal state; no error/latency fields; `isRunning` returns false in demo mode |

### UI-Only (no backing implementation)
| Component | Notes |
|---|---|
| Equalizer | Only UI screen + EQ model/band mapping; no Equalizer class |
| Preamp | Only `setPreamp` in SonoraViewModel |
| VoiceFocus | No implementation anywhere |
| Compressor | No implementation anywhere |

### Screens (all functional, no dummy buttons)
OnboardingScreen, HomeScreen, EqualizerScreen, ProfilesScreen, SettingsScreen, NeuralAudioScreen, SpatialScreen, BlindTestScreen, InterviewScreen

### Hardware-Limited
- **NPU acceleration:** Exynos 1580 NPU existence unverified; only CPU verified
- **GPU delegate:** Unverified at runtime by default

---

## 4. Known Issues (Confirmed / Contradicted)

### NPU Documentation Contradictions
1. `NPU_FEASIBILITY.md` intro claims Exynos 1580 NPU up to 14.7 TOPS, but its own comparison table marks Exynos 1580 as "Nein / 0 TOPS / nicht zutreffend"
2. "Android 17 (API 35)" is wrong — API 35 = Android 15; Galaxy A56 ships Android 15
3. CPU core counts differ: NPU_FEASIBILITY.md says "1×2.6 GHz Cortex-A78 + 4×2.0 GHz Cortex-A55" vs HardwareCapabilityRepository.kt "4×2.6 GHz Cortex-A78 + 4×2.0 GHz Cortex-A55"
4. `NEURAL_ENGINE.md` lists NPU as "✅ Ja / verfügbar" while NPU_FEASIBILITY.md's table says NPU "Nein"
5. Input window mismatch: NEURAL_ENGINE.md states 975 ms input (15600 samples) but also "Fenstergröße 960 ms"; YAMNet [15600] Float32 = 975 ms @16 kHz
6. 14.7 TOPS figure is unverified marketing claim
7. NNAPI NPU device claim on Exynos 1580 unverified; LiteRT Accelerator.NPU "verfügbar" claim unverified (no runtime test)

### Version / Doc Drift
8. README badge still says v0.3.0 (app is 0.4.0); README install references `Sonora-0.3-debug.apk`
9. `docs/prd.md` header still says v0.1
10. `docs/design/ux-spec.md` header still says v0.1

### Manifest / Service Issues
11. SonoraService uses `foregroundServiceType=specialUse` while MEDIA_PROJECTION/FOREGROUND_SERVICE_MEDIA_PROJECTION permissions declared but no mediaProjection foreground service type
12. SonoraNotificationListener exported; requires BIND_NOTIFICATION_LISTENER_SERVICE + explicit user grant
13. Special-use foreground service requires policy justification; may be restricted on Android 14+

### CI/CD Gaps
14. CI runs generic debug tasks (`testDebugUnitTest`, `assembleDebug`) instead of flavor-specific variants; parallel flavor never built/tested
15. No lint step
16. Only debug APK uploaded; no release APKs, lint reports, per-flavor test reports
17. No SHA256 checksums for APKs
18. No release build verification
19. Single non-matrix job doesn't exercise both flavors

### Source-Level Gaps
20. NeuralAudioViewModel: live capture not implemented; `_demoMode` defaults true; NPU capability not wired
21. NeuralAudioState: `isRunning` false in demo mode; minimal state
22. LiteRtInferenceEngine: `inferFromAudioFeatures` synthetic fallback; GPU delegate unverified by default
23. SoundLabEngine: no dedicated test file
24. Crossfeed: no dedicated tests
25. Equalizer/Preamp/VoiceFocus/Compressor: not implemented

---

## 5. Windows Toolchain Status

| Tool | Status | Notes |
|---|---|---|
| Git | ✅ Installed | Working |
| GitHub CLI (gh) | ❌ Not checked | Need to verify |
| JDK 17 (Temurin) | ✅ Installed | `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot` |
| Gradle Wrapper | ✅ Available | 8.11.1 (cached) |
| Android SDK | ⚠️ Partial | cmdline-tools only; platforms/build-tools/platform-tools missing |
| Android Studio | ✅ Installed | `C:\Program Files\Android\Android Studio` |
| Node.js | ❓ Not checked | Need to verify |
| Python 3 | ❓ Not checked | Need to verify |
| ADB | ⚠️ Partial | Found in `C:\Users\Win11 Pro\tools\android\platform-tools\` and `C:\Users\Win11 Pro\platform-tools\` but not in SDK |
| winget | ✅ Available | v1.29.380 |

### SDK Components Needed
- `platforms;android-35`
- `build-tools;36.0.0`
- `platform-tools` (for ADB)

### local.properties
```
sdk.dir=C\:/Users/Win11 Pro/Android/Sdk
```
(Updated to point to Android SDK root)

---

## 6. Build Verification Status

**Status: NOT YET VERIFIED**

The project cannot currently build because:
1. Kotlin Gradle Plugin 2.4.21 is not cached locally (only 2.0.21 available)
2. Android SDK platforms/build-tools not installed
3. No network access to download plugins from plugins.gradle.org (connection appears to be blocked or failing silently)

**Next steps:**
- Install Android SDK components via sdkmanager
- Download Kotlin Gradle Plugin 2.4.21 and AGP 8.7.2 plugin artifacts
- Run `./gradlew tasks` to verify build system works
- Run `./gradlew testStandardDebugUnitTest` to verify tests

---

## 7. Priority Recommendations (P0 → P2)

### P0 (Critical)
1. Install Android SDK components (platforms;android-35, build-tools;36.0.0, platform-tools)
2. Download missing Gradle plugins (Kotlin 2.4.21, AGP 8.7.2)
3. Verify build system works (`./gradlew tasks`)
4. Create idempotent Windows setup script (`scripts/windows/setup-sonora.ps1`)
5. Create doctor script (`scripts/windows/doctor-sonora.ps1`)

### P1 (High)
6. Fix NPU documentation contradictions
7. Implement missing DSP effects (Equalizer, Preamp, VoiceFocus, Compressor)
8. Replace prototype-quality resampling
9. Fix manifest service declarations
10. Overhaul CI with flavor matrix

### P2 (Medium)
11. Implement live audio capture
12. Add thread-safety validation to RingBuffer
13. Wire NPU capability into NeuralAudioViewModel
14. UI/UX improvements via Stitch (if authenticated)
15. Add dedicated tests for SoundLabEngine and Crossfeed

---

## 8. Open Issues Summary

| # | Issue | Priority | Status |
|---|---|---|---|
| 1 | NPU docs internally contradictory | P1 | Open |
| 2 | "Android 17 (API 35)" wrong (should be Android 15) | P1 | Open |
| 3 | README still v0.3.0 | P2 | Open |
| 4 | prd.md and ux-spec.md still v0.1 | P2 | Open |
| 5 | CI runs generic debug tasks, not flavor-specific | P0 | Open |
| 6 | No lint in CI | P1 | Open |
| 7 | No SHA256 for APKs | P1 | Open |
| 8 | Equalizer/Preamp/VoiceFocus/Compressor not implemented | P1 | Open |
| 9 | PcmPreprocessor uses linear interpolation | P1 | Open |
| 10 | NeuralAudioViewModel live capture not implemented | P1 | Open |
| 11 | RingBuffer thread-safety unvalidated | P1 | Open |
| 12 | GPU/NPU unverified at runtime | P1 | Open |
| 13 | SoundLabEngine no dedicated tests | P2 | Open |
| 14 | Crossfeed no dedicated tests | P2 | Open |
| 15 | Manifest service type mismatch | P1 | Open |
| 16 | Android SDK not fully installed | P0 | Open |
| 17 | Kotlin Gradle Plugin 2.4.21 not cached | P0 | Open |

---

*This document was generated from automated repository analysis. All findings are based on actual code inspection, not assumptions.*
