package de.sonora.app.core

import de.sonora.app.audio.SoundLabEngine
import de.sonora.app.audio.SoundLabGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/**
 * SoundLabEngine-Tests auf Basis der reinen, AudioTrack-freien Signal-Erzeugung
 * (SoundLabGenerator). Kein Android, kein AudioTrack, keine Demo-Werte:
 * Der FlangerDsp verändert wirklich reale PCM-Proben im Bereich [-1, 1].
 */
class SoundLabEngineTest {

    private val sampleRate = 44100

    @Test
    fun `Generator erzeugt pro Chunk genau chunkSize Samples im Bereich neg1 bis 1`() {
        val gen = SoundLabGenerator(sampleRate = sampleRate)
        val chunk = gen.generateChunk(SoundLabGenerator.SignalType.WARM_SYNTH_CHORD, 0.6f, 512)
        assertEquals(512, chunk.size)
        chunk.forEach {
            assertTrue("Sample >= -1: $it", it >= -1.0f)
            assertTrue("Sample <= 1: $it", it <= 1.0f)
            assertFalse("Kein NaN", it.isNaN())
            assertFalse("Keine Infinity", it.isInfinite())
        }
    }

    @Test
    fun `unterschiedliche SignalTypen erzeugen unterschiedliche Ausgabe`() {
        val gen = SoundLabGenerator(sampleRate = sampleRate)
        val chord = gen.generateChunk(SoundLabGenerator.SignalType.WARM_SYNTH_CHORD, 0.6f, 1024)
        gen.reset()
        val noise = gen.generateChunk(SoundLabGenerator.SignalType.PINK_NOISE, 0.6f, 1024)
        gen.reset()
        val pulse = gen.generateChunk(SoundLabGenerator.SignalType.ACOUSTIC_PULSE, 0.6f, 1024)

        assertNotEquals("Akkord und Rauschen unterscheiden sich", energy(chord), energy(noise), 0.001f)
        assertNotEquals("Rauschen und Impuls unterscheiden sich", energy(noise), energy(pulse), 0.001f)
    }

    @Test
    fun `Flanger verändert wirklich reale PCM-Daten (kein Mock)`() {
        val gen = SoundLabGenerator(sampleRate = sampleRate)
        val dry = FloatArray(2048) { sin(2.0 * PI * 440.0 * it / 44100.0).toFloat() }
        val dryCopy = dry.clone()

        // Den internen Flanger direkt auf den trockenen Puffer anwenden
        gen.flanger.process(dry)

        var diff = 0f
        for (i in dry.indices) {
            diff += kotlin.math.abs(dry[i] - dryCopy[i])
        }
        assertTrue("Der Flanger verändert reale PCM-Proben ($diff)", diff > 10f)

        // Und bleibt stabil / keine NaN
        dry.forEach {
            assertFalse(it.isNaN())
            assertFalse(it.isInfinite())
            assertTrue(kotlin.math.abs(it) <= 1.5f)
        }
    }

    @Test
    fun `Generator-Ausgabe durchläuft den Flanger und bleibt begrenzt`() {
        val gen = SoundLabGenerator(sampleRate = sampleRate)
        // 1 Sekunde Dauersignal durch den Flanger
        var maxAbs = 0f
        var anyNonZero = false
        for (i in 0 until 86) { // 86 * 512 ~ 44k Samples ~ 1 Sekunde
            val chunk = gen.generateChunk(SoundLabGenerator.SignalType.WARM_SYNTH_CHORD, 0.9f, 512)
            for (s in chunk) {
                assertFalse(s.isNaN())
                assertFalse(s.isInfinite())
                maxAbs = kotlin.math.max(maxAbs, kotlin.math.abs(s))
                if (kotlin.math.abs(s) > 0.001f) anyNonZero = true
            }
        }
        assertTrue("Signal ist nicht stumm", anyNonZero)
        assertTrue("Kein Blowup: max |x| = $maxAbs <= 1.5", maxAbs <= 1.5f)
    }

    @Test
    fun `Parametergleitung des Flangers ist klickfrei (stetiger Übergang)`() {
        val gen = SoundLabGenerator(sampleRate = sampleRate)
        gen.flanger.setParameters(rateHz = 0.5f)
        gen.flanger.setParameters(rateHz = 4.0f) // Sprunghafte Zielwert-Änderung

        gen.flanger.processSample(0f)
        val firstStep = gen.flanger.currentRateHz
        assertTrue("Erster Schritt ist stetig (0.5 < x < 0.6)", firstStep > 0.5f && firstStep < 0.6f)

        // Nach vielen Samples ist der Zielwert erreicht
        for (i in 0 until 5000) {
            gen.flanger.processSample(0f)
        }
        assertEquals(4.0f, gen.flanger.currentRateHz, 0.05f)
    }

