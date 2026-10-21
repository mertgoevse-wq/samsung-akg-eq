package de.sonora.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlindTestTest {
    @Test fun `balancierte zufallsreihenfolge enthaelt gleich viele erste positionen`() {
        val test = BlindTest(rounds = 10, orderSeed = 7L)
        val orders = (0 until test.totalRounds()).map(test::nextOrder)
        assertEquals(5, orders.count { it == BlindOrder.SONORA_FIRST })
        assertEquals(5, orders.count { it == BlindOrder.ORIGINAL_FIRST })
        assertEquals(orders, (0 until test.totalRounds()).map(BlindTest(rounds = 10, orderSeed = 7L)::nextOrder))
    }

    @Test fun `beide bedingungen werden getrennt bewertet und erst vollstaendig gemittelt`() {
        val test = BlindTest(rounds = 2, orderSeed = 0L)
        assertNull(test.average())
        assertNull(test.originalAverage())
        repeat(2) { round ->
            when (test.nextOrder(round)) {
                BlindOrder.SONORA_FIRST -> test.recordPair(8, 4)
                BlindOrder.ORIGINAL_FIRST -> test.recordPair(4, 8)
            }
        }
        assertEquals(8.0, test.average()!!, 0.001)
        assertEquals(4.0, test.originalAverage()!!, 0.001)
        assertTrue(test.isComplete())
    }

    @Test(expected = IllegalStateException::class)
    fun `bewertung nach voller anzahl runden wird abgelehnt`() {
        val test = BlindTest(rounds = 1)
        test.recordPair(5, 5)
        test.recordPair(5, 5)
    }

    @Test fun `offene runde ist nicht vollstaendig`() {
        val test = BlindTest(rounds = 10)
        repeat(9) { test.recordPair(8, 8) }
        assertFalse(test.isComplete())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `bewertung ausserhalb eins bis zehn wird abgelehnt`() {
        BlindTest().recordPair(11, 5)
    }
}
