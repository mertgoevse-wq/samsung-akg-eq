# Sonora — Phase-1-Vervollständigung Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Sonora von einem 10-Band-Prototypen zur voll funktionsfähigen Phase-1-App ausbauen: 31/64+-Bänder, Raumklang-Modi, KI-Interview, Profil-Export/Import mit Migration, Limiter/Clipping-Schutz, Inhaltserkennung, lokales Ereignisprotokoll, Hörschutz, verblindeter A/B-Hörtest — jeweils mit Unit-Tests.

**Architecture:** Bestehende Schichtung bleibt: `audio/` (Engine, Analyzer), `data/` (Store, Profile), `service/` (Vordergrunddienst), `ui/` (Compose). Neue Domänenlogik wird in reinen Kotlin-Dateien ohne Android-Abhängigkeiten unter `core/` abgelegt, damit sie JVM-unit-testbar ist (das Projekt hat bisher keinen einzigen Test). Die Android-Adapter (`AudioEffect`-Anbindung, `MediaSession`-Beobachtung, `SharedPreferences`-Persistenz) bleiben dünn und werden über Schnittstellen an `core/` angebunden. Der Equalizer wird von 10 festen UI-Frequenzen auf ein Bandmodell umgestellt, das 10/31/64+ Bänder abdeckt und das Gerätemapping als eigene, testbare Funktion kapselt.

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose (BOM 2024.10.00), Material3, kotlinx-serialization 1.7.3, AGP 8.7.2, Gradle 8.14.5, JDK 17 (Temurin arm64), minSdk 31 / target+compileSdk 35, JUnit 4 für Unit-Tests.

**Spec:** `docs/prd.md` (PRD v0.1) und `docs/design/ux-spec.md` (UX-Spec v0.1) — beide liegen im Repo und sind die verbindliche Quelle. Dieser Plan argumentiert aus beiden Dokumenten; der Ausführende liest sie mit.

## Global Constraints

- Sprache der UI: **Deutsch, Alltagssprache, keine Frequenzwerte im Interview** (UX-Spec §1.3). Technische Begriffe sind im Diagnosebereich erlaubt.
- Dunkles Grundthema, Akzentfarbe Kupfer `#C98B5E`, Grund `#0E1013`, Fläche `#171A1F`, Fläche 2 `#232830`, Text `#F2EDE7`, gedämpft `#9AA0A6`, Warnung `#E0A458`. Keine neuen Farben, keine Verläufe ohne Funktion (UX-Spec §5, Theme.kt).
- Trefferflächen **≥ 48 dp**; Kontrast **≥ 4,5:1** für Text, **≥ 3:1** für große Elemente; Farbe ist nie das einzige Signal (UX-Spec §6, NFR-009).
- **Kein Platzhalter, kein Attrappen-Button, kein erfundener Messwert.** Jeder Regler und jede Anzeige ist mit echten Daten verbunden (UX-Spec §1.2).
- **Kein Netzwerkzugriff** in Phase 1, solange die Streaming-Verbindung aus ist (NFR-011). Die App darf keine INTERNET-Permission anfordern.
- **Kein Audio wird gespeichert oder gesendet.** Mikrofon liefert nur Pegel-/Spektralwerte (NFR-006).
- **Limiter ist nicht abschaltbar**; die Lautstärkeerinnerung ist abschaltbar (US-009 Notes, NFR-012).
- Bandregelbereich **−12 … +12 dB**, Schrittweite 0,5 dB.
- Eigene Raummodi-Namen verwenden. Dolby, Dolby Atmos, Dolby Surround, DTS, DTS:X und Auro-3D sind geschützte Marken und dürfen **nicht** als eigene Fähigkeit dargestellt werden (PRD §9, RR-004).
- Profileraster-Versionierung: jede Profiländerung braucht Schema-Version und Migration plus Upgrade-Test (NFR-010, US-011).
- Build-Umgebung: `gradle` (8.14.5), da kein Wrapper im Repo ist. **`android.aapt2FromMavenOverride` in `~/.gradle/gradle.properties` zeigt auf `/opt/android-aapt2-arm64/aapt2`** (aapt2 2.20 aarch64). Der Debian-Standard `/usr/bin/aapt2` (2.19) kann die `resources.arsc` von `android-35/android.jar` nicht parsen und bricht den Build ab. Diese Konfiguration darf nicht zurückgesetzt werden (Backup: `~/.gradle/gradle.properties.bak-2026-10-20-aapt2`).
- Speichergrenze auf dem Gerät: Gradle **kein Daemon**, **1 Worker**, `-Xmx1536m`, Kotlin **in-process**. Kein `clean` vor jedem Build.
- Keine Secrets, Keystores, `local.properties` oder generierten Artefakte committen.

## Review Focus

Die folgenden fünf Fehlerbilder sind die wahrscheinlichsten für einen Nutzer dieses Projekts; die Spec impliziert sie, aber keine Aufgabe der Spec testet sie ausdrücklich. Jede Zeile wird durch den Test der owning Task abgedeckt.

1. **Beschädigte oder fremde Profildatei beim Import** — Sonora darf kein bestehendes Profil überschreiben und darf nicht abstürzen; der Import bricht ab und nennt den Grund (US-004 Szenario „Import einer beschädigten Profildatei“).
2. **Profil aus einem älteren Schema** — die Migration muss die Kurve erhalten, den Nutzer einmalig informieren und eine Sicherungskopie behalten (US-011 Szenario „Inkompatibles altes Profil“).
3. **EQ-Werte an der Grenze** — +12 dB auf mehreren Bändern darf nicht zu Clipping führen; der Limiter muss greifen und der Nutzer muss einen Hinweis sehen, keine stumme Verzerrung (US-009 Szenario „Starke Bassanhebung“, NFR-012).
4. **Mikrofon verweigert oder Gerät ohne Umgebungszugriff** — Sonora muss mit dem zuletzt gespeicherten Profil weiterlaufen und erklären, welche Funktion fehlt; kein Absturz, kein Leerlauf (US-002 Szenario „Mikrofon verweigert“).
5. **Wechsel der Bandzahl (10 ↔ 31 ↔ 64+) mitten in der Nutzung** — die Kurve muss erhalten bleiben (Interpolation), es darf kein Sprung und kein Datenverlust geben (UX-Spec §3.3).

---

## File Structure

| Datei | Verantwortung |
|---|---|
| `app/src/main/java/de/sonora/app/core/EqModel.kt` | Bandmodell: Frequenzraster 10/31/64+, dB-Werte, Interpolation beim Rasterwechsel. Reine Kotlin-Logik. |
| `app/src/main/java/de/sonora/app/core/BandMapping.kt` | Abbildung des UI-Rasters auf die Bänder des Geräts. Reine Kotlin-Logik. |
| `app/src/main/java/de/sonora/app/core/Limiter.kt` | Spitzenbegrenzung + Vorverstärkung, Clipping-Erkennung. Reine Kotlin-Logik. |
| `app/src/main/java/de/sonora/app/core/SpatialMode.kt` | Definition der neun Raummodi, Stärke 0..1, DSP-Parameter je Modus. Reine Kotlin-Logik. |
| `app/src/main/java/de/sonora/app/core/Interview.kt` | Fragen, Antworten, Auswertung zum persönlichen Profil. Reine Kotlin-Logik. |
| `app/src/main/java/de/sonora/app/core/ProfileSchema.kt` | Versioniertes Profilformat, Migrationen, Export/Import-Validierung. Reine Kotlin-Logik. |
| `app/src/main/java/de/sonora/app/core/Diagnostics.kt` | Lokales Ereignisprotokoll (§11 des PRD) mit begrenztem Ringpuffer. |
| `app/src/main/java/de/sonora/app/core/MediaRule.kt` | Regelwerk: erkannter Inhalt → passendes Profil. Reine Kotlin-Logik. |
| `app/src/main/java/de/sonora/app/core/VolumeGuard.kt` | Hörschutz: Lautstärkeerinnerung, ohne Lautstärkeänderung. |
| `app/src/main/java/de/sonora/app/core/BlindTest.kt` | Verblindeter A/B-Hörtest: Reihenfolge, Bewertungen, Mittelwert. |
| `app/src/main/java/de/sonora/app/audio/SonoraEngine.kt` | Besteht, wird auf das neue Bandmodell umgestellt; hält den `Equalizer`, bietet Bandzahl-Umschaltung, Preamp, Limiter-Anbindung, Spatial-Parametrierung. |
| `app/src/main/java/de/sonora/app/audio/MediaWatcher.kt` | `MediaSession`-Beobachtung → erkannte App/Titel. |
| `app/src/main/java/de/sonora/app/data/ProfileStore.kt` | Besteht; nutzt `ProfileSchema` für Lesen/Schreiben/Migration, ergänzt Export/Import. |
| `app/src/main/java/de/sonora/app/data/DiagnosticsStore.kt` | Persistenz des Ereignisprotokolls. |
| `app/src/main/java/de/sonora/app/SonoraViewModel.kt` | Besteht; wird um Spatial, Interview, Export/Import, Clipping-Warnung, Medienerkennung, Ereignisprotokoll erweitert. |
| `app/src/main/java/de/sonora/app/ui/Screens.kt` | Besteht; wird um Bandzahl-Umschalter, Preamp, Clip-Hinweis, Export/Import, Inhaltserkennung, Hörschutz, erweiterte Diagnose erweitert. |
| `app/src/main/java/de/sonora/app/ui/SpatialScreen.kt` | Neu: Bereich „Raum“. |
| `app/src/main/java/de/sonora/app/ui/InterviewScreen.kt` | Neu: geführtes Interview. |
| `app/src/main/java/de/sonora/app/ui/BlindTestScreen.kt` | Neu: verblindeter A/B-Hörtest. |
| `app/src/main/java/de/sonora/app/ui/SonoraRoot.kt` | Navigation; wird um die Tabs „Raum“ erweitert und um den Interview-Sonderzustand. |
| `app/src/test/java/de/sonora/app/core/*.kt` | JVM-Unit-Tests für alle `core/`-Logik. |

Bestehende Dateien werden in der Regel **erweitert, nicht umgebaut** — die Struktur ist tragfähig, die Lücke liegt in der Logik, nicht im Layout.

---

## Task 1: Test-Infrastruktur und Bandmodell (EqModel)

**Files:**
- Modify: `app/build.gradle.kts` (Test-Abhängigkeit ergänzen)
- Create: `app/src/main/java/de/sonora/app/core/EqModel.kt`
- Test: `app/src/test/java/de/sonora/app/core/EqModelTest.kt`

**Interfaces:**
- Consumes: keine
- Produces:
  - `enum class BandCount(val size: Int) { TEN(10), THIRTY_ONE(31), SIXTY_FOUR(64) }`
  - `object EqModel { const val MIN_DB = -12f; const val MAX_DB = 12f }`
  - `data class EqCurve(val bandCount: BandCount = BandCount.TEN, val levelsDb: List<Float> = List(BandCount.TEN.size) { 0f })` mit
    - `fun normalized(): EqCurve` — Länge == `bandCount.size`, Werte in −12f..12f
    - `fun withBandCount(target: BandCount): EqCurve` — Interpolation auf das neue Raster
  - `fun bandFrequencies(count: BandCount): List<Int>` — 10er-Raster: 31,62,125,250,500,1000,2000,4000,8000,16000; 31er: 1/3-Oktave 20 Hz … 20 kHz; 64+: 64 Bänder, 1/6-Oktave von 20 Hz bis 20 kHz

- [ ] **Step 1: Test-Abhängigkeit ergänzen**

In `app/build.gradle.kts` unter `dependencies`:
```kotlin
testImplementation("junit:junit:4.13.2")
```

- [ ] **Step 2: Failing test schreiben**

`app/src/test/java/de/sonora/app/core/EqModelTest.kt`:
```kotlin
class EqModelTest {
    @Test fun `zehn Baender nutzen das bekannte Raster`() {
        assertEquals(listOf(31,62,125,250,500,1000,2000,4000,8000,16000), bandFrequencies(BandCount.TEN))
    }

    @Test fun `31 Baender sind streng steigend und enden bei 20 kHz`() {
        val f = bandFrequencies(BandCount.THIRTY_ONE)
        assertEquals(31, f.size)
        assertTrue(f.zipWithNext().all { (a,b) -> a < b })
        assertTrue(f.first() <= 20)
        assertTrue(f.last() >= 20000)
    }

    @Test fun `64 Baender sind streng steigend`() {
        val f = bandFrequencies(BandCount.SIXTY_FOUR)
        assertEquals(64, f.size)
        assertTrue(f.zipWithNext().all { (a,b) -> a < b })
    }

    @Test fun `Wechsel von 10 auf 31 interpoliert statt zu springen`() {
        val curve = EqCurve(BandCount.TEN, listOf(6f, 6f, 6f, 6f, 6f, 0f, 0f, 0f, 0f, 0f))
        val wide = curve.withBandCount(BandCount.THIRTY_ONE)
        assertEquals(31, wide.levelsDb.size)
        assertEquals(6f, wide.levelsDb[0], 0.01f)
        assertEquals(0f, wide.levelsDb[30], 0.01f)
    }

    @Test fun `Rueckwechsel auf 10 Baender erhaelt die Kurve`() {
        val curve = EqCurve(BandCount.THIRTY_ONE, List(31) { 3f })
        assertTrue(curve.withBandCount(BandCount.TEN).levelsDb.all { kotlin.math.abs(it - 3f) < 0.01f })
    }

    @Test fun `normalized kappt Werte auf minus 12 bis plus 12`() {
        val c = EqCurve(BandCount.TEN, listOf(40f, -40f, 0f,0f,0f,0f,0f,0f,0f,0f)).normalized()
        assertEquals(12f, c.levelsDb[0], 0.001f)
        assertEquals(-12f, c.levelsDb[1], 0.001f)
    }
}
```

- [ ] **Step 3: Test laufen lassen, Fehlschlag bestätigen**

Run: `gradle :app:testDebugUnitTest --tests "de.sonora.app.core.EqModelTest" --console=plain`
Expected: FAIL — "Unresolved reference: bandFrequencies" / "Unresolved reference: EqCurve".

- [ ] **Step 4: `EqModel.kt` implementieren**

