package de.sonora.app.ai.inference

/**
 * Austauschbare On-Device-Inferenz-Abstraktion.
 *
 * Jede Implementierung muss ihren Backend-Status ehrlich melden: Initialisierung,
 * Ausführung und Fehlgründe. "Unbekannt" darf nie als "verfügbar" erscheinen.
 */
interface AiInferenceEngine {
    val backendName: String

    fun initialize(): Boolean
    fun isReady(): Boolean
    fun infer(features: AudioFeatures): ModelResult
    fun release()

    fun backendStatus(): BackendStatus
}

data class AudioFeatures(
    val monoPcm16k: FloatArray,
    val sampleRate: Int = 16000,
    val frameCount: Int = 0,
    val rms: Float = 0f,
    val peak: Float = 0f,
)

data class ModelResult(
    val labels: List<String>,
    val scores: FloatArray,
    val inferenceTimeMs: Long,
    val backend: String,
)

data class InferenceMetrics(
    val p50Ms: Long = 0,
    val p95Ms: Long = 0,
    val memoryBytes: Long = 0,
    val cpuPercent: Float = 0f,
)

data class RecommendationConfidence(
    val value: Float,
    val reason: String,
)

data class BackendStatus(
    val supported: Boolean,
    val initializationSucceeded: Boolean,
    val executionVerified: Boolean,
    val failureReason: String?,
)