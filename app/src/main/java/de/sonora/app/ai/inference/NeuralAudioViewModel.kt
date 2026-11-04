package de.sonora.app.ai.inference

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.sonora.app.R
import de.sonora.app.ai.inference.LiteRtInferenceEngine.Companion.YAMNET_CLASSES
import de.sonora.app.ai.inference.LiteRtInferenceEngine.Companion.YAMNET_FRAME_SIZE
import de.sonora.app.ai.inference.LiteRtInferenceEngine.Companion.YAMNET_MIN_INPUT_SAMPLES
import de.sonora.app.ai.inference.LiteRtInferenceEngine.Companion.YAMNET_MEL_BINS
import de.sonora.app.ai.inference.LiteRtInferenceEngine.Companion.YAMNET_SAMPLE_RATE
import de.sonora.app.audio.SoundLabEngine
import de.sonora.app.core.BandCount
import de.sonora.app.core.EqCurve
import de.sonora.app.data.SoundProfile
import de.sonora.app.hardware.HardwareCapabilityRepository
import de.sonora.app.ai.inference.NeuralAudioState
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
    private val soundLab = SoundLabEngine(sampleRate = 44100)

    // Thread confinement: inference runs on background dispatcher
    private val inferenceScope = viewModelScope

    // Prevent overlapping inference calls
    private val inferenceMutex = Mutex()
    private var inferenceJob: Job? = null

    // State
    private var _classificationActive by mutableStateOf(false)
    private var _demoMode by mutableStateOf(true)
    private var _backendCapabilities by mutableStateOf(HardwareCapabilities())
    private var _modelInfo by mutableStateOf(ModelInfo())
    private var _topResults by mutableStateOf<List<Pair<String, Float>>>(emptyList())
    private var _audioActivityLevel by mutableStateOf(0f)
    private var _inferenceTimeMs by mutableStateOf(0L)
    private var _statusMessage by mutableStateOf<String?>(null)

    // Ring buffer for accumulating audio samples
    private val audioBuffer = RingBuffer(YAMNET_MIN_INPUT_SAMPLES * 4)

    // Latency tracking
    private val latencyHistory = mutableListOf<Long>()

    /** Model and backend status info for diagnostics. */
    data class HardwareCapabilities(
        val cpu: BackendCapabilityInfo = BackendCapabilityInfo("CPU", true, false, false),
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
            // Hardware-Capabilities ehrlich aus dem Repository beziehen.
            // CPU ist strukturell verfügbar; NPU wird als unbestätigt gemeldet.
            val cpuCap = hardwareRepo.cpu()
            val npuCap = hardwareRepo.npu()

            val success = withContext(Dispatchers.IO) {
                engine.initialize()
            }

            if (success) {
                val engineStatus = engine.backendStatus()
                _modelInfo = ModelInfo(
                    loaded = true,
                    sha256 = engine.modelSha256()?.take(16) ?: "unbekannt",
                    labelCount = engine.labelCount(),
                    inputSize = YAMNET_MIN_INPUT_SAMPLES,
                    outputSize = YAMNET_CLASSES
                )
                // CPU gilt erst als produktiv, wenn der Warm-Up-Lauf eine echte
                // Inferenz erfolgreich absolviert hat (engineStatus.executionVerified).
                _backendCapabilities = _backendCapabilities.copy(
                    cpu = BackendCapabilityInfo(
                        name = "LiteRT CPU (YAMNet)",
                        supported = cpuCap.supported,
                        initializationSucceeded = engineStatus.initializationSucceeded,
                        executionVerified = engineStatus.executionVerified
                    ),
                    npu = BackendCapabilityInfo(
                        name = npuCap.name,
                        supported = npuCap.supported,
                        initializationSucceeded = npuCap.initializationSucceeded,
                        executionVerified = npuCap.executionVerified
                    )
                )
                if (!engineStatus.executionVerified) {
                    _statusMessage =
                        "Modell geladen, aber CPU-Inferenz konnte nicht verifiziert werden: ${engineStatus.failureReason ?: "unbekannt"}"
                }
            } else {
                _modelInfo = ModelInfo(loaded = false, sha256 = "")
                _statusMessage = "Modell konnte nicht initialisiert werden"
                _backendCapabilities = _backendCapabilities.copy(
                    cpu = BackendCapabilityInfo(
                        name = "LiteRT CPU (YAMNet)",
                        supported = cpuCap.supported,
                        initializationSucceeded = false,
                        executionVerified = false
                    ),
                    npu = BackendCapabilityInfo(
                        name = npuCap.name,
                        supported = npuCap.supported,
                        initializationSucceeded = npuCap.initializationSucceeded,
                        executionVerified = npuCap.executionVerified
                    )
                )
            }

            // Verify GPU and NPU (runs actual inference — executionVerified only after a successful run)
            verifyGpuBackend()
            verifyNpuBackend()
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
     * NPU verification runs an actual inference to confirm execution.
     * Only when executionVerified is true does NPU count as operational.
     */
    private fun verifyNpuBackend() {
        inferenceScope.launch(Dispatchers.IO) {
            try {
                val npuResult = engine.initializeNpu()
                _backendCapabilities = _backendCapabilities.copy(
                    npu = BackendCapabilityInfo(
                        name = "NPU (Exynos AI LiteCore / NNAPI)",
                        supported = npuResult.supported,
                        initializationSucceeded = npuResult.initializationSucceeded,
                        executionVerified = npuResult.executionVerified
                    )
                )
            } catch (e: Exception) {
                Log.w("NeuralAudioViewModel", "NPU verification failed: ${e.message}")
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
                    // Use real source sample rate of SoundLabEngine, then resample to YAMNet 16kHz
                    val mono = PcmPreprocessor.toMono(samples, 1)
                    val resampled = PcmPreprocessor.resample(mono, soundLab.sampleRate, YAMNET_SAMPLE_RATE)
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

    /** Returns latency percentiles for diagnostic display. Deterministic p50/p95. */
    fun latencyPercentiles(): Pair<Long, Long> {
        if (latencyHistory.isEmpty()) return 0L to 0L
        val sorted = latencyHistory.sorted()
        val n = sorted.size
        fun percentile(p: Double): Long {
            if (n == 1) return sorted[0]
            val rank = p * (n - 1)
            val lo = kotlin.math.floor(rank).toInt().coerceIn(0, n - 1)
            val hi = kotlin.math.ceil(rank).toInt().coerceIn(0, n - 1)
            if (lo == hi) return sorted[lo]
            val frac = rank - lo
            return (sorted[lo] + (sorted[hi] - sorted[lo]) * frac).toLong()
        }
        return percentile(0.5) to percentile(0.95)
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