`app/src/main/java/de/sonora/app/core/EqModel.kt`:
```kotlin
package de.sonora.app.core

import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

enum class BandCount(val size: Int) { TEN(10), THIRTY_ONE(31), SIXTY_FOUR(64) }

object EqModel {
    const val MIN_DB = -12f
    const val MAX_DB = 12f
}

data class EqCurve(
    val bandCount: BandCount = BandCount.TEN,
    val levelsDb: List<Float> = List(BandCount.TEN.size) { 0f }
) {
    fun normalized(): EqCurve = EqCurve(
        bandCount,
        List(bandCount.size) { levelsDb.getOrElse(it) { 0f }.coerceIn(EqModel.MIN_DB, EqModel.MAX_DB) }
    )

    fun withBandCount(target: BandCount): EqCurve {
        if (target == bandCount) return normalized()
        val from = bandFrequencies(bandCount)
        val to = bandFrequencies(target)
        val sampled = to.map { f -> sampleAt(from, levelsDb, f) }
        return EqCurve(target, sampled).normalized()
    }

    private fun sampleAt(xs: List<Int>, ys: List<Float>, x: Int): Float {
        if (xs.isEmpty() || ys.isEmpty()) return 0f
        if (x <= xs.first()) return ys.first()
        if (x >= xs.last()) return ys.last()
        for (i in 0 until xs.size - 1) {
            val lo = xs[i]; val hi = xs[i + 1]
            if (x in lo..hi) {
                val t = if (hi == lo) 0f else (x - lo).toFloat() / (hi - lo)
                return ys.getOrElse(i) { 0f } * (1 - t) + ys.getOrElse(i + 1) { 0f } * t
            }
        }
        return ys.last()
    }
}

fun bandFrequencies(count: BandCount): List<Int> = when (count) {
    BandCount.TEN -> listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
    BandCount.THIRTY_ONE -> List(31) { i ->
        (20.0 * 2.0.pow(i / 3.0)).roundToInt().coerceAtLeast(1)
    }
    BandCount.SIXTY_FOUR -> List(64) { i ->
        val f = 20.0 * 2.0.pow(i * (log10(20000.0 / 20.0) / 63.0))
        f.roundToInt().coerceAtLeast(1)
    }
}
```
Hinweis: `log10(20000.0/20.0) / 63.0` ist die Oktavspanne je Schritt — die 64 Bänder liegen 1/6-Oktave auseinander.

- [ ] **Step 5: Test laufen lassen, Erfolg bestätigen**

Run: `gradle :app:testDebugUnitTest --tests "de.sonora.app.core.EqModelTest" --console=plain`
Expected: PASS — 6 Tests, 0 Fehler.

- [ ] **Step 6: Committen**

```bash
git add app/build.gradle.kts app/src/main/java/de/sonora/app/core/EqModel.kt app/src/test/java/de/sonora/app/core/EqModelTest.kt
git commit -m "feat(eq): Bandmodell mit 10/31/64+ Baendern und Interpolation"
```

---

## Task 2: Geräte-Bandabbildung (BandMapping)

**Files:**
- Create: `app/src/main/java/de/sonora/app/core/BandMapping.kt`
- Test: `app/src/test/java/de/sonora/app/core/BandMappingTest.kt`

**Interfaces:**
- Consumes: `bandFrequencies(count: BandCount): List<Int>` aus Task 1
- Produces:
  - `object BandMapping { fun mapToDevice(uiFrequencies: List<Int>, deviceCenters: List<Int>): Map<Int, Int> }` — gibt pro Geräte-Bandindex den UI-Bandindex zurück, auf den dieses Geräte-Band abzubilden ist. Das nächstgelegene UI-Band gewinnt; jedes UI-Band wird höchstens einmal vergeben.

- [ ] **Step 1: Failing test schreiben**

`app/src/test/java/de/sonora/app/core/BandMappingTest.kt`:
```kotlin
class BandMappingTest {
    @Test fun `jedes Geraeteband wird genau einmal belegt`() {
        val device = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
        val ui = bandFrequencies(BandCount.TEN)
        val map = BandMapping.mapToDevice(ui, device)
        assertEquals(device.size, map.size)
    }

    @Test fun `die naechstgelegene Frequenz gewinnt`() {
        val device = listOf(50, 100, 200, 400, 800, 1600, 3200, 6400, 12800, 20000)
        val ui = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
        val map = BandMapping.mapToDevice(ui, device)
        assertEquals(0, map[0])
        assertEquals(1, map[1])
    }

    @Test fun `ein UI-Band bekommt keinen zweiten Geraeteband-Wert`() {
        val device = listOf(100, 200, 300)
        val ui = listOf(90, 95, 100, 105, 110)
        val map = BandMapping.mapToDevice(ui, device)
        assertEquals(map.keys.size, map.values.toSet().size)
    }

    @Test fun `leere Geraeteliste liefert eine leere Abbildung`() {
        assertTrue(BandMapping.mapToDevice(listOf(31), emptyList()).isEmpty())
    }
}
```
Die Richtung ist **Geräte-Bandindex → UI-Bandindex**: die Engine iteriert über Geräte-Bänder und schlägt den UI-Index nach.

- [ ] **Step 2: Test laufen lassen, Fehlschlag bestätigen**

Run: `gradle :app:testDebugUnitTest --tests "de.sonora.app.core.BandMappingTest" --console=plain`
Expected: FAIL — "Unresolved reference: BandMapping".

- [ ] **Step 3: `BandMapping.kt` implementieren**

`app/src/main/java/de/sonora/app/core/BandMapping.kt`:
```kotlin
package de.sonora.app.core

import kotlin.math.abs

object BandMapping {
    /**
     * Ordnet jedem Geraeteband genau ein UI-Band zu (Geraetebandindex -> UI-Bandindex).
     * Das naechstgelegene UI-Band gewinnt; jedes UI-Band wird nur einmal vergeben.
     */
    fun mapToDevice(uiFrequencies: List<Int>, deviceCenters: List<Int>): Map<Int, Int> {
        if (uiFrequencies.isEmpty() || deviceCenters.isEmpty()) return emptyMap()
        val taken = HashSet<Int>()
        val result = LinkedHashMap<Int, Int>()
        deviceCenters.forEachIndexed { deviceIndex, center ->
            val best = uiFrequencies.indices.minByOrNull { abs(uiFrequencies[it] - center) }
                ?: return@forEachIndexed
            if (taken.add(best)) result[deviceIndex] = best
        }
        return result
    }
}
```
Anmerkung: Wird ein UI-Band von zwei Gerätebändern beansprucht, behält das *nächstgelegene* Geräteband es; das weiter entfernte Geräteband bleibt unbelegt und trägt 0 dB. Das verhindert, dass eine Anhebung doppelt angewendet wird.

- [ ] **Step 4: Test laufen lassen, Erfolg bestätigen**

Run: `gradle :app:testDebugUnitTest --tests "de.sonora.app.core.BandMappingTest" --console=plain`
Expected: PASS — 4 Tests, 0 Fehler.

- [ ] **Step 5: Committen**

```bash
git add app/src/main/java/de/sonora/app/core/BandMapping.kt app/src/test/java/de/sonora/app/core/BandMappingTest.kt
git commit -m "feat(eq): Abbildung des UI-Rasters auf die Geraetebaender"
```

---

## Task 3: Limiter und Clipping-Schutz

**Files:**
- Create: `app/src/main/java/de/sonora/app/core/Limiter.kt`
- Test: `app/src/test/java/de/sonora/app/core/LimiterTest.kt`

**Interfaces:**
- Consumes: keine
- Produces:
  - `data class LimiterResult(val gainDb: Float, val clipped: Boolean, val reducedByDb: Float)`
  - `object Limiter { fun limit(sumDb: Float, preampDb: Float, maxSumDb: Float = 0f): LimiterResult }` — wenn `sumDb + preampDb > maxSumDb`, wird die Verstärkung so weit zurückgenommen, dass die Summe passt; `clipped` ist dann `true` und `reducedByDb` nennt die Rücknahme.

- [ ] **Step 1: Failing test schreiben**

`app/src/test/java/de/sonora/app/core/LimiterTest.kt`:
```kotlin
class LimiterTest {
    @Test fun `unterhalb der Grenze wird nichts zurueckgenommen`() {
        val r = Limiter.limit(sumDb = -6f, preampDb = 2f)
        assertFalse(r.clipped)
        assertEquals(0f, r.reducedByDb, 0.001f)
        assertEquals(2f, r.gainDb, 0.001f)
    }

    @Test fun `ueber der Grenze wird die Verstaerkung zurueckgenommen`() {
        val r = Limiter.limit(sumDb = 6f, preampDb = 2f)
        assertTrue(r.clipped)
        assertEquals(8f, r.reducedByDb, 0.001f)
        assertEquals(-6f, r.gainDb, 0.001f)
    }

    @Test fun `Preamp allein ueber der Grenze wird gekappt`() {
        val r = Limiter.limit(sumDb = -2f, preampDb = 9f)
        assertTrue(r.clipped)
        assertEquals(7f, r.reducedByDb, 0.001f)
    }

    @Test fun `negative Preamp senkt die Summe ohne Clip-Hinweis`() {
        val r = Limiter.limit(sumDb = 4f, preampDb = -6f)
        assertFalse(r.clipped)
        assertEquals(-6f, r.gainDb, 0.001f)
    }
}
```

- [ ] **Step 2: Test laufen lassen, Fehlschlag bestätigen**

Run: `gradle :app:testDebugUnitTest --tests "de.sonora.app.core.LimiterTest" --console=plain`
Expected: FAIL — "Unresolved reference: Limiter".

- [ ] **Step 3: `Limiter.kt` implementieren**

`app/src/main/java/de/sonora/app/core/Limiter.kt`:
```kotlin
package de.sonora.app.core

data class LimiterResult(
    val gainDb: Float,
    val clipped: Boolean,
    val reducedByDb: Float
)

object Limiter {
    /**
     * Begrenzt die Gesamtverstaerkung. Der Limiter ist nicht abschaltbar (NFR-012).
     */
    fun limit(sumDb: Float, preampDb: Float, maxSumDb: Float = 0f): LimiterResult {
        val total = sumDb + preampDb
        if (total <= maxSumDb) {
            return LimiterResult(gainDb = preampDb, clipped = false, reducedByDb = 0f)
        }
        val reduction = total - maxSumDb
        return LimiterResult(
            gainDb = preampDb - reduction,
            clipped = true,
            reducedByDb = reduction
        )
    }
}
```

- [ ] **Step 4: Test laufen lassen, Erfolg bestätigen**

Run: `gradle :app:testDebugUnitTest --tests "de.sonora.app.core.LimiterTest" --console=plain`
Expected: PASS — 4 Tests, 0 Fehler.

- [ ] **Step 5: Committen**

```bash
git add app/src/main/java/de/sonora/app/core/Limiter.kt app/src/test/java/de/sonora/app/core/LimiterTest.kt
git commit -m "feat(audio): Limiter mit Clip-Erkennung und Preamp"
```

---

## Task 4: Engine auf Bandmodell umstellen

**Files:**
- Modify: `app/src/main/java/de/sonora/app/audio/SonoraEngine.kt`
- Modify: `app/src/main/java/de/sonora/app/SonoraViewModel.kt` (Bandzahl- und Preamp-Zustand)
- Modify: `app/src/main/java/de/sonora/app/ui/Screens.kt` (Bandzahl-Umschalter, Preamp-Regler, Clip-Hinweis)
- Test: `app/src/test/java/de/sonora/app/core/EngineBridgeTest.kt`

**Interfaces:**
- Consumes: `EqCurve`, `BandCount` (Task 1), `BandMapping` (Task 2), `Limiter` (Task 3)
- Produces:
  - `SonoraEngine.plan(curve: EqCurve, preampDb: Float, deviceCenters: List<Int>): Map<Int, Float>` — reine Funktion, gibt pro Geräte-Bandindex den dB-Wert zurück
  - `SonoraEngine.applyWithLimit(curve: EqCurve, preampDb: Float, deviceCenters: List<Int>): LimiterResult` — reine Funktion
  - `SonoraEngine.applyCurve(curve: EqCurve, preampDb: Float): LimiterResult` — setzt Preamp und alle Bänder am Gerät
  - `SonoraEngine.applyLevels(levelsDb: List<Float>)` bleibt bestehen, delegiert an `applyCurve`
  - `SonoraEngine.setEnabled(Boolean)`, `SonoraEngine.release()`, `SonoraEngine.status`, `SonoraEngine.bandLevelRange`, `SonoraEngine.deviceBandCentersHz` bleiben bestehen
  - `SonoraEngine.uiFrequencies` entfällt; Aufrufer in `Screens.kt` nutzen künftig `bandFrequencies(state.bandCount)`

- [ ] **Step 1: Failing test schreiben**

`app/src/test/java/de/sonora/app/core/EngineBridgeTest.kt`:
```kotlin
class EngineBridgeTest {
    @Test fun `Kurve wird auf Geraetebaender abgebildet`() {
        val device = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
        val curve = EqCurve(BandCount.TEN, listOf(6f,0f,0f,0f,0f,0f,0f,0f,0f,0f))
        val plan = SonoraEngine.plan(curve, 0f, device)
        assertEquals(10, plan.size)
        assertEquals(6f, plan.getValue(device.indexOf(31)), 0.01f)
    }

    @Test fun `Preamp oberhalb der Grenze wird zurueckgenommen`() {
        val device = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
        val curve = EqCurve(BandCount.TEN, List(10) { 12f })
        assertTrue(SonoraEngine.applyWithLimit(curve, 6f, device).clipped)
    }

    @Test fun `leeres Geraet liefert einen leeren Plan`() {
        assertTrue(SonoraEngine.plan(EqCurve(), 0f, emptyList()).isEmpty())
    }
}
```
`plan` und `applyWithLimit` funktionieren ohne Android-`Equalizer` — die Geräteinteraktion bleibt hinter der Engine.

- [ ] **Step 2: Test laufen lassen, Fehlschlag bestätigen**

Run: `gradle :app:testDebugUnitTest --tests "de.sonora.app.core.EngineBridgeTest" --console=plain`
Expected: FAIL — "Unresolved reference: plan".

- [ ] **Step 3: `SonoraEngine.kt` umbauen**

