package de.sonora.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LimiterTest {

    @Test
    fun `unterhalb der Grenze wird nichts zurueckgenommen`() {
        val r = Limiter.limit(sumDb = -6f, preampDb = 2f)
        assertFalse(r.clipped)
        assertEquals(0f, r.reducedByDb, 0.001f)
        assertEquals(2f, r.gainDb, 0.001f)
    }

    @Test
    fun `ueber der Grenze wird die Verstaerkung zurueckgenommen`() {
        val r = Limiter.limit(sumDb = 6f, preampDb = 2f)
        assertTrue(r.clipped)
        assertEquals(8f, r.reducedByDb, 0.001f)
        assertEquals(-6f, r.gainDb, 0.001f)
    }

    @Test
    fun `Preamp allein ueber der Grenze wird gekappt`() {
        val r = Limiter.limit(sumDb = -2f, preampDb = 9f)
        assertTrue(r.clipped)
        assertEquals(7f, r.reducedByDb, 0.001f)
    }

    @Test
    fun `negative Preamp senkt die Summe ohne Clip-Hinweis`() {
        val r = Limiter.limit(sumDb = 4f, preampDb = -6f)
        assertFalse(r.clipped)
        assertEquals(-6f, r.gainDb, 0.001f)
    }
}
