# Sonora 0.2 — Skill- und Prompt-Orchestrierungsplan

Planungsdatum: 2026-10-22  
Autor: Sonora Core Architecture & Engineering Team  
Status: Bestätigt (Freigegeben für Ausführung)

---

## 1. Skill-, Plugin- & Werkzeuginventar

| Element | Typ | Verfügbarkeitsstatus | Priorität | Zweck | Datenschutz & Lizenz | Entscheidung |
|---|---|---|---|---|---|---|
| **Google Stitch MCP** (`mcp__stitch__*`) | MCP Server | **Verfügbar** | Hoch | High-Fidelity UI-Prototyping, Screen- & Design-System-Generierung im Hero-Stil | Google AI / Stitch terms, keine Nutzergeheimnisse übertragen | **Verwenden** |
| **Android SDK Tools** (`aapt`, `aapt2`, `apksigner`, `apkanalyzer`) | CLI Tools | **Verfügbar** | Kritisch | Manifest-, Ressourcen-, Signatur- und Größenprüfung | Apache 2.0 / Android SDK Lizenz, 100% lokal | **Verwenden** |
| **Gradle** (`gradle v8.14.5`) | Build System | **Verfügbar** | Kritisch | Kompilierung, Unit-Tests, Packaging auf Termux/PRoot | Apache 2.0, 100% lokal | **Verwenden** |
| **ADB** (`adb v1.0.41`) | Debug Bridge | **Teilweise (Server aktiv, kein Gerät)** | Hoch | Gerätediagnose, `dumpsys audio`, Logcat | Apache 2.0, lokal | **Verwenden sobald Gerät angebunden** (sonst als "ausstehend" dokumentieren) |
| **FFmpeg / FFprobe** | CLI Tools | **Verfügbar** | Mittel | Testsignal- und Audioverifikation | LGPL/GPL, lokal | **Verwenden für DSP-Signalprüfungen** |
| **Apktool / JADX** | CLI Tools | **Nicht verfügbar** (fehlen im Container) | Niedrig | APK-Dekompilierung | Apache 2.0 | **Alternative: aapt/apkanalyzer nutzen** |
| **Firebase MCP** | MCP Plugin | **Nicht verfügbar** (Verbindung fehlgeschlagen) | Niedrig | Telemetrie | Proprietär; widerspricht Zero-Network-PRD | **Nicht verwenden** (Sonora hat keine Netzwerkberechtigung) |
| **GitHub MCP** | MCP Plugin | **Nicht verfügbar** (Auth Header Fehler) | Mittel | Issue/PR-Verwaltung | GitHub API | **Alternative: Git CLI direkt nutzen** |
| **Gemini CLI / MCP** | MCP Tool | **Verbindungsabbruch** | Mittel | Externe Analyse | Google AI | **Lokal arbeiten** |

---

## 2. Standard-Ausführungsreihenfolge (14 Phasen)

1. **Repository- und Projektanalyse:** Ausgangscommit `8aaef5e`, HEAD, Branch `main`, PRD und Richtlinien vollständig erfasst. (*Bestanden*)
2. **Sicherheits- und Secret-Audit:** Prüfen auf API-Keys, Keystores, Passwörter, unerlaubte Netzwerkberechtigungen. (*Bestanden*)
3. **Architektur- und Codebase-Review:** Trennung von Audio, UI, KI, Daten/Profilen, Diagnostik und Tests. (*In Bearbeitung*)
4. **Baseline-Build und Testanalyse:** 58/58 Tests bestanden, Baseline Debug-APK SHA-256 verifiziert. (*Bestanden*)
5. **Android-, Kotlin- und Compose-Implementierung:** Saubere Trennung von UI-State und Engine, robuste Lifecycle-Integration.
6. **Audio-, DSP- und Routing-Analyse:** `AudioDeviceCallback`, echte USB-C-Erkennung, DSP-Kette (Crossfeed, Widening, Reverb, Voice, Night-DRC), NPU/GPU-Rechenpipeline.
7. **Accessibility- und UI-Qualitätsprüfung:** Mindestkontrast 4.5:1 (normal) / 3:1 (groß), 48 dp Trefferflächen, TalkBack Semantik.
8. **Stitch- oder Design-Workflow:** Erstellung des Sonora-Designsystems und der Hero-Screens via Google Stitch MCP.
9. **Testautomatisierung und Geräteverifikation:** Erweiterung der Testsuite um Routing, DSP-Signale, Limiter-NaN-Schutz, Migration.
10. **APK-, Manifest- und Dependency-Analyse:** `apkanalyzer`, `aapt dump`, `apksigner`.
11. **Datenschutz- und Netzwerkprüfung:** Bestätigung: `android.permission.INTERNET` fehlt, keine Telemetrie.
12. **README- und Dokumentationsoptimierung:** Klare Sprache, keine irreführenden Claims, Aktualisierung aller Anleitungen.
13. **GitHub- und CI/CD-Pflege:** Vorbereitung sauberer Git-Commits.
14. **Release- und Abnahmeprüfung:** Bereinigung der Contributor-Einträge ("mert" und "claude"), Push und QA-Abschlussbericht `docs/qa/sonora-0.2-report.md`.

---

## 3. Lizenz- und Datenschutzbewertung

- **Sonora Code:** MIT-Lizenz (wie in `LICENSE` definiert).
- **Abhängigkeiten:** Ausschließlich Standard-AndroidX, KotlinX Serialization und JUnit. Keine proprietären Fremd-DSP-Bibliotheken oder verdeckte GPL-Kontaminationen.
- **Datenschutz:** Absolute Vertraulichkeit. Alle Audiosignale, Pegelmessungen und Profile verbleiben zu 100% lokal im geschützten App-Speicher (`Context.filesDir` / `SharedPreferences`). Keine Telemetriedaten werden übertragen.
