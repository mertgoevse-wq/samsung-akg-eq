package de.sonora.app.core

import de.sonora.app.ai.AiAdviceRequest
import de.sonora.app.ai.AiAdviceResult
import de.sonora.app.ai.AiValidator
import de.sonora.app.ai.ExternalAiClient
import de.sonora.app.ai.HearingPreference
import de.sonora.app.ai.LocalRuleAiProvider
import de.sonora.app.data.PresetCatalog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiModuleTest {

    private val sampleProfile = PresetCatalog.presets.first().toSoundProfile()

    @Test
    fun `AiValidator besteht bei korrekten 10-Band Pegeln im Bereich`() {
        val validLevels = listOf(3f, 2f, 1f, 0f, 0f, 0f, 1f, 2f, 3f, 2f)
        val result = AiValidator.validate(BandCount.TEN, validLevels, preampDb = -1.5f)

        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
        assertTrue(result.checksPassed.size >= 4)
    }

    @Test
    fun `AiValidator scheitert sicher bei NaN oder Infinity Werten`() {
        val nanLevels = listOf(Float.NaN, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        val nanResult = AiValidator.validate(BandCount.TEN, nanLevels, preampDb = 0f)

        assertFalse(nanResult.isValid)
        assertTrue(nanResult.errors.any { it.contains("NaN") })

        val infLevels = listOf(Float.POSITIVE_INFINITY, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        val infResult = AiValidator.validate(BandCount.TEN, infLevels, preampDb = 0f)
        assertFalse(infResult.isValid)
    }

    @Test
    fun `AiValidator scheitert bei Bereichsueberschreitung ueber 12 dB`() {
        val outOfBounds = listOf(14f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        val result = AiValidator.validate(BandCount.TEN, outOfBounds, preampDb = 0f)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("±12 dB") })
    }

    @Test
    fun `AiValidator prueft Bandgroessen-Uebereinstimmung`() {
        val wrongSize = listOf(1f, 2f, 3f) // 3 statt 10 Bänder
        val result = AiValidator.validate(BandCount.TEN, wrongSize, preampDb = 0f)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("Erwartet 10") })
    }

    @Test
    fun `LocalRuleAiProvider erzeugt offline eine valide Zielkurve ohne unbelegte Modellangaben`() = runBlocking {
        val provider = LocalRuleAiProvider()
        val request = AiAdviceRequest(
            preferences = HearingPreference(
                genre = "Rock",
                bassPreference = "Kräftig",
                speechClarity = "Hervorgehoben",
                listeningEnvironment = "Ruhig"
            ),
            currentNoiseLevelDb = 55f,
            consentGiven = true
        )

        val result = provider.generateSuggestion(request, sampleProfile)
        assertTrue("Muss Success sein", result is AiAdviceResult.Success)

        val suggestion = (result as AiAdviceResult.Success).suggestion
        assertEquals(10, suggestion.levelsDb.size)
        assertTrue("ValidationStatus muss gültig sein", suggestion.validationStatus.isValid)
        assertEquals("Lokale Regeln (CPU)", suggestion.backendUsed)
        assertTrue("Latenz ist eine direkte Laufzeitmessung", suggestion.latencyMicros >= 0)
        assertEquals("Regelbasierter Vorschlag gibt keine Modell-Konfidenz vor", 0f, suggestion.confidence, 0f)
        assertTrue("Das Profil benennt sich als Regelvorschlag", suggestion.modelIdentifier.contains("Regel"))
    }

    @Test
    fun `LocalRuleAiProvider behandelt Mikrofonpegel als dBFS statt als dB SPL`() = runBlocking {
        val provider = LocalRuleAiProvider()
        val preference = HearingPreference(
            genre = "Allround",
            bassPreference = "Ausgewogen",
            speechClarity = "Normal",
            listeningEnvironment = "Ruhig"
        )

        val quiet = provider.generateSuggestion(
            AiAdviceRequest(preference, currentNoiseLevelDb = -60f), sampleProfile
        ) as AiAdviceResult.Success
        val moderate = provider.generateSuggestion(
            AiAdviceRequest(preference, currentNoiseLevelDb = -35f), sampleProfile
        ) as AiAdviceResult.Success
        val loud = provider.generateSuggestion(
            AiAdviceRequest(preference, currentNoiseLevelDb = -20f), sampleProfile
        ) as AiAdviceResult.Success

        assertEquals(quiet.suggestion.levelsDb[0], moderate.suggestion.levelsDb[0], 0f)
        assertEquals(0.5f, moderate.suggestion.levelsDb[5] - quiet.suggestion.levelsDb[5], 0.001f)
        assertEquals(1f, loud.suggestion.levelsDb[0] - quiet.suggestion.levelsDb[0], 0.001f)
        assertEquals(1f, loud.suggestion.levelsDb[5] - quiet.suggestion.levelsDb[5], 0.001f)
    }

    @Test
    fun `ExternalAiClient verweigert ohne Consent und bleibt im Offline-Fallback`() = runBlocking {
        val client = ExternalAiClient()
        val noConsent = client.generateSuggestion(
            AiAdviceRequest(HearingPreference(), consentGiven = false),
            sampleProfile
        )

        assertTrue(noConsent is AiAdviceResult.Error)
        val err1 = noConsent as AiAdviceResult.Error
        assertEquals("NO_CONSENT", err1.code)
        assertEquals(sampleProfile.id, err1.fallbackProfile?.id)

        val withConsent = client.generateSuggestion(
            AiAdviceRequest(HearingPreference(), consentGiven = true),
            sampleProfile
        )
        assertTrue(withConsent is AiAdviceResult.Error)
        val err2 = withConsent as AiAdviceResult.Error
        assertEquals("NOT_CONFIGURED", err2.code)
        assertEquals(sampleProfile.id, err2.fallbackProfile?.id)
    }
}
