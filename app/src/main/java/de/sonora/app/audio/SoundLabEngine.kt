package de.sonora.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import de.sonora.app.core.dsp.FlangerDsp
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.abs

/**
 * Klangerzeuger fuer Sonora Sound Lab.
 *
 * Ermoeglicht den echten, hoerbaren Test von DSP-Effekten (wie Flanger, Filter Sweep)
 * im Sonora-eigenen PCM-Signalweg unabhaengig von Einschraenkungen fremder Android-Apps.
 *
 * Die reine Signal-Erzeugung (Synthese + Flanger-DSP) ist in [SoundLabGenerator] gekapselt
 * und ohne Android-Abhaengigkeit (AudioTrack) auf der JVM testbar. [SoundLabEngine] selbst
 * betreibt den echten AudioTrack-Wiedergabethread und ruft den Generator pro Chunk auf.
 */
class SoundLabEngine(
    val sampleRate: Int = 44100
) {

    enum class SignalType {
        WARM_SYNTH_CHORD,
        PINK_NOISE,
        ACOUSTIC_PULSE
    }

    /** Reine Signal-Erzeugung (AudioTrack-frei, JVM-testbar). */
    internal val generator = SoundLabGenerator(sampleRate = sampleRate)

    /** Offentlich zugaenglicher Flanger (z. B. für XY-Touch-Steuerung aus der UI). */
    val flanger: FlangerDsp = generator.flanger

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
            val shortBuffer = ShortArray(chunkSize)

            while (isRunning.get()) {
                // Echte PCM-Erzeugung ohne Android: Synthese + Flanger-DSP
                val samples = generator.generateChunk(currentSignal, masterVolume.coerceIn(0f, 1f), chunkSize)

                // Kopie fuer Visualizer
                onSamplesGenerated?.invoke(samples.clone())

                // 16-Bit PCM konvertieren und schreiben
                for (i in samples.indices) {
                    val clamped = samples[i].coerceIn(-1.0f, 1.0f)
                    shortBuffer[i] = (clamped * 32767f).toInt().toShort()
                }

                track.write(shortBuffer, 0, samples.size)
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
        generator.reset()
    }

    val isActive: Boolean
        get() = isRunning.get()
}

/**
 * Reine PCM-Signal-Erzeugung fuer den Sound Lab.
 *
 * Erzeugt pro Chunk einen von drei Testsignalen (warmherziger Synthesizer-Akkord,
 * Rosa-Rauschen, akustischer Impuls) und leitet die Proben durch den echten
 * [FlangerDsp]. Keine Android-Abhaengigkeit (kein AudioTrack) - auf der JVM testbar.
 *
 * Zustande (Synthese-Phasen, Rausch-Zustand) werden zwischen den Chunks beibehalten,
 * damit das Signal chunk-übergreifend kontinuierlich bleibt.
 */
class SoundLabGenerator(
    sampleRate: Int = 44100
) {

    enum class SignalType {
        WARM_SYNTH_CHORD,
        PINK_NOISE,
        ACOUSTIC_PULSE
    }

    val flanger = FlangerDsp(sampleRate = sampleRate)

    // Synthese-Zustand (zwischen Chunks erhalten)
    var synthPhase1 = 0.0
    var synthPhase2 = 0.0
    var synthPhase3 = 0.0
    var noiseState = 0f

    // Akkord: A2 (110 Hz), E3 (164.8 Hz), C#4 (277.2 Hz)
    private val f1 = 110.0
    private val f2 = 164.81
    private val f3 = 277.18
    private val twoPi = 2.0 * PI

    fun getSampleRate(): Int = sampleRate

    fun setSampleRate(sampleRate: Int) {
        flanger.setSampleRate(sampleRate)
    }

    /** Setzt den internen Synthese- und Flanger-Zustand zurueck. */
    fun reset() {
        synthPhase1 = 0.0
        synthPhase2 = 0.0
        synthPhase3 = 0.0
        noiseState = 0f
        flanger.reset()
    }

    /**
     * Erzeugt einen Chunk von [chunkSize] PCM-Samples im Bereich [-1, 1].
     *
     * Der Chunk durchläuft den echten Flanger-DSP. Das zurückgelieferte
     * FloatArray ist ein frisches Array (kein interner Puffer).
     */
    fun generateChunk(signal: SignalType, volume: Float, chunkSize: Int): FloatArray {
        val safeVol = volume.coerceIn(0f, 1f)
        val buf = FloatArray(chunkSize)

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
            buf[i] = rawSample * safeVol
        }

        // Durch den echten Flanger DSP leiten (echte DSP-Verarbeitung, kein Mock)
        flanger.process(buf)

        return buf
    }

    companion object {
        private const val MAX_SAMPLE = 1.0f
    }
}