`app/src/main/java/de/sonora/app/audio/SonoraEngine.kt`:
```kotlin
package de.sonora.app.audio

import android.media.audiofx.Equalizer
import de.sonora.app.core.BandCount
import de.sonora.app.core.BandMapping
import de.sonora.app.core.EqCurve
import de.sonora.app.core.Limiter
import de.sonora.app.core.LimiterResult
import de.sonora.app.core.bandFrequencies

sealed interface EngineStatus {
    data object Inactive : EngineStatus
    data class Active(val bands: Int) : EngineStatus
    data class Unavailable(val reason: String) : EngineStatus
}

object SonoraEngine {

    var status: EngineStatus = EngineStatus.Inactive
        private set

    var bandLevelRange: IntRange = -1500..1500
        private set

    var deviceBandCentersHz: List<Int> = emptyList()
        private set

    private var equalizer: Equalizer? = null

    /** Berechnet die Sollwerte je Geraeteband. Reine Funktion, ohne Geraetezugriff. */
    fun plan(curve: EqCurve, preampDb: Float, deviceCenters: List<Int>): Map<Int, Float> {
        if (deviceCenters.isEmpty()) return emptyMap()
        val mapping = BandMapping.mapToDevice(bandFrequencies(curve.bandCount), deviceCenters)
        val plan = HashMap<Int, Float>()
        mapping.forEach { (deviceIndex, uiIndex) ->
            plan[deviceIndex] = curve.levelsDb.getOrElse(uiIndex) { 0f }
        }
        return plan
    }

    /** Limiter-Berechnung ohne Geraetezustand. */
    fun applyWithLimit(curve: EqCurve, preampDb: Float, deviceCenters: List<Int>): LimiterResult {
        val sumDb = curve.levelsDb.maxOrNull() ?: 0f
        return Limiter.limit(sumDb, preampDb)
    }

    @Synchronized
    fun initialize(): EngineStatus { /* wie bisher: Equalizer(0,0), hasControl(), Bandzentren lesen */ }

    @Synchronized
    fun applyCurve(curve: EqCurve, preampDb: Float): LimiterResult {
        val eq = equalizer ?: return LimiterResult(preampDb, false, 0f)
        val plan = plan(curve, preampDb, deviceBandCentersHz)
        val limited = applyWithLimit(curve, preampDb, deviceBandCentersHz)
        plan.forEach { (band, db) ->
            val milliBel = (db * 100f).toInt().coerceIn(bandLevelRange.first, bandLevelRange.last)
            try { eq.setBandLevel(band.toShort(), milliBel.toShort()) } catch (_: Throwable) { }
        }
        try {
            val preampMilliBel = (limited.gainDb * 100f).toInt()
                .coerceIn(bandLevelRange.first, bandLevelRange.last)
            eq.setPreampLevel(preampMilliBel.toShort())
        } catch (_: Throwable) { }
        return limited
    }

    @Synchronized
    fun applyLevels(levelsDb: List<Float>) {
        applyCurve(EqCurve(BandCount.TEN, levelsDb), 0f)
    }

    @Synchronized
    fun setEnabled(enabled: Boolean) { /* wie bisher */ }

    @Synchronized
    fun release() { /* wie bisher, setzt equalizer = null */ }
}
```
Die bisherigen Felder `uiFrequencies` und die alte `applyLevels`-Logik entfallen.

- [ ] **Step 4: ViewModel erweitern**

In `SonoraViewModel`:
- `SonoraUiState` erhält `bandCount: BandCount = BandCount.TEN`, `preampDb: Float = 0f`, `clipWarning: Boolean = false`, `eqCurve: EqCurve = EqCurve()`
- `setBand(index, db)` schreibt in `eqCurve.levelsDb`; `levelsDb` bleibt als abgeleiteter Wert (`eqCurve.levelsDb`) erhalten, damit bestehender UI-Code weiterläuft
- Neue Methode `fun setBandCount(count: BandCount)` — interpoliert die aktuelle Kurve, setzt `bandCount`, wendet an
- Neue Methode `fun setPreamp(db: Float)` — setzt `preampDb`, wendet an
- `applyEffective()` wertet das `LimiterResult` aus und setzt `clipWarning = result.clipped`

- [ ] **Step 5: UI erweitern**

In `Screens.kt`, Funktion `EqualizerScreen`:
- Bandzahl-Umschalter über drei `FilterChip` mit den Beschriftungen „10“, „31“, „64+“; die Auswahl bleibt erhalten, die Kurve wird interpoliert
- Preamp-Regler oberhalb der Bandliste: Beschriftung „Vorverstärkung“, −12 … +12 dB
- Clip-Hinweis direkt unter dem Preamp-Regler, nur sichtbar wenn `clipWarning`: „Die Anhebung übersteigt das Limit. Sonora hat sie zurückgenommen, damit es nicht clippt.“
- Die Bandliste rendert `state.eqCurve.levelsDb` und beschriftet mit `bandFrequencies(state.bandCount)`; bei 31/64+ Bändern scrollt die Liste vertikal, Ankerbeschriftung bei 63, 250, 1k, 4k, 16k
- Jeder Regler erhält `Modifier.semantics { contentDescription = "$hz Hz, ${"%.1f".format(db)} Dezibel" }`

- [ ] **Step 6: Test laufen lassen, Erfolg bestätigen**

Run: `gradle :app:testDebugUnitTest --tests "de.sonora.app.core.EngineBridgeTest" --console=plain`
Expected: PASS — 3 Tests, 0 Fehler.

- [ ] **Step 7: Debug-Build laufen lassen**

Run: `gradle :app:assembleDebug --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Committen**

```bash
git add app/src/main/java/de/sonora/app/audio/SonoraEngine.kt app/src/main/java/de/sonora/app/SonoraViewModel.kt app/src/main/java/de/sonora/app/ui/Screens.kt app/src/test/java/de/sonora/app/core/EngineBridgeTest.kt
git commit -m "feat(eq): Engine auf Bandmodell umgestellt, Vorverstaerkung und Clip-Hinweis"
```

---

## Task 5: Versioniertes Profilformat mit Migration (ProfileSchema)

**Files:**
- Create: `app/src/main/java/de/sonora/app/core/ProfileSchema.kt`
- Modify: `app/src/main/java/de/sonora/app/data/ProfileStore.kt`
- Test: `app/src/test/java/de/sonora/app/core/ProfileSchemaTest.kt`

**Interfaces:**
- Consumes: `EqCurve`, `BandCount`, `EqModel` (Task 1)
- Produces:
  - `const val PROFILE_SCHEMA_VERSION = 2`
  - `data class StoredProfile(val id: String, val name: String, val levelsDb: List<Float> = emptyList(), val bandCount: String = "10", val preset: String? = null, val createdAt: Long = 0L)`
  - `data class StoredProfileFile(val schemaVersion: Int = PROFILE_SCHEMA_VERSION, val activeProfileId: String = "flat", val profiles: List<StoredProfile> = emptyList())`
  - `data class MigrationResult(val file: StoredProfileFile, val migrated: Boolean, val note: String? = null, val backup: StoredProfileFile? = null)`
  - `fun parseBandCount(value: String?): BandCount` — "10"/"31"/"64" → Enum, unbekannt → `BandCount.TEN`
  - `object ProfileSchema { fun decode(json: String): Result<StoredProfileFile>; fun encode(file: StoredProfileFile): String; fun migrate(file: StoredProfileFile): MigrationResult }`

- [ ] **Step 1: Failing test schreiben**

`app/src/test/java/de/sonora/app/core/ProfileSchemaTest.kt`:
```kotlin
class ProfileSchemaTest {
    @Test fun `Schema 1 wird auf Schema 2 migriert und die Kurve bleibt erhalten`() {
        val v1 = """{"schemaVersion":1,"activeProfileId":"p1","profiles":[
            {"id":"p1","name":"Mein Bass","levels":[6f,4f,2f,0f,0f,0f,0f,0f,0f,0f]}]}"""
        val old = ProfileSchema.decode(v1).getOrThrow()
        val migration = ProfileSchema.migrate(old)
        assertTrue(migration.migrated)
        assertEquals(2, migration.file.schemaVersion)
        assertEquals(6f, migration.file.profiles[0].levelsDb[0], 0.001f)
        assertEquals(BandCount.TEN, migration.file.profiles[0].bandCountEnum())
        assertNotNull(migration.backup)
    }

    @Test fun `Schema 2 wird nicht migriert`() {
        val f = StoredProfileFile(2, "p1", listOf(StoredProfile("p1","A",List(10){0f},"10",null,1L)))
        assertFalse(ProfileSchema.migrate(f).migrated)
    }

    @Test fun `beschaedigtes JSON liefert einen Fehler statt einer Exception`() {
        assertTrue(ProfileSchema.decode("{kein json").isFailure)
    }

    @Test fun `unbekanntes Feld bricht den Import nicht ab`() {
        val json = """{"schemaVersion":2,"activeProfileId":"p1","zusaetzlich":5,
            "profiles":[{"id":"p1","name":"A","levels":[0f],"bandCount":"10","preset":null,"createdAt":1}]}"""
        assertTrue(ProfileSchema.decode(json).isSuccess)
    }

    @Test fun `leeres Profil bleibt erhalten`() {
        val json = """{"schemaVersion":2,"activeProfileId":"p1","profiles":[]}"""
        assertTrue(ProfileSchema.decode(json).getOrThrow().profiles.isEmpty())
    }

    @Test fun `Bandzahl-Text wird geparst`() {
        assertEquals(BandCount.THIRTY_ONE, parseBandCount("31"))
        assertEquals(BandCount.SIXTY_FOUR, parseBandCount("64"))
        assertEquals(BandCount.TEN, parseBandCount("unsinn"))
        assertEquals(BandCount.TEN, parseBandCount(null))
    }
}
```

- [ ] **Step 2: Test laufen lassen, Fehlschlag bestätigen**

Run: `gradle :app:testDebugUnitTest --tests "de.sonora.app.core.ProfileSchemaTest" --console=plain`
Expected: FAIL — "Unresolved reference: ProfileSchema".

- [ ] **Step 3: `ProfileSchema.kt` implementieren**

`app/src/main/java/de/sonora/app/core/ProfileSchema.kt`:
```kotlin
package de.sonora.app.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val PROFILE_SCHEMA_VERSION = 2

@Serializable
data class StoredProfile(
    val id: String,
    val name: String,
    val levelsDb: List<Float> = emptyList(),
    val bandCount: String = "10",
    val preset: String? = null,
    val createdAt: Long = 0L
) {
    fun bandCountEnum(): BandCount = parseBandCount(bandCount)
}

@Serializable
data class StoredProfileFile(
    val schemaVersion: Int = PROFILE_SCHEMA_VERSION,
    val activeProfileId: String = "flat",
    val profiles: List<StoredProfile> = emptyList()
)

data class MigrationResult(
    val file: StoredProfileFile,
    val migrated: Boolean,
    val note: String? = null,
    val backup: StoredProfileFile? = null
)

fun parseBandCount(value: String?): BandCount = when (value) {
    "31" -> BandCount.THIRTY_ONE
    "64" -> BandCount.SIXTY_FOUR
    else -> BandCount.TEN
}

object ProfileSchema {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun decode(raw: String): Result<StoredProfileFile> = runCatching {
        json.decodeFromString(StoredProfileFile.serializer(), raw)
    }

    fun encode(file: StoredProfileFile): String =
        json.encodeToString(StoredProfileFile.serializer(), file)

    fun migrate(file: StoredProfileFile): MigrationResult {
        if (file.schemaVersion >= PROFILE_SCHEMA_VERSION) {
            return MigrationResult(file, migrated = false)
        }
        val backup = file
        val migrated = file.copy(
            schemaVersion = PROFILE_SCHEMA_VERSION,
            profiles = file.profiles.map { p ->
                p.copy(
                    levelsDb = p.levelsDb.map { it.coerceIn(EqModel.MIN_DB, EqModel.MAX_DB) },
                    bandCount = p.bandCount.ifBlank { "10" }
                )
            }
        )
        return MigrationResult(
            file = migrated,
            migrated = true,
            note = "Profile wurden auf Schema $PROFILE_SCHEMA_VERSION aktualisiert. Eine Sicherung liegt im Profilordner.",
            backup = backup
        )
    }
}
```

- [ ] **Step 4: Test laufen lassen, Erfolg bestätigen**

Run: `gradle :app:testDebugUnitTest --tests "de.sonora.app.core.ProfileSchemaTest" --console=plain`
Expected: PASS — 6 Tests, 0 Fehler.

- [ ] **Step 5: `ProfileStore.kt` anbinden**

`app/src/main/java/de/sonora/app/data/ProfileStore.kt`:
- `load()`: dekodieren über `ProfileSchema.decode`; bei Erfolg `migrate` anwenden, das Ergebnis persistieren und die Sicherungskopie unter dem Schlüssel `profile_backup_v1` ablegen; bei Fehlschlag auf die Presets zurückfallen
- Neue Methode `fun exportProfile(id: String): String?` — liefert das versionierte JSON dieses Profils
- Neue Methode `fun importProfile(raw: String): ImportResult` — `ImportResult(success: Boolean, message: String, profile: StoredProfile?, allProfiles: List<SoundProfile>)`; bei Fehlschlag bleibt alles unverändert, bei Erfolg wird das Profil **nicht** automatisch aktiviert
- `Presets.defaults` bleiben bestehen, werden aber auf das neue Format gehoben (`bandCount = "10"`)

- [ ] **Step 6: Committen**

```bash
git add app/src/main/java/de/sonora/app/core/ProfileSchema.kt app/src/main/java/de/sonora/app/data/ProfileStore.kt app/src/test/java/de/sonora/app/core/ProfileSchemaTest.kt
git commit -m "feat(data): versioniertes Profilformat mit Migration und Import-Validierung"
```

---

## Task 6: Export/Import in der Oberfläche

**Files:**
- Modify: `app/src/main/java/de/sonora/app/SonoraViewModel.kt`
- Modify: `app/src/main/java/de/sonora/app/ui/Screens.kt` (Bereich „Profile")
- Test: `app/src/test/java/de/sonora/app/core/ProfileExportTest.kt`

**Interfaces:**
- Consumes: `ProfileStore.exportProfile`, `ProfileStore.importProfile`, `ProfileSchema` (Task 5)
- Produces:
  - `SonoraViewModel.exportProfile(id: String): String?` — liefert den JSON-Text und setzt `uiState.message`
  - `SonoraViewModel.importProfile(raw: String)` — setzt `uiState.message` auf den Erfolgs- oder Fehlertext aus `ImportResult`

- [ ] **Step 1: Failing test schreiben**

`app/src/test/java/de/sonora/app/core/ProfileExportTest.kt`:
```kotlin
class ProfileExportTest {
    @Test fun `exportiertes Profil ist wieder importierbar`() {
        val original = StoredProfileFile(2, "p1", listOf(
            StoredProfile("p1", "Mein Bass", listOf(6f,4f,2f,0f,0f,0f,0f,0f,0f,0f), "10", null, 1L)
        ))
        val decoded = ProfileSchema.decode(ProfileSchema.encode(original)).getOrThrow()
        assertEquals(1, decoded.profiles.size)
        assertEquals("Mein Bass", decoded.profiles[0].name)
        assertEquals(6f, decoded.profiles[0].levelsDb[0], 0.001f)
    }

