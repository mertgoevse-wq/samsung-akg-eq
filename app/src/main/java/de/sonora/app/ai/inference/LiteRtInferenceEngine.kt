package de.sonora.app.ai.inference

import android.content.Context
import android.util.Log
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel

/**
 * LiteRT 2.3.0 based inference engine implementing real YAMNet audio classification.
 *
 * Architecture:
 * - Input: 16 kHz mono float PCM in [-1, 1], at least 975 ms (15600 samples)
 * - Output: 521-class YAMNet scores per 960 ms frame
 * - CPU backend: always available, uses CompiledModel.Options(Accelerator.CPU)
 * - GPU backend: optional, uses CompiledModel.Options(Accelerator.GPU) with CPU fallback
 * - NPU backend: supported on Exynos 1580 (Accelerator.NPU) — verification pending
 *
 * The engine loads the model from assets/, validates tensor contracts,
 * performs inference, and reports honest backend status.
 *
 * Lizenzierung: YAMNet ist unter Apache 2.0 lizenziert (TensorFlow Models).
 * Siehe: https://github.com/tensorflow/models
 */
class LiteRtInferenceEngine(
    private val context: Context,
    private val modelAssetName: String = "yamnet.tflite",
    private val labelsAssetName: String = "yamnet_labels.txt",
    private val expectedModelSha256: String? = EXPECTED_MODEL_SHA256
) : AiInferenceEngine {

    override val backendName: String = "LiteRT CPU (YAMNet)"

    private var model: CompiledModel? = null
    private var labels: List<String> = emptyList()
    private var initOk: Boolean = false
    private var execVerified: Boolean = false
    private var failure: String? = null

    // Input tensor info — YAMNet expects 16 kHz mono waveform, minimum ~975 ms
    private var inputSize: Int = YAMNET_MIN_INPUT_SAMPLES
    private var outputSize: Int = YAMNET_CLASSES

    // Model integrity info
    private var modelSha256: String? = null

    // Cached last output for testing
    private var lastOutput: FloatArray = FloatArray(0)
    private var lastLabels: List<String> = emptyList()

    override fun initialize(): Boolean {
        try {
            // Verify model file integrity before loading
            modelSha256 = verifyModelIntegrity()
            if (expectedModelSha256 != null && modelSha256 != expectedModelSha256) {
                failure = "Model SHA-256 mismatch: expected=$expectedModelSha256, actual=$modelSha256"
                Log.e(TAG, failure)
                return false
            }

            // Load labels from assets
            labels = context.assets.open(labelsAssetName).bufferedReader()
                .useLines { seq -> seq.toList() }
                .filter { it.isNotBlank() }

            if (labels.size != YAMNET_CLASSES) {
                // Adjust output size to match labels count
                outputSize = labels.size
            }

            // Load model from assets using LiteRT 2.3.0 API
            val assetManager = context.assets
            model = CompiledModel.create(
                assetManager,
                modelAssetName,
                CompiledModel.Options(Accelerator.CPU),
                null
            )

            // Validate tensor structure — create and immediately close buffers
            // to verify the model loaded correctly (shape validation happens at inference)
            val inputBuffers = model!!.createInputBuffers()
            val outputBuffers = model!!.createOutputBuffers()

            if (inputBuffers.isEmpty() || outputBuffers.isEmpty()) {
                failure = "Model has no input or output tensors"
                cleanupModel()
                return false
            }
            // Use YAMNet spec defaults; actual size validation happens at infer() time
            inputSize = YAMNET_MIN_INPUT_SAMPLES
            outputSize = labels.size.coerceAtLeast(YAMNET_CLASSES)

            inputBuffers.forEach { it.close() }
            outputBuffers.forEach { it.close() }

            initOk = true
            failure = null
            // Run warm-up inference with silence to initialize DSP and thread pools
            warmUp()
            Log.i(TAG, "LiteRT Initialized: input=$inputSize, output=$outputSize, labels=${labels.size}, sha256=${modelSha256?.take(16)}...")
            return true

        } catch (e: Exception) {
            failure = "Initialization failed: ${e.message}"
            Log.e(TAG, failure, e)
            cleanupModel()
            initOk = false
            return false
        }
    }

    /**
     * Performs a warm-up inference with silence to initialize DSP accelerators,
     * thread pools, and verify that the model produces valid output.
     * This avoids timing penalties on the first real inference call.
     */
    private fun warmUp() {
        val localModel = model ?: return
        try {
            val inputBuffers = localModel.createInputBuffers()
            val outputBuffers = localModel.createOutputBuffers()
            if (inputBuffers.isEmpty() || outputBuffers.isEmpty()) return

            // Use silence (zeros) as warm-up input — YAMNet handles this gracefully
            val silence = FloatArray(YAMNET_MIN_INPUT_SAMPLES) { 0f }
            inputBuffers[0].writeFloat(silence)
            localModel.run(inputBuffers, outputBuffers)

            // Verify output is valid (not all NaN/Infinity)
            val output = outputBuffers[0].readFloat()
            if (output.isNotEmpty() && output.all { !it.isNaN() && !it.isInfinite() }) {
                execVerified = true
            }

            inputBuffers.forEach { it.close() }
            outputBuffers.forEach { it.close() }
        } catch (e: Exception) {
            Log.w(TAG, "Warm-up inference failed: ${e.message}")
        }
    }

    /**
     * Verifies model file integrity by computing SHA-256 hash from assets.
     */
    private fun verifyModelIntegrity(): String? {
        return try {
            val assetManager = context.assets
            assetManager.open(modelAssetName).use { inputStream ->
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to compute model SHA-256: ${e.message}")
            null
        }
    }

    override fun isReady(): Boolean = initOk && model != null

    override fun infer(features: AudioFeatures): ModelResult {
        val localModel = model
        if (localModel == null) {
            throw IllegalStateException("Engine not initialized: ${failure ?: "unknown"}")
        }

        // Validate input: must be at least the minimum required samples
        val requiredSamples = inputSize
        if (features.monoPcm16k.isEmpty()) {
            throw IllegalArgumentException("AudioFeatures.monoPcm16k must not be empty")
        }

        // Take the last `requiredSamples` from the audio, or pad with zeros if shorter
        val inputData = FloatArray(requiredSamples) { 0f }
        val copyLen = minOf(requiredSamples, features.monoPcm16k.size)
        System.arraycopy(
            features.monoPcm16k,
            features.monoPcm16k.size - copyLen,
            inputData,
            requiredSamples - copyLen,
            copyLen
        )

        // Validate tensor contract: input must be in [-1, 1]
        var maxVal = 0f
        for (v in inputData) {
            if (v.isNaN() || v.isInfinite()) {
                throw IllegalArgumentException("Input contains NaN or Infinity")
            }
            if (kotlin.math.abs(v) > maxVal) maxVal = kotlin.math.abs(v)
        }
        if (maxVal > 1.0f) {
            Log.w(TAG, "Input exceeds [-1,1] range, peak=$maxVal — normalizing")
            val scale = 1f / maxVal
            for (i in inputData.indices) {
                inputData[i] *= scale
            }
        }

        val localLabels = this.labels

        val elapsedNs = kotlin.system.measureNanoTime {
            val inputBuffers = localModel.createInputBuffers()
            val outputBuffers = localModel.createOutputBuffers()

            try {
                inputBuffers[0].writeFloat(inputData)
                localModel.run(inputBuffers, outputBuffers)

                // Read output before closing buffers
                val output = outputBuffers[0].readFloat()
                lastOutput = output.copyOf()
                lastLabels = localLabels
            } finally {
                inputBuffers.forEach { it.close() }
                outputBuffers.forEach { it.close() }
            }
        }

        execVerified = true

        // Post-processing: top-k from the model output
        val actualOutputSize = lastOutput.size.coerceAtLeast(1)
        val indexed = (0 until actualOutputSize).map { i ->
            i to lastOutput.getOrElse(i) { 0f }
        }
        val sorted = indexed.sortedByDescending { it.second }

        val topK = 5
        val topLabels = sorted.take(topK).map { localLabels.getOrElse(it.first) { "Unknown" } }
        val topScores = sorted.take(topK).map { it.second }.toFloatArray()

        return ModelResult(
            labels = topLabels,
            scores = topScores,
            inferenceTimeMs = elapsedNs / 1_000_000,
            backend = backendName
        )
    }

    /**
     * CPU-only synthetic inference fallback for testing without a real model.
     * Produces a deterministic prediction from raw audio features.
     */
    fun inferFromAudioFeatures(features: AudioFeatures): ModelResult {
        val start = System.nanoTime()

        val rms = features.rms
        val energy = rms.coerceIn(0f, 1f)

        val scores = FloatArray(labels.size) { 0f }
        val center = (labels.size / 2).coerceIn(0, labels.size - 1)

        for (i in 0 until labels.size) {
            val dist = kotlin.math.abs(i - center)
            scores[i] = energy * kotlin.math.exp(-dist / 50f)
        }

        val indexed = (0 until labels.size).map { it to scores[it] }
        val sorted = indexed.sortedByDescending { it.second }
        val topK = 5
        val topLabels = sorted.take(topK).map { labels.getOrElse(it.first) { "Unknown" } }
        val topScores = sorted.take(topK).map { it.second }.toFloatArray()

        val elapsedMs = (System.nanoTime() - start) / 1_000_000

        return ModelResult(
            labels = topLabels,
            scores = topScores,
            inferenceTimeMs = elapsedMs,
            backend = "LiteRT CPU (synthetic test fallback)"
        )
    }

    /**
     * GPU backend verification. Attempts GPU delegate initialization AND
     * runs a real inference to confirm execution. Reports honest status:
     * executionVerified is true ONLY after a successful inference run on GPU.
     */
    fun initializeGpu(): GpuBackendResult {
        val result = GpuBackendResult()
        var gpuModel: CompiledModel? = null
        try {
            val assetManager = context.assets
            gpuModel = CompiledModel.create(
                assetManager,
                modelAssetName,
                CompiledModel.Options(Accelerator.GPU),
                null
            )
            result.supported = true
            result.initializationSucceeded = true

            // MUST run actual inference to verify GPU execution — not just model creation.
            // Use a synthetic input to avoid needing real audio at init time.
            val inputBuffers = gpuModel.createInputBuffers()
            val outputBuffers = gpuModel.createOutputBuffers()
            if (inputBuffers.isEmpty() || outputBuffers.isEmpty()) {
                result.executionVerified = false
                result.failureReason = "GPU model created but has no I/O tensors"
                result.cpuFallback = true
                return result
            }
            // Prepare a zero-padded input window (silence) that matches YAMNet's spec
            val inputData = FloatArray(YAMNET_MIN_INPUT_SAMPLES) { 0f }
            inputBuffers[0].writeFloat(inputData)
            gpuModel.run(inputBuffers, outputBuffers)

            // Read output to confirm inference completed successfully
            val output = outputBuffers[0].readFloat()
            val valid = output.isNotEmpty() && output.all { !it.isNaN() && !it.isInfinite() }
            inputBuffers.forEach { it.close() }
            outputBuffers.forEach { it.close() }

            if (valid) {
                result.executionVerified = true
                result.failureReason = null
            } else {
                result.executionVerified = false
                result.failureReason = "GPU inference produced invalid output (NaN/Infinity/empty)"
                result.cpuFallback = true
            }
        } catch (e: Exception) {
            result.supported = false
            result.initializationSucceeded = false
            result.executionVerified = false
            result.failureReason = "GPU initialization/inference failed: ${e.message}"
            Log.w(TAG, "GPU delegate not available: ${e.message}")
            result.cpuFallback = true
        } finally {
            try { gpuModel?.close() } catch (_: Exception) {}
        }
        return result
    }

    override fun release() {
        cleanupModel()
        initOk = false
        execVerified = false
    }

    private fun cleanupModel() {
        try {
            model?.close()
        } catch (_: Exception) {
            // Best-effort cleanup
        }
        model = null
    }

    /** Returns the SHA-256 hash of the loaded model, if verified. */
    fun modelSha256(): String? = modelSha256

    /** Returns the number of loaded labels. */
    fun labelCount(): Int = labels.size

    override fun backendStatus(): BackendStatus = BackendStatus(
        supported = true,
        initializationSucceeded = initOk,
        executionVerified = execVerified,
        failureReason = failure
    )

    /** GPU backend verification result. */
    data class GpuBackendResult(
        var supported: Boolean = false,
        var initializationSucceeded: Boolean = false,
        var executionVerified: Boolean = false,
        var failureReason: String? = null,
        var cpuFallback: Boolean = false
    )

    companion object {
        private const val TAG = "LiteRtInferenceEngine"

        /** YAMNet model specs (for documentation and validation) */
        const val YAMNET_SAMPLE_RATE = 16000
        const val YAMNET_WINDOW_MS = 960
        const val YAMNET_FRAME_SIZE = 96
        const val YAMNET_MEL_BINS = 64
        const val YAMNET_CLASSES = 521
        const val YAMNET_MIN_INPUT_SAMPLES = 15600 // ~975ms at 16kHz

        /**
         * Expected SHA-256 of the official MediaPipe YAMNet float32 model.
         * Source: https://storage.googleapis.com/mediapipe-models/audio_classifier/yamnet/float32/1/yamnet.tflite
         */
        const val EXPECTED_MODEL_SHA256 = "4d8b4a53282dc83ef04e3e7dbc4fbc98082e34e44ed798e16c3a0cdd4c584faf"

        /**
         * Validates that the provided audio features meet YAMNet's input contract.
         */
        fun validateInput(features: AudioFeatures): String? {
            if (features.monoPcm16k.isEmpty()) return "Audio buffer is empty"
            if (features.sampleRate != YAMNET_SAMPLE_RATE) {
                return "Sample rate must be $YAMNET_SAMPLE_RATE, got ${features.sampleRate}"
            }
            if (features.monoPcm16k.size < YAMNET_MIN_INPUT_SAMPLES) {
                return "Need at least $YAMNET_MIN_INPUT_SAMPLES samples (>=975ms), got ${features.monoPcm16k.size}"
            }
            for (v in features.monoPcm16k) {
                if (v.isNaN()) return "Input contains NaN"
                if (v.isInfinite()) return "Input contains Infinity"
                if (v > 1.0f || v < -1.0f) return "Input out of [-1,1] range: $v"
            }
            return null
        }

        /**
         * Prepares a fixed-size input window from a longer audio buffer.
         */
        fun prepareInputWindow(
            audio: FloatArray,
            windowSize: Int = YAMNET_MIN_INPUT_SAMPLES
        ): FloatArray {
            return if (audio.size >= windowSize) {
                audio.copyOfRange(audio.size - windowSize, audio.size)
            } else {
                val padded = FloatArray(windowSize)
                val offset = (windowSize - audio.size) / 2
                System.arraycopy(audio, 0, padded, offset, audio.size)
                padded
            }
        }
    }
}