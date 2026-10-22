package de.sonora.app.core

import de.sonora.app.audio.dsp.HardwareDspAccelerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class DspSignalTest {

    private val sampleRate = 48000f

    // 1. Synthesize Test Signals
    private fun generateImpulse(length: Int = 256): FloatArray {
        val buffer = FloatArray(length)
        buffer[0] = 1.0f
        return buffer
    }

    private fun generateSine(freqHz: Float = 1000f, length: Int = 480): FloatArray {
        return FloatArray(length) { i ->
            sin(2.0 * PI * freqHz * i / sampleRate).toFloat()
        }
    }

    private fun generateStereoPanned(length: Int = 480): Pair<FloatArray, FloatArray> {
        val left = generateSine(1000f, length)
        val right = FloatArray(length) { 0f } // Complete silence on right channel
        return Pair(left, right)
    }

    @Test
    fun `Crossfeed erzeugt bei einseitigem Stereosignal nachweisbares Uebersprechen auf den Gegenkanal`() {
        val (leftIn, rightIn) = generateStereoPanned(480)
        val matrix = HardwareDspAccelerator.calculateCrossfeed(strength = 0.8f)

        assertTrue("Direct Gain muss positiv sein", matrix.directGain > 0.5f)
        assertTrue("Crossfeed Gain muss positiv sein", matrix.crossfeedGain > 0f)
        assertTrue("Delay muss mindestens 1 Sample betragen", matrix.delaySamples >= 1)

        // Process through Bauer crossfeed model
        val rightOut = FloatArray(leftIn.size)
        for (i in matrix.delaySamples until leftIn.size) {
            rightOut[i] = rightIn[i] * matrix.directGain + leftIn[i - matrix.delaySamples] * matrix.crossfeedGain
        }

        // Before crossfeed: right channel energy is 0
        val rightInEnergy = rightIn.map { it * it }.sum()
        assertEquals(0f, rightInEnergy, 0.00001f)

        // After crossfeed: right channel contains delayed left signal
        val rightOutEnergy = rightOut.map { it * it }.sum()
        assertTrue("Rechter Kanal muss nach Crossfeed hörbares Signal enthalten", rightOutEnergy > 0.01f)
    }

    @Test
    fun `Crossfeed und Stereo-Widening erzeugen bei identischem Eingangssignal unterschiedliche Ausgaben`() {
        val (leftIn, rightIn) = generateStereoPanned(256)

        // 1. Crossfeed output
        val cf = HardwareDspAccelerator.calculateCrossfeed(0.7f)
        val cfLeft = FloatArray(leftIn.size) { i ->
            val cross = if (i >= cf.delaySamples) rightIn[i - cf.delaySamples] * cf.crossfeedGain else 0f
            leftIn[i] * cf.directGain + cross
        }
        val cfRight = FloatArray(rightIn.size) { i ->
            val cross = if (i >= cf.delaySamples) leftIn[i - cf.delaySamples] * cf.crossfeedGain else 0f
            rightIn[i] * cf.directGain + cross
        }

        // 2. Widening output (Mid/Side matrix: L' = L + k*(L-R), R' = R - k*(L-R))
        val widenFactor = 0.6f
        val widenLeft = FloatArray(leftIn.size) { i -> leftIn[i] + widenFactor * (leftIn[i] - rightIn[i]) }
        val widenRight = FloatArray(rightIn.size) { i -> rightIn[i] - widenFactor * (leftIn[i] - rightIn[i]) }

        // Compare sample difference
        var diffLeft = 0f
        var diffRight = 0f
        for (i in leftIn.indices) {
            diffLeft += kotlin.math.abs(cfLeft[i] - widenLeft[i])
            diffRight += kotlin.math.abs(cfRight[i] - widenRight[i])
        }

        assertTrue("Crossfeed und Widening müssen auf dem linken Kanal unterschiedliche Ausgaben erzeugen", diffLeft > 1.0f)
        assertTrue("Crossfeed und Widening müssen auf dem rechten Kanal unterschiedliche Ausgaben erzeugen", diffRight > 1.0f)
    }

    @Test
    fun `Impulssignal durch Peaking Biquad Filter bleibt stabil ohne NaN oder Divergenz`() {
        val impulse = generateImpulse(128)
        val biquad = HardwareDspAccelerator.calculatePeakingBiquad(centerFreqHz = 1000f, gainDb = 6.0f, q = 1.414f)

        assertTrue(biquad.isValid())

        // Direct Form I biquad filter loop
        var x1 = 0f; var x2 = 0f
        var y1 = 0f; var y2 = 0f
        val output = FloatArray(impulse.size)

        for (i in impulse.indices) {
            val x0 = impulse[i]
            val y0 = biquad.b0 * x0 + biquad.b1 * x1 + biquad.b2 * x2 - biquad.a1 * y1 - biquad.a2 * y2
            output[i] = y0

            x2 = x1; x1 = x0
            y2 = y1; y1 = y0

            assertFalse("Filter-Ausgabe darf kein NaN sein", y0.isNaN())
            assertFalse("Filter-Ausgabe darf nicht unendlich sein", y0.isInfinite())
            assertTrue("Impulsantwort muss abklingen (unter Schranke 10.0)", kotlin.math.abs(y0) < 10.0f)
        }

        // Energy must decay towards the end
        val tailEnergy = output.takeLast(20).map { it * it }.sum()
        assertTrue("Impulsantwort muss im Tail abgeklungen sein", tailEnergy < 0.001f)
    }

    @Test
    fun `Biquad-Berechnung widersteht extremen Werten wie NaN und Infinity sicher`() {
        val nanBiquad = HardwareDspAccelerator.calculatePeakingBiquad(Float.NaN, Float.NaN, Float.NaN)
        assertTrue("Biquad mit NaN-Eingang muss valide Koeffizienten liefern", nanBiquad.isValid())

        val infBiquad = HardwareDspAccelerator.calculatePeakingBiquad(Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 0f)
        assertTrue("Biquad mit Infinity-Eingang muss valide Koeffizienten liefern", infBiquad.isValid())
    }

    @Test
    fun `Night Mode DRC berechnet wirksame Kompressionsparameter`() {
        val drc = HardwareDspAccelerator.calculateNightModeDrc(intensity = 0.8f)

        assertTrue("Threshold muss im Bereich -28 bis -12 dB liegen", drc.thresholdDb in -28f..-12f)
        assertTrue("Ratio muss mindestens 2:1 sein", drc.ratio >= 2.0f)
        assertTrue("Knee width muss positiv sein", drc.kneeWidthDb > 0f)
    }
}