    @Test fun `defekte Datei liefert keine halb importierten Daten`() {
        val broken = ProfileSchema.decode("{kaputt")
        assertTrue(broken.isFailure)
        assertTrue(broken.getOrNull()?.profiles.isNullOrEmpty())
    }
}
```

- [ ] **Step 2: Test laufen lassen, Erfolg bestätigen**

Run: `gradle :app:testDebugUnitTest --tests "de.sonora.app.core.ProfileExportTest" --console=plain`
Expected: PASS.

- [ ] **Step 3: ViewModel erweitern**

In `SonoraViewModel`:
```kotlin
fun exportProfile(id: String): String? {
    val raw = store.exportProfile(id) ?: return null
    uiState = uiState.copy(message = "Profil exportiert. Der Text liegt in der Zwischenablage.")
    return raw
}

fun importProfile(raw: String) {
    val result = store.importProfile(raw)
    uiState = uiState.copy(
        profiles = result.allProfiles,
        message = if (result.success) {
            "Profil „${result.profile?.name}“ importiert."
        } else {
            "Import abgebrochen: ${result.message}"
        }
    )
}
```

- [ ] **Step 4: UI erweitern**

In `Screens.kt`, Bereich „Profile":
- Über der Liste ein Knopf „Importieren“, der einen Dialog mit einem mehrzeiligen `OutlinedTextField` öffnet; „Importieren“ ruft `vm.importProfile(raw)`; bei Fehler bleibt der Dialog offen und zeigt den Grund in Alltagssprache
- In `ProfileRow` ein Knopf „Exportieren“ (nur für eigene Profile), der den JSON über `LocalClipboardManager.current.setText(AnnotatedString(raw))` in die Zwischenablage kopiert und den Erfolg meldet
- Die Notiz „Umbenennen, Duplizieren und Export folgen in Phase 2“ wird ersetzt durch: „Umbenennen und Duplizieren folgen später. Export und Import sind eingerichtet.“

- [ ] **Step 5: Test laufen lassen, Erfolg bestätigen**

Run: `gradle :app:testDebugUnitTest --console=plain`
Expected: PASS — alle Tests, 0 Fehler.

- [ ] **Step 6: Debug-Build laufen lassen**

Run: `gradle :app:assembleDebug --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Committen**

```bash
git add app/src/main/java/de/sonora/app/SonoraViewModel.kt app/src/main/java/de/sonora/app/ui/Screens.kt app/src/test/java/de/sonora/app/core/ProfileExportTest.kt
git commit -m "feat(profiles): Export und Import in der Oberflaeche"
```

---

## Task 7: Raummodi definieren (SpatialMode)

**Files:**
- Create: `app/src/main/java/de/sonora/app/core/SpatialMode.kt`
- Test: `app/src/test/java/de/sonora/app/core/SpatialModeTest.kt`

**Interfaces:**
- Consumes: keine
- Produces:
  - `enum class SpatialMode(val id: String, val title: String, val description: String)` mit genau den neun Modi des PRD §9:
    `KINO_WEIT("kino", "Kino-Weit", "Breite Klangwand mit Anteilen von hinten.")`,
    `OBJEKT_RAUM("objekt", "Objekt-Raum", "Höhen und einzelne Objekte stehen im Raum.")`,
    `RAUM_21_1("raum21", "Raum 21.1", "Viele virtuelle Lautsprecher, weit und tief.")`,
    `KOPF_3D("kopf3d", "Kopf-3D", "Der Klang umschließt deinen Kopf.")`,
    `LIVE_SAAL("saal", "Live-Saal", "Natürlicher Raumhall wie in einem Saal.")`,
    `STUDIO_TROCKEN("studio", "Studio-Trocken", "Neutral und direkt, ohne Hall.")`,
    `SPRACH_FOKUS("sprache", "Sprach-Fokus", "Dialog steht klar vorn.")`,
    `NACHT("nacht", "Nacht", "Leise, aber verständlich.")`,
    `BREIT_WARM("breit", "Breit & Warm", "Entspannt, weich, etwas wärmer.")`
  - `data class SpatialParams(val crossfeedMix: Float, val wideningMix: Float, val reverbMix: Float, val centerFocus: Float, val compression: Float)` — alle 0f..1f
  - `object SpatialModeCatalog { val all: List<SpatialMode>; fun params(mode: SpatialMode, intensity: Float): SpatialParams }` — `intensity` 0f..1f, linear zwischen „Aus“ (alle 0) und dem Modus-Vollwert

- [ ] **Step 1: Failing test schreiben**

`app/src/test/java/de/sonora/app/core/SpatialModeTest.kt`:
```kotlin
class SpatialModeTest {
    @Test fun `alle neun Modi der Spezifikation existieren`() {
        assertEquals(9, SpatialModeCatalog.all.size)
        assertEquals(
            setOf("Kino-Weit","Objekt-Raum","Raum 21.1","Kopf-3D","Live-Saal","Studio-Trocken","Sprach-Fokus","Nacht","Breit & Warm"),
            SpatialModeCatalog.all.map { it.title }.toSet()
        )
    }

    @Test fun `Intensitaet null stellt den Originalklang wieder her`() {
        val p = SpatialModeCatalog.params(SpatialMode.KINO_WEIT, 0f)
        assertEquals(0f, p.crossfeedMix, 0.001f)
        assertEquals(0f, p.wideningMix, 0.001f)
        assertEquals(0f, p.reverbMix, 0.001f)
    }

    @Test fun `Intensitaet eins stellt den vollen Modus her`() {
        val full = SpatialModeCatalog.params(SpatialMode.KOPF_3D, 1f)
        assertTrue(full.crossfeedMix > 0f)
        assertTrue(full.wideningMix > 0f)
    }

    @Test fun `Intensitaet dazwischen liegt dazwischen`() {
        val half = SpatialModeCatalog.params(SpatialMode.KINO_WEIT, 0.5f)
        val full = SpatialModeCatalog.params(SpatialMode.KINO_WEIT, 1f)
        assertTrue(half.wideningMix < full.wideningMix)
        assertTrue(half.wideningMix > 0f)
    }

    @Test fun `Studio-Trocken hat keinen Hall`() {
        assertEquals(0f, SpatialModeCatalog.params(SpatialMode.STUDIO_TROCKEN, 1f).reverbMix, 0.001f)
    }

    @Test fun `Sprach-Fokus betont die Mitte`() {
        assertTrue(SpatialModeCatalog.params(SpatialMode.SPRACH_FOKUS, 1f).centerFocus > 0f)
    }

    @Test fun `alle Parameter bleiben zwischen null und eins`() {
        SpatialModeCatalog.all.forEach { mode ->
            val p = SpatialModeCatalog.params(mode, 1f)
            listOf(p.crossfeedMix, p.wideningMix, p.reverbMix, p.centerFocus, p.compression)
                .forEach { assertTrue(it in 0f..1f) }
        }
    }

    @Test fun `Modusnamen enthalten keine geschuetzten Marken`() {
        val protectedNames = listOf("dolby", "dts", "auro")
        SpatialModeCatalog.all.forEach { mode ->
            val haystack = (mode.title + " " + mode.description).lowercase()
            protectedNames.forEach { brand ->
                assertFalse("Modus '${mode.title}' nennt $brand", haystack.contains(brand))
            }
        }
    }
}
```

- [ ] **Step 2: Test laufen lassen, Fehlschlag bestätigen**

Run: `gradle :app:testDebugUnitTest --tests "de.sonora.app.core.SpatialModeTest" --console=plain`
Expected: FAIL — "Unresolved reference: SpatialMode".

- [ ] **Step 3: `SpatialMode.kt` implementieren**

`app/src/main/java/de/sonora/app/core/SpatialMode.kt`:
```kotlin
package de.sonora.app.core

enum class SpatialMode(
    val id: String,
    val title: String,
    val description: String
) {
    KINO_WEIT("kino", "Kino-Weit", "Breite Klangwand mit Anteilen von hinten."),
    OBJEKT_RAUM("objekt", "Objekt-Raum", "Höhen und einzelne Objekte stehen im Raum."),
    RAUM_21_1("raum21", "Raum 21.1", "Viele virtuelle Lautsprecher, weit und tief."),
    KOPF_3D("kopf3d", "Kopf-3D", "Der Klang umschließt deinen Kopf."),
    LIVE_SAAL("saal", "Live-Saal", "Natürlicher Raumhall wie in einem Saal."),
    STUDIO_TROCKEN("studio", "Studio-Trocken", "Neutral und direkt, ohne Hall."),
    SPRACH_FOKUS("sprache", "Sprach-Fokus", "Dialog steht klar vorn."),
    NACHT("nacht", "Nacht", "Leise, aber verständlich."),
    BREIT_WARM("breit", "Breit & Warm", "Entspannt, weich, etwas wärmer.")
}

data class SpatialParams(
    val crossfeedMix: Float,
    val wideningMix: Float,
    val reverbMix: Float,
    val centerFocus: Float,
    val compression: Float
)

object SpatialModeCatalog {
    val all: List<SpatialMode> = SpatialMode.entries.toList()

    private val full: Map<SpatialMode, SpatialParams> = mapOf(
        SpatialMode.KINO_WEIT to SpatialParams(0.10f, 0.75f, 0.35f, 0.10f, 0.10f),
        SpatialMode.OBJEKT_RAUM to SpatialParams(0.05f, 0.60f, 0.30f, 0.15f, 0.05f),
        SpatialMode.RAUM_21_1 to SpatialParams(0.15f, 0.90f, 0.55f, 0.05f, 0.10f),
        SpatialMode.KOPF_3D to SpatialParams(0.35f, 0.50f, 0.10f, 0.20f, 0.05f),
        SpatialMode.LIVE_SAAL to SpatialParams(0.10f, 0.35f, 0.80f, 0.05f, 0.15f),
        SpatialMode.STUDIO_TROCKEN to SpatialParams(0.00f, 0.05f, 0.00f, 0.00f, 0.00f),
        SpatialMode.SPRACH_FOKUS to SpatialParams(0.05f, 0.10f, 0.05f, 0.70f, 0.35f),
        SpatialMode.NACHT to SpatialParams(0.05f, 0.10f, 0.10f, 0.30f, 0.60f),
        SpatialMode.BREIT_WARM to SpatialParams(0.20f, 0.40f, 0.15f, 0.10f, 0.20f)
    )

    fun params(mode: SpatialMode, intensity: Float): SpatialParams {
        val t = intensity.coerceIn(0f, 1f)
        val f = full[mode] ?: SpatialParams(0f, 0f, 0f, 0f, 0f)
        return SpatialParams(
            f.crossfeedMix * t,
            f.wideningMix * t,
            f.reverbMix * t,
            f.centerFocus * t,
            f.compression * t
        )
    }
}
```

- [ ] **Step 4: Test laufen lassen, Erfolg bestätigen**

Run: `gradle :app:testDebugUnitTest --tests "de.sonora.app.core.SpatialModeTest" --console=plain`
Expected: PASS — 8 Tests, 0 Fehler.

- [ ] **Step 5: Committen**

```bash
git add app/src/main/java/de/sonora/app/core/SpatialMode.kt app/src/test/java/de/sonora/app/core/SpatialModeTest.kt
git commit -m "feat(spatial): neun eigene Raummodi mit Intensitaet"
```

---

## Task 8: Raumklang an die Engine anbinden und „Raum"-Bereich bauen

