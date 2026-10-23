# Sonora 0.3 – Audit-Ausgangsstand

## Umgebung
- OS: Debian GNU/Linux 13 (trixie), Arch: aarch64
- Freier Speicher: ~2.2 GB (98 % belegt) — kein voller Gradle-Rebuild ohne Freigabe
- RAM: 7.2 GB total, 2.3 GB verfügbar
- Gradle-Cache: 8.8 GB
- Projektgröße: 218 MB

## Toolchain
- JDK: OpenJDK 17.0.20.1 (Temurin)
- Gradle: 8.14.5
- ADB: 1.0.41 (34.0.5-debian)
- Claude Code: 2.1.309
- Android SDK: /home/mert/Android/Sdk

## Git
- Branch: feat/sonora-0.3-personal-audio (neu aus main)
- main: 441a9fd (1 Commit ahead of origin/main 69fb56c)
- Remote: https://github.com/mertgoevse-wq/samsung-akg-eq.git
- ADB: kein Gerät verbunden (Wireless Debugging manuell erforderlich)

## APK-Hashes
- Eigene Dateien/Sonora-0.2.0-debug.apk: 3d15457406640d9184642d1d88e309c1acc2acf388b6dcde1f9209a75230bc56
- app/build/outputs/apk/debug/app-debug.apk: identisch (3d1545...)
- Referenz-SHA aus Bericht (3f3a71...) stimmt NICHT mit gemessenem Hash überein.

## Tests
- 83 Unit-Tests bestanden, 0 fehlgeschlagen (testDebugUnitTest)

## Änderungen (diese Session)
- SpatialMode.kt: Crossfeed, Sprachfokus, DRC von ACTIVE auf PARTIALLY_ACTIVE korrigiert (ehrlicher DSP-Status)
- InterviewScreen.kt: Textfarbe der "überspringen"-Schaltfläche auf Paper (Kontrast-Fix)
- Branch erstellt: feat/sonora-0.3-personal-audio
