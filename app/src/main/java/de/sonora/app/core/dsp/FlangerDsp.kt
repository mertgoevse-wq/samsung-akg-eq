package de.sonora.app.core.dsp

import kotlin.math.PI
import kotlin.math.sin

/**
 * Reiner Kotlin DSP-Flanger fuer interaktive Echtzeit-Klangmodulation.
 *
 * Eigenschaften:
 * - Variabler Kreisring-Delay-Puffer mit linearer Bruchteil-Interpolation
 * - Sinus-LFO zur Modulation der Verzoegerungszeit (0.1 Hz bis 5.0 Hz)
 * - Rueckkopplungsschleife mit sanfter Saettigung (Anti-Blowup / Soft-Clipping)
 * - Glatte Parameteruebergaenge (One-Pole Low-Pass Smoothing), um Knackgeraeusche bei Touch-Bewegungen zu verhindern
 * - Trocken-/Nass-Mischung (Dry/Wet)
 * - Keine externen oder Android-spezifischen Abhaengigkeiten, direkt auf JVM testbar
 */
class FlangerDsp(
    val sampleRate: Int = 44100,
    initialRateHz: Float = 0.5f,
    initialDepth: Float = 0.7f,
    initialFeedback: Float = 0.6f,
    initialMix: Float = 0.5f
) {
    companion object {
        const val MAX_DELAY_MS = 10f
        const val MIN_DELAY_MS = 0.5f
        const val MAX_FEEDBACK = 0.85f
    }

    // Puffergroesse fuer bis zu 10 ms Verzoegerung + Sicherheitsreserve
    private val bufferSize = (sampleRate * (MAX_DELAY_MS / 1000f)).toInt() + 16
    private val delayBuffer = FloatArray(bufferSize)
    private var writeIndex = 0

    // LFO-Zustand
    private var lfoPhase = 0.0
    private val twoPi = 2.0 * PI

    // Zielparameter (von aussen gesetzt)
    var targetRateHz: Float = initialRateHz.coerceIn(0.1f, 5.0f)
        private set
    var targetDepth: Float = initialDepth.coerceIn(0f, 1f)
        private set
    var targetFeedback: Float = initialFeedback.coerceIn(-MAX_FEEDBACK, MAX_FEEDBACK)
        private set
    var targetMix: Float = initialMix.coerceIn(0f, 1f)
        private set
    var baseDelayMs: Float = 2.0f
        set(value) {
            field = value.coerceIn(MIN_DELAY_MS, MAX_DELAY_MS - 2f)
        }

    // Aktuelle geglaettete Parameter fuer klickfreie Uebergaenge
    var currentRateHz: Float = targetRateHz
        private set
    var currentDepth: Float = targetDepth
        private set
    var currentFeedback: Float = targetFeedback
        private set
    var currentMix: Float = targetMix
        private set

    // Glaettungskoeffizient je Sample (~20ms Einstellzeit)
    private val smoothingFactor = 1.0f - kotlin.math.exp(-1.0f / (sampleRate * 0.020f))

    /**
     * Aktualisiert die Zielparameter anhand normalisierter XY-Touch-Koordinaten (0..1).
     * @param xNorm Horizontale Position: steuert Modulationsfrequenz / LFO-Rate (0.1 Hz .. 4.5 Hz)
     * @param yNorm Vertikale Position: steuert Modulationstiefe & Feedback (0.0 .. 0.82)
     */
    fun setParametersFromXy(xNorm: Float, yNorm: Float) {
        val safeX = xNorm.coerceIn(0f, 1f)
        val safeY = yNorm.coerceIn(0f, 1f)

        // X: 0.15 Hz (langsam, schwebend) bis 4.0 Hz (schnelles Wabern)
        targetRateHz = 0.15f + safeX * 3.85f

        // Y: Modulationstiefe und Feedback steigen mit Y-Position
        targetDepth = 0.2f + safeY * 0.8f
        targetFeedback = safeY * MAX_FEEDBACK
        targetMix = 0.5f // 50/50 fuer tiefste Ausloeschungskammfilter
    }

    /** Setzt manuelle Parameter mit Bereichsueberpruefung. */
    fun setParameters(rateHz: Float? = null, depth: Float? = null, feedback: Float? = null, mix: Float? = null) {
        rateHz?.let { targetRateHz = it.coerceIn(0.1f, 5.0f) }
        depth?.let { targetDepth = it.coerceIn(0f, 1f) }
        feedback?.let { targetFeedback = it.coerceIn(-MAX_FEEDBACK, MAX_FEEDBACK) }
        mix?.let { targetMix = it.coerceIn(0f, 1f) }
    }

    /**
     * Verarbeitet ein einzelnes Audiosample und liefert das geflangerte Ausgangssample.
     */
    fun processSample(input: Float): Float {
        // 1. Parameterglaettung
        currentRateHz += (targetRateHz - currentRateHz) * smoothingFactor
        currentDepth += (targetDepth - currentDepth) * smoothingFactor
        currentFeedback += (targetFeedback - currentFeedback) * smoothingFactor
        currentMix += (targetMix - currentMix) * smoothingFactor

        // 2. LFO-Schritt
        val lfoValue = sin(lfoPhase).toFloat() // -1.0 .. +1.0
        val phaseInc = (twoPi * currentRateHz) / sampleRate
        lfoPhase += phaseInc
        if (lfoPhase >= twoPi) {
            lfoPhase -= twoPi
        }

        // 3. Modulierte Verzoegerungszeit in Samples
        // z.B. Basis 2ms, Hub +- 1.5ms
        val sweepMs = 1.5f * currentDepth
        val delayMs = (baseDelayMs + sweepMs * lfoValue).coerceIn(MIN_DELAY_MS, MAX_DELAY_MS)
        val delaySamples = delayMs * (sampleRate / 1000f)

        // 4. Leseindex mit linearer Interpolation
        var readPos = writeIndex - delaySamples
        while (readPos < 0f) readPos += bufferSize
        while (readPos >= bufferSize) readPos -= bufferSize

        val index0 = readPos.toInt()
        val index1 = (index0 + 1) % bufferSize
        val frac = readPos - index0

        val delayedSample = (1f - frac) * delayBuffer[index0] + frac * delayBuffer[index1]

        // 5. Rueckkopplung mit sanfter Saettigung (verhindert digitales Uebersteuern)
        val feedbackSample = delayedSample * currentFeedback
        val saturatedFeedback = softSaturate(feedbackSample)

        // 6. In Verzoegerungspuffer schreiben
        val toBuffer = input + saturatedFeedback
        delayBuffer[writeIndex] = softSaturate(toBuffer)
        writeIndex = (writeIndex + 1) % bufferSize

        // 7. Ausgangsmischung (Dry / Wet)
        return (1f - currentMix) * input + currentMix * delayedSample
    }

    /**
     * Verarbeitet einen Puffer von Samples in-place.
     */
    fun process(samples: FloatArray, count: Int = samples.size) {
        val safeCount = count.coerceIn(0, samples.size)
        for (i in 0 until safeCount) {
            samples[i] = processSample(samples[i])
        }
    }

    /** Setzt den internen Puffer zurueck. */
    fun reset() {
        delayBuffer.fill(0f)
        writeIndex = 0
        lfoPhase = 0.0
    }

    /**
     * Sanfter kubischer Soft-Clipper: fuer |x| < 1 bleibt Signal fast linear,
     * darueber flacht es asymptotisch ab.
     */
    private fun softSaturate(x: Float): Float {
        return when {
            x > 1.5f -> 1.0f
            x < -1.5f -> -1.0f
            x > 0.7f -> x - (x - 0.7f) * 0.4f
            x < -0.7f -> x - (x + 0.7f) * 0.4f
            else -> x
        }
    }
}
