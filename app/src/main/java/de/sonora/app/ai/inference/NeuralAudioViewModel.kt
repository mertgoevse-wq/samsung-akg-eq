package de.sonora.app.ai.inference

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.sonora.app.R
import de.sonora.app.audio.SoundLabEngine
import de.sonora.app.core.BandCount
import de.sonora.app.core.EqCurve
import de.sonora.app.data.SoundProfile
import de.sonora.app.hardware.HardwareCapabilityRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.sin
import kotlin.system.measureNanoTime

/**
 * ViewModel for Neural Audio Lab.
 *
 * Orchestrates:
 * - LiteRT YAMNet inference on a dedicated background thread
 * - PCM pipeline: demo signal generation or live capture (future)
 * - Adaptive audio intelligence: combines YAMNet labels with RMS/spectral features
 * - Backend capability verification (CPU/GPU/NPU)
 * - Honest model status reporting (load, version, SHA-256)
 */
class NeuralAudioViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = LiteRtInferenceEngine(application)
    private val hardwareRepo = HardwareCapabilityRepository(application)
    private val soundLab = SoundLabEngine(sampleRate = 16000)

    // Thread confinement: inference runs on background dispatcher
    private val inferenceScope = viewModelScope

    // Prevent overlapping inference calls
    private val inferenceMutex = Mutex()
    private var inferenceJob: Job? = null

    // State
    @Volatile
    private var _classificationActive = false
    @Volatile
    private var _demoMode = true
    @Volatile
    private var _backendCapabilities: HardwareCapabilities = HardwareCapabilities()
    @Volatile
    private var _modelInfo: ModelInfo = ModelInfo()
    @Volatile
    private var _topResults: List<Pair<String, Float>> = emptyList()
    @Volatile
    private var _audioActivityLevel: Float = 0f
    @Volatile
    private var _inferenceTimeMs: Long = 0L
    @Volatile
    private var _statusMessage: String? = null

    // Ring buffer for accumulating audio samples
    private val audioBuffer = RingBuffer(YAMNET_MIN_INPUT_SAMPLES * 4)

    // Latency tracking
    private val latencyHistory = mutableListOf<Long>()

    /** Model and backend status info for diagnostics. */
    data class HardwareCapabilities(
        val cpu: BackendCapabilityInfo = BackendCapabilityInfo("CPU", true, true, true),
        val gpu: BackendCapabilityInfo = BackendCapabilityInfo("GPU (LiteRT GPU)", false, false, false),
        val npu: BackendCapabilityInfo = BackendCapabilityInfo("NPU (Exynos AI)", false, false, false)
    )

    data class BackendCapabilityInfo(
        val name: String,
        val supported: Boolean,
        val initializationSucceeded: Boolean,
        val executionVerified: Boolean
    )

    data class ModelInfo(
        val loaded: Boolean = false,
        val version: String = "YAMNet float32 v1",
        val sha256: String = "",
        val labelCount: Int = 0,
        val inputSize: Int = 0,
        val outputSize: Int = 0
    )

    /** Public state for UI */
    val uiState: NeuralAudioState
        get() = NeuralAudioState(
            isActive = _classificationActive,
            isDemoMode = _demoMode,
            backendName = _backendCapabilities.currentBackend(),
            inferenceTimeMs = _inferenceTimeMs,
            topResults = _topResults,
            audioActivityLevel = _audioActivityLevel,
            statusMessage = _statusMessage
        )

    init {
        initializeModel()
    }

    /**
     * Initializes the LiteRT engine and verifies model integrity.
     * This runs the warm-up inference and model hash verification.
     */
    private fun initializeModel() {
        inferenceScope.launch {
            val success = withContext(Dispatchers.IO) {
                engine.initialize()
            }

            if (success) {
                _modelInfo = ModelInfo(
                    loaded = true,
                    sha256 = engine.modelSha256()?.take(16) ?: "unbekannt",
                    labelCount = engine.labelCount(),
                    inputSize = YAMNET_MIN_INPUT_SAMPLES,
                    outputSize = YAMNET_CLASSES
                )
                _backendCapabilities = _backendCapabilities.copy(
                    cpu = BackendCapabilityInfo("CPU", true, true, true)
                )
            } else {
                _modelInfo = ModelInfo(loaded = false, sha256 = "")
                _statusMessage = "Modell konnte nicht initialisiert werden"
            }

            // Verify GPU (runs actual inference)
            verifyGpuBackend()
        }
    }

    /**
     * GPU verification runs an actual inference to confirm execution.
     * Only when executionVerified is true does GPU count as operational.
     */
    private fun verifyGpuBackend() {
        inferenceScope.launch(Dispatchers.IO) {
            try {
                val gpuResult = engine.initializeGpu()
                _backendCapabilities = _backendCapabilities.copy(
                    gpu = BackendCapabilityInfo(
                        name = "GPU (LiteRT GPU)",
                        supported = gpuResult.supported,
                        initializationSucceeded = gpuResult.initializationSucceeded,
                        executionVerified = gpuResult.executionVerified
                    )
                )
            } catch (e: Exception) {
                Log.w("NeuralAudioViewModel", "GPU verification failed: ${e.message}")
            }
        }
    }

    /**
     * HardwareCapabilities.currentBackend returns the name of the actually
     * verified backend, preferring GPU only if executionVerified is true.
     */
    private fun HardwareCapabilities.currentBackend(): String {
        if (gpu.executionVerified) return gpu.name
        if (npu.executionVerified) return npu.name
        return "LiteRT CPU (YAMNet)"
    }

    /** Toggles classification on/off. */
    fun toggleClassification() {
        if (!_classificationActive) {
            startClassification()
        } else {
            stopClassification()
        }
    }

    /** Toggles between demo mode (local signal) and live capture (future). */
    fun toggleDemoMode() {
        if (_demoMode) {
            // Switching from demo to live — stop sound lab
            soundLab.stop()
        } else {
            // Switching to demo mode — start sound lab
            soundLab.start()
        }
        _demoMode = !_demoMode
    }

    private fun startClassification() {
        if (_modelInfo.loaded && !_classificationActive) {
            _classificationActive = true
            _statusMessage = null

            if (_demoMode) {
                soundLab.onSamplesGenerated = { samples ->
                    // Convert 44.1kHz to 16kHz and normalize to [-1,1]
                    val mono = PcmPreprocessor.toMono(samples, 1)
                    val resampled = PcmPreprocessor.resample(mono, 44100, 16000)
                    val clamped = PcmPreprocessor.clampToUnitRange(resampled)

                    // Calculate audio activity level (RMS-based)
                    var sumSq = 0.0
                    for (v in clamped) sumSq += v * v
                    val rms = kotlin.math.sqrt(sumSq / maxOf(clamped.size, 1))
                    _audioActivityLevel = rms.toFloat().coerceIn(0f, 1f)

                    // Write to ring buffer
                    audioBuffer.write(clamped)

                    // Run inference if we have enough samples
                    if (audioBuffer.available() >= YAMNET_MIN_INPUT_SAMPLES) {
                        val window = audioBuffer.readLatest(YAMNET_MIN_INPUT_SAMPLES)
                        runInference(window)
                    }
                }
                soundLab.start()
            } else {
                // Live mode would use MediaProjection / AudioPlaybackCapture
                // For now, fall back to demo mode
                _statusMessage = "Live-Aufnahme erfordert Berechtigung"
                _demoMode = true
                _classificationActive = false
            }
        }
    }

    private fun stopClassification() {
        _classificationActive = false
        if (_demoMode) {
            soundLab.stop()
        }
    }

    /**
     * Runs YAMNet inference on the background thread.
     * Thread-confined: only one inference at a time via mutex.
     */
    private fun runInference(audioWindow: FloatArray) {
        if (!_classificationActive) return

        inferenceJob?.cancel()
        inferenceJob = inferenceScope.launch(Dispatchers.IO) {
            inferenceMutex.withLock {
                val features = AudioFeatures(
                    monoPcm16k = audioWindow,
                    sampleRate = YAMNET_SAMPLE_RATE,
                    frameCount = audioWindow.size
                )

                val elapsedNs = measureNanoTime {
                    try {
                        val result = engine.infer(features)
                        _topResults = result.labels.zip(result.scores.toList())
                        _inferenceTimeMs = result.inferenceTimeMs

                        // Track latency
                        if (latencyHistory.size >= 100) latencyHistory.removeAt(0)
                        latencyHistory.add(result.inferenceTimeMs)
                    } catch (e: Exception) {
                        Log.w("NeuralAudioViewModel", "Inference error: ${e.message}")
                        _statusMessage = "Inferenzfehler: ${e.message}"
                    }
                }
            }
        }
    }

    /**
     * Generates an adaptive audio intelligence recommendation based on
     * YAMNet classification, RMS levels, and spectral features.
     *
     * This is a suggestion engine only — it does NOT modify EQ automatically.
     */
    fun generateAdaptiveSuggestion(
        currentProfile: SoundProfile,
        rmsDb: Float?
    ): AdaptiveSuggestion? {
        val results = _topResults
        if (results.isEmpty()) return null

        val topLabel = results.first().first.lowercase()
        val topScore = results.first().second

        // Rule-based suggestion combining YAMNet label with audio features
        val levels = Array(10) { 0f }

        // Music genre detection
        when {
            topLabel.contains("speech") || topLabel.contains("conversation") -> {
                // Speech: boost mid frequencies for intelligibility
                levels[4] += 3f  // 500 Hz
                levels[5] += 2f  // 1 kHz
                levels[6] += 1f  // 2 kHz
            }
            topLabel.contains("music") -> {
                // Generic music: balanced enhancement with bass lift
                levels[0] += 2f  // 31 Hz
                levels[1] += 1.5f // 62 Hz
                levels[8] += 1f  // 8 kHz for air
            }
            topLabel.contains("rock") || topLabel.contains("metal") -> {
                levels[0] += 4f  // Heavy bass
                levels[5] += 2f  // Vocals
                levels[6] += 3f  // Presence
                levels[8] += 2f  // Cymbals
            }
            topLabel.contains("electronic") || topLabel.contains("techno") -> {
                levels[0] += 4f
                levels[1] += 3f
                levels[6] += 2f
                levels[7] += 3f
                levels[8] += 2f
            }
            topLabel.contains("classical") -> {
                levels[6] += 2f
                levels[7] += 2f
                levels[8] += 3f
                levels[9] += 3f
            }
            topLabel.contains("speech") -> {
                levels[5] += 3f
                levels[6] += 3f
            }
            else -> {
                // Ambient/environmental: gentle enhancement
                levels[8] += 1f
                levels[9] += 1f
            }
        }

        // Environment noise adaptation
        rmsDb?.let { dbfs ->
            when {
                dbfs < -50f -> {
                    // Very quiet — gentle lift
                    for (i in 4..8) levels[i] += 1.5f
                }
                dbfs > -25f -> {
                    // Loud — reduce bass to avoid muddiness
                    levels[0] -= 2f
                    levels[1] -= 1.5f
                }
            }
        }

        // Clamp and ensure headroom
        val safeLevels = levels.map { it.coerceIn(-6f, 6f) }
        val maxBoost = safeLevels.maxOrNull()?.coerceAtLeast(0f) ?: 0f
        val preampDb = if (maxBoost > 1.5f) (-maxBoost * 0.6f).coerceIn(-6f, 0f) else 0f

        // Validate
        val curve = EqCurve(BandCount.TEN, safeLevels)
        val isValid = curve.levelsDb.all { !it.isNaN() && !it.isInfinite() &&
            it >= -12f && it <= 12f }

        if (!isValid) return null

        return AdaptiveSuggestion(
            profileName = "Adaptive: ${results.first().first}",
            levelsDb = safeLevels,
            preampDb = preampDb,
            confidence = topScore,
            sourceLabels = results.take(3).map { it.first },
            explanation = buildString {
                append("Basierend auf YAMNet-Klassifikation: ")
                append(results.take(3).joinToString(", ") { "${it.first} (${(it.second * 100).toInt()}%)" })
                append(". RMS: ${rmsDb?.let { "%.0f dBFS".format(it) } ?: "nicht verfügbar"}.")
            }
        )
    }

    data class AdaptiveSuggestion(
        val profileName: String,
        val levelsDb: List<Float>,
        val preampDb: Float,
        val confidence: Float,
        val sourceLabels: List<String>,
        val explanation: String
    ) {
        fun toSoundProfile(): SoundProfile = SoundProfile(
            id = java.util.UUID.randomUUID().toString(),
            name = profileName,
            levels = levelsDb,
            preset = null,
            bandCount = "10",
            preampDb = preampDb,
            origin = "Neural Audio Intelligence (YAMNet)",
            uncertainty = "Konfidenz: ${(confidence * 100).toInt()} %",
            boundaryLimits = "Geprüft: Headroom und Bereichsbegrenzung eingehalten",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }

    /** Returns latency percentiles for diagnostic display. */
    fun latencyPercentiles(): Pair<Long, Long> {
        if (latencyHistory.isEmpty()) return 0L to 0L
        val sorted = latencyHistory.sorted()
        val p50 = sorted[sorted.size * 0.5 / 100.coerceAtLeast(1)]
        val p95Index = (sorted.size * 0.95).toInt().coerceAtMost(sorted.size - 1)
        return sorted[sorted.size / 2] to sorted[p95Index]
    }

    /** Returns model diagnostic info. */
    fun modelDiagnostics(): ModelInfo = _modelInfo

    /** Returns hardware capabilities. */
    fun hardwareCapabilities(): HardwareCapabilities = _backendCapabilities

    /** Public API for UI integration */
    // Indicates whether the model is loaded and ready for inference
    val neuralAudioReady: Boolean get() = _modelInfo.loaded

    // Provides initialization error message if model failed to load
    val neuralAudioInitError: String?
        get() = if (!_modelInfo.loaded) _statusMessage else null

    // Wrapper methods matching UI expectations
    fun startNeuralClassification() {
        if (!_classificationActive) startClassification()
    }

    fun stopNeuralClassification() {
        if (_classificationActive) stopClassification()
    }

    fun toggleNeuralDemoMode() {
        toggleDemoMode()
    }

    override fun onCleared() {
        super.onCleared()
        _classificationActive = false
        inferenceJob?.cancel()
        soundLab.stop()
        engine.release()
    }
}
