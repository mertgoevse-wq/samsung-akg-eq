package de.sonora.app.ai.inference

import org.junit.Assert.*
import org.junit.Test

class PcmPreprocessorTest {

    @Test
    fun monoPassthrough() {
        val input = floatArrayOf(0.1f, 0.2f, 0.3f)
        val out = PcmPreprocessor.toMono(input, 1)
        assertEquals(input.size, out.size)
        assertArrayEquals(input, out, 1e-6f)
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
        assertArrayEquals(input, out, 1e-6f)
    }

    @Test
    fun resampleDownsampleHalvesLength() {
        // 44100 -> 16000: roughly half the samples for a 1-second signal
        val input = FloatArray(44100) { i ->
            kotlin.math.sin(2.0 * Math.PI * 440 * i / 44100).toFloat() * 0.5f
        }
        val out = PcmPreprocessor.resample(input, 44100, 16000)
        // Expected length ~ 44100 / (44100/16000) = 16000
        assertEquals(16000, out.size)
        // All output values must remain within [-1, 1]
        assertTrue(out.all { v -> v >= -1f && v <= 1f })
    }

    @Test
    fun resampleUpsampleIncreasesLength() {
        val input = floatArrayOf(1f, 2f, 3f)
        val out = PcmPreprocessor.resample(input, 8000, 16000)
        assertTrue(out.size > input.size)
    }

    @Test
    fun normalizeDoesNotPeakNormalize() {
        // normalize() must NOT amplify quiet signals to peak 1.0.
        val input = floatArrayOf(0.1f, -0.05f, 0.2f)
        val out = PcmPreprocessor.normalize(input)
        // Values stay unchanged (clamp is identity for already-in-range input)
        assertArrayEquals(input, out, 1e-6f)
        // Crucially: peak is NOT scaled to 1.0 (that would be peak normalization).
        assertNotEquals(1f, out[0], 1e-6f)
    }

    @Test
    fun normalizeClampsOutOfRangeWithoutDistortingInRange() {
        val input = floatArrayOf(1.5f, -2.0f, 0.3f, -0.3f)
        val out = PcmPreprocessor.normalize(input)
        assertEquals(1f, out[0], 1e-6f)       // clamped
        assertEquals(-1f, out[1], 1e-6f)      // clamped
        assertEquals(0.3f, out[2], 1e-6f)     // unchanged
        assertEquals(-0.3f, out[3], 1e-6f)    // unchanged
    }

    @Test
    fun clampToUnitRangeKeepsInRangeValues() {
        val input = floatArrayOf(0.5f, -0.25f, 0f)
        val out = PcmPreprocessor.clampToUnitRange(input)
        assertArrayEquals(input, out, 1e-6f)
    }

    @Test
    fun clampToUnitRangeClampsOutOfRange() {
        val input = floatArrayOf(2.0f, -3.0f, 1.0f, -1.0f)
        val out = PcmPreprocessor.clampToUnitRange(input)
        assertEquals(1f, out[0], 1e-6f)
        assertEquals(-1f, out[1], 1e-6f)
        assertEquals(1f, out[2], 1e-6f)
        assertEquals(-1f, out[3], 1e-6f)
    }

    @Test
    fun prepareForYamnetProducesClampedMono16k() {
        // Stereo 44.1 kHz signal -> mono 16 kHz, clamped to [-1, 1]
        val stereo = FloatArray(44100 * 2) { i ->
            kotlin.math.sin(2.0 * Math.PI * 440 * i / 44100).toFloat() * 0.5f
        }
        val out = PcmPreprocessor.prepareForYamnet(stereo, channels = 2, fromRate = 44100)
        assertEquals(16000, out.size)
        assertTrue(out.all { v -> v >= -1f && v <= 1f })
    }

    @Test
    fun prepareForYamnetNoPeakNormalization() {
        // Quiet signal must stay quiet (no amplification to peak 1.0).
        val quiet = floatArrayOf(0.01f, -0.02f, 0.005f)
        val out = PcmPreprocessor.prepareForYamnet(quiet, channels = 1, fromRate = 16000)
        assertArrayEquals(quiet, out, 1e-6f)
    }

    @Test
    fun emptyInputReturnsEmpty() {
        assertEquals(0, PcmPreprocessor.toMono(floatArrayOf(), 1).size)
        assertEquals(0, PcmPreprocessor.resample(floatArrayOf(), 44100, 16000).size)
        assertEquals(0, PcmPreprocessor.normalize(floatArrayOf()).size)
        assertEquals(0, PcmPreprocessor.clampToUnitRange(floatArrayOf()).size)
    }
}
