package de.sonora.app.ai.inference

import org.junit.Assert.assertEquals
import org.junit.Test

class PcmPreprocessorTest {
    @Test
    fun monoPassthrough() {
        val input = floatArrayOf(0.1f, 0.2f, 0.3f)
        val out = PcmPreprocessor.toMono(input, 1)
        assertEquals(input.size, out.size)
    }

    @Test
    fun stereoToMonoAverages() {
        val input = floatArrayOf(1f, -1f, 0.5f, 0.5f)
        val out = PcmPreprocessor.toMono(input, 2)
        assertEquals(2, out.size)
        assertEquals(0f, out[0], 1e-6f)
        assertEquals(0.5f, out[1], 1e-6f)
    }

    @Test
    fun resampleSameRateCopies() {
        val input = floatArrayOf(1f, 2f, 3f)
        val out = PcmPreprocessor.resample(input, 16000, 16000)
        assertEquals(input.size, out.size)
    }

    @Test
    fun normalizeScalesToPeak() {
        val input = floatArrayOf(0.5f, -0.25f)
        val out = PcmPreprocessor.normalize(input)
        assertEquals(1f, out[0], 1e-6f)
        assertEquals(-0.5f, out[1], 1e-6f)
    }
}
