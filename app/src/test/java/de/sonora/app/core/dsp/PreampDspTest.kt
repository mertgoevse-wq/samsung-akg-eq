package de.sonora.app.core.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreampDspTest {

    private val sampleRate = 44100

    @Test
    fun `deaktivierter Preamp leitet trocken durch (True Bypass, 0 dB)`() {
        val pre = PreampDsp(sampleRate)
        pre.setEnabled(false)
        pre.setTargetGainDb(12f)
        val input = floatArrayOf(0.1f, -0.5f, 0.9f)
        val copy = input.clone()
        pre.process(copy)
        for (i in input.indices) {
            assertEquals(input[i], copy[i], 0.0001f)
        }
    }

    @Test
    fun `Preamp verstärkt nach Einstellzeit um den eingestellten dB-Wert`() {
        val pre = PreampDsp(sampleRate)
        pre.setEnabled(true)
        pre.setTargetGainDb(6f) // +6 dB ~ linearer Faktor 2
        // Lang genug für die One-Pole-Glättung (~20 ms Zeitkonstante)
        val input = FloatArray(20000) { 0.1f }
        pre.process(input)
        val tail = input.sliceArray(15000 until 20000)
        val mean = tail.average()
        assertEquals(0.2f, mean, 0.02f)
    }

    @Test
    fun `negative Gain dämpft das Signal`() {
        val pre = PreampDsp(sampleRate)
        pre.setEnabled(true)
        pre.setTargetGainDb(-12f) // ~ Faktor 0.25
        for (i in 0 until 5000) pre.processSample(0f)
        val input = FloatArray(5000) { 0.8f }
        val copy = input.clone()
        pre.process(input)
        val tail = input.sliceArray(1000 until 5000)
        val mean = tail.average()
        assertTrue("Negativer Gain dämpft ($mean < 0.8)", mean < 0.3f)
    }

    @Test
    fun `Headroom begrenzt die wirksame Verstärkung`() {
        val pre = PreampDsp(sampleRate)
        pre.setEnabled(true)
        pre.setTargetGainDb(12f) // Ziel wäre +12 dB
        pre.setHeadroomDb(3f) // aber Headroom begrenzt auf +3 dB (~ Faktor 1.41)
        for (i in 0 until 5000) pre.processSample(0f)
        val input = FloatArray(5000) { 0.5f }
        val copy = input.clone()
        pre.process(input)
        val tail = input.sliceArray(1000 until 5000)
        val mean = tail.average()
        // Sollte näher an 0.5 * 1.41 = 0.705 sein als an 0.5 * 4 = 2.0
        assertTrue("Headroom begrenzt Gain ($mean <= ~0.75)", mean <= 0.75f)
        assertTrue("Headroom bringt aber etwas Gain ($mean > 0.5)", mean > 0.5f)
    }

    @Test
    fun `Gain 0 dB lässt Signal unverändert (nach Einstellzeit)`() {
        val pre = PreampDsp(sampleRate)
        pre.setEnabled(true)
        pre.setTargetGainDb(0f)
        for (i in 0 until 5000) pre.processSample(0f)
        val input = FloatArray(5000) { 0.5f }
        val copy = input.clone()
        pre.process(input)
        var diff = 0f
        for (i in input.indices) diff += kotlin.math.abs(input[i] - copy[i])
        assertTrue("0 dB Gain lässt Signal unverändert ($diff)", diff < 0.01f)
    }

    @Test
    fun `kein NaN/Infinity bei Dauerbetrieb und hohen Pegeln`() {
        val pre = PreampDsp(sampleRate)
        pre.setEnabled(true)
        pre.setTargetGainDb(12f)
        val buffer = FloatArray(44100) { if (it % 10 == 0) 1.0f else 0.5f }
        pre.process(buffer)
        buffer.forEach {
            assertFalse("Kein NaN", it.isNaN())
            assertFalse("Keine Infinity", it.isInfinite())
            assertTrue("Kein Blowup", kotlin.math.abs(it) <= 2f)
        }
    }

    @Test
    fun `NaN/Infinity am Eingang werden wiederhergestellt`() {
        val pre = PreampDsp(sampleRate)
        pre.setEnabled(true)
        pre.setTargetGainDb(6f)
        val buffer = floatArrayOf(Float.NaN, Float.POSITIVE_INFINITY, -Float.POSITIVE_INFINITY, 0.5f, 0f)
        val copy = buffer.clone()
        pre.process(copy)
        assertFalse(copy[3].isNaN())
        assertFalse(copy[3].isInfinite())
        // Nachfolgende saubere Proben bleiben endlich
        for (i in 0 until 100) pre.processSample(0.1f)
        val tail = FloatArray(100) { 0.2f }
        pre.process(tail)
        tail.forEach { assertFalse(it.isNaN()); assertFalse(it.isInfinite()) }
    }

    @Test
    fun `Abtastrate-Wechsel bleibt stabil`() {
        val pre = PreampDsp(sampleRate)
        pre.setSampleRate(48000)
        pre.setSampleRate(22050)
        pre.setTargetGainDb(6f)
        val buf = FloatArray(512) { 0.3f }
        pre.process(buf)
        buf.forEach { assertFalse(it.isNaN()); assertFalse(it.isInfinite()) }
    }

    @Test
    fun `Stereo-Kanäle werden beide verstärkt`() {
        val pre = PreampDsp(sampleRate)
        pre.setEnabled(true)
        pre.setTargetGainDb(6f)
        for (i in 0 until 5000) pre.processSample(0f)
        val left = FloatArray(512) { 0.5f }
        val right = FloatArray(512) { -0.3f }
        pre.processStereo(left, right)
        left.forEach { assertFalse(it.isNaN()); assertFalse(it.isInfinite()) }
        right.forEach { assertFalse(it.isNaN()); assertFalse(it.isInfinite()) }
    }

    @Test
    fun `extreme Denormal-Werte führen nicht zu NaN`() {
        val pre = PreampDsp(sampleRate)
        pre.setEnabled(true)
        pre.setTargetGainDb(6f)
        val buf = FloatArray(5000) { 1e-25f }
        pre.process(buf)
        buf.forEach { assertFalse(it.isNaN()); assertFalse(it.isInfinite()) }
    }

    @Test
    fun `reset springt Glättung auf Zielwert`() {
        val pre = PreampDsp(sampleRate)
        pre.setEnabled(true)
        pre.setTargetGainDb(12f)
        // Vor dem Reset ist der Gain noch niedrig (Glättung)
        val before = pre.processSample(1.0f)
        pre.reset()
        val after = pre.processSample(1.0f)
        assertTrue("Nach Reset ist Gain näher am Ziel", after > before)
    }
}
