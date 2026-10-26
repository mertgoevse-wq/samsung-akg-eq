package de.sonora.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import de.sonora.app.core.dsp.FlangerDsp
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.sin

/**
 * Klangerzeuger fuer Sonora Sound Lab.
 *
 * Ermoeglicht den echten, hoerbaren Test von DSP-Effekten (wie Flanger, Filter Sweep)
 * im Sonora-eigenen PCM-Signalweg unabhaengig von Einschraenkungen fremder Android-Apps.
 */
class SoundLabEngine(
    val sampleRate: Int = 44100
) {
    enum class SignalType {
        WARM_SYNTH_CHORD,
        PINK_NOISE,
        ACOUSTIC_PULSE
    }

    val flanger = FlangerDsp(sampleRate = sampleRate)

    private val isRunning = AtomicBoolean(false)
    private var audioTrack: AudioTrack? = null
    private var playbackThread: Thread? = null

    var currentSignal: SignalType = SignalType.WARM_SYNTH_CHORD
    var masterVolume: Float = 0.6f

    // Callback fuer die audioreaktive Visualisierung der intern erzeugten Samples
    var onSamplesGenerated: ((samples: FloatArray) -> Unit)? = null

    fun start() {
        if (isRunning.getAndSet(true)) return

        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = minBufferSize.coerceAtLeast(4096)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        track.play()
        audioTrack = track

        playbackThread = thread(name = "SonoraSoundLabAudioThread", priority = Thread.MAX_PRIORITY) {
            val chunkSize = 512
            val floatBuffer = FloatArray(chunkSize)
            val shortBuffer = ShortArray(chunkSize)
            var synthPhase1 = 0.0
            var synthPhase2 = 0.0
            var synthPhase3 = 0.0
            var noiseState = 0f

            // Akkord: A2 (110 Hz), E3 (164.8 Hz), C#4 (277.2 Hz)
            val f1 = 110.0
            val f2 = 164.81
            val f3 = 277.18
            val twoPi = 2.0 * PI

            while (isRunning.get()) {
                val signal = currentSignal
                val vol = masterVolume.coerceIn(0f, 1f)

                for (i in 0 until chunkSize) {
                    val rawSample: Float = when (signal) {
                        SignalType.WARM_SYNTH_CHORD -> {
                            val s1 = sin(synthPhase1).toFloat()
                            val s2 = sin(synthPhase2).toFloat()
                            val s3 = sin(synthPhase3).toFloat()
                            synthPhase1 = (synthPhase1 + twoPi * f1 / sampleRate) % twoPi
                            synthPhase2 = (synthPhase2 + twoPi * f2 / sampleRate) % twoPi
                            synthPhase3 = (synthPhase3 + twoPi * f3 / sampleRate) % twoPi
                            (s1 * 0.45f + s2 * 0.35f + s3 * 0.20f)
                        }
                        SignalType.PINK_NOISE -> {
                            val white = (Math.random() * 2.0 - 1.0).toFloat()
                            noiseState = 0.95f * noiseState + 0.05f * white
                            noiseState * 1.8f
                        }
                        SignalType.ACOUSTIC_PULSE -> {
                            val pos = (synthPhase1 * sampleRate / twoPi).toInt() % (sampleRate / 2)
                            synthPhase1 = (synthPhase1 + twoPi * 2.0 / sampleRate) % twoPi
                            if (pos < 60) sin(pos * PI / 60).toFloat() else 0f
                        }
                    }
                    floatBuffer[i] = rawSample * vol
                }

                // Durch den Flanger DSP leiten
                flanger.process(floatBuffer, chunkSize)

                // Kopie fuer Visualizer
                onSamplesGenerated?.invoke(floatBuffer.clone())

                // 16-Bit PCM konvertieren
                for (i in 0 until chunkSize) {
                    val clamped = floatBuffer[i].coerceIn(-1.0f, 1.0f)
                    shortBuffer[i] = (clamped * 32767f).toInt().toShort()
                }

                track.write(shortBuffer, 0, chunkSize)
            }

            try {
                track.stop()
                track.release()
            } catch (_: Exception) {}
        }
    }

    fun stop() {
        isRunning.set(false)
        try {
            playbackThread?.join(500)
        } catch (_: Exception) {}
        playbackThread = null
        audioTrack = null
        flanger.reset()
    }

    val isActive: Boolean
        get() = isRunning.get()
}
