package de.sonora.app.visualizer

import de.sonora.app.visualizer.FftReducer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class FftReducerTest {

    @Test
    fun `leere FFT-Daten liefern Null-Magnitudes`() {
        assertNull(FftReducer.magnitudes(ByteArray(0)))
    }

    @Test
    fun `zu kleine FFT-Daten liefern Null`() {
        assertNull(FftReducer.magnitudes(ByteArray(1)))
    }

    @Test
    fun `Stille liefert Null-Baender`() {
        val fft = ByteArray(64) // all zeros -> magnitude 0 after centering? zeros -> (0-128) magnitude
        // Actually zero bytes -> real= -128? No: (0 & 0xFF)=0, 0-128 = -128. Magnitude 128/128=1.
        // So use 128 bytes (neutral) instead:
        val neutral = ByteArray(64) { 128.toByte() }
        val bands = FftReducer.bandEnergies(FftReducer.magnitudes(neutral))
        bands.forEach { assertEquals(0f, it, 0.01f) }
    }

    @Test
    fun `DC-Bin wird bei bandEnergies ignoriert`() {
        // First bin (DC) set to max, rest zero -> should not produce high energy in any band
        val fft = ByteArray(64) { 0 }
        fft[0] = 127 // DC real max
        fft[1] = 0
        val mags = FftReducer.magnitudes(fft)!!
        val bands = FftReducer.bandEnergies(mags)
        // DC is bin 0, bandEnergies starts at bin 1, so energy should be low
        assertTrue(bands.max() < 0.5f)
    }

    @Test
    fun `voll Amplitude erzeugt nicht-NaN und begrenzte Werte`() {
        val fft = ByteArray(64) { 255.toByte() }
        val reduced = FftReducer.reduce(fft, null)
        reduced.forEach {
            assertFalse(it.isNaN())
            assertFalse(it.isInfinite())
            assertTrue(it in 0f..1f)
        }
    }

    @Test
    fun `smooth mit null previous uebernimmt current direkt`() {
        val current = floatArrayOf(0.5f, 0.8f, 0.2f)
        val result = FftReducer.smooth(null, current)
        assertArrayEquals(current, result, 0.0001f)
    }

    @Test
    fun `smooth attack ist schneller als release`() {
        val prev = floatArrayOf(0.5f)
        val up = FftReducer.smooth(prev, floatArrayOf(1.0f), attack = 0.5f, release = 0.1f)
        val down = FftReducer.smooth(prev, floatArrayOf(0.0f), attack = 0.5f, release = 0.1f)
        assertTrue("Aufstieg groesser als Abstieg", (up[0] - 0.5f) > (0.5f - down[0]))
    }

    @Test
    fun `reduce liefert BAND_COUNT Werte`() {
        val fft = ByteArray(128)
        val result = FftReducer.reduce(fft, null)
        assertEquals(FftReducer.BAND_COUNT, result.size)
    }

    @Test
    fun `bandEnergies bei null magnitudes liefert Null-Baender`() {
        val bands = FftReducer.bandEnergies(null)
        assertEquals(FftReducer.BAND_COUNT, bands.size)
        bands.forEach { assertEquals(0f, it, 0.001f) }
    }

}