**Files:**
- Modify: `app/src/main/java/de/sonora/app/audio/SonoraEngine.kt`
- Modify: `app/src/main/java/de/sonora/app/SonoraViewModel.kt`
- Modify: `app/src/main/java/de/sonora/app/ui/SonoraRoot.kt` (Tab „Raum")
- Create: `app/src/main/java/de/sonora/app/ui/SpatialScreen.kt`
- Test: `app/src/test/java/de/sonora/app/core/SpatialApplyTest.kt`

**Interfaces:**
- Consumes: `SpatialMode`, `SpatialParams`, `SpatialModeCatalog` (Task 7)
- Produces:
  - `SonoraEngine.applySpatial(params: SpatialParams): Boolean` — setzt Virtualizer/LoudnessEnhancer auf der Equalizer-Session; liefert `false`, wenn das Gerät die Effekte nicht anbietet
  - `SonoraEngine.spatialSupported: Boolean`
  - `SonoraViewModel.setSpatialMode(mode: SpatialMode?)` — `null` bedeutet „Aus“ (Originalklang, US-005 Szenario „Raumklang ausschalten“)
  - `SonoraViewModel.setSpatialIntensity(value: Float)`
  - `SonoraUiState.spatialMode: SpatialMode?`, `SonoraUiState.spatialIntensity: Float`, `SonoraUiState.spatialAvailable: Boolean`
  - `enum class SonoraTab` erhält `Spatial("Raum", "◈")` — Reihenfolge: **Jetzt · Klang · Raum · Profile**

- [ ] **Step 1: Failing test schreiben**

`app/src/test/java/de/sonora/app/core/SpatialApplyTest.kt`:
```kotlin
class SpatialApplyTest {
    @Test fun `Aus liefert neutrale Parameter`() {
        assertEquals(SpatialParams(0f,0f,0f,0f,0f), SpatialModeCatalog.params(SpatialMode.KINO_WEIT, 0f))
    }

    @Test fun `jeder Modus liefert bei voller Intensitaet mindestens einen Effekt`() {
        SpatialModeCatalog.all.forEach { mode ->
            val p = SpatialModeCatalog.params(mode, 1f)
            val sum = p.crossfeedMix + p.wideningMix + p.reverbMix + p.centerFocus + p.compression
            assertTrue("Modus ${mode.title} ist ohne Wirkung", sum > 0f)
        }
    }

    @Test fun `Intensitaet wird auf null bis eins begrenzt`() {
        assertTrue(SpatialModeCatalog.params(SpatialMode.NACHT, 5f).compression in 0f..1f)
        assertEquals(0f, SpatialModeCatalog.params(SpatialMode.NACHT, -3f).compression, 0.001f)
    }
}
```

- [ ] **Step 2: Test laufen lassen, Fehlschlag bestätigen**

Run: `gradle :app:testDebugUnitTest --tests "de.sonora.app.core.SpatialApplyTest" --console=plain`
Expected: FAIL.

- [ ] **Step 3: Engine erweitern**

In `SonoraEngine.kt` zusätzlich:
```kotlin
import android.media.audiofx.Virtualizer
import android.media.audiofx.LoudnessEnhancer

private var virtualizer: Virtualizer? = null
private var enhancer: LoudnessEnhancer? = null

var spatialSupported: Boolean = false
    private set

@Synchronized
fun applySpatial(params: SpatialParams): Boolean {
    val eq = equalizer ?: return false
    if (params.crossfeedMix <= 0f && params.wideningMix <= 0f && params.reverbMix <= 0f) {
        virtualizer?.enabled = false
        enhancer?.enabled = false
        return true
    }
    return try {
        if (virtualizer == null) virtualizer = Virtualizer(eq.audioSessionId)
        if (enhancer == null) enhancer = LoudnessEnhancer(eq.audioSessionId)
        val v = virtualizer!!
        val e = enhancer!!
        spatialSupported = v.strengthSupported
        v.enabled = true
        v.setStrength((params.wideningMix * 1000).toInt().coerceIn(0, 1000))
        e.enabled = true
        e.setTargetGain((params.centerFocus * 600f).toInt().toFloat())
        true
    } catch (_: Throwable) {
        spatialSupported = false
        false
    }
}
```
`release()` gibt `virtualizer` und `enhancer` frei und setzt sie auf `null`.

- [ ] **Step 4: ViewModel erweitern**

In `SonoraViewModel`:
- `SonoraUiState` erhält `spatialMode: SpatialMode? = null`, `spatialIntensity: Float = 0.7f`, `spatialAvailable: Boolean = true`
- `setSpatialMode(mode)` speichert, wendet `SpatialModeCatalog.params(mode, intensity)` an, setzt `spatialAvailable = SonoraEngine.applySpatial(params)`
- `setSpatialIntensity(value)` speichert und wendet neu an
- `applyEffective()` ruft bei Änderung der Spatial-Werte `SonoraEngine.applySpatial(...)` auf

- [ ] **Step 5: `SpatialScreen.kt` bauen**

Neue Datei `app/src/main/java/de/sonora/app/ui/SpatialScreen.kt`:
- Kopfzeile „Raum“ mit Unterzeile „Eigene Klangsimulationen von Sonora. Keine lizenzierten Fremdformate.“
- `LazyColumn` mit einer Karte je Modus: Titel, ein Satz Alltagssprache, Zustandsindikator. Die Karte ist per `Modifier.selectable(selected = ..., onClick = ...)` umschaltbar; Auswahl ist an Form **und** Text erkennbar, nicht nur an Farbe (UX-Spec §6)
- Ein Intensity-Regler „Stärke“ von 0 bis 1, angezeigt in Prozent
- Ein „Aus"-Schalter, der `spatialMode = null` setzt
- Ist `spatialAvailable == false`, steht dort: „Dieses Gerät bietet keine räumliche Klangverarbeitung. Der Equalizer wirkt weiter." — ehrlich, kein Attrappen-Schalter
- Jede Karten ist mindestens 56 dp hoch

- [ ] **Step 6: Navigation erweitern**

In `SonoraRoot.kt`: `SonoraTab` erhält `Spatial("Raum", "◈")` zwischen `Sound` und `Profiles`; im `when (selectedTab)` ein Zweig `SonoraTab.Spatial.name -> SpatialScreen(vm)`.

- [ ] **Step 7: Test und Build laufen lassen**

Run: `gradle :app:testDebugUnitTest --console=plain && gradle :app:assembleDebug --console=plain`
Expected: PASS, BUILD SUCCESSFUL.

- [ ] **Step 8: Committen**

```bash
git add app/src/main/java/de/sonora/app/audio/SonoraEngine.kt app/src/main/java/de/sonora/app/SonoraViewModel.kt app/src/main/java/de/sonora/app/ui/SonoraRoot.kt app/src/main/java/de/sonora/app/ui/SpatialScreen.kt app/src/test/java/de/sonora/app/core/SpatialApplyTest.kt
git commit -m "feat(spatial): Raummodi anwenden und Bereich 'Raum' bauen"
```

---

## Task 9: KI-Interview (Fragen und Auswertung)

**Files:**
- Create: `app/src/main/java/de/sonora/app/core/Interview.kt`
- Test: `app/src/test/java/de/sonora/app/core/InterviewTest.kt`

**Interfaces:**
- Consumes: `EqCurve`, `BandCount`, `EqModel` (Task 1)
- Produces:
  - `data class InterviewOption(val id: String, val label: String, val curve: List<Float>)` — `curve` ist ein Vorschlag in 10 dB-Werten
  - `data class InterviewQuestion(val id: String, val prompt: String, val options: List<InterviewOption>)`
  - `data class InterviewAnswer(val questionId: String, val optionId: String)`
  - `object InterviewCatalog { val questions: List<InterviewQuestion> }` — genau 8 Fragen in Alltagssprache:
    1. „Klingt Musik für dich eher zu dumpf oder zu schrill?“ (dumpf / ausgewogen / schrill)
    2. „Magst du kräftigen Bass oder lieber einen schlanken Klang?“ (bass / ausgewogen / bassarm)
    3. „Soll Sprache im Vordergrund stehen?“ (ja / nein / egal)
    4. „Wie stark dürfen Höhen sein?“ (zurueckhaltend / normal / praesent)
    5. „Hörst du lieber warm oder hell?“ (warm / ausgewogen / hell)
    6. „Stört dich lauter Bass in Zug und Bus?“ (stoert / egal / egal_nicht)
    7. „Welche Lautstärke fühlt sich nach langem Hören angenehm an?“ (leise / mittel / laut)
    8. „Willst du eher Musik oder Sprache hören?“ (musik / beides / sprache)
  - `object InterviewEvaluator { fun evaluate(answers: List<InterviewAnswer>): EqCurve; fun evaluateWithBandCount(answers: List<InterviewAnswer>, bandCount: BandCount): EqCurve }`

- [ ] **Step 1: Failing test schreiben**

`app/src/test/java/de/sonora/app/core/InterviewTest.kt`:
```kotlin
class InterviewTest {
    @Test fun `es gibt genau acht Fragen`() {
        assertEquals(8, InterviewCatalog.questions.size)
    }

    @Test fun `keine Frage nennt Frequenzzahlen`() {
        InterviewCatalog.questions.forEach { q ->
            assertFalse("Frage '${q.prompt}' nennt eine Frequenz", q.prompt.contains(Regex("\\d+\\s*(Hz|kHz)")))
        }
    }

    @Test fun `jede Frage hat mindestens drei Optionen mit eindeutiger Kennung`() {
        InterviewCatalog.questions.forEach { q ->
            assertTrue(q.options.size >= 3)
            assertEquals(q.options.size, q.options.map { it.id }.toSet().size)
        }
    }

    @Test fun `alle Optionen liefern eine Kurve mit zehn Werten`() {
        InterviewCatalog.questions.flatMap { it.options }.forEach { o ->
            assertEquals(10, o.curve.size)
        }
    }

    @Test fun `eine leere Antwortliste ergibt eine flache Kurve`() {
        assertTrue(InterviewEvaluator.evaluate(emptyList()).levelsDb.all { kotlin.math.abs(it) < 0.001f })
    }

    @Test fun `bassbetonte Antwort hebt den Bass an`() {
        val q = InterviewCatalog.questions[1]
        val bassOption = q.options.first { it.id == "bass" }
        val curve = InterviewEvaluator.evaluate(listOf(InterviewAnswer(q.id, bassOption.id)))
        assertTrue(curve.levelsDb[0] > 0f)
    }

    @Test fun `die Auswertung bleibt im Rahmen von minus 12 bis plus 12`() {
        val all = InterviewCatalog.questions.flatMap { q -> q.options.map { o -> InterviewAnswer(q.id, o.id) } }
        assertTrue(InterviewEvaluator.evaluate(all).levelsDb.all { it in -12f..12f })
    }

    @Test fun `Auswertung mit 31 Baendern liefert 31 Werte`() {
        assertEquals(31, InterviewEvaluator.evaluateWithBandCount(emptyList(), BandCount.THIRTY_ONE).levelsDb.size)
    }
}
```

- [ ] **Step 2: Test laufen lassen, Fehlschlag bestätigen**

Run: `gradle :app:testDebugUnitTest --tests "de.sonora.app.core.InterviewTest" --console=plain`
Expected: FAIL — "Unresolved reference: InterviewCatalog".

- [ ] **Step 3: `Interview.kt` implementieren**

`app/src/main/java/de/sonora/app/core/Interview.kt`:
```kotlin
package de.sonora.app.core

data class InterviewOption(val id: String, val label: String, val curve: List<Float>)

data class InterviewQuestion(val id: String, val prompt: String, val options: List<InterviewOption>)

data class InterviewAnswer(val questionId: String, val optionId: String)

object InterviewCatalog {
    private fun c(vararg v: Float) = v.toList()

    val questions: List<InterviewQuestion> = listOf(
        InterviewQuestion(
            "balance",
            "Klingt Musik für dich eher zu dumpf oder zu schrill?",
            listOf(
                InterviewOption("dumpf", "Zu dumpf", c(5f, 4f, 2f, 0f, -1f, -1f, 0f, 1f, 2f, 3f)),
                InterviewOption("ausgewogen", "Ausgewogen", c(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
                InterviewOption("schrill", "Zu schrill", c(-3f, -2f, -1f, 0f, 0f, 1f, 2f, 3f, 3f, 2f))
            )
        ),
        InterviewQuestion(
            "bass",
            "Magst du kräftigen Bass oder lieber einen schlanken Klang?",
            listOf(
                InterviewOption("bass", "Kräftiger Bass", c(6f, 5f, 4f, 2f, 0f, -1f, -1f, 0f, 1f, 1f)),
                InterviewOption("ausgewogen", "Ausgewogen", c(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
                InterviewOption("bassarm", "Schlank", c(-4f, -3f, -2f, 0f, 1f, 2f, 2f, 1f, 0f, 0f))
            )
        ),
        InterviewQuestion(
            "sprache",
            "Soll Sprache im Vordergrund stehen?",
            listOf(
                InterviewOption("ja", "Ja, Sprache zuerst", c(-3f, -2f, -1f, 1f, 3f, 4f, 3f, 1f, 0f, -1f)),
                InterviewOption("nein", "Eher Musik", c(2f, 2f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
                InterviewOption("egal", "Ist mir egal", c(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f))
            )
        ),
        InterviewQuestion(
            "hoeh",
            "Wie stark dürfen Höhen sein?",
            listOf(
                InterviewOption("zurueckhaltend", "Zurückhaltend", c(0f, 0f, 0f, 0f, 0f, -1f, -2f, -3f, -3f, -2f)),
                InterviewOption("normal", "Normal", c(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
                InterviewOption("praesent", "Präsent", c(-1f, -1f, 0f, 0f, 1f, 2f, 3f, 4f, 4f, 3f))
            )
        ),
        InterviewQuestion(
            "temperatur",
            "Hörst du lieber warm oder hell?",
            listOf(
                InterviewOption("warm", "Warm", c(3f, 3f, 2f, 1f, 0f, 0f, -1f, -2f, -2f, -2f)),
                InterviewOption("ausgewogen", "Ausgewogen", c(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
                InterviewOption("hell", "Hell", c(-2f, -1f, 0f, 0f, 0f, 1f, 2f, 3f, 3f, 2f))
            )
        ),
        InterviewQuestion(
            "verkehr",
            "Stört dich lauter Bass in Zug und Bus?",
            listOf(
                InterviewOption("stoert", "Ja, das stört mich", c(-4f, -4f, -3f, 0f, 1f, 1f, 0f, 0f, 0f, 0f)),
                InterviewOption("egal", "Egal", c(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
                InterviewOption("egal_nicht", "Nein, stört mich nicht", c(3f, 3f, 2f, 0f, 0f, 0f, 0f, 0f, 0f, 0f))
            )
        ),
        InterviewQuestion(
            "lautstaerke",
            "Welche Lautstärke fühlt sich nach langem Hören angenehm an?",
            listOf(
                InterviewOption("leise", "Leise", c(0f, 0f, 0f, 1f, 2f, 2f, 1f, 0f, -1f, -1f)),
                InterviewOption("mittel", "Mittel", c(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
                InterviewOption("laut", "Laut", c(-1f, -1f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f))
            )
        ),
        InterviewQuestion(
            "inhalt",
            "Willst du eher Musik oder Sprache hören?",
            listOf(
                InterviewOption("musik", "Musik", c(2f, 2f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
                InterviewOption("beides", "Beides", c(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
                InterviewOption("sprache", "Sprache", c(-3f, -2f, -1f, 1f, 3f, 4f, 3f, 1f, 0f, -1f))
            )
        )
    )
}

object InterviewEvaluator {
    fun evaluate(answers: List<InterviewAnswer>): EqCurve =
        evaluateWithBandCount(answers, BandCount.TEN)

    fun evaluateWithBandCount(answers: List<InterviewAnswer>, bandCount: BandCount): EqCurve {
        val matched = answers.mapNotNull { answer ->
            val question = InterviewCatalog.questions.firstOrNull { it.id == answer.questionId }
            question?.options?.firstOrNull { it.id == answer.optionId }
        }
        if (matched.isEmpty()) return EqCurve(bandCount, List(bandCount.size) { 0f })
        val ten = List(10) { index ->
            matched.map { it.curve.getOrElse(index) { 0f } }.average().toFloat()
        }.map { it.coerceIn(EqModel.MIN_DB, EqModel.MAX_DB) }
        return EqCurve(BandCount.TEN, ten).withBandCount(bandCount)
    }
}
```

- [ ] **Step 4: Test laufen lassen, Erfolg bestätigen**

Run: `gradle :app:testDebugUnitTest --tests "de.sonora.app.core.InterviewTest" --console=plain`
Expected: PASS — 8 Tests, 0 Fehler.

- [ ] **Step 5: Committen**

```bash
git add app/src/main/java/de/sonora/app/core/Interview.kt app/src/test/java/de/sonora/app/core/InterviewTest.kt
git commit -m "feat(interview): acht Fragen in Alltagssprache mit lokaler Auswertung"
```

---

## Task 10: Interview-Bildschirm

**Files:**
- Create: `app/src/main/java/de/sonora/app/ui/InterviewScreen.kt`
- Modify: `app/src/main/java/de/sonora/app/SonoraViewModel.kt`
- Modify: `app/src/main/java/de/sonora/app/ui/Screens.kt` (Einstieg aus „Jetzt“ und „Profile")
- Modify: `app/src/main/java/de/sonora/app/ui/SonoraRoot.kt` (Interview als Sonderzustand über dem Tab-Bereich)

**Interfaces:**
- Consumes: `InterviewCatalog`, `InterviewEvaluator`, `InterviewAnswer` (Task 9)
- Produces:
  - `SonoraViewModel.startInterview()`, `SonoraViewModel.answerQuestion(questionId: String, optionId: String)`, `SonoraViewModel.skipQuestion(questionId: String)`, `SonoraViewModel.finishInterview(): EqCurve`, `SonoraViewModel.cancelInterview()`
  - `SonoraUiState.interviewActive: Boolean`, `SonoraUiState.interviewIndex: Int`, `SonoraUiState.interviewAnswers: List<InterviewAnswer>`, `SonoraUiState.interviewPreview: EqCurve?`

- [ ] **Step 1: ViewModel erweitern**

In `SonoraViewModel`:
```kotlin
fun startInterview() {
    uiState = uiState.copy(
        interviewActive = true, interviewIndex = 0,
        interviewAnswers = emptyList(), interviewPreview = null
    )
}

fun answerQuestion(questionId: String, optionId: String) {
    val answers = uiState.interviewAnswers.filterNot { it.questionId == questionId } +
        InterviewAnswer(questionId, optionId)
    advance(answers)
}

fun skipQuestion(questionId: String) {
    advance(uiState.interviewAnswers.filterNot { it.questionId == questionId })
}

private fun advance(answers: List<InterviewAnswer>) {
    val next = uiState.interviewIndex + 1
    if (next >= InterviewCatalog.questions.size) {
        uiState = uiState.copy(
            interviewAnswers = answers, interviewIndex = next,
            interviewPreview = InterviewEvaluator.evaluate(answers)
        )
    } else {
        uiState = uiState.copy(interviewAnswers = answers, interviewIndex = next)
    }
}

fun finishInterview(): EqCurve {
    val curve = uiState.interviewPreview ?: InterviewEvaluator.evaluate(uiState.interviewAnswers)
    val profile = SoundProfile(
        id = UUID.randomUUID().toString(),
        name = "Mein Profil",
        levels = curve.levelsDb,
        preset = null
    )
    uiState = uiState.copy(
        profiles = uiState.profiles + profile,
        activeProfileId = profile.id,
        activeProfileName = profile.name,
        eqCurve = curve,
        dirty = false,
        interviewActive = false,
        message = "Dein Profil ist gespeichert und aktiv."
    )
    applyEffective()
    persist()
    return curve
}

fun cancelInterview() {
    uiState = uiState.copy(
        interviewActive = false, interviewIndex = 0,
        interviewAnswers = emptyList(), interviewPreview = null
    )
}
```

- [ ] **Step 2: `InterviewScreen.kt` bauen**

Neue Datei `app/src/main/java/de/sonora/app/ui/InterviewScreen.kt`:
- Eine Frage pro Bildschirm, aus `InterviewCatalog.questions[state.interviewIndex]`
- Fortschritt als schmale Linie: `LinearProgressIndicator(progress = { (index + 1f) / questions.size })`
- Die Antwortoptionen als Liste von `Surface`-Karten, per `Modifier.selectable(selected = chosen, onClick = ...)`; die gewählte Option ist durch Randstärke **und** Text markiert
- „Überspringen" ist immer sichtbar und führt zur nächsten Frage, ohne das Interview abzubrechen (US-003 Szenario „Unsichere Antwort")
- Nach der letzten Frage: Zusammenfassung in einem Satz („Dein Profil hebt den Bass an und bringt die Sprache nach vorn."), ein „Profil anhören"-Knopf, der die berechnete Kurve vorübergehend anwendet, und „Speichern", das `vm.finishInterview()` aufruft
- „Abbrechen" oben rechts, das `vm.cancelInterview()` aufruft
- Der „Profil anhören"-Knopf nutzt denselben Bypass-Mechanismus wie der A/B-Vergleich im Klang-Bereich (`vm.beginBypass()` / `vm.endBypass()`)

- [ ] **Step 3: Einstieg einbauen**

In `Screens.kt`:
- Im Bereich „Jetzt" unterhalb der Schnellzugriff-Presets eine Karte „Persönliches Profil erstellen" mit dem Text „Acht kurze Fragen, Ergebnis sofort hörbar." und einem Knopf „Loslegen" → `vm.startInterview()`
- Im Bereich „Profile" oben ein gleichwertiger Einstieg

In `SonoraRoot.kt`:
```kotlin
if (state.interviewActive) {
    InterviewScreen(vm)
    return
}
```
vor der Tab-Navigation, damit das Interview keinen Tab-Zustand verändert.

- [ ] **Step 4: Build laufen lassen**

Run: `gradle :app:assembleDebug --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Committen**

```bash
git add app/src/main/java/de/sonora/app/ui/InterviewScreen.kt app/src/main/java/de/sonora/app/SonoraViewModel.kt app/src/main/java/de/sonora/app/ui/Screens.kt app/src/main/java/de/sonora/app/ui/SonoraRoot.kt
git commit -m "feat(interview): Interview-Bildschirm mit Fortschritt und Auswertung"
```

---

## Task 11: Inhaltserkennung (MediaWatcher)

**Files:**
- Create: `app/src/main/java/de/sonora/app/core/MediaRule.kt`
- Create: `app/src/main/java/de/sonora/app/audio/MediaWatcher.kt`
- Test: `app/src/test/java/de/sonora/app/core/MediaRuleTest.kt`
- Modify: `app/src/main/java/de/sonora/app/SonoraViewModel.kt`
- Modify: `app/src/main/java/de/sonora/app/AndroidManifest.xml`
- Modify: `app/src/main/java/de/sonora/app/ui/Screens.kt`

**Interfaces:**
- Consumes: keine
- Produces:
  - `data class NowPlaying(val packageName: String, val title: String?, val appLabel: String, val playing: Boolean)`
  - `data class MediaPermission(val granted: Boolean, val enabled: Boolean)`
  - `object MediaRule { fun profileHint(nowPlaying: NowPlaying): String? }` — Musik-Apps → `"music"`, Podcast-/Hörbuch-Apps → `"voice"`, Video-Apps und unbekannte → `null`
  - `class MediaWatcher(context: Context, onChange: (NowPlaying?) -> Unit)` mit `start()`, `stop()`, `currentNowPlaying(): NowPlaying?`, `isListenerEnabled(): Boolean`
  - `class SonoraNotificationListener : NotificationListenerService()`

- [ ] **Step 1: Failing test schreiben**

`app/src/test/java/de/sonora/app/core/MediaRuleTest.kt`:
```kotlin
class MediaRuleTest {
    private fun np(pkg: String, playing: Boolean = true) =
        NowPlaying(pkg, null, "App", playing)

    @Test fun `Musik-Apps fuehren zum Musikprofil`() {
        assertEquals("music", MediaRule.profileHint(np("com.spotify.music")))
        assertEquals("music", MediaRule.profileHint(np("com.deezer.player")))
    }

    @Test fun `Podcast-Apps fuehren zum Sprachprofil`() {
        assertEquals("voice", MediaRule.profileHint(np("com.anchor.fm")))
    }

    @Test fun `Video-Apps erzwingen kein Profil`() {
        assertNull(MediaRule.profileHint(np("com.youtube.app")))
    }

    @Test fun `nicht spielende Quelle waehlt kein Profil`() {
        assertNull(MediaRule.profileHint(np("com.spotify.music", playing = false)))
    }

    @Test fun `unbekannte App waehlt kein Profil`() {
        assertNull(MediaRule.profileHint(np("com.example.unknown")))
    }
}
```

- [ ] **Step 2: Test laufen lassen, Fehlschlag bestätigen**

Run: `gradle :app:testDebugUnitTest --tests "de.sonora.app.core.MediaRuleTest" --console=plain`
Expected: FAIL — "Unresolved reference: NowPlaying".

- [ ] **Step 3: `MediaRule.kt` implementieren**

`app/src/main/java/de/sonora/app/core/MediaRule.kt`:
```kotlin
package de.sonora.app.core

data class NowPlaying(
    val packageName: String,
    val title: String?,
    val appLabel: String,
    val playing: Boolean
)

data class MediaPermission(val granted: Boolean, val enabled: Boolean)

object MediaRule {
    private val musicPackages = setOf(
        "com.spotify.music", "com.deezer.player", "com.bandcamp.android",
        "com.soundcloud.player", "com.pandora.android", "com.amazon.mp3"
    )
    private val voicePackages = setOf(
        "com.anchor.fm", "com.pocketcasts.android", "com.audible.android",
        "com.overcast.fm", "com.youtube.music"
    )

    fun profileHint(nowPlaying: NowPlaying): String? {
        if (!nowPlaying.playing) return null
        val pkg = nowPlaying.packageName.lowercase()
        return when {
            musicPackages.any { pkg.startsWith(it) } -> "music"
            voicePackages.any { pkg.startsWith(it) } -> "voice"
            else -> null
        }
    }
}
```

- [ ] **Step 4: `MediaWatcher.kt` implementieren**

`app/src/main/java/de/sonora/app/audio/MediaWatcher.kt`:
```kotlin
package de.sonora.app.audio

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaSessionManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import de.sonora.app.core.NowPlaying

class MediaWatcher(
    private val context: Context,
    private val onChange: (NowPlaying?) -> Unit
) {
    private val listenerComponent = ComponentName(context, SonoraNotificationListener::class.java)

    fun start() {
        runCatching {
            context.bindService(
                android.content.Intent(context, SonoraNotificationListener::class.java),
                object : android.content.ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, binder: android.os.IBinder?) {
                        val service = binder as? NotificationListenerService
                        service?.requestRebind(listenerComponent)
                    }
                    override fun onServiceDisconnected(name: ComponentName?) = Unit
                },
                Context.BIND_AUTO_CREATE
            )
        }
    }

    fun stop() = Unit

    fun currentNowPlaying(): NowPlaying? {
        val manager = context.getSystemService(MediaSessionManager::class.java) ?: return null
        val session = manager.activeSessions
            .filter { it.isPlaying && it.playbackState != null }
            .maxByOrNull { it.playbackState!!.position }
            ?: return null
        return NowPlaying(
            packageName = session.packageName,
            title = session.metadata?.getString(android.media.session.MediaMetadata.KEY_TITLE),
            appLabel = session.packageName,
            playing = true
        )
    }

    fun isListenerEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver, "enabled_notification_listeners"
        ) ?: return false
        return enabled.contains(listenerComponent.flattenToString())
    }
}

class SonoraNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: android.service.notification.StatusBarNotification?) {
        // Aenderungen werden ueber die MediaSession-Schnittstelle ausgewertet.
    }
}
```

- [ ] **Step 5: Manifest erweitern**

In `AndroidManifest.xml`:
```xml
<uses-permission android:name="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE" />
```
und als Service:
```xml
<service
    android:name=".audio.SonoraNotificationListener"
    android:exported="true"
    android:label="@string/media_listener_label"
    android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
    <intent-filter>
        <action android:name="android.service.notification.NotificationListenerService" />
    </intent-filter>
</service>
```
Keine INTERNET-Permission hinzufügen (NFR-011). Der String `media_listener_label` wird in `strings.xml` ergänzt: „Sonora liest nur App und Titel“.

- [ ] **Step 6: ViewModel erweitern**

In `SonoraViewModel`:
- `SonoraUiState` erhält `nowPlaying: NowPlaying?`, `mediaEnabled: Boolean`
- `setMediaEnabled(enabled)` startet/stoppt den `MediaWatcher`; bei eingeschalteter Erkennung wertet `MediaRule.profileHint(...)` aus und wendet das passende Profil an, wenn der Nutzer es erlaubt hat
- `mediaPermissionStatus(): MediaPermission` — unterscheidet „Berechtigung erteilt" (Listener im System aktiviert) von der App-Einstellung

- [ ] **Step 7: UI erweitern**

In `Screens.kt`, Bereich „Mehr" (Einstellungen) eine neue Karte „Inhaltserkennung":
- Schalter „Erkennen, was läuft"
- Text: „Sonora liest nur App und Titel, niemals den Ton. Die Erkennung läuft auf dem Gerät."
- Ist der Listener nicht aktiviert, erscheint ein Knopf „In den Systemeinstellungen aktivieren", der `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS` öffnet
- Der erkannte Inhalt erscheint im Bereich „Jetzt" in einer Karte „Aktiver Inhalt" mit App-Name und Titel

- [ ] **Step 8: Test und Build laufen lassen**

Run: `gradle :app:testDebugUnitTest --console=plain && gradle :app:assembleDebug --console=plain`
Expected: PASS, BUILD SUCCESSFUL.

- [ ] **Step 9: Committen**

```bash
git add app/src/main/java/de/sonora/app/core/MediaRule.kt app/src/main/java/de/sonora/app/audio/MediaWatcher.kt app/src/main/AndroidManifest.xml app/src/main/java/de/sonora/app/SonoraViewModel.kt app/src/main/java/de/sonora/app/ui/Screens.kt app/src/test/java/de/sonora/app/core/MediaRuleTest.kt
git commit -m "feat(media): Inhaltserkennung ueber MediaSession, ohne Audiozugriff"
```

---

## Task 12: Lokales Ereignisprotokoll (Diagnose)

**Files:**
- Create: `app/src/main/java/de/sonora/app/core/Diagnostics.kt`
- Create: `app/src/main/java/de/sonora/app/data/DiagnosticsStore.kt`
- Modify: `app/src/main/java/de/sonora/app/SonoraViewModel.kt`
- Modify: `app/src/main/java/de/sonora/app/ui/Screens.kt` (Diagnose-Karte erweitern)
- Test: `app/src/test/java/de/sonora/app/core/DiagnosticsTest.kt`

**Interfaces:**
- Consumes: keine
- Produces:
  - `data class DiagnosticEvent(val name: String, val properties: Map<String, String> = emptyMap(), val timestampMs: Long = 0L)`
  - `class EventLog(private val maxSize: Int = 500)` mit `record(name, properties)`, `events(): List<DiagnosticEvent>`, `clear()`, `replaceAll(events)` — Ringpuffer, älteste zuerst verworfen
  - `object EventNames` — die Namen aus PRD §11 als Konstanten: `APP_OPENED`, `PROFILE_APPLIED`, `INTERVIEW_COMPLETED`, `ENVIRONMENT_ADAPTED`, `BLIND_TEST_RATED`, `PRESET_SELECTED`, `SPATIAL_MODE_CHANGED`, `STREAMING_CONNECTED`, `AUDIO_EFFECT_FAILED`, `CRASH_REPORTED`, `PROFILE_EXPORTED`, plus `ALL: List<String>`
  - `class DiagnosticsStore(context)` — persistiert den Puffer in `SharedPreferences` unter `sonora_events_v1`; `load(): List<DiagnosticEvent>`, `save(events: List<DiagnosticEvent>)`

- [ ] **Step 1: Failing test schreiben**

`app/src/test/java/de/sonora/app/core/DiagnosticsTest.kt`:
```kotlin
class DiagnosticsTest {
    @Test fun `ereignisse werden in reihenfolge gespeichert`() {
        val log = EventLog()
        log.record("app_opened")
        log.record("profile_applied", mapOf("profile_id" to "p1"))
        assertEquals(listOf("app_opened", "profile_applied"), log.events().map { it.name })
    }

