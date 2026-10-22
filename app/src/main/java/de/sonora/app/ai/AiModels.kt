package de.sonora.app.ai

import androidx.compose.runtime.Immutable
import de.sonora.app.core.BandCount
import de.sonora.app.data.SoundProfile
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class HearingPreference(
    val genre: String = "Allround",
    val bassPreference: String = "Ausgewogen",
    val speechClarity: String = "Normal",
    val listeningEnvironment: String = "Ruhig"
)

@Immutable
@Serializable
data class ValidationStatus(
    val isValid: Boolean,
    val checksPassed: List<String>,
    val errors: List<String>
)

@Immutable
@Serializable
data class AiSuggestion(
    val profileName: String,
    val explanation: String,
    val bandCount: BandCount,
    val levelsDb: List<Float>,
    val preampDb: Float,
    val confidence: Float,
    val modelIdentifier: String,
    val modelVersion: String,
    val backendUsed: String,
    val latencyMicros: Long,
    val validationStatus: ValidationStatus
) {
    fun toSoundProfile(): SoundProfile = SoundProfile(
        id = java.util.UUID.randomUUID().toString(),
        name = profileName,
        levels = levelsDb,
        preset = null,
        bandCount = bandCount.size.toString(),
        preampDb = preampDb,
        origin = "$modelIdentifier v$modelVersion ($backendUsed)",
        uncertainty = "Konfidenz: ${(confidence * 100).toInt()} %",
        boundaryLimits = "Geprüft: Headroom und Bereichsbegrenzung eingehalten",
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )
}

@Immutable
data class AiAdviceRequest(
    val preferences: HearingPreference,
    val currentNoiseLevelDb: Float? = null,
    val consentGiven: Boolean = true
)

sealed interface AiAdviceResult {
    data class Success(val suggestion: AiSuggestion) : AiAdviceResult
    data class Error(val code: String, val message: String, val fallbackProfile: SoundProfile?) : AiAdviceResult
}
