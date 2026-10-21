package de.sonora.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InterviewTest {
    @Test fun `interview hat hoechstens acht fragen und keine frequenzsprache`() {
        assertEquals(8, InterviewCatalog.questions.size)
        InterviewCatalog.questions.forEach { question ->
            assertFalse(Regex("\\d+\\s*(Hz|kHz)", RegexOption.IGNORE_CASE).containsMatchIn(question.prompt))
            assertTrue(question.options.size >= 3)
            question.options.forEach { assertEquals(10, it.curve.size) }
        }
    }

    @Test fun `basswunsch erzeugt bassbetonte kurve`() {
        val question = InterviewCatalog.questions.first { it.id == "bass" }
        val curve = InterviewEvaluator.evaluate(listOf(InterviewAnswer(question.id, "bass")))
        assertTrue(curve.levelsDb.first() > 0f)
    }

    @Test fun `uebersprungene antworten ergeben ein neutrales profil`() {
        assertTrue(InterviewEvaluator.evaluate(emptyList()).levelsDb.all { it == 0f })
    }

    @Test fun `auswertung passt zur gewaelten bandzahl und bleibt im sicheren rahmen`() {
        val answers = InterviewCatalog.questions.map { InterviewAnswer(it.id, it.options.first().id) }
        val result = InterviewEvaluator.evaluateWithBandCount(answers, BandCount.THIRTY_ONE)
        assertEquals(31, result.levelsDb.size)
        assertTrue(result.levelsDb.all { it in -12f..12f })
    }
}
