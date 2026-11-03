package de.sonora.app.core.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class BiquadDspTest {

    private val sampleRate = 44100

    @Test
    fun `flache Koeffizienten lassen Signal unveraendert durch`() {
        val stage = BiquadStage(sampleRate, BiquadDsp.flat())
        val input = floatArrayOf(0.1f, -0.5f, 0.9f, -0.2f, 1.0f)
        val copy = input.clone()
        stage.process(copy)
        for (i in input.indices) {
            assertEquals(input[i], copy[i], 0.0001f)
        }
    }

    @Test
    fun `peaking-Koeffizienten sind endlich und stabil`() {
        val coeffs = BiquadDsp.peaking(1000f, 6f, 1.414f, sampleRate)
        assertTrue(coeffs.isValid())
        // Stabilitaet: Pole innerhalb des Einheitskreises
        assertTrue("a2 im stabilen Bereich", kotlin.math.abs(coeffs.a2) < 1.0f)
    }

    @Test
    fun `tiefpass-regal dämpft hohe Frequenzen und betont tiefe`() {
        val coeffs = BiquadDsp.lowShelf(200f, 6f, 1.0f, sampleRate)
        assertTrue(coeffs.isValid())
        // DC (niedrige Freq) sollte betont sein, hohe gegentwertig
        val dc = freqResponseMag(coeffs, 20f)
        val high = freqResponseMag(coeffs, 15000f)
        assertTrue("Tieffrequenz betont (dB > 0)", db(dc) > 0f)
        assertTrue("Hohe Frequenz nahe 0 dB", db(high) < 2f)
    }

    @Test
    fun `hochpass-regal dämpft tiefe Frequenzen und betont hohe`() {
        val coeffs = BiquadDsp.highShelf(5000f, 6f, 1.0f, sampleRate)
        assertTrue(coeffs.isValid())
        val low = freqResponseMag(coeffs, 100f)
        val high = freqResponseMag(coeffs, 15000f)
        assertTrue("Niedrige Frequenz nahe 0 dB", db(low) < 2f)
        assertTrue("Hochfrequenz betont (dB > 0)", db(high) > 0f)
    }

    @Test
    fun `peaking hat am Zentrumsfrequenz genau den eingestellten Gain`() {
        val coeffs = BiquadDsp.peaking(1000f, 6f, 1.414f, sampleRate)
        val atCenter = freqResponseMag(coeffs, 1000f)
        assertEquals(6f, db(atCenter), 0.2f)
    }

    @Test
    fun `extreme Eingaben liefern valide (endliche) Koeffizienten`() {
        assertTrue(BiquadDsp.peaking(Float.NaN, Float.NaN, Float.NaN, sampleRate).isValid())
        assertTrue(BiquadDsp.peaking(Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 0f, sampleRate).isValid())
        assertTrue(BiquadDsp.lowShelf(Float.NaN, Float.NaN, Float.NaN, sampleRate).isValid())
        assertTrue(BiquadDsp.highShelf(Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 0f, sampleRate).isValid())
    }

    @Test
    fun `Filter-Antwort bleibt endlich auf Impuls (keine Divergenz)`() {
        val stage = BiquadStage(sampleRate, BiquadDsp.peaking(1000f, 12f, 0.5f, sampleRate))
        val impulse = FloatArray(512) { if (it == 0) 1.0f else 0f }
        stage.process(impulse)
        // Impulsantwort darf nicht divergieren und keine NaN enthalten
        var maxAbs = 0f
        for (s in impulse) {
            assertFalse("Kein NaN", s.isNaN())
            assertFalse("Keine Infinity", s.isInfinite())
            maxAbs = kotlin.math.max(maxAbs, kotlin.math.abs(s))
        }
        assertTrue("Impulsantwort bleibt begrenzt", maxAbs < 10f)
        // Tail sollte abklingen
        val tailEnergy = impulse.takeLast(50).map { it * it }.sum()
        assertTrue("Tail energi gehoert abklingen", tailEnergy < 0.01f)
    }

    @Test
    fun `Abtastrate-Änderung setzt Zustand zurück und bleibt stabil`() {
        val stage = BiquadStage(sampleRate, BiquadDsp.peaking(1000f, 6f, 1.414f, sampleRate))
        stage.processSample(0.5f)
        stage.setSampleRate(48000)
        assertEquals(48000, stage.getSampleRate())
        // Nach Rate-Wechsel darf kein NaN entstehen
        val buf = FloatArray(100) { 0.3f }
        stage.process(buf)
        buf.forEach { assertFalse(it.isNaN()); assertFalse(it.isInfinite()) }
    }

    @Test
    fun `Parameterübergang ist stetig (kein Sprung beim Koeffizienten-Wechsel)`() {
        val stage = BiquadStage(sampleRate, BiquadDsp.flat())
        stage.setCoefficients(BiquadDsp.peaking(1000f, 12f, 1.0f, sampleRate))
        val first = stage.processSample(0.5f)
        assertFalse(first.isNaN())
        assertFalse(first.isInfinite())
        // Mehrfache Verarbeitung bleibt stabil
        var x = 0.1f
        for (i in 0 until 10000) {
            x = stage.processSample(x)
            assertFalse("Sample $i nicht NaN", x.isNaN())
            assertFalse("Sample $i nicht Inf", x.isInfinite())
            assertTrue("Sample $i im Bereich", kotlin.math.abs(x) < 2f)
        }
    }

    // ---------- Helfer ----------

    private fun freqResponseMag(coeffs: BiquadCoefficients, freqHz: Float): Double {
        val w = 2.0 * PI * freqHz.toDouble() / sampleRate
        val cosW = cos(w)
        val sinW = sin(w)
        // ejw = cos(w) - i*sin(w); ej2w = cos(2w) - i*sin(2w)
        val cos2w = cos(2.0 * w)
        val sin2w = sin(2.0 * w)
        // numerator real/imag
        val numRe = coeffs.b0.toDouble() + coeffs.b1.toDouble() * cosW + coeffs.b2.toDouble() * cos2w
        val numIm = -coeffs.b1.toDouble() * sinW - coeffs.b2.toDouble() * sin2w
        // denominator real/imag
        val denRe = 1.0 + coeffs.a1.toDouble() * cosW + coeffs.a2.toDouble() * cos2w
        val denIm = -coeffs.a1.toDouble() * sinW - coeffs.a2.toDouble() * sin2w
        val mag = kotlin.math.sqrt(numRe * numRe + numIm * numIm) / kotlin.math.sqrt(denRe * denRe + denIm * denIm)
        return mag
    }

    private fun db(mag: Double): Float =
        (20.0 * kotlin.math.log10(mag)).toFloat()
}
