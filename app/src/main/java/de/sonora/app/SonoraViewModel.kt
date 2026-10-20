package de.sonora.app

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import de.sonora.app.audio.EngineStatus
import de.sonora.app.audio.EnvironmentAnalyzer
import de.sonora.app.audio.EnvironmentReading
import de.sonora.app.audio.SonoraEngine
import de.sonora.app.data.Presets
import de.sonora.app.data.ProfileFile
import de.sonora.app.data.ProfileStore
import de.sonora.app.data.SoundProfile
import de.sonora.app.service.SonoraService
import java.util.UUID

data class SonoraUiState(
    val onboardingDone: Boolean = false,
    val engineStatus: EngineStatus = EngineStatus.Inactive,
    val masterEnabled: Boolean = true,
    val levelsDb: List<Float> = List(10) { 0f },
    val profiles: List<SoundProfile> = emptyList(),
    val activeProfileId: String = "flat",
    val activeProfileName: String = "Neutral",
    val environment: EnvironmentReading? = null,
    val autoAdjust: Boolean = false,
    val dirty: Boolean = false,
    val message: String? = null
)

class SonoraViewModel(application: Application) : AndroidViewModel(application) {

    private val store = ProfileStore(application)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var analyzer: EnvironmentAnalyzer? = null
    private var bypassed = false

    var uiState by mutableStateOf(SonoraUiState())
        private set

    init {
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
            profiles = file.profiles,
            activeProfileId = active.id,
            activeProfileName = active.name,
            levelsDb = normalize(active.levels)
        )
        applyEffective()
    }

    fun setMasterEnabled(enabled: Boolean) {
        uiState = uiState.copy(masterEnabled = enabled)
        applyEffective()
    }

    fun setBand(index: Int, db: Float) {
        if (index !in 0..9) return
        val levels = uiState.levelsDb.toMutableList()
        levels[index] = db.coerceIn(-12f, 12f)
        uiState = uiState.copy(levelsDb = levels, dirty = true)
        applyEffective()
    }

    fun applyPreset(profile: SoundProfile) {
        uiState = uiState.copy(
            activeProfileId = profile.id,
            activeProfileName = profile.name,
            levelsDb = normalize(profile.levels),
            dirty = false
        )
        applyEffective()
        persist()
    }

    fun selectProfile(id: String) {
        val profile = uiState.profiles.firstOrNull { it.id == id } ?: return
        applyPreset(profile)
    }

    fun setAutoAdjust(enabled: Boolean) {
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
        val clean = name.trim().ifBlank { "Mein Profil" }
        val profile = SoundProfile(
            id = UUID.randomUUID().toString(),
            name = clean,
            levels = uiState.levelsDb,
            preset = null
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
            activeProfileName = active.name,
            levelsDb = normalize(active.levels),
            dirty = false,
            message = "Profil gelöscht."
        )
        applyEffective()
        persist()
    }

    fun resetLevels() {
        uiState = uiState.copy(levelsDb = List(10) { 0f }, dirty = true)
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
    }

    fun beginBypass() {
        bypassed = true
        SonoraEngine.setEnabled(false)
    }

    fun endBypass() {
        bypassed = false
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

    private fun startAnalyzer() {
        if (analyzer != null) return
        analyzer = EnvironmentAnalyzer { reading ->
            mainHandler.post {
                val previousLevel = uiState.environment?.level
                uiState = uiState.copy(environment = reading)
                if (reading.level != previousLevel) applyEffective()
            }
        }.also { it.start() }
    }

    private fun stopAnalyzer() {
        analyzer?.stop()
        analyzer = null
    }

    private fun applyEffective() {
        if (bypassed) return
        if (!uiState.masterEnabled) {
            SonoraEngine.setEnabled(false)
            return
        }
        SonoraEngine.setEnabled(true)
        SonoraEngine.applyLevels(effectiveLevels())
    }

    private fun effectiveLevels(): List<Float> {
        val environment = uiState.environment
        if (!uiState.autoAdjust || environment == null) return uiState.levelsDb
        val offset = when (environment.level) {
            2 -> listOf(-2f, -2f, -1f, 0f, 2f, 3f, 2f, 0f, -1f, -2f)
            1 -> List(10) { 0f }
            else -> listOf(1f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        }
        return uiState.levelsDb.mapIndexed { index, value ->
            (value + offset.getOrElse(index) { 0f }).coerceIn(-12f, 12f)
        }
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
        stopAnalyzer()
        super.onCleared()
    }
}
