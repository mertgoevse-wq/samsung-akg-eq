package de.sonora.app.visualizer

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Reines, Android-unabhängiges Reducer-Objekt für FFT-Daten.
 *
 * Androids Visualizer liefert FFT-Bytes gemäß Spezifikation:
 *   - fft[0]: Realteil DC (0 Hz), Imaginärteil ist 0
 *   - fft[1]: Realteil Nyquist (Fs / 2), Imaginärteil ist 0
 *   - fft[2*k]: Realteil für Frequenz k * Fs / n (1 <= k < n/2)
 *   - fft[2*k + 1]: Imaginärteil für Frequenz k * Fs / n (1 <= k < n/2)
 * Alle Werte sind signed 8-Bit Ganzzahlen (-128 bis 127).
 *
 * Wir berechnen hieraus n/2 + 1 Magnituden-Bins (DC bis Nyquist)
 * und aggregieren sie in [BAND_COUNT] logarithmische Frequenzbänder
 * mit konfigurierbarem Attack/Release-Smoothing.
 */
object FftReducer {

    /** Standard-Bandanzahl für das Sonora Partikelfeld. */
    const val BAND_COUNT = 8

    /** Standard-Attack (wie schnell Energie steigt: 0..1). */
    const val DEFAULT_ATTACK = 0.35f

    /** Standard-Release (wie schnell Energie fällt: 0..1). */
    const val DEFAULT_RELEASE = 0.12f

    /**
     * Berechnet die Magnitude pro FFT-Bin aus dem Roh-Byte-Array.
     * Liefert ein FloatArray der Länge n/2 + 1 (Bin 0 = DC, Bin n/2 = Nyquist).
     * Gibt null zurück bei leeren oder ungültigen Daten (< 2 Bytes).
     */
    fun magnitudes(fft: ByteArray): FloatArray? {
        if (fft.isEmpty() || fft.size < 2) return null
        val n = fft.size
        val binCount = n / 2 + 1
        val result = FloatArray(binCount)

        // Bin 0: DC (0 Hz) -> fft[0] (Real), Imag = 0
        val dcReal = fft[0].toInt()
        result[0] = (abs(dcReal) / 128f).coerceIn(0f, 1f)

        // Bin n/2: Nyquist (Fs / 2) -> fft[1] (Real), Imag = 0
        val nyquistReal = fft[1].toInt()
        result[binCount - 1] = (abs(nyquistReal) / 128f).coerceIn(0f, 1f)

        // Bins 1 until n/2: k = 1 until n/2
        for (k in 1 until n / 2) {
            val real = fft[k * 2].toInt()
            val imag = fft[k * 2 + 1].toInt()
            val mag = hypot(real.toFloat(), imag.toFloat())
            result[k] = (mag / 128f).coerceIn(0f, 1f)
        }

        return result
    }

    /**
     * Gruppiert Magnitudes in [BAND_COUNT] logarithmische Bänder.
     * Bin 0 (DC) wird ignoriert, um Offset-Verfälschungen zu vermeiden.
     * Nyquist (letzter Bin) wird im höchsten Band erfasst.
     */
    fun bandEnergies(magnitudes: FloatArray?, sampleRateHz: Int = 44100): FloatArray {
        val bands = FloatArray(BAND_COUNT)
        if (magnitudes == null || magnitudes.isEmpty()) return bands
        val binCount = magnitudes.size
        if (binCount < 2) return bands

        // Logarithmische Bandgrenzen von ~60 Hz bis Nyquist
        val minFreq = 60f
        val maxFreq = (sampleRateHz / 2).coerceAtLeast(1000).toFloat()
        val logMin = ln(minFreq)
        val logMax = ln(maxFreq)
        val step = (logMax - logMin) / BAND_COUNT

        for (band in 0 until BAND_COUNT) {
            val fLow = if (band == 0) minFreq else exp(logMin + band * step)
            val fHigh = if (band == BAND_COUNT - 1) maxFreq else exp(logMin + (band + 1) * step)

            // Mappe Frequenzen auf Bin-Indizes (DC bei Bin 0 wird ausgeschlossen)
            val binLow = ((fLow / maxFreq) * (binCount - 1)).roundToInt().coerceIn(1, binCount - 1)
            val binHigh = ((fHigh / maxFreq) * (binCount - 1)).roundToInt().coerceIn(binLow, binCount - 1)

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
     * Wendet Attack/Release-Smoothing auf die Bandwerte an.
     * @param previous vorherige geglättete Werte (null = direkt übernehmen)
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
        if (previous == null) {
            return FloatArray(current.size) { current[it].coerceIn(0f, 1f) }
        }
        val safeAttack = attack.coerceIn(0f, 1f)
        val safeRelease = release.coerceIn(0f, 1f)
        val result = FloatArray(current.size)
        for (i in current.indices) {
            val prev = previous.getOrNull(i) ?: 0f
            val target = current.getOrElse(i) { 0f }.coerceIn(0f, 1f)
            val coef = if (target > prev) safeAttack else safeRelease
            result[i] = (prev + (target - prev) * coef).coerceIn(0f, 1f)
        }
        return result
    }

    /**
     * Volle Pipeline: Roh-FFT -> geglättete Bandenergien.
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
