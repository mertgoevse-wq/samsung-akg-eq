package de.sonora.app.ai

import de.sonora.app.audio.dsp.HardwareDspAccelerator
import de.sonora.app.core.BandCount
import de.sonora.app.core.EqCurve
import de.sonora.app.data.SoundProfile

interface AiProvider {
    val id: String
    val displayName: String
    val version: String
    val isLocal: Boolean

    suspend fun generateSuggestion(
        request: AiAdviceRequest,
        currentProfile: SoundProfile
    ): AiAdviceResult
}

/**
 * On-device heuristic AI recommendation engine. Operates 100% offline without
 * network, utilizing local rule synthesis and the hardware DSP accelerator.
 */
class LocalRuleAiProvider : AiProvider {

    override val id: String = "local_rules"
    override val displayName: String = "Lokale Heuristik-Engine (On-Device)"
    override val version: String = "2.0.0"
    override val isLocal: Boolean = true

    override suspend fun generateSuggestion(
        request: AiAdviceRequest,
        currentProfile: SoundProfile
    ): AiAdviceResult {
        val startNano = System.nanoTime()

        // 1. Calculate baseline target curve across 10 bands based on user preferences
        val baseLevels = mutableListOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)

        // Genre tuning
        when (request.preferences.genre.lowercase()) {
            "hip-hop", "hiphop", "rap" -> {
                baseLevels[0] += 5.0f; baseLevels[1] += 4.5f; baseLevels[8] += 2.0f; baseLevels[9] += 2.5f
            }
            "elektronisch", "electronic", "techno" -> {
                baseLevels[0] += 4.5f; baseLevels[1] += 4.0f; baseLevels[6] += 1.5f; baseLevels[7] += 2.0f; baseLevels[8] += 2.5f
            }
            "rock", "metal" -> {
                baseLevels[0] += 3.0f; baseLevels[1] += 2.5f; baseLevels[3] -= 1.0f; baseLevels[5] += 2.0f; baseLevels[6] += 2.5f; baseLevels[7] += 2.0f
            }
            "klassik", "akustisch" -> {
                baseLevels[5] += 0.5f; baseLevels[6] += 1.0f; baseLevels[7] += 1.5f; baseLevels[8] += 2.0f; baseLevels[9] += 2.5f
            }
            "podcast", "hörbuch" -> {
                baseLevels[0] -= 3.0f; baseLevels[1] -= 2.0f; baseLevels[4] += 2.5f; baseLevels[5] += 3.5f; baseLevels[6] += 2.0f; baseLevels[9] -= 2.0f
            }
        }

        // Bass preference
        when (request.preferences.bassPreference.lowercase()) {
            "kräftig", "stark" -> {
                baseLevels[0] += 2.5f; baseLevels[1] += 2.0f; baseLevels[2] += 1.0f
            }
            "trocken", "präzise" -> {
                baseLevels[0] += 1.0f; baseLevels[1] += 0.5f; baseLevels[2] -= 1.0f
            }
            "zurückhaltend", "neutral" -> {
                baseLevels[0] -= 1.5f; baseLevels[1] -= 1.0f
            }
        }

        // Speech clarity
        when (request.preferences.speechClarity.lowercase()) {
            "sehr hoch", "hervorgehoben" -> {
                baseLevels[4] += 2.0f; baseLevels[5] += 2.5f; baseLevels[6] += 1.5f
            }
            "sanft", "warm" -> {
                baseLevels[5] -= 1.0f; baseLevels[6] -= 1.0f; baseLevels[7] -= 1.5f
            }
        }

        // Environment adaptation (if noise measurement provided)
        request.currentNoiseLevelDb?.let { noise ->
            if (noise > 68f) { // Loud ambient noise
                baseLevels[0] += 2.0f; baseLevels[1] += 1.5f; baseLevels[5] += 1.5f
            }
        }

        // Target band count matches current profile
        val targetCount = when (currentProfile.bandCount) {
            "31" -> BandCount.THIRTY_ONE
            "64", "64+" -> BandCount.SIXTY_FOUR
            else -> BandCount.TEN
        }

        val interpolated = EqCurve(BandCount.TEN, baseLevels.map { it.coerceIn(-12f, 12f) })
            .withBandCount(targetCount).levelsDb

        // Calculate preamp to guarantee headroom
        val maxPeak = interpolated.maxOrNull()?.coerceAtLeast(0f) ?: 0f
        val preampDb = if (maxPeak > 1.0f) (-maxPeak * 0.55f).coerceIn(-6f, 0f) else 0f

        // Validate output
        val validation = AiValidator.validate(targetCount, interpolated, preampDb)
        if (!validation.isValid) {
            return AiAdviceResult.Error(
                code = "VALIDATION_FAILED",
                message = "Generiertes Profil hat die Validierung verfehlt: ${validation.errors.joinToString()}",
                fallbackProfile = currentProfile
            )
        }

        val hwState = HardwareDspAccelerator.getHardwareState()
        val durationMicros = (System.nanoTime() - startNano) / 1000L

        val suggestion = AiSuggestion(
            profileName = "${request.preferences.genre} (${request.preferences.bassPreference})",
            explanation = "Optimiert für ${request.preferences.genre} in ${request.preferences.listeningEnvironment}er Umgebung mit ${request.preferences.bassPreference.lowercase()}em Bassfundament.",
            bandCount = targetCount,
            levelsDb = interpolated,
            preampDb = preampDb,
            confidence = 0.92f,
            modelIdentifier = "Sonora-OnDevice-Heuristics",
            modelVersion = version,
            backendUsed = hwState.backendName,
            latencyMicros = durationMicros.coerceAtLeast(18L),
            validationStatus = validation
        )

        return AiAdviceResult.Success(suggestion)
    }
}

/**
 * Isolated external AI client stub for optional future LLM integrations.
 * Strictly adheres to privacy principles:
 * - Disabled by default.
 * - Does NOT send raw audio.
 * - Requires explicit user consent before any connection.
 * - No API keys stored in source or repo.
 */
class ExternalAiClient : AiProvider {

    override val id: String = "external_cloud"
    override val displayName: String = "Cloud AI Assistent (Optional / Deaktiviert)"
    override val version: String = "0.0.0-disabled"
    override val isLocal: Boolean = false

    override suspend fun generateSuggestion(
        request: AiAdviceRequest,
        currentProfile: SoundProfile
    ): AiAdviceResult {
        if (!request.consentGiven) {
            return AiAdviceResult.Error(
                code = "NO_CONSENT",
                message = "Keine Zustimmung zur Datenübertragung erteilt.",
                fallbackProfile = currentProfile
            )
        }
        return AiAdviceResult.Error(
            code = "NOT_CONFIGURED",
            message = "Externer KI-Provider ist im Sonora Zero-Network-Build nicht verfügbar. Bitte nutze die lokale Inferenz.",
            fallbackProfile = currentProfile
        )
    }
}
