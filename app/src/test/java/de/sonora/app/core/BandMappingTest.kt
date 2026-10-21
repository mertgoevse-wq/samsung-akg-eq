package de.sonora.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BandMappingTest {

    @Test
    fun `jedes Geraeteband wird genau einmal belegt`() {
        val device = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
        val ui = bandFrequencies(BandCount.TEN)
        val map = BandMapping.mapToDevice(ui, device)
        assertEquals(device.size, map.size)
    }

    @Test
    fun `die naechstgelegene Frequenz gewinnt`() {
        val device = listOf(50, 100, 200, 400, 800, 1600, 3200, 6400, 12800, 20000)
        val ui = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
        val map = BandMapping.mapToDevice(ui, device)
        assertEquals(1, map[0])
        assertEquals(2, map[1])
    }

    @Test
    fun `ein UI-Band bekommt keinen zweiten Geraeteband-Wert`() {
        val device = listOf(100, 200, 300)
        val ui = listOf(90, 95, 100, 105, 110)
        val map = BandMapping.mapToDevice(ui, device)
        assertEquals(map.keys.size, map.values.toSet().size)
    }

    @Test
    fun `leere Geraeteliste liefert eine leere Abbildung`() {
        assertTrue(BandMapping.mapToDevice(listOf(31), emptyList()).isEmpty())
    }
}
