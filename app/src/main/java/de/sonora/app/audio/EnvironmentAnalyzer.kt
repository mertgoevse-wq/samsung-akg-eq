package de.sonora.app.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.log10
import kotlin.math.sqrt

data class EnvironmentReading(val rmsDb: Float, val level: Int)

/**
 * Measures loudness with the microphone and classifies it into three honest
 * stages. Audio never leaves the device and is never written to storage.
 */
class EnvironmentAnalyzer(
    private val onReading: (EnvironmentReading) -> Unit
) {
    @Volatile
    private var running = false

    private var record: AudioRecord? = null
    private var thread: Thread? = null

    @SuppressLint("MissingPermission")
    fun start() {
        if (running) return
        val sampleRate = 16000
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) return
        val bufferSize = maxOf(minBuffer * 2, 4096)
        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        } catch (_: Throwable) {
            return
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return
        }
        record = recorder
        running = true
        thread = Thread {
            val buffer = ShortArray(bufferSize / 2)
            try {
                recorder.startRecording()
                while (running) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        var sum = 0.0
                        for (index in 0 until read) {
                            val value = buffer[index].toDouble()
                            sum += value * value
                        }
                        val rms = sqrt(sum / read)
                        val db = if (rms <= 1.0) {
                            -90f
                        } else {
                            (20.0 * log10(rms / 32768.0)).toFloat()
                        }
                        val level = when {
                            db < -45f -> 0
                            db < -25f -> 1
                            else -> 2
                        }
                        onReading(EnvironmentReading(db, level))
                    }
                }
            } catch (_: Throwable) {
            } finally {
                try {
                    recorder.stop()
                } catch (_: Throwable) {
                }
                recorder.release()
            }
        }.also {
            it.isDaemon = true
            it.start()
        }
    }

    fun stop() {
        running = false
        try {
            record?.stop()
        } catch (_: Throwable) {
        }
        try {
            thread?.join(500)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        thread = null
        record = null
    }
}
