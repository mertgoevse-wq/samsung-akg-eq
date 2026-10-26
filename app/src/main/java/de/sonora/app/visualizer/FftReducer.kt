package de.sonora.app.visualizer

/**
 * Reines, Android-unabhaengiges Reducer-Objekt fuer FFT-Daten.
 *
 * Androids Visualizer liefert FFT-Bytes im folgenden Format (pro Bin, 2 Bytes):
 *   - Byte 0: Realteil (signed)
 *   - Byte 1: Imaginaerteil (signed)
 * DC (Bin 0) und Nyquist (letzter Bin) sind Realwerte (Imaginaerteil = 0).
 *
 * Wir wandeln das in normalisierte Energie pro logarithmisch gruppiertem Band
 * (Bass bis Hoehen) um und wenden Attack/Release-Smoothing an.
 */
object FftReducer {

    /** Standard-Bandanzahl fuer das Partikelnetz. */
    const val BAND_COUNT = 8

    /** Standard-Attack (wie schnell Energie steigt). */
    const val DEFAULT_ATTACK = 0.35f

    /** Standard-Release (wie schnell Energie faellt). */
    const val DEFAULT_RELEASE = 0.12f

    /**
     * Berechnet die Magnitude pro FFT-Bin aus dem Roh-Byte-Array.
     * Gibt null zurueck bei leeren/ungueltigen Daten.
     */
    fun magnitudes(fft: ByteArray): FloatArray? {
        if (fft.isEmpty() || fft.size < 2) return null
        val binCount = fft.size / 2
        val result = FloatArray(binCount)
        for (i in 0 until binCount) {
            // Android Visualizer liefert signed 8-bit Real/Imag-Werte.
            val real = fft[i * 2].toInt()
            val imag = if (i == 0 || i == binCount - 1) 0 else fft[i * 2 + 1].toInt()
            val mag = kotlin.math.sqrt((real * real + imag * imag).toDouble()).toFloat()
            result[i] = (mag / 128f).coerceIn(0f, 1f)
        }
        // DC-Bin (Bin 0) ist oft ein Offset; wir behalten ihn, aber markieren ihn
        // nicht als Peak. Der Reducer filtert DC separat (siehe [bandEnergies]).
        return result
    }

    /**
     * Gruppiert Magnitudes in [BAND_COUNT] logarithmische Baender.
     * Bin 0 (DC) wird ignoriert, um Offset-Fehler zu vermeiden.
     */
    fun bandEnergies(magnitudes: FloatArray?, sampleRateHz: Int = 44100): FloatArray {
        val bands = FloatArray(BAND_COUNT)
        if (magnitudes == null || magnitudes.isEmpty()) return bands
        val binCount = magnitudes.size
        if (binCount < 2) return bands

        // Logarithmische Bandgrenzen von ~60 Hz bis Nyquist
        val minFreq = 60f
        val maxFreq = (sampleRateHz / 2).coerceAtLeast(1000).toFloat()
        val logMin = kotlin.math.ln(minFreq)
        val logMax = kotlin.math.ln(maxFreq)
        val step = (logMax - logMin) / BAND_COUNT

        for (band in 0 until BAND_COUNT) {
            val fLow = kotlin.math.exp(logMin + band * step)
            val fHigh = kotlin.math.exp(logMin + (band + 1) * step)
            // Mappe Frequenz auf Bin-Index
            val binLow = ((fLow / maxFreq) * (binCount - 1)).toInt().coerceIn(1, binCount - 1)
            val binHigh = ((fHigh / maxFreq) * (binCount - 1)).toInt().coerceIn(binLow, binCount - 1)
            if (binHigh < binLow) continue
            var sum = 0f
            var count = 0
            for (b in binLow..binHigh) {
                sum += magnitudes[b]
                count++
            }
            bands[band] = if (count > 0) (sum / count).coerceIn(0f, 1f) else 0f
        }
        return bands
    }

    /**
     * Wendet Attack/Release-Smoothing auf die aktuellen Bandwerte an.
     * @param previous vorherige geglaettete Werte (null = keine Glättung, direkt uebernehmen)
     * @param current neue rohe Bandwerte
     * @param attack Anstiegsgeschwindigkeit (0..1)
     * @param release Abfallgeschwindigkeit (0..1)
     */
    fun smooth(
        previous: FloatArray?,
        current: FloatArray,
        attack: Float = DEFAULT_ATTACK,
        release: Float = DEFAULT_RELEASE
    ): FloatArray {
        val safeAttack = attack.coerceIn(0f, 1f)
        val safeRelease = release.coerceIn(0f, 1f)
        val result = FloatArray(current.size)
        for (i in current.indices) {
            val prev = previous?.getOrNull(i) ?: 0f
            val target = current.getOrElse(i) { 0f }.coerceIn(0f, 1f)
            val coef = if (target > prev) safeAttack else safeRelease
            result[i] = (prev + (target - prev) * coef).coerceIn(0f, 1f)
        }
        return result
    }

    /**
     * Volle Pipeline: Roh-FFT -> geglaettete Bandenergien.
     */
    fun reduce(
        fft: ByteArray,
        previous: FloatArray?,
        sampleRateHz: Int = 44100,
        attack: Float = DEFAULT_ATTACK,
        release: Float = DEFAULT_RELEASE
    ): FloatArray {
        val mags = magnitudes(fft)
        val bands = bandEnergies(mags, sampleRateHz)
        return smooth(previous, bands, attack, release)
    }
}
