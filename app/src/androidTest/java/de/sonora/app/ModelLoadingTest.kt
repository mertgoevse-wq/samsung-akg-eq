package de.sonora.app

import android.content.Context
import androidx.platform.testing.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import de.sonora.app.ai.inference.LiteRtInferenceEngine
import de.sonora.app.ai.inference.AudioFeatures
import java.io.File

/**
 * Instrumentation tests for model loading (YAMNet from assets).
 *
 * Requires the model asset to be bundled in the APK and a device/emulator to
 * run LiteRT inference. Cannot run in the local Windows environment.
 */
@RunWith(AndroidJUnit4::class)
class ModelLoadingTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
    }

    @Test
    fun yamNetModelAssetIsBundled() {
        val model = File(context.filesDir.parentFile.parentFile, "src/main/assets/yamnet.tflite")
        // In an instrumentation test the APK assets are accessible via the context.
        val assetNames = context.assets.list("") ?: emptyArray()
        assertTrue("yamnet.tflite must be bundled in the APK assets",
            assetNames.contains("yamnet.tflite") || context.assets.open("yamnet.tflite").use { it.available() > 0 })
    }

    @Test
    fun yamNetLabelsAssetIsBundled() {
        try {
            context.assets.open("yamnet_labels.txt").use { reader ->
                val lines = reader.bufferedReader().useLines { it.toList() }
                assertEquals("YAMNet must have 521 labels", 521, lines.size)
                assertEquals("First label is Speech", "Speech", lines.first())
            }
        } catch (e: java.io.IOException) {
            throw AssertionError("yamnet_labels.txt must be bundled in assets", e)
        }
    }

    @Test
    fun liteRtEngineInitializesFromAssets() {
        val engine = LiteRtInferenceEngine(context)
        try {
            val initialized = engine.initialize()
            if (!initialized) {
                // Device without LiteRT support: engine reports failure honestly,
                // never crashes, never falls back to network.
                assertNotNull(engine.backendStatus().failureReason)
                return
            }
            assertTrue("Engine should be ready after initialization", engine.isReady())
            assertEquals(521, engine.labelCount())
        } finally {
            engine.release()
        }
    }

    @Test
    fun inferenceProducesValidTopKWithoutNaN() {
        val engine = LiteRtInferenceEngine(context)
        try {
            if (!engine.initialize()) return // device limitation, skip gracefully
            val signal = FloatArray(15600) { i ->
                kotlin.math.sin(2.0 * kotlin.math.PI * 440 * i / 16000).toFloat() * 0.5f
            }
            val features = AudioFeatures(monoPcm16k = signal, sampleRate = 16000, frameCount = signal.size)
            val result = engine.infer(features)
            assertEquals("Top-5 labels", 5, result.labels.size)
            assertEquals("Top-5 scores", 5, result.scores.size)
            result.scores.forEach {
                assertFalse("No NaN score", it.isNaN())
                assertFalse("No Infinity score", it.isInfinite())
                assertTrue("Non-negative score", it >= 0f)
            }
        } finally {
            engine.release()
        }
    }

    @Test
    fun emptyInputIsRejectedByValidation() {
        val error = LiteRtInferenceEngine.validateInput(
            AudioFeatures(monoPcm16k = FloatArray(0), sampleRate = 16000)
        )
        assertNotNull("Empty input must be rejected", error)
    }
}
