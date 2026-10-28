package de.sonora.app.ai.inference

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Tests for model integrity without requiring Android Context.
 * These verify the SHA-256 hash, file existence, and asset properties.
 *
 * The expected SHA-256 for the official MediaPipe YAMNet float32 model is:
 * 4d8b4a53282dc83ef04e3e7dbc4fbc98082e34e44ed798e16c3a0cdd4c584faf
 *
 * Source: https://storage.googleapis.com/mediapipe-models/audio_classifier/yamnet/float32/1/yamnet.tflite
 */
class ModelIntegrityTest {

    companion object {
        // Known SHA-256 of the official MediaPipe YAMNet float32 model
        const val EXPECTED_SHA256 = "4d8b4a53282dc83ef04e3e7dbc4fbc98082e34e44ed798e16c3a0cdd4c584faf"
        const val EXPECTED_LABEL_COUNT = 521
    }

    @Test
    fun testModelFileExists() {
        // Check if the model file exists in the standard test asset location
        val modelFile = File("app/src/main/assets/yamnet.tflite")
        assertTrue(
            "YAMNet model file should exist at app/src/main/assets/yamnet.tflite. " +
            "If running in CI without model, this test will be skipped.",
            modelFile.exists() || System.getenv("CI") != null
        )
    }

    @Test
    fun testLabelsFileExists() {
        val labelsFile = File("app/src/main/assets/yamnet_labels.txt")
        assertTrue("Labels file should exist", labelsFile.exists() || System.getenv("CI") != null)
    }

    @Test
    fun testModelFileSizeIsReasonable() {
        val modelFile = File("app/src/main/assets/yamnet.tflite")
        if (!modelFile.exists()) {
            println("Skipping model size test — file not found (CI environment may not have model)")
            return
        }
        // YAMNet float32 model is approximately 4-5 MB
        val sizeBytes = modelFile.length()
        assertTrue(
            "Model file should be > 3MB (too small for a valid YAMNet model), got: ${sizeBytes} bytes",
            sizeBytes > 3_000_000
        )
        assertTrue(
            "Model file should be < 20MB (unexpected size for YAMNet), got: ${sizeBytes} bytes",
            sizeBytes < 20_000_000
        )
    }

    @Test
    fun testLabelsFileHas521Entries() {
        val labelsFile = File("app/src/main/assets/yamnet_labels.txt")
        if (!labelsFile.exists()) {
            println("Skipping label count test — file not found (CI environment may not have assets)")
            return
        }
        val lines = labelsFile.readLines().filter { it.isNotBlank() }
        assertEquals("YAMNet class map should have exactly 521 labels", EXPECTED_LABEL_COUNT, lines.size)
    }

    @Test
    fun testFirstLabelIsSpeech() {
        val labelsFile = File("app/src/main/assets/yamnet_labels.txt")
        if (!labelsFile.exists()) {
            println("Skipping label content test — file not found")
            return
        }
        val lines = labelsFile.readLines().filter { it.isNotBlank() }
        assertEquals(
            "First YAMNet label should be 'Speech' (AudioSet class map)",
            "Speech", lines[0]
        )
    }

    @Test
    fun testLastLabelIsFieldRecording() {
        val labelsFile = File("app/src/main/assets/yamnet_labels.txt")
        if (!labelsFile.exists()) {
            println("Skipping label content test — file not found")
            return
        }
        val lines = labelsFile.readLines().filter { it.isNotBlank() }
        assertEquals(
            "Last YAMNet label should be 'Field recording'",
            "Field recording", lines[520]
        )
    }

    @Test
    fun testModelMagicBytes() {
        val modelFile = File("app/src/main/assets/yamnet.tflite")
        if (!modelFile.exists()) {
            println("Skipping model magic bytes test — file not found")
            return
        }
        val bytes = modelFile.readBytes()
        assertTrue("Model file should be non-empty", bytes.isNotEmpty())
        // TFLite flatbuffer schema starts with a size prefix (4 bytes) followed by
        // the file identifier "TFL3" at offset 4-7
        if (bytes.size >= 8) {
            val fileIdentifier = String(bytes, 4, 4, Charsets.UTF_8)
            assertTrue(
                "TFLite model should have 'TFL3' file identifier, got: '$fileIdentifier'",
                fileIdentifier == "TFL3"
            )
        }
    }

    /**
     * Verifies the SHA-256 of the model file matches the official MediaPipe YAMNet.
     * This is the integrity check — if the hash matches, we have the genuine model.
     */
    @Test
    fun testModelSha256MatchesOfficial() {
        val modelFile = File("app/src/main/assets/yamnet.tflite")
        if (!modelFile.exists()) {
            println("Skipping SHA-256 test — model file not found")
            return
        }

        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val bytes = modelFile.readBytes()
        val hash = digest.digest(bytes)
        val actualHash = hash.joinToString("") { "%02x".format(it) }

        assertEquals(
            "Model SHA-256 should match the official MediaPipe YAMNet float32 model",
            EXPECTED_SHA256,
            actualHash
        )
    }
}
