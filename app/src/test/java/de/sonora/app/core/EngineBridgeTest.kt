package de.sonora.app.core

import de.sonora.app.audio.SonoraEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineBridgeTest {

    @Test
    fun `Kurve wird auf Geraetebaender abgebildet`() {
        val device = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
        val curve = EqCurve(BandCount.TEN, listOf(6f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f))

        val plan = SonoraEngine.plan(curve, 0f, device)

        assertEquals(10, plan.size)
        assertEquals(6f, plan.getValue(device.indexOf(31)), 0.01f)
    }

    @Test
    fun `Preamp oberhalb der Grenze wird zurueckgenommen`() {
        val device = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
        val curve = EqCurve(BandCount.TEN, List(10) { 12f })

        assertTrue(SonoraEngine.applyWithLimit(curve, 6f, device).clipped)
    }

    @Test
    fun `leeres Geraet liefert einen leeren Plan`() {
        assertTrue(SonoraEngine.plan(EqCurve(), 0f, emptyList()).isEmpty())
    }
}
