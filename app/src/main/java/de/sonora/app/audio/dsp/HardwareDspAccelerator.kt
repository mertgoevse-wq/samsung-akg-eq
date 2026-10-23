package de.sonora.app.audio.dsp

import androidx.compose.runtime.Immutable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

enum class AccelerationBackend {
    VECTOR_CPU
}

@Immutable
data class DspHardwareState(
    val backend: AccelerationBackend,
    val backendName: String,
    val isHardwareAccelerated: Boolean,
    val latencyMicros: Long,
    val details: String
)

@Immutable
data class BiquadCoefficients(
    val b0: Float,
    val b1: Float,
    val b2: Float,
    val a1: Float,
    val a2: Float
) {
    fun isValid(): Boolean =
        !b0.isNaN() && !b0.isInfinite() &&
        !b1.isNaN() && !b1.isInfinite() &&
        !b2.isNaN() && !b2.isInfinite() &&
        !a1.isNaN() && !a1.isInfinite() &&
        !a2.isNaN() && !a2.isInfinite()
}

@Immutable
data class CrossfeedMatrix(
    val directGain: Float,
    val crossfeedGain: Float,
    val delaySamples: Int,
    val lowpassCutoffHz: Float
)

@Immutable
data class DrcParameters(
    val thresholdDb: Float,
    val ratio: Float,
    val kneeWidthDb: Float,
    val makeupGainDb: Float
)

/**
 * Computes DSP parameters on the CPU with strict NaN and Infinity protection.
 * No NNAPI/NPU or GPU compute backend is currently connected.
 */
object HardwareDspAccelerator {

    private const val SAMPLE_RATE = 48000f

    @Volatile
    private var lastCoefficientCalculationUs: Long? = null

    fun getHardwareState(): DspHardwareState {
        val measuredLatencyUs = lastCoefficientCalculationUs
        return DspHardwareState(
            backend = AccelerationBackend.VECTOR_CPU,
            backendName = "CPU (Kotlin)",
            isHardwareAccelerated = false,
            latencyMicros = measuredLatencyUs ?: 0L,
            details = if (measuredLatencyUs == null) {
                "Die Filterkoeffizienten werden in Kotlin auf der CPU berechnet. NNAPI/NPU und GPU-Compute sind nicht eingebunden."
            } else {
                "CPU-Laufzeit der letzten Koeffizientenberechnung: $measuredLatencyUs µs. NNAPI/NPU und GPU-Compute sind nicht eingebunden."
            }
        )
    }

    /**
     * Computes peaking EQ biquad filter coefficients for a frequency band.
     * Guaranteed to protect against NaN, Infinity, division-by-zero, and frequency bounds.
     */
    fun calculatePeakingBiquad(
        centerFreqHz: Float,
        gainDb: Float,
        q: Float = 1.414f,
        sampleRate: Float = SAMPLE_RATE
    ): BiquadCoefficients {
        val startNano = System.nanoTime()

        // Input sanitization
        val safeFreq = if (centerFreqHz.isNaN() || centerFreqHz.isInfinite()) 1000f
            else centerFreqHz.coerceIn(20f, (sampleRate / 2f) - 100f)
        val safeGain = if (gainDb.isNaN() || gainDb.isInfinite()) 0f
            else gainDb.coerceIn(-12f, 12f)
        val safeQ = if (q.isNaN() || q.isInfinite() || q <= 0f) 1.0f else q.coerceIn(0.1f, 10f)

        val a = 10f.pow(safeGain / 40f)
        val w0 = (2f * PI.toFloat() * safeFreq) / sampleRate
        val sinW0 = sin(w0)
        val cosW0 = cos(w0)
        val alpha = sinW0 / (2f * safeQ)

        val b0 = 1f + (alpha * a)
        val b1 = -2f * cosW0
        val b2 = 1f - (alpha * a)
        val a0 = 1f + (alpha / a)
        val a1 = -2f * cosW0
        val a2 = 1f - (alpha / a)

        // Normalize by a0
        val safeA0 = if (a0 == 0f || a0.isNaN()) 1f else a0
        val normB0 = b0 / safeA0
        val normB1 = b1 / safeA0
        val normB2 = b2 / safeA0
        val normA1 = a1 / safeA0
        val normA2 = a2 / safeA0

        lastCoefficientCalculationUs = max(1L, (System.nanoTime() - startNano) / 1000L)

        val result = BiquadCoefficients(
            b0 = if (normB0.isNaN() || normB0.isInfinite()) 1f else normB0,
            b1 = if (normB1.isNaN() || normB1.isInfinite()) 0f else normB1,
            b2 = if (normB2.isNaN() || normB2.isInfinite()) 0f else normB2,
            a1 = if (normA1.isNaN() || normA1.isInfinite()) 0f else normA1,
            a2 = if (normA2.isNaN() || normA2.isInfinite()) 0f else normA2
        )
        return result
    }

    /**
     * Computes Bauer Crossfeed parameters (simulating speaker listening on headphones).
     * Cuts high frequencies in the cross-channel and adds an Interaural Time Delay (ITD).
     */
    fun calculateCrossfeed(strength: Float): CrossfeedMatrix {
        val safeStrength = if (strength.isNaN() || strength.isInfinite()) 0f else strength.coerceIn(0f, 1f)
        if (safeStrength <= 0.01f) {
            return CrossfeedMatrix(directGain = 1.0f, crossfeedGain = 0.0f, delaySamples = 0, lowpassCutoffHz = 700f)
        }

        // Bauer crossfeed specification:
        // Delay corresponds to ~280 microseconds at 48kHz (approx 13-14 samples)
        val delaySamples = (14f * safeStrength).toInt().coerceIn(1, 24)
        // Crossfeed level typically -6 dB to -12 dB relative to direct channel
        val crossfeedDb = -18f + (10f * safeStrength) // -18dB up to -8dB
        val crossGain = 10f.pow(crossfeedDb / 20f)
        val directGain = 1.0f - (crossGain * 0.35f) // Preserve overall RMS power

        return CrossfeedMatrix(
            directGain = directGain,
            crossfeedGain = crossGain,
            delaySamples = delaySamples,
            lowpassCutoffHz = 700f
        )
    }

    /**
     * Dynamic Range Compression & Limiter parameters for Night Mode.
     * Prevents startling explosions and loud effects while maintaining clear dialogue.
     */
    fun calculateNightModeDrc(intensity: Float): DrcParameters {
        val safeIntensity = if (intensity.isNaN() || intensity.isInfinite()) 0f else intensity.coerceIn(0f, 1f)
        val thresholdDb = -12f - (16f * safeIntensity) // -12 dB down to -28 dB
        val ratio = 2.0f + (4.0f * safeIntensity)      // 2:1 up to 6:1
        val kneeWidthDb = 4.0f + (4.0f * safeIntensity)
        val makeupGainDb = (safeIntensity * 3.5f)      // Mild makeup for low sounds

        return DrcParameters(
            thresholdDb = thresholdDb,
            ratio = ratio,
            kneeWidthDb = kneeWidthDb,
            makeupGainDb = makeupGainDb
        )
    }

    /**
     * Sanitizes an entire curve array, ensuring every float is finite and in range.
     */
    fun sanitizeCurveLevels(levels: List<Float>): List<Float> {
        return levels.map { value ->
            if (value.isNaN() || value.isInfinite()) 0.0f
            else value.coerceIn(-12.0f, 12.0f)
        }
    }
}
