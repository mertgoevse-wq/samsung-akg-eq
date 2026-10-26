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
        val silence = ByteArray(64) { 0 }
        val bands = FftReducer.bandEnergies(FftReducer.magnitudes(silence))
        bands.forEach { assertEquals(0f, it, 0.01f) }
    }

    @Test
    fun `DC-Bin wird bei bandEnergies ignoriert`() {
        // DC-Real (fft[0]) auf Maximum, Rest Null -> kein Band darf hohe Energie aufweisen
        val fft = ByteArray(64) { 0 }
        fft[0] = 127 // DC real max
        fft[1] = 0   // Nyquist zero
        val mags = FftReducer.magnitudes(fft)!!
        assertEquals(0.992f, mags[0], 0.01f) // DC magnitude captured
        val bands = FftReducer.bandEnergies(mags)
        // DC ist Bin 0, bandEnergies startet bei Bin >= 1, daher alle Bänder 0
        bands.forEach { assertEquals(0f, it, 0.001f) }
    }

    @Test
    fun `Nyquist-Bin wird im hoechsten Band erfasst`() {
        // Nyquist-Real (fft[1]) auf Maximum, Rest Null
        val fft = ByteArray(64) { 0 }
        fft[1] = 127 // Nyquist real max
        val mags = FftReducer.magnitudes(fft)!!
        val nyquistBinIndex = mags.size - 1
        assertEquals(0.992f, mags[nyquistBinIndex], 0.01f)
        val bands = FftReducer.bandEnergies(mags)
        // Band 7 (höchstes Band) muss Energie haben, während tiefe Bänder 0 sind
        assertTrue("Höchstes Band muss Energie enthalten", bands[FftReducer.BAND_COUNT - 1] > 0.01f)
        assertEquals(0f, bands[0], 0.001f)
        assertEquals(0f, bands[1], 0.001f)
    }

    @Test
    fun `mittlere Frequenz regt entsprechendes Band an`() {
        // 64 Bytes FFT = 33 Bins (0..32). Bin 8 liegt im oberen Mittenbereich.
        val fft = ByteArray(64) { 0 }
        fft[2 * 8] = 100 // Realwert für Bin 8
        fft[2 * 8 + 1] = 0 // Imag für Bin 8
        val mags = FftReducer.magnitudes(fft)!!
        val bands = FftReducer.bandEnergies(mags)
        // Bänder 0 und 1 (Tiefbass) müssen 0 sein, ein Mittenband muss ausschlagen
        assertEquals(0f, bands[0], 0.001f)
        assertTrue("Mittenband muss Peak haben", bands.max() > 0.05f)
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
    fun `smooth konvergiert ueber mehrere Frames`() {
        var current = floatArrayOf(0.0f)
        val target = floatArrayOf(1.0f)
        for (frame in 0 until 10) {
            current = FftReducer.smooth(current, target, attack = 0.4f, release = 0.1f)
        }
        assertTrue("Wert nähert sich 1.0 an", current[0] > 0.95f)
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
