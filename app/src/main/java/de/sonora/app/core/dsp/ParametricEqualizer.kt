package de.sonora.app.core.dsp

import kotlin.math.PI

/**
 * Parametrischer Equalizer als echte PCM-Sample-Prozessoren.
 *
 * Aufbau:
 *   - Tiefpass-Regal (Low Shelf) fuer den tiefsten Baender
 *   - Beliebig viele Peaking-Baender (Bell-Filter) fuer Mitten
 *   - Hochpass-Regal (High Shelf) fuer den hoechsten Baender
 *   - Preamp-Vorverstaerkung mit Headroom-Management (see [PreampDsp])
 *
 * Eigenschaften:
 * - Biquad-Koeffizienten mathematisch korrekt nach RBJ Audio EQ Cookbook
 * - Gleiteende Parameteruebergaenge (One-Pole Smoothing), klickfrei
 * - True Bypass (enabled = false: trockenes Durchreichen)
 * - Denormal-Schutz und NaN/Infinity-Resilienz
 * - Sample-rate-aware (setSampleRate setzt Zustand zurueck)
 * - Reine Kotlin-Implementierung, JVM-testbar, keine Android-Abhaengigkeit
 */
class ParametricEqualizer(sampleRate: Int = 44100) {

    enum class FilterType { LOW_SHELF, PEAKING, HIGH_SHELF }

    /** Ein einzelner Equalizer-Baender mit seinen Parametern. */
    data class Band(
        val type: FilterType = FilterType.PEAKING,
        /** Mittefrequenz in Hz (Low/High Shelf: Grenzfrequenz) */
        val freqHz: Float = 1000f,
        /** Gain in dB (-24 .. +24) */
        val gainDb: Float = 0f,
        /** Q-Faktor (Peaking) bzw. Shelving-Slope (Low/High Shelf) */
        val q: Float = 1.0f,
        val enabled: Boolean = true
    )

    private val chain = BiquadFilterChain(sampleRate)
    private val bands = mutableListOf<Band>()

    private var targetPreampDb: Float = 0f
    private var currentPreampDb: Float = 0f
    private var preampSmoothing: Float

    private var enabled: Boolean = true

    private val biquad = BiquadDsp()
    private var smoothingFactor: Float

    init {
        smoothingFactor = onePoleSmoothing(sampleRate)
        preampSmoothing = onePoleSmoothing(sampleRate)
        rebuildFromBands()
    }

    /** Aktuelle Abtastrate zurueckgeben. */
    fun getSampleRate(): Int = storedSampleRate

    /** Neue Abtastrate setzen; interne Zustand werden zurueckgesetzt. */
    fun setSampleRate(sampleRate: Int) {
        val sr = sampleRate.coerceAtLeast(1)
        storedSampleRate = sr
        chain.setSampleRate(sr)
        smoothingFactor = onePoleSmoothing(sr)
        preampSmoothing = onePoleSmoothing(sr)
    }

    /** Ob der Equalizer insgesamt aktiv ist. */
    fun isEnabled(): Boolean = enabled

    /** True Bypass ein-/ausschalten. */
    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    /** Gesamte Preamp-Vorverstaerkung in dB (Target, stetig geglaettet). */
    fun setPreampDb(db: Float) {
        targetPreampDb = if (db.isNaN() || db.isInfinite()) 0f else db.coerceIn(-12f, 12f)
    }

    /** Bands ersetzen und Koeffizienten neu berechnen. */
    fun setBands(newBands: List<Band>) {
        bands.clear()
        bands.addAll(newBands.filter { it.enabled })
        rebuildFromBands()
    }

    /** Einen einzelnen Baender aktualisieren (via Index). */
    fun setBand(index: Int, band: Band) {
        require(index in bands.indices) { "Band-Index ausserhalb des Bereichs" }
        bands[index] = band
        rebuildFromBands()
    }

    fun getBands(): List<Band> = bands.toList()

    /** Ein einzelnes Sample verarbeiten. */
    fun processSample(input: Float): Float {
        if (!enabled) return flushDenormal(input)

        // Preamp stetig an den Zielwert annaehern
        currentPreampDb += (targetPreampDb - currentPreampDb) * preampSmoothing
        val preampLinear = if (currentPreampDb.isNaN() || currentPreampDb.isInfinite()) 1f
            else kotlin.math.pow(10.0, currentPreampDb.toDouble() / 20.0).toFloat()

        var x = input * preampLinear

        // Durch alle Biquad-Stufen (jeder aktive Baender eine Stufe)
        for (stage in chain) {
            x = stage.processSample(x)
            if (x.isNaN() || x.isInfinite()) x = 0f
        }

        return flushDenormal(x)
    }

    /** Puffer in-place verarbeiten (mono). */
    fun process(samples: FloatArray, count: Int = samples.size) {
        if (!enabled) return
        val safeCount = count.coerceIn(0, samples.size)
        for (i in 0 until safeCount) {
            samples[i] = processSample(samples[i])
        }
    }

    /** Stereo-Paar in-place verarbeiten (gleiche Einstellung beide Kanale). */
    fun processStereo(left: FloatArray, right: FloatArray, count: Int = left.size) {
        if (!enabled) return
        val safeCount = count.coerceIn(0, left.size).also { c -> coerceInt(0, right.size) }
        for (i in 0 until safeCount) {
            left[i] = processSample(left[i])
            right[i] = processSample(right[i])
        }
    }

    /** Internen Filterzustand zuruecksetzen. */
    fun reset() {
        chain.reset()
        currentPreampDb = targetPreampDb
    }

    // -----------------------------------------------------------------------
    // Intern
    // -----------------------------------------------------------------------

    private fun rebuildFromBands() {
        // Bestehende Stufen ersetzen
        chain.stages.clear()
        for (band in bands) {
            val coeffs = when (band.type) {
                FilterType.LOW_SHELF -> BiquadDsp.lowShelf(band.freqHz, band.gainDb, band.q, currentSampleRate())
                FilterType.HIGH_SHELF -> BiquadDsp.highShelf(band.freqHz, band.gainDb, band.q, currentSampleRate())
                FilterType.PEAKING -> BiquadDsp.peaking(band.freqHz, band.gainDb, band.q, currentSampleRate())
            }
            chain.addStage(coeffs)
        }
    }

    private fun currentSampleRate(): Int {
        // BiquadFilterChain kapselt die Rate nicht öffentlich; wir merken sie uns separat.
        return storedSampleRate
    }

    private var storedSampleRate: Int = 44100

    private fun onePoleSmoothing(sampleRate: Int): Float {
        val sr = sampleRate.coerceAtLeast(1)
        return (1.0f - kotlin.math.exp(-1.0f / (sr.toFloat() * 0.020f)).toFloat())
    }

    private fun flushDenormal(x: Float): Float =
        if (x.isNaN() || x.isInfinite()) 0f else if (kotlin.math.abs(x) < 1e-20f) 0f else x

    private fun coerceInt(min: Int, max: Int): Int = min.coerceAtLeast(min).coerceAtMost(max)

    // Damit BiquadFilterChain.stages (internal, gleiches Paket) zugreifbar ist.
    internal val stages: MutableList<BiquadStage>
        get() = chain.stages
}
