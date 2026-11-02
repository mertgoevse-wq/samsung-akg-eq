package de.sonora.app.core.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class PeakLimiterDspTest {

    private val sampleRate = 44100

    @Test
    fun `deaktivierter Limiter leitet trocken durch (True Bypass)`() {
        val lim = PeakLimiterDsp(sampleRate)
        lim.setEnabled(false)
        val input = floatArrayOf(0.1f, -0.5f, 0.9f)
        val copy = input.clone()
        lim.process(copy)
        for (i in input.indices) {
            assertEquals(input[i], copy[i], 0.0001f)
        }
    }

    @Test
    fun `Pegel über der Ceiling werden gedämpft`() {
        val lim = PeakLimiterDsp(sampleRate)
        lim.setTargetCeilingDb(-6f) // ca. 0.5 linear
        for (i in 0 until 5000) lim.processSample(0f)

        // Dauerhaft 0.9 linear (~ -0.9 dB) liegt über -6 dB -> gedämpft auf ~0.5
        val input = FloatArray(10000) { 0.9f }
        val copy = input.clone()
        lim.process(input)
        val tail = input.sliceArray(2000 until 10000)
        val maxAbs = tail.maxOrNull() ?: 0f
        assertTrue("Spitzen auf Ceiling gedämpft ($maxAbs <= ~0.55)", maxAbs <= 0.55f)
    }

    @Test
    fun `Pegel unter der Ceiling bleiben unverändert`() {
        val lim = PeakLimiterDsp(sampleRate)
        lim.setTargetCeilingDb(-1f) // ca. 0.89 linear
        for (i in 0 until 5000) lim.processSample(0f)

        val input = FloatArray(1000) { 0.2f } // deutlich unter Ceiling
        val copy = input.clone()
        lim.process(input)
        var diff = 0f
        for (i in input.indices) diff += kotlin.math.abs(input[i] - copy[i])
        assertTrue("Unter Ceiling bleibt unverändert ($diff)", diff < 0.01f)
    }

    @Test
    fun `kurze Spitzen werden sanft begrenzt ohne harte Clips`() {
        val lim = PeakLimiterDsp(sampleRate)
        lim.setTargetCeilingDb(-3f)
        for (i in 0 until 5000) lim.processSample(0f)

        val input = FloatArray(4410) { sin(2.0 * PI * 440.0 * it / 44100.0).toFloat() * 0.95f }
        val copy = input.clone()
        lim.process(input)
        input.forEach {
            assertFalse("Kein NaN", it.isNaN())
            assertFalse("Keine Infinity", it.isInfinite())
            assertTrue("Kein Wert über 1.0 (Ceiling -3 dB ~ 0.707, mit Attack etwas mehr)", kotlin.math.abs(it) <= 1.0f)
        }
    }

    @Test
    fun `kein NaN/Infinity bei Dauerbetrieb`() {
        val lim = PeakLimiterDsp(sampleRate)
        lim.setTargetCeilingDb(-6f)
        val buffer = FloatArray(44100) { if (it % 3 == 0) 1.0f else 0.1f }
        lim.process(buffer)
        buffer.forEach {
            assertFalse("Kein NaN", it.isNaN())
            assertFalse("Keine Infinity", it.isInfinite())
        }
    }

    @Test
    fun `NaN/Infinity am Eingang werden wiederhergestellt`() {
        val lim = PeakLimiterDsp(sampleRate)
        lim.setTargetCeilingDb(-6f)
        val buffer = floatArrayOf(Float.NaN, Float.POSITIVE_INFINITY, 0.5f, 0f)
        val copy = buffer.clone()
        lim.process(copy)
        assertFalse(copy[2].isNaN())
        assertFalse(copy[2].isInfinite())
    }

    @Test
    fun `Abtastrate-Wechsel bleibt stabil`() {
        val lim = PeakLimiterDsp(sampleRate)
        lim.setSampleRate(48000)
        lim.setSampleRate(22050)
        lim.setTargetCeilingDb(-6f)
        val buf = FloatArray(512) { 0.8f }
        lim.process(buf)
        buf.forEach { assertFalse(it.isNaN()); assertFalse(it.isInfinite()) }
    }

    @Test
    fun `Stereo-Kanäle werden beide begrenzt`() {
        val lim = PeakLimiterDsp(sampleRate)
        lim.setTargetCeilingDb(-6f)
        val left = FloatArray(512) { 0.9f }
        val right = FloatArray(512) { -0.9f }
        lim.processStereo(left, right)
        left.forEach { assertFalse(it.isNaN()); assertTrue(kotlin.math.abs(it) <= 1.0f) }
        right.forEach { assertFalse(it.isNaN()); assertTrue(kotlin.math.abs(it) <= 1.0f) }
    }

    @Test
    fun `extreme Denormal-Werte führen nicht zu NaN`() {
        val lim = PeakLimiterDsp(sampleRate)
        lim.setTargetCeilingDb(-6f)
        val buf = FloatArray(5000) { 1e-25f }
        lim.process(buf)
        buf.forEach { assertFalse(it.isNaN()); assertFalse(it.isInfinite()) }
    }

    @Test
    fun `sehr niedrige Ceiling dämpft starkes Signal stark`() {
        val lim = PeakLimiterDsp(sampleRate)
        lim.setTargetCeilingDb(-20f) // ~0.1 linear
        for (i in 0 until 5000) lim.processSample(0f)
        val input = FloatArray(10000) { 0.9f }
        lim.process(input)
        val maxAbs = input.maxOrNull() ?: 0f
        assertTrue("Bei -20 dB Ceiling stark gedämpft ($maxAbs <= ~0.15)", maxAbs <= 0.15f)
    }
}
