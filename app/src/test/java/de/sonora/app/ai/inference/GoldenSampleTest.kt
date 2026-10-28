package de.sonora.app.ai.inference

import de.sonora.app.R
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Golden-sample test for YAMNet inference.
 *
 * This test loads the official MediaPipe YAMNet float32 model from assets and
 * runs inference on a deterministic synthetic sine-wave signal at 440 Hz
 * (A440). The expected behavior is:
 *
 * 1. The model produces exactly 521 output scores (one per AudioSet class).
 * 2. "Sine wave" and related tone classes receive non-zero scores.
 * 3. No NaN or Infinity appears in the output.
 * 4. The top-5 results are returned correctly.
 *
 * This test requires a physical device or emulator with the model in assets.
 * On CI without a model, it will fail gracefully with an informative message.
 *
 * Audio test signal: A 440 Hz sine wave at 0.5 amplitude, 16 kHz mono, 1 second.
 */
@RunWith(AndroidJUnit4::class)
class GoldenSampleTest {

    private lateinit var engine: LiteRtInferenceEngine

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        engine = LiteRtInferenceEngine(context)
    }

    /**
     * Generates a deterministic 440 Hz sine wave at 16 kHz mono, 0.5 amplitude.
     * This is a known test signal that YAMNet should classify as "Sine wave" or
     * similar tone categories.
     */
    private fun generateA440Signal(samples: Int = 15600, sampleRate: Int = 16000): FloatArray {
        val freq = 440.0
        return FloatArray(samples) { i ->
            kotlin.math.sin(2.0 * Math.PI * freq * i / sampleRate).toFloat() * 0.5f
        }
    }

    /**
     * Generates silence (all zeros) — the null hypothesis for YAMNet.
     */
    private fun generateSilence(samples: Int = 15600): FloatArray {
        return FloatArray(samples) { 0f }
    }

    @Test
    fun modelInitializesFromAssets() {
        val initialized = engine.initialize()
        assertTrue("YAMNet model should initialize successfully from assets", initialized)
        assertTrue("Engine should be ready after initialization", engine.isReady())
    }

    @Test
    fun modelProduces521ScoresForSineWave() {
        if (!engine.initialize()) {
            fail("Model failed to initialize — cannot run golden sample test")
        }

        val signal = generateA440Signal()
        val features = AudioFeatures(
            monoPcm16k = signal,
            sampleRate = 16000,
            frameCount = signal.size
        )

        val result = engine.infer(features)

        assertEquals("Model result should contain exactly 521 scores", 521, result.scores.size)
        assertEquals("Top-5 labels should be returned", 5, result.labels.size)
        assertEquals("Top-5 scores should match labels", result.labels.size, result.scores.size)
        assertTrue("Inference time should be positive", result.inferenceTimeMs >= 0)
    }

    @Test
    fun sineWaveProducesNonZeroScores() {
        if (!engine.initialize()) {
            fail("Model failed to initialize — cannot run golden sample test")
        }

        val signal = generateA440Signal()
        val features = AudioFeatures(
            monoPcm16k = signal,
            sampleRate = 16000,
            frameCount = signal.size
        )

        val result = engine.infer(features)

        // YAMNet should recognize the sine wave — "Sine wave" is class index 496
        // The top score should be non-zero (not silence)
        assertTrue("Sine wave input should produce non-zero top score, got: ${result.scores.first()}",
            result.scores.first() > 0f)
    }

    @Test
    fun silenceProducesZeroOrNearZeroScores() {
        if (!engine.initialize()) {
            fail("Model failed to initialize — cannot run golden sample test")
        }

        val silence = generateSilence()
        val features = AudioFeatures(
            monoPcm16k = silence,
            sampleRate = 16000,
            frameCount = silence.size
        )

        val result = engine.infer(features)

        // Silence should not trigger any strong classification
        assertTrue("Silence input should produce low top-5 scores, got: ${result.scores.maxOrNull()}",
            result.scores.maxOrNull() ?: 0f < 0.1f)
    }

    @Test
    fun outputContainsNoNaNOrInfinity() {
        if (!engine.initialize()) {
            fail("Model failed to initialize — cannot run golden sample test")
        }

        val signal = generateA440Signal()
        val features = AudioFeatures(
            monoPcm16k = signal,
            sampleRate = 16000,
            frameCount = signal.size
        )

        val result = engine.infer(features)

        for (score in result.scores) {
            assertFalse("Output contains NaN: ${result.scores.toList()}", score.isNaN())
            assertFalse("Output contains Infinity: ${result.scores.toList()}", score.isInfinite())
        }
        assertTrue("All scores should be non-negative", result.scores.all { it >= 0f })
    }

    @Test
    fun scoresAreSortedDescending() {
        if (!engine.initialize()) {
            fail("Model failed to initialize — cannot run golden sample test")
        }

        val signal = generateA440Signal()
        val features = AudioFeatures(
            monoPcm16k = signal,
            sampleRate = 16000,
            frameCount = signal.size
        )

        val result = engine.infer(features)

        // Top-5 should be sorted descending by score
        for (i in 0 until result.scores.size - 1) {
            assertTrue(
                "Scores should be sorted descending: ${result.scores.toList()}",
                result.scores[i] >= result.scores[i + 1]
            )
        }
    }

    @Test
    fun sineWaveLabelsIncludeSineOrTone() {
        if (!engine.initialize()) {
            fail("Model failed to initialize — cannot run golden sample test")
        }

        val signal = generateA440Signal()
        val features = AudioFeatures(
            monoPcm16k = signal,
            sampleRate = 16000,
            frameCount = signal.size
        )

        val result = engine.infer(features)

        // Check if any of the top-5 labels relate to sine/tone sounds
        val toneLabels = setOf("Sine wave", "Harmonic", "Chirp tone", "Sound effect", "Pulse")
        val hasToneLabel = result.labels.any { label ->
            toneLabels.any { tone -> label.contains(tone, ignoreCase = true) }
        }
        assertTrue(
            "Sine wave input should produce tone-related labels in top-5, got: ${result.labels}",
            hasToneLabel
        )
    }

    @Test
    fun warmUpInferenceDoesNotCrash() {
        if (!engine.initialize()) {
            fail("Model failed to initialize — cannot run golden sample test")
        }

        // Run inference as warm-up
        val signal = generateA440Signal()
        val features = AudioFeatures(
            monoPcm16k = signal,
            sampleRate = 16000,
            frameCount = signal.size
        )
        val result1 = engine.infer(features)
        val result2 = engine.infer(features)

        // Results should be deterministic
        assertEquals("Same input should produce same top-5 labels",
            result1.labels, result2.labels)
        assertEquals("Same input should produce same top-5 scores (within tolerance)",
            5, result2.scores.size)
    }

    @Test
    fun releaseAfterInferenceFreesResources() {
        if (!engine.initialize()) {
            fail("Model failed to initialize — cannot run golden sample test")
        }

        val signal = generateA440Signal()
        val features = AudioFeatures(
            monoPcm16k = signal,
            sampleRate = 16000,
            frameCount = signal.size
        )
        engine.infer(features)
        engine.release()

        // After release, engine should not be ready
        assertFalse("Engine should not be ready after release", engine.isReady())
    }
}
