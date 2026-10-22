package de.sonora.app.core

import de.sonora.app.audio.EngineStatus
import de.sonora.app.audio.SonoraEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineControlTest {

    @Test
    fun `EngineStatus unterscheidet angefordert, technisch verfuegbar und bestaetigt`() {
        val active = EngineStatus.Active(
            bands = 10,
            processingRequested = true,
            processingAvailable = true,
            processingConfirmed = true
        )
        assertTrue(active.processingRequested)
        assertTrue(active.processingAvailable)
        assertTrue(active.processingConfirmed)

        val unavailable = EngineStatus.Unavailable(
            reason = "OEM blockiert Session 0",
            processingRequested = true,
            processingAvailable = false,
            processingConfirmed = false
        )
        assertTrue(unavailable.processingRequested)
        assertFalse(unavailable.processingAvailable)
        assertFalse(unavailable.processingConfirmed)
    }

    @Test
    fun `plan und applyWithLimit schuetzen vor NaN und Infinity`() {
        val nanCurve = EqCurve(BandCount.TEN, listOf(Float.NaN, Float.POSITIVE_INFINITY, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f))
        val deviceCenters = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

        val planned = SonoraEngine.plan(nanCurve, preampDb = Float.NaN, deviceCenters = deviceCenters)
        planned.values.forEach { db ->
            assertFalse("Geplante dB dürfen kein NaN sein", db.isNaN())
            assertFalse("Geplante dB dürfen nicht unendlich sein", db.isInfinite())
            assertTrue("Geplante dB müssen im Bereich ±12 dB liegen", db in -12f..12f)
        }

        val limited = SonoraEngine.applyWithLimit(nanCurve, preampDb = Float.POSITIVE_INFINITY, deviceCenters = deviceCenters)
        assertFalse("Preamp darf kein NaN sein", limited.gainDb.isNaN())
        assertFalse("Preamp darf nicht unendlich sein", limited.gainDb.isInfinite())
    }

    @Test
    fun `leere Geraetebaender fuehren zu leerer Planung ohne Exception`() {
        val curve = EqCurve(BandCount.TEN, List(10) { 2f })
        val planned = SonoraEngine.plan(curve, 0f, emptyList())
        assertTrue(planned.isEmpty())
    }
}
