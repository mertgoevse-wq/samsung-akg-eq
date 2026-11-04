package de.sonora.app.core.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class StereoCrossfeedDspTest {

    private val sampleRate = 44100

    @Test
    fun `deaktivierter Crossfeed leitet beide Kanäle trocken durch (True Bypass)`() {
        val cf = StereoCrossfeedDsp(sampleRate)
        cf.setEnabled(false)
        val left = floatArrayOf(0.1f, -0.5f, 0.9f)
        val right = floatArrayOf(-0.2f, 0.4f, -0.1f)
        val lCopy = left.clone()
        val rCopy = right.clone()
        cf.processStereo(left, right)
        for (i in left.indices) {
            assertEquals(lCopy[i], left[i], 0.0001f)
            assertEquals(rCopy[i], right[i], 0.0001f)
        }
    }

    @Test
    fun `einseitiges Stereosignal erzeugt hörbares Übersprechen auf dem Gegenkanal`() {
        val cf = StereoCrossfeedDsp(sampleRate)
        cf.setTargetStrength(0.8f)
        // Einregeln
        for (i in 0 until 5000) cf.processSample(0f, 0f)

        val left = FloatArray(2048) { sin(2.0 * PI * 1000.0 * it / 44100.0).toFloat() }
        val right = FloatArray(2048) { 0f } // rechter Kanal stumm
        val lCopy = left.clone()
        val rCopy = right.clone()
        cf.processStereo(left, right)

        // Linker Kanal bleibt weitgehend unverändert (direkt + schwaches Übersprechen von rechts=0)
        var leftChanged = 0
        for (i in left.indices) {
            if (kotlin.math.abs(left[i] - lCopy[i]) > 0.001f) leftChanged++
        }
        // Rechter Kanal enthält nun verzögertes Linkssignal (Übersprechen)
        var rightEnergy = 0f
        for (i in right.indices) rightEnergy += right[i] * right[i]
        assertTrue("Rechter Kanal enthält nach Crossfeed hörbares Signal", rightEnergy > 0.01f)
        assertTrue("Linker Kanal bleibt nahezu unverändert", leftChanged < 50)
    }

    @Test
    fun `Stärke 0 erzeugt das Bauer -18 dB Floor-Übersprechen (nicht null, aber minimal)`() {
        val cf = StereoCrossfeedDsp(sampleRate)
        cf.setTargetStrength(0.0f)
        for (i in 0 until 5000) cf.processSample(0f, 0f)

        val left = FloatArray(1024) { 0.5f }
        val right = FloatArray(1024) { 0f }
        cf.processStereo(left, right)

        // Bauer-Spezifikation: auch bei Stärke 0 bleibt das Übersprechen am -18 dB Floor.
        // crossGain = 10^(-18/20) ≈ 0.126, rechtes Signal ≈ 0.5 * 0.126 ≈ 0.063 (RMS)
        var rightEnergy = 0f
        for (i in right.indices) rightEnergy += right[i] * right[i]
        val rms = kotlin.math.sqrt(rightEnergy / right.size)
        assertTrue("Rechter Kanal beim Bauer -18 dB Floor (~0.063 rms, ist $rms)", kotlin.math.abs(rms - 0.063f) < 0.02f)
    }

    @Test
    fun `kein NaN_Infinity bei Dauerbetrieb`() {
        val cf = StereoCrossfeedDsp(sampleRate)
        cf.setTargetStrength(1.0f)
        val left = FloatArray(44100) { if (it % 3 == 0) 1.0f else 0.2f }
        val right = FloatArray(44100) { if (it % 5 == 0) -0.8f else 0.1f }
        cf.processStereo(left, right)
        left.forEach {
            assertFalse("Kein NaN links", it.isNaN())
            assertFalse("Keine Infinity links", it.isInfinite())
        }
        right.forEach {
            assertFalse("Kein NaN rechts", it.isNaN())
            assertFalse("Keine Infinity rechts", it.isInfinite())
        }
    }

    @Test
    fun `NaN_Infinity am Eingang werden wiederhergestellt`() {
        val cf = StereoCrossfeedDsp(sampleRate)
        cf.setTargetStrength(0.7f)
        val left = floatArrayOf(Float.NaN, Float.POSITIVE_INFINITY, 0.5f, 0f)
        val right = floatArrayOf(0.3f, Float.NaN, -0.2f, 0f)
        val lCopy = left.clone()
        val rCopy = right.clone()
        cf.processStereo(left, right)
        // Nach den ungültigen Werten sollten folgende Proben endlich sein
        assertFalse(left[2].isNaN())
        assertFalse(left[2].isInfinite())
        assertFalse(right[2].isNaN())
        assertFalse(right[2].isInfinite())
    }

    @Test
    fun `Abtastrate-Wechsel bleibt stabil und verzögert nicht unendlich`() {
        val cf = StereoCrossfeedDsp(sampleRate)
        cf.setSampleRate(48000)
        cf.setSampleRate(22050)
        cf.setTargetStrength(0.6f)
        val left = FloatArray(512) { 0.3f }
        val right = FloatArray(512) { -0.2f }
        cf.processStereo(left, right)
        left.forEach { assertFalse(it.isNaN()); assertFalse(it.isInfinite()) }
        right.forEach { assertFalse(it.isNaN()); assertFalse(it.isInfinite()) }
    }

    @Test
    fun `Mono-Pfad wird trocken durchgereicht (kein Gegenkanal)`() {
        val cf = StereoCrossfeedDsp(sampleRate)
        cf.setTargetStrength(0.8f)
        val mono = floatArrayOf(0.5f, -0.3f, 0.1f)
        val copy = mono.clone()
        cf.processMono(mono)
        for (i in mono.indices) {
            assertEquals(copy[i], mono[i], 0.0001f)
        }
    }

    @Test
    fun `extreme Denormal-Werte führen nicht zu NaN`() {
        val cf = StereoCrossfeedDsp(sampleRate)
        cf.setTargetStrength(0.9f)
        val left = FloatArray(5000) { 1e-25f }
        val right = FloatArray(5000) { -1e-25f }
        cf.processStereo(left, right)
        left.forEach { assertFalse(it.isNaN()); assertFalse(it.isInfinite()) }
        right.forEach { assertFalse(it.isNaN()); assertFalse(it.isInfinite()) }
    }
}