    @Test
    fun `Generator reagiert auf Lautstärke (Volume Jumps _ Lautstärke-Sprünge)`() {
        val gen = SoundLabGenerator(sampleRate = sampleRate)
        val quiet = gen.generateChunk(SoundLabGenerator.SignalType.WARM_SYNTH_CHORD, 0.1f, 512)
        gen.reset()
        val loud = gen.generateChunk(SoundLabGenerator.SignalType.WARM_SYNTH_CHORD, 0.9f, 512)

        val eq = energy(quiet)
        val el = energy(loud)
        assertTrue("Lautere Ausgabe hat mehr Energie ($el > $eq)", el > eq * 2f)
    }

    @Test
    fun `Audio-Dropout-Resilienz_ leere_Stille-Chunks führen nicht zu NaN oder Blowup`() {
        val gen = SoundLabGenerator(sampleRate = sampleRate)
        // Viele stille Chunks (volume = 0) zwischen echten Signalen
        for (i in 0 until 100) {
            gen.generateChunk(SoundLabGenerator.SignalType.WARM_SYNTH_CHORD, 0.0f, 512)
        }
        val chunk = gen.generateChunk(SoundLabGenerator.SignalType.WARM_SYNTH_CHORD, 0.6f, 512)
        chunk.forEach {
            assertFalse(it.isNaN())
            assertFalse(it.isInfinite())
            assertTrue(kotlin.math.abs(it) <= 1.5f)
        }
    }

    @Test
    fun `Impuls-Signal bleibt stabil und klingt ab (Impulsantwort)`() {
        val gen = SoundLabGenerator(sampleRate = sampleRate)
        // Ein einzelner Impuls durch den Generator (ACOUSTIC_PULSE ähnelt einem Impulszug)
        val impulse = FloatArray(512) { if (it == 0) 1.0f else 0.0f }
        gen.flanger.process(impulse)

        var maxAbs = 0f
        var tailEnergy = 0f
        for (i in impulse.indices) {
            val s = impulse[i]
            assertFalse("Kein NaN bei $i", s.isNaN())
            assertFalse("Keine Infinity bei $i", s.isInfinite())
            maxAbs = kotlin.math.max(maxAbs, kotlin.math.abs(s))
            if (i > 100) tailEnergy += s * s
        }
        assertTrue("Impulsantwort bleibt begrenzt: $maxAbs", maxAbs < 5f)
        assertTrue("Impulsantwort klingt im Tail ab: $tailEnergy", tailEnergy < 1.0f)
    }

    @Test
    fun `NaN_Infinity am Eingang des Flangers werden nicht zum dauerhaften Ausfall`() {
        val gen = SoundLabGenerator(sampleRate = sampleRate)
        val bad = floatArrayOf(Float.NaN, Float.POSITIVE_INFINITY, -Float.POSITIVE_INFINITY, 0.5f, 0f)
        val copy = bad.clone()
        gen.flanger.process(copy)
        assertFalse(copy[3].isNaN())
        assertFalse(copy[3].isInfinite())

        // Danach wieder saubere Verarbeitung
        val clean = FloatArray(512) { 0.3f }
        gen.flanger.process(clean)
        clean.forEach { assertFalse(it.isNaN()); assertFalse(it.isInfinite()) }
    }

    @Test
    fun `reset setzt Flanger-Zustand zurück`() {
        val gen = SoundLabGenerator(sampleRate = sampleRate)
        gen.generateChunk(SoundLabGenerator.SignalType.WARM_SYNTH_CHORD, 0.6f, 512)
        gen.reset()
        // Nach reset muss weiteres Generieren sauber bleiben
        val chunk = gen.generateChunk(SoundLabGenerator.SignalType.ACOUSTIC_PULSE, 0.6f, 512)
        chunk.forEach { assertFalse(it.isNaN()); assertFalse(it.isInfinite()) }
    }

    @Test
    fun `Abtastrate-Änderung am Generator bleibt stabil`() {
        val gen = SoundLabGenerator(sampleRate = sampleRate)
        gen.setSampleRate(48000)
        assertEquals(48000, gen.getSampleRate())
        val chunk = gen.generateChunk(SoundLabGenerator.SignalType.WARM_SYNTH_CHORD, 0.6f, 512)
        chunk.forEach { assertFalse(it.isNaN()); assertFalse(it.isInfinite()); assertTrue(kotlin.math.abs(it) <= 1.5f) }
    }

    // ---------- Helfer ----------

    private fun energy(samples: FloatArray): Float {
        var sum = 0f
        for (s in samples) sum += s * s
        return sum
    }
}
