package de.sonora.app.core.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class CompressorDspTest {

    private val sampleRate = 44100

    @Test
    fun `deaktivierter Kompressor leitet trocken durch (True Bypass)`() {
        val comp = CompressorDsp(sampleRate)
        comp.setEnabled(false)
        val input = floatArrayOf(0.1f, -0.5f, 0.9f)
        val copy = input.clone()
        comp.process(copy)
        for (i in input.indices) {
            assertEquals(input[i], copy[i], 0.0001f)
        }
    }

    @Test
    fun `Signale unter dem Schwellenwert bleiben unverändert`() {
        val comp = CompressorDsp(sampleRate)
        comp.setTargetParameters(thresholdDb = -6f, ratio = 4f, kneeDb = 0f, makeupDb = 0f)
        // Glättung ausfahren
        for (i in 0 until 5000) comp.processSample(0f)

        // -12 dBFS ~ 0.25 linear, unter Schwellenwert -6 dB (~0.5)
        val input = FloatArray(1000) { 0.25f }
        val copy = input.clone()
        comp.process(input)
        var diff = 0f
        for (i in input.indices) diff += kotlin.math.abs(input[i] - copy[i])
        assertTrue("Unter dem Schwellenwert bleibt Signal unverändert ($diff)", diff < 0.01f)
    }

    @Test
    fun `Signale über dem Schwellenwert werden gedämpft`() {
        val comp = CompressorDsp(sampleRate)
        comp.setTargetParameters(thresholdDb = -20f, ratio = 10f, kneeDb = 0f, makeupDb = 0f)
        for (i in 0 until 5000) comp.processSample(0f)

        // 0.8 linear ~ -1.9 dB, deutlich über -20 dB Schwellenwert -> gedämpft
        val input = FloatArray(5000) { 0.8f }
        val copy = input.clone()
        comp.process(input)
        // Nach Einstellzeit sollte der Pegel niedriger sein als 0.8
        val tail = input.sliceArray(1000 until 5000)
        val mean = tail.average()
        assertTrue("Über Schwellenwert gedämpft ($mean < 0.8)", mean < 0.7f)
    }

    @Test
    fun `Ratio 1_1 verändert das Signal nicht`() {
        val comp = CompressorDsp(sampleRate)
        comp.setTargetParameters(thresholdDb = -20f, ratio = 1f, kneeDb = 0f, makeupDb = 0f)
        for (i in 0 until 5000) comp.processSample(0f)

        val input = FloatArray(1000) { 0.8f }
        val copy = input.clone()
        comp.process(input)
        var diff = 0f
        for (i in input.indices) diff += kotlin.math.abs(input[i] - copy[i])
        assertTrue("Ratio 1:1 lässt Signal unverändert ($diff)", diff < 0.01f)
    }

    @Test
    fun `kein NaN_Infinity bei Dauerbetrieb und lauten Pegeln`() {
        val comp = CompressorDsp(sampleRate)
        comp.setTargetParameters(thresholdDb = -30f, ratio = 8f, kneeDb = 4f, makeupDb = 6f)
        val buffer = FloatArray(44100) { if (it % 4 == 0) 1.0f else 0.05f }
        comp.process(buffer)
        buffer.forEach {
            assertFalse("Kein NaN", it.isNaN())
            assertFalse("Keine Infinity", it.isInfinite())
            assertTrue("Kein Blowup", kotlin.math.abs(it) <= 2f)
        }
    }

    @Test
    fun `NaN_Infinity am Eingang werden wiederhergestellt`() {
        val comp = CompressorDsp(sampleRate)
        comp.setTargetParameters(thresholdDb = -20f, ratio = 4f, kneeDb = 2f, makeupDb = 0f)
        val buffer = floatArrayOf(Float.NaN, Float.POSITIVE_INFINITY, -Float.POSITIVE_INFINITY, 0.5f, 0f)
        val copy = buffer.clone()
        comp.process(copy)
        assertFalse(copy[3].isNaN())
        assertFalse(copy[3].isInfinite())
    }

    @Test
    fun `Makeup-Gain hebt leises Signal an`() {
        val comp = CompressorDsp(sampleRate)
        comp.setTargetParameters(thresholdDb = -60f, ratio = 2f, kneeDb = 0f, makeupDb = 12f)
        for (i in 0 until 5000) comp.processSample(0f)

        // Leises Signal mit viel Makeup-Gain sollte lauter werden
        val input = FloatArray(10000) { 0.05f }
        val copy = input.clone()
        comp.process(input)
        val tail = input.sliceArray(2000 until 10000)
        val mean = tail.average()
        assertTrue("Makeup-Gain hebt Pegel an ($mean > 0.05)", mean > 0.08f)
    }

    @Test
    fun `Abtastrate-Wechsel bleibt stabil`() {
        val comp = CompressorDsp(sampleRate)
        comp.setSampleRate(48000)
        comp.setSampleRate(22050)
        comp.setTargetParameters(thresholdDb = -20f, ratio = 4f, kneeDb = 2f, makeupDb = 0f)
        val buf = FloatArray(512) { 0.3f }
        comp.process(buf)
        buf.forEach { assertFalse(it.isNaN()); assertFalse(it.isInfinite()) }
    }

    @Test
    fun `Stereo-Kanäle werden beide verarbeitet`() {
        val comp = CompressorDsp(sampleRate)
        comp.setTargetParameters(thresholdDb = -20f, ratio = 4f, kneeDb = 2f, makeupDb = 0f)
        val left = FloatArray(512) { 0.8f }
        val right = FloatArray(512) { -0.6f }
        comp.processStereo(left, right)
        left.forEach { assertFalse(it.isNaN()); assertFalse(it.isInfinite()) }
        right.forEach { assertFalse(it.isNaN()); assertFalse(it.isInfinite()) }
    }

    @Test
    fun `extreme Denormal-Werte führen nicht zu NaN`() {
        val comp = CompressorDsp(sampleRate)
        comp.setTargetParameters(thresholdDb = -20f, ratio = 4f, kneeDb = 2f, makeupDb = 0f)
        val buf = FloatArray(5000) { 1e-25f }
        comp.process(buf)
        buf.forEach { assertFalse(it.isNaN()); assertFalse(it.isInfinite()) }
    }
}