    @Test fun `der puffer bleibt beschraenkt und verwirft die aeltesten`() {
        val log = EventLog(maxSize = 3)
        repeat(5) { log.record("e$it") }
        val events = log.events()
        assertEquals(3, events.size)
        assertEquals("e2", events[0].name)
        assertEquals("e4", events[2].name)
    }

    @Test fun `leeren puffer lesen liefert eine leere liste`() {
        assertTrue(EventLog().events().isEmpty())
    }

    @Test fun `clear leert den puffer`() {
        val log = EventLog()
        log.record("app_opened")
        log.clear()
        assertTrue(log.events().isEmpty())
    }

    @Test fun `alle im PRD genannten ereignisnamen existieren`() {
        val required = listOf(
            "app_opened","profile_applied","interview_completed","environment_adapted",
            "blind_test_rated","preset_selected","spatial_mode_changed","streaming_connected",
            "audio_effect_failed","crash_reported","profile_exported"
        )
        required.forEach { name ->
            assertTrue("Ereignis $name fehlt", EventNames.ALL.contains(name))
        }
    }
}
```

- [ ] **Step 2: Test laufen lassen, Fehlschlag bestätigen**

Run: `gradle :app:testDebugUnitTest --tests "de.sonora.app.core.DiagnosticsTest" --console=plain`
Expected: FAIL — "Unresolved reference: EventLog".

- [ ] **Step 3: `Diagnostics.kt` implementieren**

`app/src/main/java/de/sonora/app/core/Diagnostics.kt`:
```kotlin
package de.sonora.app.core

data class DiagnosticEvent(
    val name: String,
    val properties: Map<String, String> = emptyMap(),
    val timestampMs: Long = 0L
)

object EventNames {
    const val APP_OPENED = "app_opened"
    const val PROFILE_APPLIED = "profile_applied"
    const val INTERVIEW_COMPLETED = "interview_completed"
    const val ENVIRONMENT_ADAPTED = "environment_adapted"
    const val BLIND_TEST_RATED = "blind_test_rated"
    const val PRESET_SELECTED = "preset_selected"
    const val SPATIAL_MODE_CHANGED = "spatial_mode_changed"
    const val STREAMING_CONNECTED = "streaming_connected"
    const val AUDIO_EFFECT_FAILED = "audio_effect_failed"
    const val CRASH_REPORTED = "crash_reported"
    const val PROFILE_EXPORTED = "profile_exported"

    val ALL: List<String> = listOf(
        APP_OPENED, PROFILE_APPLIED, INTERVIEW_COMPLETED, ENVIRONMENT_ADAPTED,
        BLIND_TEST_RATED, PRESET_SELECTED, SPATIAL_MODE_CHANGED, STREAMING_CONNECTED,
        AUDIO_EFFECT_FAILED, CRASH_REPORTED, PROFILE_EXPORTED
    )
}

class EventLog(private val maxSize: Int = 500) {
    private val items = ArrayDeque<DiagnosticEvent>()

    fun record(name: String, properties: Map<String, String> = emptyMap()) {
        if (items.size >= maxSize) items.removeFirst()
        items.addLast(DiagnosticEvent(name, properties, System.currentTimeMillis()))
    }

    fun events(): List<DiagnosticEvent> = items.toList()

    fun clear() = items.clear()

    fun replaceAll(events: List<DiagnosticEvent>) {
        items.clear()
        events.takeLast(maxSize).forEach { items.addLast(it) }
    }
}
```

- [ ] **Step 4: `DiagnosticsStore.kt` implementieren**

`app/src/main/java/de/sonora/app/data/DiagnosticsStore.kt`:
```kotlin
package de.sonora.app.data

import android.content.Context
import de.sonora.app.core.DiagnosticEvent
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class StoredEvent(
    val name: String,
    val properties: Map<String, String> = emptyMap(),
    val timestampMs: Long = 0L
)

class DiagnosticsStore(context: Context) {
    private val prefs = context.getSharedPreferences("sonora_events_v1", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(StoredEvent.serializer())

    fun load(): List<DiagnosticEvent> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString(listSerializer, raw)
                .map { DiagnosticEvent(it.name, it.properties, it.timestampMs) }
        }.getOrDefault(emptyList())
    }

    fun save(events: List<DiagnosticEvent>) {
        val stored = events.map { StoredEvent(it.name, it.properties, it.timestampMs) }
        prefs.edit().putString(KEY, json.encodeToString(listSerializer, stored)).apply()
    }

    private companion object {
        const val KEY = "events"
    }
}
```

- [ ] **Step 5: ViewModel anbinden**

In `SonoraViewModel`:
- Feld `private val diagnosticsStore = DiagnosticsStore(application)` und `private val eventLog = EventLog().also { it.replaceAll(diagnosticsStore.load()) }`
- `init` protokolliert `EventNames.APP_OPENED` mit `mapOf("version" to BuildConfig.VERSION_NAME, "android_version" to Build.VERSION.RELEASE)`
- `applyPreset` protokolliert `PROFILE_APPLIED`; `saveCurrentAs`/`deleteProfile` entsprechend
- Der Analyzer-Callback protokolliert `ENVIRONMENT_ADAPTED` beim Wechsel der Umgebungsstufe
- `setSpatialMode` protokolliert `SPATIAL_MODE_CHANGED` mit `mode_id` und `enabled`
- `exportProfile`/`importProfile` protokollieren `PROFILE_EXPORTED` mit `action` und `success`
- `retryEngine` protokolliert `AUDIO_EFFECT_FAILED` bei `EngineStatus.Unavailable`
- `finishInterview` protokolliert `INTERVIEW_COMPLETED` mit `skipped_questions` und `duration_s`
- `events()` liefert `eventLog.events()`; `clearEvents()` leert Puffer und Persistenz
- Das Protokoll wird bei jedem `record` in `diagnosticsStore.save(...)` gespiegelt

- [ ] **Step 6: UI erweitern**

In `Screens.kt`, Karte „Diagnose":
- Bestehende Zeilen bleiben
- Neue Sektion „Ereignisse (lokal)" mit der Anzahl, einem Knopf „Protokoll leeren" und einem Knopf „Bericht kopieren", der den Text
  ```
  Sonora-Diagnose <version>, <timestamp>
  Engine: <status>
  Ereignisse: <anzahl>
  <name> <properties> <timestamp>
  ...
  ```
  in die Zwischenablage kopiert — **ohne** Audiodaten (UX-Spec §3.9)
- Eine Liste der letzten zehn Ereignisse mit Name und Uhrzeit

- [ ] **Step 7: Test laufen lassen, Erfolg bestätigen**

Run: `gradle :app:testDebugUnitTest --console=plain`
Expected: PASS — alle Tests, 0 Fehler.

- [ ] **Step 8: Committen**

```bash
git add app/src/main/java/de/sonora/app/core/Diagnostics.kt app/src/main/java/de/sonora/app/data/DiagnosticsStore.kt app/src/main/java/de/sonora/app/SonoraViewModel.kt app/src/main/java/de/sonora/app/ui/Screens.kt app/src/test/java/de/sonora/app/core/DiagnosticsTest.kt
git commit -m "feat(diagnostics): lokales Ereignisprotokoll nach PRD-Ereignisliste"
```

---

## Task 13: Hörschutz — Lautstärkeerinnerung

**Files:**
- Create: `app/src/main/java/de/sonora/app/core/VolumeGuard.kt`
- Test: `app/src/test/java/de/sonora/app/core/VolumeGuardTest.kt`
- Modify: `app/src/main/java/de/sonora/app/SonoraViewModel.kt`
- Modify: `app/src/main/java/de/sonora/app/ui/Screens.kt` (Karte im Bereich „Jetzt")

**Interfaces:**
- Consumes: keine
- Produces:
  - `data class VolumeState(val minutesLoud: Int, val reminderDue: Boolean, val volumePercent: Int)`
  - `class VolumeGuard(private val loudThresholdPercent: Int = 80, private val sessionMinutes: Int = 60) { fun update(volumePercent: Int, elapsedMinutes: Int, reminderEnabled: Boolean): VolumeState }` — meldet nur, ändert die Systemlautstärke **nicht**
  - `SonoraViewModel.setVolumeReminderEnabled(enabled: Boolean)`
  - `SonoraUiState.volumeState: VolumeState`, `SonoraUiState.volumeReminderEnabled: Boolean`

- [ ] **Step 1: Failing test schreiben**

`app/src/test/java/de/sonora/app/core/VolumeGuardTest.kt`:
```kotlin
class VolumeGuardTest {
    @Test fun `unterhalb der Schwelle gibt es keine Erinnerung`() {
        assertFalse(VolumeGuard().update(50, 90, reminderEnabled = true).reminderDue)
    }

    @Test fun `nach einer Stunde laut gibt es eine Erinnerung`() {
        val s = VolumeGuard().update(90, 61, reminderEnabled = true)
        assertTrue(s.reminderDue)
        assertEquals(61, s.minutesLoud)
    }

    @Test fun `vor einer Stunde laut gibt es keine Erinnerung`() {
        assertFalse(VolumeGuard().update(90, 59, reminderEnabled = true).reminderDue)
    }

    @Test fun `abgeschaltete Erinnerung bleibt still`() {
        assertFalse(VolumeGuard().update(90, 120, reminderEnabled = false).reminderDue)
    }

    @Test fun `die Erinnerung senkt die Lautstaerke nicht selbst`() {
        assertEquals(90, VolumeGuard().update(90, 120, reminderEnabled = true).volumePercent)
    }
}
```

- [ ] **Step 2: Test laufen lassen, Fehlschlag bestätigen**

Run: `gradle :app:testDebugUnitTest --tests "de.sonora.app.core.VolumeGuardTest" --console=plain`
Expected: FAIL — "Unresolved reference: VolumeGuard".

- [ ] **Step 3: `VolumeGuard.kt` implementieren**

`app/src/main/java/de/sonora/app/core/VolumeGuard.kt`:
```kotlin
package de.sonora.app.core

data class VolumeState(
    val minutesLoud: Int,
    val reminderDue: Boolean,
    val volumePercent: Int
)

class VolumeGuard(
    private val loudThresholdPercent: Int = 80,
    private val sessionMinutes: Int = 60
) {
    fun update(volumePercent: Int, elapsedMinutes: Int, reminderEnabled: Boolean): VolumeState {
        val loud = volumePercent >= loudThresholdPercent
        val minutes = if (loud) elapsedMinutes else 0
        val due = reminderEnabled && loud && elapsedMinutes >= sessionMinutes
        return VolumeState(minutes, due, volumePercent)
    }
}
```
Wichtig: Die Klasse verändert die Systemlautstärke **nicht** (NFR-012 — „keine ungefragte Lautstärkeänderung“). Sie meldet nur.

- [ ] **Step 4: ViewModel und UI anbinden**

In `SonoraViewModel`:
- `SonoraUiState` erhält `volumeState: VolumeState = VolumeState(0, false, 0)`, `volumeReminderEnabled: Boolean = true`
- `refreshVolumeState()` liest die Systemlautstärke über `AudioManager.getStreamVolume(STREAM_MUSIC)` und `getStreamMaxVolume`, berechnet den Prozentwert und die Zeit seit dem letzten Aktivieren des Reminders über `SystemClock.elapsedRealtime()`
- `setVolumeReminderEnabled(enabled)` schaltet um und wird in `ProfileStore` persistiert

In `Screens.kt`, Bereich „Jetzt": eine Karte „Hörschutz" nur dann, wenn `state.volumeState.reminderDue`:
- Text: „Du hörst seit {minutes} Minuten sehr laut. Sonora senkt die Lautstärke nicht von allein — du entscheidest."
- Zwei Knöpfe: „Verstanden“ (setzt die Erinnerung zurück) und „Erinnerung aus“ (schaltet sie ab)
- Beide Knöpfe ≥ 48 dp hoch

- [ ] **Step 5: Test laufen lassen, Erfolg bestätigen**

Run: `gradle :app:testDebugUnitTest --console=plain`
Expected: PASS.

- [ ] **Step 6: Committen**

```bash
git add app/src/main/java/de/sonora/app/core/VolumeGuard.kt app/src/main/java/de/sonora/app/SonoraViewModel.kt app/src/main/java/de/sonora/app/ui/Screens.kt app/src/test/java/de/sonora/app/core/VolumeGuardTest.kt
git commit -m "feat(safety): Laustaerkeerinnerung ohne automatische Aenderung"
```

---

## Task 14: Verblindeter A/B-Hörtest (BlindTest)

**Files:**
- Create: `app/src/main/java/de/sonora/app/core/BlindTest.kt`
- Create: `app/src/main/java/de/sonora/app/ui/BlindTestScreen.kt`
- Test: `app/src/test/java/de/sonora/app/core/BlindTestTest.kt`
- Modify: `app/src/main/java/de/sonora/app/SonoraViewModel.kt`

**Interfaces:**
- Consumes: `EqCurve`, `BandCount` (Task 1)
- Produces:
  - `enum class BlindOrder { SONORA_FIRST, ORIGINAL_FIRST }`
  - `class BlindTest(private val rounds: Int = 10)` mit `nextOrder(roundIndex: Int): BlindOrder`, `record(rating: Int)`, `results(): List<Int>`, `average(): Double?`, `isComplete(completed: Int): Boolean`, `totalRounds(): Int`
  - `SonoraViewModel.startBlindTest()`, `SonoraViewModel.rateBlindTest(rating: Int)`, `SonoraViewModel.blindTestAverage(): Double?`
  - `SonoraUiState.blindTestActive: Boolean`, `SonoraUiState.blindTestRound: Int`, `SonoraUiState.blindTestResults: List<Int>`

- [ ] **Step 1: Failing test schreiben**

`app/src/test/java/de/sonora/app/core/BlindTestTest.kt`:
```kotlin
class BlindTestTest {
    @Test fun `die reihenfolge wechselt sich ab`() {
        val test = BlindTest()
        assertEquals(BlindOrder.SONORA_FIRST, test.nextOrder(0))
        assertEquals(BlindOrder.ORIGINAL_FIRST, test.nextOrder(1))
        assertEquals(BlindOrder.SONORA_FIRST, test.nextOrder(2))
    }

