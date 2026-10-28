package de.sonora.app

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.media.AudioManager
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.sonora.app.ai.AiAdviceRequest
import de.sonora.app.ai.AiAdviceResult
import de.sonora.app.ai.AiSuggestion
import de.sonora.app.ai.HearingPreference
import de.sonora.app.ai.LocalRuleAiProvider
import de.sonora.app.ai.inference.NeuralAudioState
import de.sonora.app.audio.AudioDeviceSnapshot
import de.sonora.app.audio.AudioRoutingManager
import de.sonora.app.audio.EngineStatus
import de.sonora.app.audio.EnvironmentAnalyzer
import de.sonora.app.audio.EnvironmentReading
import de.sonora.app.audio.SoundLabEngine
import de.sonora.app.visualizer.FftReducer
import de.sonora.app.ui.visualizer.ParticleMode
import de.sonora.app.audio.MediaWatcher
import de.sonora.app.audio.SonoraEngine
import de.sonora.app.audio.dsp.DspHardwareState
import de.sonora.app.audio.dsp.HardwareDspAccelerator
import de.sonora.app.core.BandCount
import de.sonora.app.core.EqCurve
import de.sonora.app.core.SpatialMode
import de.sonora.app.core.SpatialModeCatalog
import de.sonora.app.core.SpatialParams
import de.sonora.app.core.InterviewAnswer
import de.sonora.app.core.InterviewCatalog
import de.sonora.app.core.InterviewEvaluator
import de.sonora.app.core.DiagnosticEvent
import de.sonora.app.core.EventLog
import de.sonora.app.core.EventNames
import de.sonora.app.core.MediaPermission
import de.sonora.app.core.MediaRule
import de.sonora.app.core.NowPlaying
import de.sonora.app.core.VolumeGuard
import de.sonora.app.core.VolumeState
import de.sonora.app.core.VOLUME_LOUD_SINCE_KEY
import de.sonora.app.core.VOLUME_REMINDER_ACKNOWLEDGED_KEY
import de.sonora.app.core.VOLUME_REMINDER_ENABLED_KEY
import de.sonora.app.core.VOLUME_REMINDER_NOTIFICATION_ID
import de.sonora.app.core.VOLUME_REMINDER_SENT_KEY
import de.sonora.app.core.VOLUME_REMINDER_THRESHOLD_PERCENT
import de.sonora.app.core.BlindTest
import de.sonora.app.data.DiagnosticsStore
import de.sonora.app.data.Presets
import de.sonora.app.data.ProfileFile
import de.sonora.app.data.ProfileStore
import de.sonora.app.data.SoundProfile
import de.sonora.app.service.SonoraService
import kotlinx.coroutines.launch
import java.util.UUID

@Immutable
data class SonoraUiState(
    val onboardingDone: Boolean = false,
    val engineStatus: EngineStatus = EngineStatus.Inactive,
    val masterEnabled: Boolean = true,
    val bypassActive: Boolean = false,
    val bandCount: BandCount = BandCount.TEN,
    val eqCurve: EqCurve = EqCurve(),
    val levelsDb: List<Float> = eqCurve.levelsDb,
    val preampDb: Float = 0f,
    val clipWarning: Boolean = false,
    val spatialMode: SpatialMode? = null,
    val spatialIntensity: Float = 0.7f,
    val spatialAvailable: Boolean = false,
    val spatialAvailabilityMessage: String? = null,
    val audioRouting: AudioDeviceSnapshot = AudioDeviceSnapshot(),
    val hardwareState: DspHardwareState = HardwareDspAccelerator.getHardwareState(),
    val aiSuggestion: AiSuggestion? = null,
    val aiLoading: Boolean = false,
    val aiPreferences: HearingPreference = HearingPreference(),
    val interviewActive: Boolean = false,
    val interviewIndex: Int = 0,
    val interviewAnswers: List<InterviewAnswer> = emptyList(),
    val interviewPreview: EqCurve? = null,
    val diagnosticEvents: List<DiagnosticEvent> = emptyList(),
    val volumeState: VolumeState = VolumeState(0, false, 0),
    val volumeReminderEnabled: Boolean = true,
    val nowPlaying: NowPlaying? = null,
    val mediaEnabled: Boolean = false,
    val mediaPermissionGranted: Boolean = false,
    val mediaRequested: Boolean = false,
    val blindTestActive: Boolean = false,
    val blindTestRound: Int = 0,
    val blindTestResults: List<Int> = emptyList(),
    val blindTestOriginalResults: List<Int> = emptyList(),
    val blindTestPresented: Boolean = false,
    val blindTestReady: Boolean = false,
    val blindTestRatingA: Int = 5,
    val blindTestRatingB: Int = 5,
    val blindTestFeedbackSaved: Boolean = false,
    val blindTestMessage: String? = null,
    val profiles: List<SoundProfile> = emptyList(),
    val activeProfileId: String = "neutral",
    val activeProfileName: String = "Neutral",
    val environment: EnvironmentReading? = null,
    val playbackWaveform: List<Float> = emptyList(),
    val playbackWaveformAvailable: Boolean = false,
    val playbackVisualizationEnabled: Boolean = false,
    val playbackSpectrum: List<Float> = emptyList(),
    val particleMode: de.sonora.app.ui.visualizer.ParticleMode = de.sonora.app.ui.visualizer.ParticleMode.CONSTELLATION,
    val soundLabActive: Boolean = false,
    val soundLabSignal: de.sonora.app.audio.SoundLabEngine.SignalType = de.sonora.app.audio.SoundLabEngine.SignalType.WARM_SYNTH_CHORD,
    val flangerRateHz: Float = 0.5f,
    val flangerDepth: Float = 0.7f,
    val flangerFeedback: Float = 0.6f,
    val autoAdjust: Boolean = false,
    val dirty: Boolean = false,
    val message: String? = null
)

class SonoraViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val MASTER_ENABLED_KEY = "master_enabled"
        const val BLIND_TEST_SEGMENT_MS = 3_000L
        const val BLIND_TEST_PLAYBACK_CHECK_MS = 500L
        const val VOLUME_REFRESH_INTERVAL_MS = 60_000L
    }

    private val store = ProfileStore(application)
    private val diagnosticsStore = DiagnosticsStore(application)
    private val eventLog = EventLog().also { it.replaceAll(diagnosticsStore.load()) }
    private val volumeGuard = VolumeGuard()
    private var blindTest = BlindTest()
    private val safetyPreferences = application.getSharedPreferences("sonora_safety_v1", 0)
    private val settingsPreferences = application.getSharedPreferences("sonora_settings_v1", 0)
    private val routingManager = AudioRoutingManager(application) { snapshot ->
        mainHandler.post {
            uiState = uiState.copy(audioRouting = snapshot)
        }
    }
    private val localAiProvider = LocalRuleAiProvider()
    private var blindTestSessionId: Int = 0
    private var blindTestSecondSegment: Runnable? = null
    private var blindTestFinishSegment: Runnable? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val volumeRefresh = object : Runnable {
        override fun run() {
            refreshVolumeState()
            mainHandler.postDelayed(this, VOLUME_REFRESH_INTERVAL_MS)
        }
    }
    private val blindTestPlaybackMonitor = object : Runnable {
        override fun run() {
            if (!uiState.blindTestActive || !uiState.blindTestPresented) return
            if (!playbackIsActive()) {
                cancelBlindTestPlayback()
                uiState = uiState.copy(blindTestMessage = "Die Wiedergabe wurde unterbrochen. Diese Runde wurde verworfen.")
                return
            }
            mainHandler.postDelayed(this, BLIND_TEST_PLAYBACK_CHECK_MS)
        }
    }
    private var analyzer: EnvironmentAnalyzer? = null
    private var playbackVisualizer: android.media.audiofx.Visualizer? = null
    private var playbackVisualizationVisible = false
    private var playbackVisualizerSessionId: Int? = null
    private var lastSmoothedFftBands: FloatArray? = null
    private var mediaWatcher: MediaWatcher? = null
    private var interviewStartedElapsedMs: Long? = null
    private var bypassed = false

    var uiState by mutableStateOf(SonoraUiState())
        private set

    init {
        routingManager.register()
        val file = store.load()
        val active = file.profiles.firstOrNull { it.id == file.activeProfileId }
            ?: file.profiles.firstOrNull()
            ?: Presets.defaults.first()
        val status = if (SonoraEngine.status is EngineStatus.Active) {
            SonoraEngine.status
        } else {
            SonoraEngine.initialize()
        }
        uiState = uiState.copy(
            onboardingDone = store.onboarded(),
            engineStatus = status,
            masterEnabled = settingsPreferences.getBoolean(MASTER_ENABLED_KEY, true),
            profiles = file.profiles,
            activeProfileId = active.id,
            activeProfileName = active.name,
            preampDb = active.preampDb,
            bandCount = profileBandCount(active),
            eqCurve = EqCurve(profileBandCount(active), active.levelsFor(profileBandCount(active))),
            levelsDb = active.levelsFor(profileBandCount(active)),
            audioRouting = routingManager.currentSnapshot,
            hardwareState = HardwareDspAccelerator.getHardwareState()
        )
        recordEvent(EventNames.APP_OPENED, mapOf("version" to de.sonora.app.BuildConfig.VERSION_NAME, "android_version" to android.os.Build.VERSION.RELEASE))
        refreshVolumeState()
        mainHandler.postDelayed(volumeRefresh, VOLUME_REFRESH_INTERVAL_MS)
        uiState = uiState.copy(message = store.consumeMigrationNotice())
        applyEffective()
    }

    fun setMasterEnabled(enabled: Boolean) {
        if (!enabled && uiState.blindTestActive) cancelBlindTest()
        if (!enabled && bypassed) endBypass()
        settingsPreferences.edit().putBoolean(MASTER_ENABLED_KEY, enabled).apply()
        uiState = uiState.copy(masterEnabled = enabled)
        applyEffective()
    }

    fun setBand(index: Int, db: Float) {
        if (index !in uiState.eqCurve.levelsDb.indices) return
        val levels = uiState.eqCurve.levelsDb.toMutableList()
        levels[index] = db.coerceIn(-12f, 12f)
        val curve = EqCurve(uiState.bandCount, levels).normalized()
        uiState = uiState.copy(eqCurve = curve, levelsDb = curve.levelsDb, dirty = true)
        applyEffective()
    }

    fun setBandCount(count: BandCount) {
        val curve = uiState.eqCurve.withBandCount(count)
        uiState = uiState.copy(
            bandCount = count,
            eqCurve = curve,
            levelsDb = curve.levelsDb,
            dirty = true
        )
        applyEffective()
    }

    fun setPreamp(db: Float) {
        uiState = uiState.copy(preampDb = db.coerceIn(-12f, 12f), dirty = true)
        applyEffective()
    }

    fun setSpatialMode(mode: SpatialMode?) {
        uiState = uiState.copy(spatialMode = mode)
        applySpatial()
        recordEvent(EventNames.SPATIAL_MODE_CHANGED, mapOf("mode_id" to (mode?.id ?: "off"), "enabled" to (mode != null).toString()))
        persist()
    }

    fun startInterview() {
        interviewStartedElapsedMs = SystemClock.elapsedRealtime()
        uiState = uiState.copy(interviewActive = true, interviewIndex = 0, interviewAnswers = emptyList(), interviewPreview = null)
    }

    fun answerInterview(questionId: String, optionId: String) {
        val answers = uiState.interviewAnswers.filterNot { it.questionId == questionId } + InterviewAnswer(questionId, optionId)
        advanceInterview(answers)
    }

    fun skipInterviewQuestion(questionId: String) {
        advanceInterview(uiState.interviewAnswers.filterNot { it.questionId == questionId })
    }

    private fun advanceInterview(answers: List<InterviewAnswer>) {
        val next = uiState.interviewIndex + 1
        uiState = if (next >= InterviewCatalog.questions.size) {
            uiState.copy(interviewIndex = next, interviewAnswers = answers, interviewPreview = InterviewEvaluator.evaluate(answers))
        } else {
            uiState.copy(interviewIndex = next, interviewAnswers = answers)
        }
    }

    fun previewInterview() {
        val curve = uiState.interviewPreview ?: InterviewEvaluator.evaluate(uiState.interviewAnswers)
        uiState = uiState.copy(eqCurve = curve, bandCount = curve.bandCount, levelsDb = curve.levelsDb)
        applyEffective()
    }

    fun finishInterview() {
        val skippedQuestions = InterviewCatalog.questions.size - uiState.interviewAnswers.size
        val durationSeconds = interviewStartedElapsedMs?.let { ((SystemClock.elapsedRealtime() - it).coerceAtLeast(0L) / 1_000L).toString() } ?: "0"
        interviewStartedElapsedMs = null
        val curve = uiState.interviewPreview ?: InterviewEvaluator.evaluate(uiState.interviewAnswers)
        val profile = SoundProfile(
            id = UUID.randomUUID().toString(),
            name = "Mein Profil",
            levels = curve.levelsDb,
            preset = null,
            bandCount = curve.bandCount.size.toString()
        )
        uiState = uiState.copy(
            profiles = uiState.profiles + profile,
            activeProfileId = profile.id,
            activeProfileName = profile.name,
            eqCurve = curve,
            bandCount = curve.bandCount,
            levelsDb = curve.levelsDb,
            dirty = false,
            interviewActive = false,
            interviewIndex = 0,
            interviewAnswers = emptyList(),
            interviewPreview = null,
            message = "Dein Profil ist gespeichert und aktiv."
        )
        applyEffective()
        recordEvent(EventNames.INTERVIEW_COMPLETED, mapOf(
            "duration_s" to durationSeconds,
            "skipped_questions" to skippedQuestions.toString(),
            "model_version" to "local-rules-v1"
        ))
        persist()
    }

    fun cancelInterview() {
        interviewStartedElapsedMs = null
        uiState = uiState.copy(interviewActive = false, interviewIndex = 0, interviewAnswers = emptyList(), interviewPreview = null)
    }

    fun setSpatialIntensity(value: Float) {
        uiState = uiState.copy(spatialIntensity = value.coerceIn(0f, 1f))
        applySpatial()
    }

    fun applyPreset(profile: SoundProfile, source: String = "manual") {
        val startedAt = SystemClock.elapsedRealtime()
        val eventName = if (profile.preset != null) EventNames.PRESET_SELECTED else EventNames.PROFILE_APPLIED
        uiState = uiState.copy(
            activeProfileId = profile.id,
            activeProfileName = profile.name,
            eqCurve = EqCurve(profileBandCount(profile), profile.levelsFor(profileBandCount(profile)))
                .withBandCount(uiState.bandCount),
            levelsDb = EqCurve(profileBandCount(profile), profile.levelsFor(profileBandCount(profile)))
                .withBandCount(uiState.bandCount).levelsDb,
            dirty = false
        )
        applyEffective()
        val eventProperties = if (eventName == EventNames.PRESET_SELECTED) {
            mapOf("preset_id" to profile.id, "source" to source)
        } else {
            mapOf("profile_id" to profile.id, "source" to source, "latency_ms" to (SystemClock.elapsedRealtime() - startedAt).toString())
        }
        recordEvent(eventName, eventProperties)
        persist()
    }

    fun selectProfile(id: String) {
        val profile = uiState.profiles.firstOrNull { it.id == id } ?: return
        applyPreset(profile)
    }

    fun setAutoAdjust(enabled: Boolean) {
        if (uiState.autoAdjust == enabled && (enabled || analyzer == null)) return
        if (enabled && !hasMicPermission()) {
            uiState = uiState.copy(
                message = "Mikrofon ist nicht erlaubt. Du kannst es in den Einstellungen freigeben."
            )
            return
        }
        uiState = uiState.copy(
            autoAdjust = enabled,
            environment = if (enabled) uiState.environment else null
        )
        if (enabled) startAnalyzer() else stopAnalyzer()
        applyEffective()
    }

    fun saveCurrentAs(name: String) {
        val clean = name.trim().take(60).ifBlank { "Mein Profil" }
        val profile = SoundProfile(
            id = UUID.randomUUID().toString(),
            name = clean,
            levels = uiState.eqCurve.levelsDb,
            preset = null,
            bandCount = uiState.bandCount.size.toString()
        )
        uiState = uiState.copy(
            profiles = uiState.profiles + profile,
            activeProfileId = profile.id,
            activeProfileName = profile.name,
            dirty = false,
            message = "Profil „$clean“ gespeichert."
        )
        persist()
    }

    fun deleteProfile(id: String) {
        if (id == "flat") return
        val remaining = uiState.profiles.filterNot { it.id == id }
        if (remaining.isEmpty()) return
        val active = if (uiState.activeProfileId == id) {
            remaining.first()
        } else {
            remaining.firstOrNull { it.id == uiState.activeProfileId } ?: remaining.first()
        }
        uiState = uiState.copy(
            profiles = remaining,
            activeProfileId = active.id,
            activeProfileName = active.name,            eqCurve = EqCurve(profileBandCount(active), active.levelsFor(profileBandCount(active))).withBandCount(uiState.bandCount),
            levelsDb = EqCurve(profileBandCount(active), active.levelsFor(profileBandCount(active))).withBandCount(uiState.bandCount).levelsDb,
            dirty = false,
            message = "Profil gelöscht."
        )
        applyEffective()
        persist()
    }

    fun resetLevels() {
        val curve = EqCurve(uiState.bandCount, List(uiState.bandCount.size) { 0f })
        uiState = uiState.copy(eqCurve = curve, levelsDb = curve.levelsDb, dirty = true)
        applyEffective()
    }

    fun completeOnboarding() {
        store.setOnboarded(true)
        uiState = uiState.copy(onboardingDone = true)
    }

    fun retryEngine() {
        val status = SonoraEngine.initialize()
        uiState = uiState.copy(engineStatus = status)
        applyEffective()
        if (status is EngineStatus.Active && uiState.masterEnabled) {
            applyPreset(uiState.profiles.firstOrNull { it.id == uiState.activeProfileId } ?: return, source = "recovered")
        }
    }

    fun beginBypass() {
        if (!uiState.masterEnabled || (uiState.blindTestActive && !uiState.blindTestPresented)) return
        if (bypassed) return
        bypassed = true
        SonoraEngine.setEnabled(false)
        uiState = uiState.copy(bypassActive = true, spatialAvailable = false, spatialAvailabilityMessage = null)
    }

    fun endBypass() {
        if (!bypassed) return
        bypassed = false
        uiState = uiState.copy(bypassActive = false)
        applyEffective()
    }

    fun startService() {
        ContextCompat.startForegroundService(
            getApplication(),
            Intent(getApplication(), SonoraService::class.java)
        )
        uiState = uiState.copy(message = "Hintergrunddienst gestartet.")
    }

    fun stopService() {
        if (uiState.blindTestActive) cancelBlindTest()
        if (bypassed) endBypass()
        getApplication<Application>().stopService(
            Intent(getApplication(), SonoraService::class.java)
        )
        uiState = uiState.copy(
            engineStatus = EngineStatus.Inactive,
            message = "Hintergrunddienst beendet."
        )
    }

    fun clearMessage() {
        uiState = uiState.copy(message = null)
    }

    fun exportProfile(id: String): String? {
        val raw = store.exportProfile(id) ?: return null
        uiState = uiState.copy(message = "Profil exportiert. Der JSON-Text kann jetzt geteilt werden.")
        recordEvent(EventNames.PROFILE_EXPORTED, mapOf(
            "action" to "export",
            "format_version" to de.sonora.app.core.PROFILE_SCHEMA_VERSION.toString(),
            "success" to "true"
        ))
        return raw
    }

    fun importProfile(raw: String): Boolean {
        val result = store.importProfile(raw)
        uiState = uiState.copy(profiles = result.allProfiles, message = result.message)
        recordEvent(EventNames.PROFILE_EXPORTED, mapOf(
            "action" to "import",
            "format_version" to de.sonora.app.core.PROFILE_SCHEMA_VERSION.toString(),
            "success" to result.success.toString()
        ))
        return result.success
    }

    private fun startAnalyzer() {
        if (analyzer != null) return
        analyzer = EnvironmentAnalyzer { reading ->
            mainHandler.post {
                val previousLevel = uiState.environment?.level
                uiState = uiState.copy(environment = reading)
                if (reading.level != previousLevel) {
                    recordEvent(EventNames.ENVIRONMENT_ADAPTED, mapOf("noise_class" to reading.level.toString()))
                    applyEffective()
                }
            }
        }.also { it.start() }
    }

    private fun stopAnalyzer() {
        analyzer?.stop()
        analyzer = null
    }

    fun setPlaybackVisualizationEnabled(enabled: Boolean) {
        playbackVisualizationVisible = enabled
        if (!enabled) {
            stopPlaybackVisualizer()
            uiState = uiState.copy(
                playbackWaveform = emptyList(),
                playbackWaveformAvailable = false,
                playbackVisualizationEnabled = false
            )
            return
        }
        if (playbackVisualizer == null) {
            uiState = uiState.copy(
                playbackWaveform = emptyList(),
                playbackWaveformAvailable = false,
                playbackVisualizationEnabled = false
            )
        }
        if (!hasMicPermission()) {
            playbackVisualizationVisible = false
            uiState = uiState.copy(
                playbackWaveform = emptyList(),
                playbackWaveformAvailable = false,
                playbackVisualizationEnabled = false,
                message = "Für die Live-Wellenform ist die optionale Mikrofonfreigabe erforderlich."
            )
            return
        }

        // Session 0 is Android's global output mix; OEM and permission policy may still deny capture.
        val activeSessionId = 0
        if (playbackVisualizer != null && playbackVisualizerSessionId == activeSessionId) return
        stopPlaybackVisualizer()
        var visualizer: android.media.audiofx.Visualizer? = null
        try {
            visualizer = android.media.audiofx.Visualizer(activeSessionId)
            visualizer.captureSize = android.media.audiofx.Visualizer.getCaptureSizeRange()[1]
            visualizer.setDataCaptureListener(
                object : android.media.audiofx.Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        effect: android.media.audiofx.Visualizer,
                        waveform: ByteArray,
                        samplingRate: Int
                    ) {
                        val samples = waveform.map { ((it.toInt() and 0xFF) - 128) / 128f }
                        mainHandler.post {
                            if (playbackVisualizer === effect) {
                                uiState = uiState.copy(
                                    playbackWaveform = samples,
                                    playbackWaveformAvailable = samples.any { kotlin.math.abs(it) > 0.02f }
                                )
                            }
                        }
                    }

                    override fun onFftDataCapture(
                        effect: android.media.audiofx.Visualizer,
                        fft: ByteArray,
                        samplingRate: Int
                    ) {
                        val bands = FftReducer.reduce(
                            fft = fft,
                            previous = lastSmoothedFftBands,
                            sampleRateHz = samplingRate / 1000
                        )
                        lastSmoothedFftBands = bands
                        mainHandler.post {
                            if (playbackVisualizer === effect) {
                                uiState = uiState.copy(playbackSpectrum = bands.toList())
                            }
                        }
                    }
                },
                android.media.audiofx.Visualizer.getMaxCaptureRate() / 2,
                true,
                true
            )
            visualizer.enabled = true
            playbackVisualizer = visualizer
            playbackVisualizerSessionId = activeSessionId
            uiState = uiState.copy(playbackVisualizationEnabled = true, playbackWaveformAvailable = false)
        } catch (_: SecurityException) {
            try { visualizer?.release() } catch (_: RuntimeException) {}
            playbackVisualizer = null
            playbackVisualizerSessionId = null
            playbackVisualizationVisible = false
            uiState = uiState.copy(
                playbackWaveform = emptyList(),
                playbackWaveformAvailable = false,
                playbackVisualizationEnabled = false,
                message = "Android hat den Zugriff auf die Wiedergabe-Wellenform nicht freigegeben."
            )
        } catch (_: RuntimeException) {
            try { visualizer?.release() } catch (_: RuntimeException) {}
            playbackVisualizer = null
            playbackVisualizerSessionId = null
            playbackVisualizationVisible = false
            uiState = uiState.copy(
                playbackWaveform = emptyList(),
                playbackWaveformAvailable = false,
                playbackVisualizationEnabled = false
            )
        }
    }

    private fun stopPlaybackVisualizer() {
        playbackVisualizer?.let { visualizer ->
            try {
                visualizer.enabled = false
            } catch (_: RuntimeException) {
            }
            try {
                visualizer.release()
            } catch (_: RuntimeException) {
            }
        }
        playbackVisualizer = null
        playbackVisualizerSessionId = null
        lastSmoothedFftBands = null
    }

    private val soundLabEngine by lazy {
        SoundLabEngine().also { engine ->
            engine.onSamplesGenerated = { samples ->
                mainHandler.post {
                    if (uiState.soundLabActive) {
                        uiState = uiState.copy(
                            playbackWaveform = samples.toList(),
                            playbackWaveformAvailable = true
                        )
                    }
                }
            }
        }
    }

    fun setParticleMode(mode: ParticleMode) {
        uiState = uiState.copy(particleMode = mode)
    }

    fun toggleSoundLab() {
        val willActive = !uiState.soundLabActive
        if (willActive) {
            soundLabEngine.start()
        } else {
            soundLabEngine.stop()
        }
        uiState = uiState.copy(soundLabActive = willActive)
    }

    fun setSoundLabSignal(signal: SoundLabEngine.SignalType) {
        soundLabEngine.currentSignal = signal
        uiState = uiState.copy(soundLabSignal = signal)
    }

    fun onParticleFieldTouch(xNorm: Float, yNorm: Float, isTouching: Boolean) {
        soundLabEngine.flanger.setParametersFromXy(xNorm, yNorm)
        uiState = uiState.copy(
            flangerRateHz = soundLabEngine.flanger.targetRateHz,
            flangerDepth = soundLabEngine.flanger.targetDepth,
            flangerFeedback = soundLabEngine.flanger.targetFeedback
        )
    }

    fun refreshMicrophonePermission() {
        if (!hasMicPermission()) {
            stopAnalyzer()
            stopPlaybackVisualizer()
            if (uiState.autoAdjust || uiState.playbackVisualizationEnabled || uiState.playbackWaveformAvailable) {
                uiState = uiState.copy(
                    autoAdjust = false,
                    environment = null,
                    playbackWaveform = emptyList(),
                    playbackWaveformAvailable = false,
                    playbackVisualizationEnabled = false,
                    message = "Mikrofonfreigabe wurde entzogen. Das gespeicherte Profil bleibt aktiv."
                )
                applyEffective()
            }
            playbackVisualizationVisible = false
        } else {
            if (uiState.autoAdjust && analyzer == null) startAnalyzer()
        }
    }

    fun mediaPermissionStatus(): MediaPermission = MediaPermission(
        granted = mediaWatcher?.isListenerEnabled() ?: MediaWatcher(getApplication()) { }.isListenerEnabled(),
        enabled = uiState.mediaEnabled
    )

    fun refreshMediaPermission() {
        val granted = mediaWatcher?.isListenerEnabled() ?: MediaWatcher(getApplication()) { }.isListenerEnabled()
        uiState = uiState.copy(mediaPermissionGranted = granted)
        if (!granted && uiState.mediaEnabled) {
            mediaWatcher?.stop()
            mediaWatcher = null
            uiState = uiState.copy(mediaEnabled = false, nowPlaying = null)
        } else if (granted && uiState.mediaRequested && !uiState.mediaEnabled) {
            setMediaEnabled(true)
        }
    }

    fun setMediaEnabled(enabled: Boolean) {
        if (!enabled) {
            if (bypassed) endBypass()
            mediaWatcher?.stop()
            mediaWatcher = null
            getApplication<Application>().getSharedPreferences("sonora_media_v1", 0)
                .edit().putBoolean("enabled", false).apply()
            uiState = uiState.copy(mediaEnabled = false, mediaRequested = false, nowPlaying = null)
            return
        }
        uiState = uiState.copy(mediaRequested = true)
        val watcher = mediaWatcher ?: MediaWatcher(getApplication()) { nowPlaying ->
            mainHandler.post {
                uiState = uiState.copy(nowPlaying = nowPlaying)
                val profileHint = nowPlaying?.let(MediaRule::profileHint)
                val profile = uiState.profiles.firstOrNull { it.preset == profileHint }
                if (profile != null && profile.id != uiState.activeProfileId) applyPreset(profile, source = "media_session")
            }
        }.also { mediaWatcher = it }
        if (!watcher.isListenerEnabled()) {
            uiState = uiState.copy(
                mediaEnabled = false,
                mediaPermissionGranted = false,
                message = "Inhaltserkennung braucht deine Freigabe in den Android-Benachrichtigungseinstellungen."
            )
            return
        }
        val started = try {
            watcher.start()
        } catch (_: SecurityException) {
            false
        }
        uiState = uiState.copy(
            mediaEnabled = started,
            mediaPermissionGranted = true,
            nowPlaying = if (started) watcher.currentNowPlaying() else null,
            message = if (started) null else "Die Wiedergabe konnte nicht gelesen werden. Prüfe die Android-Freigabe."
        )
        getApplication<Application>().getSharedPreferences("sonora_media_v1", 0)
            .edit().putBoolean("enabled", started).apply()
    }

    fun onMediaPermissionSettingsReturned() {
        refreshMediaPermission()
    }

    fun setVolumeReminderEnabled(enabled: Boolean) {
        uiState = uiState.copy(volumeReminderEnabled = enabled)
        safetyPreferences.edit().putBoolean(VOLUME_REMINDER_ENABLED_KEY, enabled).apply()
        if (!enabled) {
            safetyPreferences.edit().remove(VOLUME_LOUD_SINCE_KEY)
                .remove(VOLUME_REMINDER_SENT_KEY)
                .remove(VOLUME_REMINDER_ACKNOWLEDGED_KEY)
                .apply()
            getApplication<Application>().getSystemService(android.app.NotificationManager::class.java)
                ?.cancel(VOLUME_REMINDER_NOTIFICATION_ID)
        }
        refreshVolumeState()
    }

    fun acknowledgeVolumeReminder() {
        safetyPreferences.edit().putBoolean(VOLUME_REMINDER_ACKNOWLEDGED_KEY, true).apply()
        getApplication<Application>().getSystemService(android.app.NotificationManager::class.java)
            ?.cancel(VOLUME_REMINDER_NOTIFICATION_ID)
        refreshVolumeState()
    }

    fun duplicateProfile(id: String) {
        val copy = store.duplicateProfile(id) ?: return
        val currentProfiles = store.load().profiles
        uiState = uiState.copy(
            profiles = currentProfiles,
            activeProfileId = copy.id,
            activeProfileName = copy.name,
            message = "Profil „${copy.name}“ dupliziert."
        )
        applyPreset(copy, source = "duplicate")
    }

    fun renameProfile(id: String, newName: String) {
        val success = store.renameProfile(id, newName)
        if (success) {
            val updated = store.load().profiles
            val activeName = updated.firstOrNull { it.id == uiState.activeProfileId }?.name ?: uiState.activeProfileName
            uiState = uiState.copy(
                profiles = updated,
                activeProfileName = activeName,
                message = "Profil umbenannt in „${newName.trim()}“."
            )
        }
    }

    fun requestAiSuggestion(preference: HearingPreference = uiState.aiPreferences) {
        val current = uiState.profiles.firstOrNull { it.id == uiState.activeProfileId } ?: return
        uiState = uiState.copy(aiLoading = true, aiPreferences = preference)
        viewModelScope.launch {
            val measuredNoiseDbfs = uiState.environment?.rmsDb
            val request = AiAdviceRequest(
                preferences = preference,
                currentNoiseLevelDb = measuredNoiseDbfs,
                consentGiven = true
            )
            val result = localAiProvider.generateSuggestion(request, current)
            mainHandler.post {
                when (result) {
                    is AiAdviceResult.Success -> {
                        uiState = uiState.copy(aiSuggestion = result.suggestion, aiLoading = false)
                    }
                    is AiAdviceResult.Error -> {
                        uiState = uiState.copy(aiLoading = false, message = result.message)
                    }
                }
            }
        }
    }

    fun applyAiSuggestion() {
        val suggestion = uiState.aiSuggestion ?: return
        val newProfile = suggestion.toSoundProfile()
        val updated = uiState.profiles + newProfile
        uiState = uiState.copy(
            profiles = updated,
            activeProfileId = newProfile.id,
            activeProfileName = newProfile.name,
            eqCurve = EqCurve(suggestion.bandCount, suggestion.levelsDb).withBandCount(uiState.bandCount),
            levelsDb = EqCurve(suggestion.bandCount, suggestion.levelsDb).withBandCount(uiState.bandCount).levelsDb,
            preampDb = suggestion.preampDb,
            aiSuggestion = null,
            message = "KI-Vorschlag „${newProfile.name}“ übernommen."
        )
        applyEffective()
        store.save(ProfileFile(activeProfileId = newProfile.id, profiles = updated))
    }

    fun dismissAiSuggestion() {
        uiState = uiState.copy(aiSuggestion = null)
    }

    fun refreshAudioRouting() {
        routingManager.refresh("Manuelle Diagnoseprüfung")
        uiState = uiState.copy(
            audioRouting = routingManager.currentSnapshot,
            hardwareState = HardwareDspAccelerator.getHardwareState()
        )
    }

    fun onAppResumed() {
        refreshVolumeState()
        routingManager.refresh("onAppResumed")
        uiState = uiState.copy(
            audioRouting = routingManager.currentSnapshot,
            hardwareState = HardwareDspAccelerator.getHardwareState()
        )
        val mediaOptIn = getApplication<Application>().getSharedPreferences("sonora_media_v1", 0).getBoolean("enabled", false)
        refreshMicrophonePermission()
        if (playbackVisualizationVisible) setPlaybackVisualizationEnabled(true)
        else if (uiState.playbackVisualizationEnabled) {
            stopPlaybackVisualizer()
            uiState = uiState.copy(
                playbackWaveform = emptyList(),
                playbackWaveformAvailable = false,
                playbackVisualizationEnabled = false
            )
        }
        if (mediaOptIn != uiState.mediaEnabled) setMediaEnabled(mediaOptIn)
        else if (uiState.mediaRequested || mediaOptIn) refreshMediaPermission()
    }

    fun onActivityStopped() {
        if (uiState.blindTestActive) cancelBlindTest()
        if (bypassed) endBypass()
        val resumeVisualization = playbackVisualizationVisible
        stopPlaybackVisualizer()
        uiState = uiState.copy(
            playbackWaveform = emptyList(),
            playbackWaveformAvailable = false,
            playbackVisualizationEnabled = false
        )
        playbackVisualizationVisible = resumeVisualization
        mediaWatcher?.stop()
        mediaWatcher = null
    }

    fun refreshVolumeState() {
        val app = getApplication<Application>()
        val manager = app.getSystemService(AudioManager::class.java)
        val current = manager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
        val maximum = manager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 1
        val percent = ((current * 100f) / maximum).toInt().coerceIn(0, 100)
        val activelyPlaying = manager?.isMusicActive == true
        val now = SystemClock.elapsedRealtime()
        val existingStart = safetyPreferences.getLong(VOLUME_LOUD_SINCE_KEY, -1L).takeIf { it >= 0L && it <= now }
        val setting = safetyPreferences.getBoolean(VOLUME_REMINDER_ENABLED_KEY, true)
        if (setting && (!activelyPlaying || percent < VOLUME_REMINDER_THRESHOLD_PERCENT)) {
            safetyPreferences.edit().remove(VOLUME_REMINDER_SENT_KEY).remove(VOLUME_REMINDER_ACKNOWLEDGED_KEY).apply()
            getApplication<Application>().getSystemService(android.app.NotificationManager::class.java)
                ?.cancel(VOLUME_REMINDER_NOTIFICATION_ID)
        }
        val sessionStart = if (setting) {
            volumeGuard.loudSessionStart(percent, activelyPlaying, existingStart, now)
        } else {
            null
        }
        if (sessionStart == null) {
            safetyPreferences.edit().remove(VOLUME_LOUD_SINCE_KEY)
                .remove(VOLUME_REMINDER_SENT_KEY)
                .remove(VOLUME_REMINDER_ACKNOWLEDGED_KEY)
                .apply()
            getApplication<Application>().getSystemService(android.app.NotificationManager::class.java)
                ?.cancel(VOLUME_REMINDER_NOTIFICATION_ID)
        } else if (sessionStart != existingStart) {
            safetyPreferences.edit().putLong(VOLUME_LOUD_SINCE_KEY, sessionStart).putBoolean(VOLUME_REMINDER_SENT_KEY, false).apply()
        }
        val minutes = volumeGuard.elapsedMinutes(sessionStart, now)
        val nextVolumeState = volumeGuard.update(percent, minutes, setting)
        val acknowledged = safetyPreferences.getBoolean(VOLUME_REMINDER_ACKNOWLEDGED_KEY, false)
        val visibleVolumeState = nextVolumeState.copy(
            reminderDue = volumeGuard.shouldShowReminder(nextVolumeState.reminderDue, acknowledged)
        )
        uiState = uiState.copy(
            volumeReminderEnabled = setting,
            volumeState = visibleVolumeState
        )
    }

    fun startBlindTest() {
        if (uiState.engineStatus !is EngineStatus.Active || !uiState.masterEnabled) {
            uiState = uiState.copy(message = "Der Hörtest braucht den aktiven Klangeffekt. Das Gerät meldet ihn gerade nicht als verfügbar.")
            return
        }
        cancelBlindTestPlayback()
        blindTest = BlindTest()
        uiState = uiState.copy(
            blindTestActive = true,
            blindTestRound = 0,
            blindTestResults = emptyList(),
            blindTestOriginalResults = emptyList(),
            blindTestPresented = false,
            blindTestReady = false,
            blindTestRatingA = 5,
            blindTestRatingB = 5,
            blindTestFeedbackSaved = false,
            blindTestMessage = null
        )
    }

    fun presentBlindTestRound() {
        if (uiState.engineStatus !is EngineStatus.Active || !uiState.masterEnabled || !uiState.blindTestActive) return
        if (uiState.blindTestRound !in 0 until blindTest.totalRounds() || uiState.blindTestPresented) return
        cancelBlindTestPlayback()
        val round = uiState.blindTestRound
        val session = blindTestSessionId
        val order = blindTest.nextOrder(round)
        if (!playbackIsActive()) {
            uiState = uiState.copy(blindTestMessage = "Es läuft keine Medienwiedergabe. Starte denselben Titel in einer anderen App und versuche es erneut.")
            return
        }
        val segmentDurationMs = BLIND_TEST_SEGMENT_MS
        uiState = uiState.copy(
            blindTestPresented = true,
            blindTestReady = false,
            blindTestRatingA = 5,
            blindTestRatingB = 5,
            blindTestFeedbackSaved = false,
            blindTestMessage = "Bewerte A und B unabhängig. Die Zuordnung bleibt verborgen. Android/OEM kann globale Effekte je nach App ignorieren."
        )
        val firstIsSonora = order == de.sonora.app.core.BlindOrder.SONORA_FIRST
        if (!firstIsSonora) beginBypass()
        val secondSegment = Runnable {
            if (session != blindTestSessionId || !uiState.blindTestActive || round != uiState.blindTestRound) return@Runnable
            if (!playbackIsActive()) {
                endBypass()
                uiState = uiState.copy(blindTestPresented = false, blindTestMessage = "Die Wiedergabe wurde unterbrochen. Diese Runde wurde verworfen.")
                return@Runnable
            }
            if (firstIsSonora) beginBypass() else endBypass()
        }
        val finishSegment = Runnable {
            if (session != blindTestSessionId || !uiState.blindTestActive || round != uiState.blindTestRound) return@Runnable
            if (!playbackIsActive()) {
                endBypass()
                uiState = uiState.copy(blindTestPresented = false, blindTestMessage = "Die Wiedergabe wurde unterbrochen. Diese Runde wurde verworfen.")
                return@Runnable
            }
            endBypass()
            uiState = uiState.copy(blindTestReady = true, blindTestMessage = "Beide Abschnitte gehört. Bewerte A und B; die Zuordnung bleibt bis zum Ende verborgen.")
        }
        blindTestSecondSegment = secondSegment
        blindTestFinishSegment = finishSegment
        mainHandler.postDelayed(blindTestPlaybackMonitor, BLIND_TEST_PLAYBACK_CHECK_MS)
        mainHandler.postDelayed(secondSegment, segmentDurationMs)
        mainHandler.postDelayed(finishSegment, segmentDurationMs * 2)
    }

    fun updateBlindTestRatingA(rating: Int) {
        if (rating in 1..10) uiState = uiState.copy(blindTestRatingA = rating)
    }

    fun updateBlindTestRatingB(rating: Int) {
        if (rating in 1..10) uiState = uiState.copy(blindTestRatingB = rating)
    }

    fun rateBlindTest() {
        if (!uiState.blindTestReady || uiState.blindTestRound !in 0 until blindTest.totalRounds()) return
        val roundOrder = blindTest.nextOrder(uiState.blindTestRound)
        val sonoraRating = if (roundOrder == de.sonora.app.core.BlindOrder.SONORA_FIRST) uiState.blindTestRatingA else uiState.blindTestRatingB
        val originalRating = if (roundOrder == de.sonora.app.core.BlindOrder.ORIGINAL_FIRST) uiState.blindTestRatingA else uiState.blindTestRatingB
        blindTest.recordPair(uiState.blindTestRatingA, uiState.blindTestRatingB)
        val results = blindTest.results()
        val originalResults = blindTest.originalResults()
        recordEvent(EventNames.BLIND_TEST_RATED, mapOf(
            "rating_1_10" to sonoraRating.toString(),
            "rating_a_1_10" to uiState.blindTestRatingA.toString(),
            "rating_b_1_10" to uiState.blindTestRatingB.toString(),
            "sonora_rating_1_10" to sonoraRating.toString(),
            "original_rating_1_10" to originalRating.toString(),
            "mode" to (uiState.spatialMode?.id ?: "off"),
            "profile_id" to uiState.activeProfileId
        ))
        val complete = blindTest.isComplete()
        uiState = uiState.copy(
            blindTestResults = results,
            blindTestOriginalResults = originalResults,
            blindTestRound = results.size,
            blindTestPresented = false,
            blindTestReady = false,
            blindTestFeedbackSaved = complete,
            blindTestMessage = if (complete) "Alle Runden sind bewertet. Die Zuordnung ist jetzt sichtbar." else "Bewertung gespeichert. Du kannst die nächste Runde starten.",
            message = if (complete) "Der Hörtest ist abgeschlossen." else null
        )
    }

    fun blindTestAverage(): Double? = blindTest.average()
    fun blindTestOriginalAverage(): Double? = blindTest.originalAverage()

    fun finishBlindTest() {
        if (!blindTest.isComplete()) return
        uiState = uiState.copy(blindTestActive = false, message = "Hörtest im lokalen Diagnosebericht gespeichert.")
    }

    fun cancelBlindTest() {
        cancelBlindTestPlayback()
        uiState = uiState.copy(blindTestActive = false, blindTestPresented = false, blindTestReady = false, blindTestMessage = null)
    }

    private fun cancelBlindTestPlayback() {
        val wasPresented = uiState.blindTestPresented
        blindTestSessionId++
        uiState = uiState.copy(blindTestPresented = false, blindTestReady = false)
        mainHandler.removeCallbacks(blindTestPlaybackMonitor)
        blindTestSecondSegment?.let(mainHandler::removeCallbacks)
        blindTestFinishSegment?.let(mainHandler::removeCallbacks)
        blindTestSecondSegment = null
        blindTestFinishSegment = null
        if (bypassed || wasPresented) endBypass()
    }

    private fun playbackIsActive(): Boolean =
        getApplication<Application>().getSystemService(AudioManager::class.java)?.isMusicActive == true

    fun clearDiagnosticEvents() {
        eventLog.clear()
        blindTest = BlindTest()
        uiState = uiState.copy(
            diagnosticEvents = emptyList(),
            blindTestResults = emptyList(),
            blindTestOriginalResults = emptyList(),
            blindTestRound = 0,
            blindTestActive = false,
            blindTestPresented = false,
            blindTestReady = false,
            blindTestFeedbackSaved = false
        )
        diagnosticsStore.save(emptyList())
    }

    fun diagnosticReport(): String = buildString {
        appendLine("Sonora-Diagnose ${de.sonora.app.BuildConfig.VERSION_NAME}, ${System.currentTimeMillis()}")
        blindTest.average()?.let { appendLine("Blindtest Sonora: ${"%.1f".format(java.util.Locale.GERMAN, it)} von 10") }
        blindTest.originalAverage()?.let { appendLine("Blindtest Original: ${"%.1f".format(java.util.Locale.GERMAN, it)} von 10") }
        appendLine("Engine: ${uiState.engineStatus}")
        appendLine("Ereignisse: ${eventLog.events().size}")
        eventLog.events().takeLast(100).forEach { event ->
            val properties = event.properties.toSortedMap().entries.joinToString(", ") { (key, value) ->
                val safeValue = value.take(200).replace(Regex("[\\r\\n\\t]"), " ")
                "$key=$safeValue"
            }
            appendLine("${event.name} {$properties} ${event.timestampMs}")
        }
    }

    private fun recordEvent(name: String, properties: Map<String, String> = emptyMap()) {
        eventLog.record(name, properties)
        val events = eventLog.events()
        diagnosticsStore.save(events)
        uiState = uiState.copy(diagnosticEvents = events)
    }

    private fun applyEffective() {
        if (bypassed) return
        if (!uiState.masterEnabled) {
            SonoraEngine.setEnabled(false)
            uiState = uiState.copy(bypassActive = false, spatialAvailable = false, spatialAvailabilityMessage = null)
            return
        }
        val previousStatus = SonoraEngine.status
        SonoraEngine.setEnabled(true)
        val limited = SonoraEngine.applyCurve(
            EqCurve(uiState.bandCount, effectiveLevels()),
            uiState.preampDb
        )
        val spatialAvailable = applySpatialEffect()
        uiState = uiState.copy(
            clipWarning = limited.clipped,
            bypassActive = false,
            spatialAvailable = spatialAvailable,
            spatialAvailabilityMessage = SonoraEngine.spatialFailureReason
        )
        if (previousStatus is EngineStatus.Active && SonoraEngine.status !is EngineStatus.Active) {
            recordEvent(EventNames.AUDIO_EFFECT_FAILED, mapOf(
                "error_code" to "effect_unavailable",
                "app_package" to "",
                "android_version" to android.os.Build.VERSION.RELEASE
            ))
        }
    }

    private fun applySpatial() {
        if (bypassed || !uiState.masterEnabled) {
            uiState = uiState.copy(spatialAvailable = false, spatialAvailabilityMessage = null)
            return
        }
        val spatialAvailable = applySpatialEffect()
        uiState = uiState.copy(
            spatialAvailable = spatialAvailable,
            spatialAvailabilityMessage = SonoraEngine.spatialFailureReason
        )
        if (!spatialAvailable && uiState.spatialMode != null) recordEvent(EventNames.AUDIO_EFFECT_FAILED, mapOf(
            "error_code" to "spatial_effect_unavailable",
            "reason" to (SonoraEngine.spatialFailureReason ?: "unavailable"),
            "app_package" to (uiState.nowPlaying?.packageName ?: ""),
            "android_version" to android.os.Build.VERSION.RELEASE
        ))
    }

    private fun applySpatialEffect(): Boolean {
        val mode = uiState.spatialMode
        val params = if (mode == null) {
            SpatialParams(0f, 0f, 0f, 0f, 0f)
        } else {
            SpatialModeCatalog.params(mode, uiState.spatialIntensity)
        }
        return SonoraEngine.applySpatial(params)
    }

    private fun effectiveLevels(): List<Float> {
        val environment = uiState.environment
        if (!uiState.autoAdjust || environment == null) return uiState.eqCurve.levelsDb
        val levelsAtTenBands = uiState.eqCurve.withBandCount(BandCount.TEN).levelsDb
        val offset = when (environment.level) {
            2 -> listOf(-2f, -2f, -1f, 0f, 2f, 3f, 2f, 0f, -1f, -2f)
            1 -> List(10) { 0f }
            else -> listOf(1f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        }
        val adapted = levelsAtTenBands.mapIndexed { index, value ->
            (value + offset[index]).coerceIn(-12f, 12f)
        }
        return EqCurve(BandCount.TEN, adapted).withBandCount(uiState.bandCount).levelsDb
    }

    private fun profileBandCount(profile: SoundProfile): BandCount = when (profile.bandCount) {
        "31" -> BandCount.THIRTY_ONE
        "64", "64+" -> BandCount.SIXTY_FOUR
        else -> BandCount.TEN
    }

    private fun normalize(levels: List<Float>): List<Float> =
        List(10) { index -> levels.getOrElse(index) { 0f }.coerceIn(-12f, 12f) }

    private fun persist() {
        store.save(
            ProfileFile(
                activeProfileId = uiState.activeProfileId,
                profiles = uiState.profiles
            )
        )
    }

    private fun hasMicPermission(): Boolean = ContextCompat.checkSelfPermission(
        getApplication<Application>(),
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    override fun onCleared() {
        soundLabEngine.stop()
        playbackVisualizationVisible = false
        stopPlaybackVisualizer()
        routingManager.unregister()
        mainHandler.removeCallbacksAndMessages(null)
        if (bypassed) endBypass()

        stopAnalyzer()
        mediaWatcher?.stop()
        mediaWatcher = null
        super.onCleared()
    }
}
