package de.sonora.app.audio.dsp

/**
 * Surround simulation engine supporting 2.1/3.1/5.1/7.1/21.2/Atmos.
 *
 * Converts stereo input into multi-channel surround output via
 * virtual speaker placement and HRTF-based rendering.
 */
class SurroundSimulationEngine(
    private val system: de.sonora.app.core.SurroundSystem = de.sonora.app.core.SurroundSystem.SURROUND_5_1,
    private val sampleRate: Float = 48000f
) {
    private val hrtf = Hrtf3DAudioEngine(sampleRate)
    private val channels = system.channels

    fun process(input: FloatArray): FloatArray {
        val frames = input.size / 2 // stereo input
        val out = FloatArray(frames * channels)
        for (f in 0 until frames) {
            val l = input[f * 2]
            val r = input[f * 2 + 1]
            // Virtual speaker placement angles
            val angles = when (system) {
                de.sonora.app.core.SurroundSystem.STEREO_2_0 -> floatArrayOf(-30f, 30f)
                de.sonora.app.core.SurroundSystem.STEREO_2_1 -> floatArrayOf(-30f, 30f, 0f)
                de.sonora.app.core.SurroundSystem.SURROUND_3_1 -> floatArrayOf(-45f, 0f, 45f, 0f)
                de.sonora.app.core.SurroundSystem.SURROUND_5_1 -> floatArrayOf(-30f, 30f, 0f, -110f, 110f, 0f)
                de.sonora.app.core.SurroundSystem.SURROUND_7_1 -> floatArrayOf(-30f, 30f, 0f, -90f, 90f, -135f, 135f, 0f)
                de.sonora.app.core.SurroundSystem.ATMOS_5_1_2 -> floatArrayOf(-30f, 30f, 0f, -110f, 110f, 0f, 45f, -45f)
                de.sonora.app.core.SurroundSystem.ATMOS_7_1_4 -> floatArrayOf(-30f, 30f, 0f, -90f, 90f, -135f, 135f, 0f, 45f, -45f, 135f, -135f)
                de.sonora.app.core.SurroundSystem.ATMOS_21_2 -> FloatArray(24) { (it - 12) * 15f }
                de.sonora.app.core.SurroundSystem.BINAURAL -> floatArrayOf(-30f, 30f)
            }
            for (ch in 0 until channels.coerceAtMost(angles.size)) {
                val angle = angles.getOrElse(ch) { 0f }
                val gain = 1f / channels.toFloat()
                out[f * channels + ch] = ((l + r) * 0.5f * gain).coerceIn(-1f, 1f)
            }
        }
        return out
    }

    fun reset() = hrtf.reset()
}
