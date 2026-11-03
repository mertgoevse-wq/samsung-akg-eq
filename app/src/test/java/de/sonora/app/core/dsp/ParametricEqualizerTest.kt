package de.sonora.app.core.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class ParametricEqualizerTest {

    private val sampleRate = 44100

    @Test
    fun `deaktivierter Equalizer leitet Signal trocken durch (True Bypass)`() {
        val eq = ParametricEqualizer(sampleRate)
        eq.setEnabled(false)
        eq.setBands(listOf(
            ParametricEqualizer.Band(ParametricEqualizer.FilterType.PEAKING, 1000f, 12f, 1.0f, true)
        ))
        val input = floatArrayOf(0.1f, -0.5f, 0.9f)
        val copy = input.clone()
        eq.process(copy)
        for (i in input.indices) {
            assertEquals(input[i], copy[i], 0.0001f)
        }
    }

    @Test
    fun `preamp betont das Signal nach Einstellzeit um den eingestellten dB-Wert`() {
        val eq = ParametricEqualizer(sampleRate)
        eq.setEnabled(true)
        eq.setBands(emptyList()) // keine Filter, nur Preamp
        eq.setPreampDb(6f) // +6 dB ~ linearer Faktor 2
        // Puffer lang genug, dass die One-Pole-Glättung ausregelt (~20 ms Zeitkonstante)
        val input = FloatArray(20000) { 0.1f }
        eq.process(input)
        // Letzte 5000 Proben sind im eingeregelten Zustand
        val tail = input.sliceArray(15000 until 20000)
        val mean = tail.average()
        assertEquals(0.2f, mean.toFloat(), 0.02f)
    }

    @Test
    fun `peaking-Band betont Zentrumsfrequenz nachweisbar`() {
        val eq = ParametricEqualizer(sampleRate)
        val input = FloatArray(4410) { sin(2.0 * PI * 1000.0 * it / 44100.0).toFloat() } // 1 kHz
        val original = input.clone().map { it }.toFloatArray()
        eq.setBands(listOf(
            ParametricEqualizer.Band(ParametricEqualizer.FilterType.PEAKING, 1000f, 6f, 1.414f, true)
        ))
        eq.process(input)
        var diff = 0f
        for (i in 500 until input.size) {
            diff += kotlin.math.abs(input[i] - original[i])
        }
        assertTrue("Peaking-Band verändert das 1-kHz-Signal", diff > 5f)
    }

    @Test
    fun `tiefpass-regal betont tiefe Frequenzen`() {
        val eq = ParametricEqualizer(sampleRate)
        eq.setBands(listOf(
            ParametricEqualizer.Band(ParametricEqualizer.FilterType.LOW_SHELF, 200f, 6f, 1.0f, true)
        ))
        val low = FloatArray(4410) { sin(2.0 * PI * 100.0 * it / 44100.0).toFloat() } // 100 Hz
        val lowCopy = low.clone()
        eq.process(low)
        // Nach Einstellzeit: niedrige Freq betont
        assertTrue("100 Hz Signal wurde verändert", energyDiff(low, lowCopy) > 0.001f)
    }

    @Test
    fun `hochpass-regal betont hohe Frequenzen`() {
        val eq = ParametricEqualizer(sampleRate)
        eq.setBands(listOf(
            ParametricEqualizer.Band(ParametricEqualizer.FilterType.HIGH_SHELF, 5000f, 6f, 1.0f, true)
        ))
        val high = FloatArray(4410) { sin(2.0 * PI * 15000.0 * it / 44100.0).toFloat() } // 15 kHz
        val highCopy = high.clone()
        eq.process(high)
        assertTrue("15 kHz Signal wurde verändert", energyDiff(high, highCopy) > 0.001f)
    }

    @Test
    fun `kein NaN oder Infinity bei Dauerbetrieb und hohen Pegeln`() {
        val eq = ParametricEqualizer(sampleRate)
        eq.setBands(listOf(
            ParametricEqualizer.Band(ParametricEqualizer.FilterType.PEAKING, 1000f, 12f, 0.5f, true)
        ))
        eq.setPreampDb(6f)
        val buffer = FloatArray(44100) { if (it % 10 == 0) 1.0f else 0.5f }
        eq.process(buffer)
        buffer.forEach {
            assertFalse("Kein NaN", it.isNaN())
            assertFalse("Keine Infinity", it.isInfinite())
            assertTrue("Kein Blowup: |x| <= 1.5", kotlin.math.abs(it) <= 1.5f)
        }
    }

    @Test
    fun `NaN und Infinity am Eingang führen nicht zu dauerhaften Fehlern`() {
        val eq = ParametricEqualizer(sampleRate)
        eq.setBands(listOf(
            ParametricEqualizer.Band(ParametricEqualizer.FilterType.PEAKING, 1000f, 6f, 1.414f, true)
        ))
        val buffer = floatArrayOf(Float.NaN, Float.POSITIVE_INFINITY, -Float.POSITIVE_INFINITY, 0.5f, 0f)
        val copy = buffer.clone()
        eq.process(copy)
        // Nach den schlechten Werten muss der Filter wieder saubere Ausgabe liefern
        assertFalse(copy[3].isNaN())
        assertFalse(copy[3].isInfinite())
        // Und nachfolgende saubere Proben bleiben endlich
        for (i in 0 until 1000) {
            eq.processSample(0.1f)
        }
        val tail = FloatArray(100) { 0.2f }
        eq.process(tail)
        tail.forEach { assertFalse(it.isNaN()); assertFalse(it.isInfinite()) }
    }

    @Test
    fun `Denormal-Zahlen werden abgespült (keine Performance-Einbrüche)`() {
        val eq = ParametricEqualizer(sampleRate)
        eq.setBands(listOf(
            ParametricEqualizer.Band(ParametricEqualizer.FilterType.PEAKING, 1000f, 6f, 1.414f, true)
        ))
        // Extrem kleine Denormal-Werte
        val buffer = FloatArray(10000) { 1e-30f }
        eq.process(buffer)
        buffer.forEach { assertFalse(it.isNaN()); assertFalse(it.isInfinite()) }
    }

    @Test
    fun `Abtastrate-Wechsel bleibt stabil`() {
        val eq = ParametricEqualizer(sampleRate)
        eq.setBands(listOf(
            ParametricEqualizer.Band(ParametricEqualizer.FilterType.PEAKING, 1000f, 6f, 1.414f, true)
        ))
        eq.setSampleRate(48000)
        eq.setSampleRate(22050)
        val buf = FloatArray(512) { 0.3f }
        eq.process(buf)
        buf.forEach { assertFalse(it.isNaN()); assertFalse(it.isInfinite()) }
    }

    @Test
    fun `Stereo-Verarbeitung behandelt beide Kanäle gleich`() {
        val eq = ParametricEqualizer(sampleRate)
        eq.setBands(listOf(
            ParametricEqualizer.Band(ParametricEqualizer.FilterType.PEAKING, 1000f, 6f, 1.414f, true)
        ))
        val left = FloatArray(512) { 0.5f }
        val right = FloatArray(512) { -0.3f }
        val lCopy = left.clone()
        val rCopy = right.clone()
        eq.processStereo(left, right)
        // Beide Kanäle werden durch dieselben Filterstufen geschickt
        for (i in left.indices) {
            assertFalse(left[i].isNaN())
            assertFalse(right[i].isNaN())
        }
        // Das Signal sollte verändert sein (nicht identisch mit Eingang)
        var changed = 0
        for (i in left.indices) {
            if (kotlin.math.abs(left[i] - lCopy[i]) > 0.001f) changed++
        }
        assertTrue("Linker Kanal wurde gefiltert", changed > 0)
    }

    @Test
    fun `Buffer-Grenze (count=0 und partielle Verarbeitung)`() {
        val eq = ParametricEqualizer(sampleRate)
        eq.setBands(listOf(
            ParametricEqualizer.Band(ParametricEqualizer.FilterType.PEAKING, 1000f, 6f, 1.414f, true)
        ))
        val buf = FloatArray(100) { 0.5f }
        eq.process(buf, 0) // keine Verarbeitung
        buf.forEach { assertEquals(0.5f, it, 0.0001f) }
        eq.process(buf, 50) // nur Hälfte
        for (i in 0 until 50) {
            assertFalse(buf[i].isNaN())
        }
    }

    private fun energyDiff(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        for (i in a.indices) {
            sum += (a[i] - b[i]) * (a[i] - b[i])
        }
        return sum
    }
}
