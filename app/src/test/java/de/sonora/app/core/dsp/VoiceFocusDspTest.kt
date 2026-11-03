package de.sonora.app.core.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class VoiceFocusDspTest {

    private val sampleRate = 44100

    @Test
    fun `deaktivierter VoiceFocus leitet trocken durch (True Bypass)`() {
        val vf = VoiceFocusDsp(sampleRate)
        vf.setEnabled(false)
        val input = floatArrayOf(0.1f, -0.5f, 0.9f)
        val copy = input.clone()
        vf.process(copy)
        for (i in input.indices) {
            assertEquals(input[i], copy[i], 0.0001f)
        }
    }

    @Test
    fun `Sprachband (2 kHz) wird betont, tiefe Frequenzen nicht gleichermaßen`() {
        val vf = VoiceFocusDsp(sampleRate)
        vf.setTargetIntensity(1.0f)
        // Einregeln der Intensität (Glättung)
        for (i in 0 until 5000) vf.processSample(0f)

        // 2 kHz (im Sprachband) vs 100 Hz (außerhalb)
        val speech = FloatArray(4410) { sin(2.0 * PI * 2000.0 * it / 44100.0).toFloat() }
        val speechCopy = speech.clone()
        vf.process(speech)
        val speechDiff = energyDiff(speech, speechCopy)

        val low = FloatArray(4410) { sin(2.0 * PI * 100.0 * it / 44100.0).toFloat() }
        val lowCopy = low.clone()
        vf.process(low)
        val lowDiff = energyDiff(low, lowCopy)

        // Das Sprachband-Signal soll stärker verändert werden als das tiefe
        assertTrue("Sprachband verändert: $speechDiff", speechDiff > 0.001f)
        assertTrue("Sprachband stärker verändert als Tiefbass ($speechDiff vs $lowDiff)", speechDiff > lowDiff)
    }

    @Test
    fun `kein NaN_Infinity bei Dauerbetrieb`() {
        val vf = VoiceFocusDsp(sampleRate)
        vf.setTargetIntensity(1.0f)
        val buffer = FloatArray(44100) { if (it % 7 == 0) 1.0f else 0.2f }
        vf.process(buffer)
        buffer.forEach {
            assertFalse("Kein NaN", it.isNaN())
            assertFalse("Keine Infinity", it.isInfinite())
            assertTrue("Kein Blowup", kotlin.math.abs(it) <= 2f)
        }
    }

    @Test
    fun `NaN_Infinity am Eingang werden wiederhergestellt`() {
        val vf = VoiceFocusDsp(sampleRate)
        vf.setTargetIntensity(1.0f)
        val buffer = floatArrayOf(Float.NaN, Float.POSITIVE_INFINITY, 0.5f, 0f)
        val copy = buffer.clone()
        vf.process(copy)
        assertFalse(copy[2].isNaN())
        assertFalse(copy[2].isInfinite())
    }

    @Test
    fun `Intensität 0 verändert Signal nicht (nach Einstellzeit)`() {
        val vf = VoiceFocusDsp(sampleRate)
        vf.setTargetIntensity(0.0f)
        // Glättung ausfahren lassen (Intensität startet bei 1, fährt auf 0 ab)
        val dummy = FloatArray(20000) { 0f }
        vf.process(dummy)

        val input = FloatArray(1000) { 0.5f }
        val copy = input.clone()
        vf.process(input)
        // Bei Intensität 0 sollte das Signal trocken durchgehen (außer möglichem leisen Offset)
        var diff = 0f
        for (i in input.indices) diff += kotlin.math.abs(input[i] - copy[i])
        assertTrue("Bei Intensität 0 ist Signal nahezu unverändert ($diff)", diff < 0.01f)
    }

    @Test
    fun `Abtastrate-Wechsel bleibt stabil`() {
        val vf = VoiceFocusDsp(sampleRate)
        vf.setSampleRate(48000)
        vf.setSampleRate(22050)
        vf.setTargetIntensity(0.8f)
        val buf = FloatArray(512) { 0.3f }
        vf.process(buf)
        buf.forEach { assertFalse(it.isNaN()); assertFalse(it.isInfinite()) }
    }

    @Test
    fun `Stereo-Kanäle werden beide verarbeitet`() {
        val vf = VoiceFocusDsp(sampleRate)
        vf.setTargetIntensity(1.0f)
        val left = FloatArray(512) { 0.5f }
        val right = FloatArray(512) { -0.3f }
        vf.processStereo(left, right)
        left.forEach { assertFalse(it.isNaN()); assertFalse(it.isInfinite()) }
        right.forEach { assertFalse(it.isNaN()); assertFalse(it.isInfinite()) }
    }

    @Test
    fun `extreme Werte (Denormal) führen nicht zu NaN`() {
        val vf = VoiceFocusDsp(sampleRate)
        vf.setTargetIntensity(1.0f)
        val buf = FloatArray(5000) { 1e-25f }
        vf.process(buf)
        buf.forEach { assertFalse(it.isNaN()); assertFalse(it.isInfinite()) }
    }

    private fun energyDiff(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        for (i in a.indices) sum += (a[i] - b[i]) * (a[i] - b[i])
        return sum
    }
}