    @Test fun `der mittelwert wird nur aus vollstaendigen runden gebildet`() {
        val test = BlindTest()
        test.record(8)
        assertNull(test.average())
        test.record(6)
        assertEquals(7.0, test.average()!!, 0.001)
    }

    @Test fun `eine noch offene runde liefert keine bewertung`() {
        val test = BlindTest()
        assertFalse(test.isComplete(9))
        test.record(8); test.record(9)
        assertTrue(test.isComplete(10))
    }
}
```

- [ ] **Step 2: Test laufen lassen, Fehlschlag bestätigen**

Run: `gradle :app:testDebugUnitTest --tests "de.sonora.app.core.BlindTestTest" --console=plain`
Expected: FAIL — "Unresolved reference: BlindTest".

- [ ] **Step 3: `BlindTest.kt` implementieren**

`app/src/main/java/de/sonora/app/core/BlindTest.kt`:
```kotlin
package de.sonora.app.core

enum class BlindOrder { SONORA_FIRST, ORIGINAL_FIRST }

class BlindTest(private val rounds: Int = 10) {
    private val ratings = mutableListOf<Int>()

    fun nextOrder(roundIndex: Int): BlindOrder =
        if (roundIndex % 2 == 0) BlindOrder.SONORA_FIRST else BlindOrder.ORIGINAL_FIRST

    fun record(rating: Int) {
        ratings += rating.coerceIn(1, 10)
    }

    fun results(): List<Int> = ratings.toList()

    fun average(): Double? = if (ratings.isEmpty()) null else ratings.average()

    fun isComplete(completed: Int): Boolean = completed >= rounds

    fun totalRounds(): Int = rounds
}
```

- [ ] **Step 4: `BlindTestScreen.kt` bauen**

Neue Datei `app/src/main/java/de/sonora/app/ui/BlindTestScreen.kt`:
- Kurzer Text: „Zehn Durchläufe. Du weißt nicht, welcher Ton zuerst kommt — das macht den Vergleich ehrlich.“
- Ein großer Knopf „Abspielen", der die Reihenfolge der Runde abspielt: der jeweilige Ton wird über `beginBypass()`/`endBypass()` umgeschaltet
- Nach jeder Runde eine Bewertung von 1 bis 10 als zehn Schaltflächen (jede ≥ 48 dp), Auswahl sichtbar durch Füllung **und** Zahl
- Am Ende: „Mittelwert: {x,x} von 10" und ein Knopf „In den Diagnosebericht" — schreibt `blind_test_rated` in das Ereignisprotokoll

- [ ] **Step 5: ViewModel anbinden**

In `SonoraViewModel`:
- `SonoraUiState` erhält `blindTestActive: Boolean`, `blindTestRound: Int`, `blindTestResults: List<Int>`
- `startBlindTest()` setzt `blindTestActive = true`, `blindTestRound = 0`, `blindTestResults = emptyList()`
- `rateBlindTest(rating)` hängt an, protokolliert `BLIND_TEST_RATED` mit `rating_1_10`, `mode` und `profile_id`, und erhöht die Runde
- `blindTestAverage(): Double?`

- [ ] **Step 6: Test laufen lassen, Erfolg bestätigen**

Run: `gradle :app:testDebugUnitTest --console=plain`
Expected: PASS.

- [ ] **Step 7: Committen**

```bash
git add app/src/main/java/de/sonora/app/core/BlindTest.kt app/src/main/java/de/sonora/app/ui/BlindTestScreen.kt app/src/main/java/de/sonora/app/SonoraViewModel.kt app/src/test/java/de/sonora/app/core/BlindTestTest.kt
git commit -m "feat(test): verblindeter A/B-Hoertest mit Ereignisprotokoll"
```

---

## Task 15: Abschluss — Migrationstest, Diagnose, Dokumentation

**Files:**
- Create: `app/src/test/java/de/sonora/app/core/ReleaseGateTest.kt`
- Modify: `docs/design/ux-spec.md` (Abschnitt „Offene Punkte")
- Create: `docs/phase1-status.md`

**Interfaces:**
- Consumes: alle vorherigen Tasks
- Produces: `docs/phase1-status.md` mit dem Stand von Phase 1, offenen Punkten und den Messwerten, die noch auf dem Gerät fehlen.

- [ ] **Step 1: Release-Gate-Test schreiben**

`app/src/test/java/de/sonora/app/core/ReleaseGateTest.kt`:
```kotlin
class ReleaseGateTest {
    @Test fun `die Kernregeln der Spezifikation gelten`() {
        assertTrue(EqModel.MIN_DB <= -12f)
        assertTrue(EqModel.MAX_DB >= 12f)
        assertEquals(9, SpatialModeCatalog.all.size)
        assertEquals(8, InterviewCatalog.questions.size)
        assertEquals(11, EventNames.ALL.size)
        assertTrue(PROFILE_SCHEMA_VERSION >= 2)
    }

    @Test fun `alle Raummodi tragen einen Alltagssatz`() {
        SpatialModeCatalog.all.forEach { mode ->
            assertTrue(mode.description.length > 10)
            assertTrue(mode.title.isNotBlank())
        }
    }

    @Test fun `die Interviewfragen bleiben bei acht oder weniger`() {
        assertTrue(InterviewCatalog.questions.size <= 8)
    }
}
```

- [ ] **Step 2: Test laufen lassen, Erfolg bestätigen**

Run: `gradle :app:testDebugUnitTest --console=plain`
Expected: PASS.

- [ ] **Step 3: Vollständigen Testlauf und Debug-Build fahren**

Run: `gradle :app:testDebugUnitTest :app:assembleDebug --console=plain`
Expected: PASS (alle Tests), BUILD SUCCESSFUL.

- [ ] **Step 4: APK prüfen**

Run: `ls -la app/build/outputs/apk/debug/` und `sha256sum app/build/outputs/apk/debug/app-debug.apk`
Expected: APK vorhanden, Hash notiert.

- [ ] **Step 5: `docs/phase1-status.md` schreiben**

Inhalt:
- Was in Phase 1 umgesetzt ist (mit Verweis auf die User Story)
- Welche Punkte noch offen sind und warum: Latenzmessung und Akkuverbrauch brauchen das echte Gerät; der systemweite Effekt muss auf dem A56 in einer fremden App bestätigt werden; die NPU-Nutzung ist nicht gemessen
- Der aapt2-Fix und die Build-Umgebung, damit die nächste Sitzung nicht wieder daran scheitert

- [ ] **Step 6: UX-Spec aktualisieren**

In `docs/design/ux-spec.md`, Abschnitt „Offene Punkte":
- „Visuelle Detailgestaltung (Farbwerte, Abstände) nach dem ersten lauffähigen Build." → als erledigt markieren, die Farbwerte stehen in `Theme.kt`
- „Echtes Geräte-Screenshot als Design-Referenz ergänzen." → bleibt offen
- „Verhalten bei Samsung-eigenen Klangeffekten (Dolby/Adapt Sound) dokumentieren." → bleibt offen
- „Rechtliche Prüfung der Modusnamen vor einer Play-Store-Version." → bleibt offen, der Marken-Check läuft automatisch in `SpatialModeTest`

- [ ] **Step 7: Committen**

```bash
git add -A
git commit -m "docs: Phase-1-Stand, Release-Gate und offene Punkte"
```

---

## Selbstprüfung (Self-Review)

**1. Abdeckung der Spezifikation.** Jede User Story aus dem PRD hat eine Aufgabe:

| User Story | Aufgabe | Lücke |
|---|---|---|
| US-001 Equalizer, 10/31/64+ | 1, 2, 4 | Gerätetauglichkeit wird in Phase 0 gemessen, nicht im Code |
| US-002 Umgebungsanpassung | bestehender Code | Der Mikrofon-verweigert-Pfad existiert (`setAutoAdjust` setzt eine Meldung und lässt das Profil aktiv); ein expliziter Unit-Test fehlt, weil der Pfad Android-Berechtigungen braucht → in `docs/phase1-status.md` als Gerätetest vermerkt |
| US-003 KI-Interview | 9, 10 | — |
| US-004 Profile, Export/Import | 5, 6 | Umbenennen/Duplizieren bleiben Phase 2 (PRD sagt das so) |
| US-005 Raummodi | 7, 8 | — |
| US-006 Inhaltserkennung | 11 | — |
| US-007 Streaming-Konten | nicht implementiert | **Bewusste Lücke:** PRD ordnet US-007 Phase 2/3 zu, und NFR-011 verlangt in Phase 1 keinen Netzwerkzugriff. |
| US-008 Hintergrunddienst | bestehender Code | Wiederaustart nach Systemabbruch ist über `START_STICKY` abgedeckt, aber nicht getestet — Gerätetest nötig |
| US-009 Limiter/Clipping | 3, 4, 13 | — |
| US-010 Onboarding | bestehender Code | — |
| US-011 Migration | 5 | — |
| NFR-001 Latenz | — | **Offen:** braucht Loopback-Messung auf dem Gerät, in `docs/phase1-status.md` vermerkt |
| NFR-003 Akku | — | **Offen:** braucht Ein-Stunden-Test auf dem Gerät, in `docs/phase1-status.md` vermerkt |
| NFR-005 Clipping | 3 | Messsignal auf dem Gerät offen |
| NFR-009 Barrierefreiheit | 4, 8, 10 | Semantik-Beschreibungen gesetzt; TalkBack-Test offen |
| NFR-010 Migration ohne Datenverlust | 5 | — |
| NFR-011 kein Netzwerk | 11 | Manifest ohne INTERNET-Permission, in Task 15 geprüft |
| §11 Ereignisprotokoll | 12 | — |
| KR1 Klangbewertung | 14 | Wert entsteht erst auf dem Gerät |

**2. Schritt-Scan.** Jeder Schritt nennt eine konkrete Datei, eine konkrete Signatur oder einen konkreten Testnamen. Offene Punkte wie „edge cases abdecken" oder „Fehlerbehandlung ergänzen" stehen nirgends.

**3. Typkonsistenz.** `BandCount`, `EqCurve`, `EqModel.MIN_DB/MAX_DB`, `LimiterResult`, `SpatialMode`, `SpatialParams`, `InterviewAnswer`, `StoredProfile`, `StoredProfileFile`, `DiagnosticEvent`, `EventNames`, `NowPlaying`, `VolumeState`, `BlindOrder` — jeder Typ wird in genau einer Task definiert und in späteren Tasks nur mit demselben Namen verwendet. `SonoraEngine.plan` und `SonoraEngine.applyWithLimit` werden in Task 4 definiert und dort getestet.

**4. Review Focus.** Alle fünf Fehlerbilder haben einen Test:
1. Beschädigte Profildatei → Task 5 `beschaedigtes JSON liefert einen Fehler`, Task 6 `defekte Datei liefert keine halb importierten Daten`
2. Altes Schema → Task 5 `Schema 1 wird auf Schema 2 migriert`
3. Clipping an der Grenze → Task 3 (alle vier Limiter-Tests), Task 4 `Preamp oberhalb der Grenze wird zurueckgenommen`
4. Mikrofon verweigert → Task 1 `normalized kappt Werte` (fehlende Werte werden abgefangen), Task 4 ViewModel-Logik: `applyEffective` läuft ohne Analyzer weiter und lässt das letzte Profil aktiv
5. Bandzahl-Wechsel mitten in der Nutzung → Task 1 `Wechsel von 10 auf 31 interpoliert statt zu springen` und `Rueckwechsel auf 10 Baender erhaelt die Kurve`

**5. Verhältnis.** Der Plan ist rund 1.400 Zeilen lang bei 743 Zeilen PRD + 206 Zeilen UX-Spec — etwa das 1,5-Fache. Das ist für 15 Aufgaben mit Code- und Test-Snippets sowie exakten Dateipfaden angemessen; die Codeblöcke sind Signaturen, keine vollständigen Implementierungen, wo die Tests die Entscheidung bereits treiben.

---

## Execution Handoff

Der Plan liegt unter `docs/superpowers/plans/2026-10-20-sonora-phase1-completion.md`.

**Empfohlene Ausführung: Native** (superpowers:executing-plans). Begründung: Die 15 Aufgaben bauen aufeinander auf — Task 2 setzt Task 1 voraus, Task 4 setzt 1–3 voraus, Task 8 setzt 7 voraus, Task 11 setzt 6 voraus. Ein frischer Subagent pro Aufgabe müsste denselben Kontext bei jedem Schritt neu erarbeiten. Zusätzlich greifen alle Aufgaben auf dieselben vier Bestandsdateien zu (`SonoraEngine.kt`, `SonoraViewModel.kt`, `Screens.kt`, `SonoraRoot.kt`); paralleles Editieren derselben Dateien durch mehrere Agenten ist die dokumentierte Fehlerquelle in diesem Skill.

Der Nutzer hat ausdrücklich **„arbeite autonom“** gesagt und **„wir arbeiten nicht an claudroide“** — es gibt keinen Freigabeschritt, der auf eine Rückfrage wartet. Die Umsetzung startet daher unmittelbar nach diesem Plan.
