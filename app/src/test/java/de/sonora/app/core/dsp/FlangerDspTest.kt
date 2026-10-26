package de.sonora.app.core.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class FlangerDspTest {

    @Test
    fun `stille am Eingang erzeugt Stille am Ausgang`() {
        val flanger = FlangerDsp(sampleRate = 44100)
        val silence = FloatArray(512) { 0f }
        flanger.process(silence)
        silence.forEach { assertEquals(0f, it, 0.0001f) }
    }

    @Test
    fun `mix 0 Prozent laesst Eingangssignal unveraendert durch`() {
        val flanger = FlangerDsp(sampleRate = 44100, initialMix = 0f)
        val input = floatArrayOf(0.1f, 0.5f, -0.3f, 0.8f, -0.9f)
        val copy = input.clone()
        flanger.process(copy)
        for (i in input.indices) {
            assertEquals(input[i], copy[i], 0.0001f)
        }
    }

    @Test
    fun `setParametersFromXy haelt alle Parameter in sicheren Grenzen`() {
        val flanger = FlangerDsp(sampleRate = 44100)

        // Ecken des Touchfelds pruefen
        val testPoints = listOf(
            0f to 0f,
            1f to 1f,
            0.5f to 0.5f,
            -1f to 2f // Unzulaessige Extrema
        )

        for ((x, y) in testPoints) {
            flanger.setParametersFromXy(x, y)
            assertTrue(flanger.targetRateHz in 0.1f..5.0f)
            assertTrue(flanger.targetDepth in 0f..1f)
            assertTrue(flanger.targetFeedback in -FlangerDsp.MAX_FEEDBACK..FlangerDsp.MAX_FEEDBACK)
            assertTrue(flanger.targetMix in 0f..1f)
        }
    }

    @Test
    fun `hohe Amplitude und Feedback fuehren nicht zu Blowup oder NaN`() {
        val flanger = FlangerDsp(
            sampleRate = 44100,
            initialFeedback = 0.85f,
            initialDepth = 1.0f,
            initialMix = 0.5f
        )
        // 1 Sekunde starker Impulszug
        val testBuffer = FloatArray(44100) { i ->
            if (i % 100 == 0) 1.0f else 0.0f
        }
        flanger.process(testBuffer)

        testBuffer.forEach { sample ->
            assertFalse("Sample darf nicht NaN sein", sample.isNaN())
            assertFalse("Sample darf nicht Infinite sein", sample.isInfinite())
            assertTrue("Sample muss gedaempft sein (|x| <= 1.5)", kotlin.math.abs(sample) <= 1.5f)
        }
    }

    @Test
    fun `sinussignal wird durch Flanger moduliert`() {
        val flanger = FlangerDsp(
            sampleRate = 44100,
            initialRateHz = 1.0f,
            initialDepth = 0.8f,
            initialFeedback = 0.5f,
            initialMix = 0.5f
        )
        // 440 Hz Sinus
        val count = 2048
        val original = FloatArray(count) { i ->
            sin(2.0 * Math.PI * 440.0 * i / 44100.0).toFloat()
        }
        val processed = original.clone()
        flanger.process(processed)

        // Nach Anlaufzeit des Delay-Puffers muessen Effektausgang und Eingang sich unterscheiden
        var diffSum = 0f
        for (i in 500 until count) {
            diffSum += kotlin.math.abs(processed[i] - original[i])
        }
        assertTrue("Flanger muss das Audiosignal merklich veraendern", diffSum > 10f)
    }

    @Test
    fun `parameterglaettung laeuft stetig ohne Spruenge`() {
        val flanger = FlangerDsp(sampleRate = 44100, initialRateHz = 0.5f)
        flanger.setParameters(rateHz = 4.0f) // Sprunghafte Sollwert-Aenderung

        // Nach einem einzelnen Sample darf currentRateHz sich nur minimal bewegt haben
        flanger.processSample(0f)
        val firstStep = flanger.currentRateHz
        assertTrue("Erster Schritt muss stetig sein", firstStep > 0.5f && firstStep < 0.6f)

        // Nach vielen Samples muss der Zielwert erreicht werden
        for (i in 0 until 5000) {
            flanger.processSample(0f)
        }
        assertEquals(4.0f, flanger.currentRateHz, 0.05f)
    }
}
