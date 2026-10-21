package de.sonora.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EqModelTest {

    @Test
    fun `zehn Baender nutzen das bekannte Raster`() {
        assertEquals(
            listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000),
            bandFrequencies(BandCount.TEN)
        )
    }

    @Test
    fun `31 Baender sind streng steigend und enden bei 20 kHz`() {
        val f = bandFrequencies(BandCount.THIRTY_ONE)
        assertEquals(31, f.size)
        assertTrue(f.zipWithNext().all { (a, b) -> a < b })
        assertTrue(f.first() <= 20)
        assertTrue(f.last() >= 20000)
    }

    @Test
    fun `64 Baender sind streng steigend`() {
        val f = bandFrequencies(BandCount.SIXTY_FOUR)
        assertEquals(64, f.size)
        assertTrue(f.zipWithNext().all { (a, b) -> a < b })
    }

    @Test
    fun `Wechsel von 10 auf 31 interpoliert statt zu springen`() {
        val curve = EqCurve(
            BandCount.TEN,
            listOf(6f, 6f, 6f, 6f, 6f, 0f, 0f, 0f, 0f, 0f)
        )
        val wide = curve.withBandCount(BandCount.THIRTY_ONE)
        assertEquals(31, wide.levelsDb.size)
        assertEquals(6f, wide.levelsDb[0], 0.01f)
        assertEquals(0f, wide.levelsDb[30], 0.01f)
    }

    @Test
    fun `Rueckwechsel auf 10 Baender erhaelt die Kurve`() {
        val curve = EqCurve(BandCount.THIRTY_ONE, List(31) { 3f })
        assertTrue(
            curve.withBandCount(BandCount.TEN).levelsDb.all {
                kotlin.math.abs(it - 3f) < 0.01f
            }
        )
    }

    @Test
    fun `normalized kappt Werte auf minus 12 bis plus 12`() {
        val c = EqCurve(
            BandCount.TEN,
            listOf(40f, -40f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        ).normalized()
        assertEquals(12f, c.levelsDb[0], 0.001f)
        assertEquals(-12f, c.levelsDb[1], 0.001f)
    }
}
