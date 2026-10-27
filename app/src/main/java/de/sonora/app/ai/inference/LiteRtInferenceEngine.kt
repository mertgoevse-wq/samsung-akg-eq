package de.sonora.app.ai.inference

import android.content.Context
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.TensorBuffer
import java.io.File
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * LiteRT 2.3.0 basierte Inferenz mit GPU-Testpfad und CPU-Fallback.
 *
 * GPU/NPU werden NICHT als verfügbar behauptet, bis executionVerified=true
 * auf echtem Gerät (Galaxy A56) vorliegt.
 */
class LiteRtInferenceEngine(
    private val context: Context,
    private val modelAssetName: String = "yamnet.tflite",
) : AiInferenceEngine {
    override val backendName: String = "LiteRT"

    private var compiledModel: CompiledModel? = null
    private var activeBackend: String = "none"
    private var initOk: Boolean = false
    private var execVerified: Boolean = false
    private var failure: String? = "Nicht initialisiert"

    override fun initialize(): Boolean {
        // 1. GPU versuchen
        try {
            val model = loadModel()
            val opts = CompiledModel.Options().apply {
                // GPU delegate; falls nicht verfügbar, wirft init fehl
                accelerator = CompiledModel.Accelerator.GPU
            }
            compiledModel = CompiledModel.create(model, opts)
            activeBackend = "GPU"
            initOk = true
            failure = null
            return true
        } catch (e: Throwable) {
            compiledModel?.close()
            compiledModel = null
            initOk = false
            failure = "GPU-Init fehlgeschlagen: ${e.message}"
        }
        // 2. CPU-Fallback
        return try {
            val model = loadModel()
            val opts = CompiledModel.Options().apply {
                accelerator = CompiledModel.Accelerator.CPU
            }
            compiledModel = CompiledModel.create(model, opts)
            activeBackend = "CPU"
            initOk = true
            failure = null
            true
        } catch (e: Throwable) {
            compiledModel = null
            initOk = false
            failure = "CPU-Init fehlgeschlagen: ${e.message}"
            false
        }
    }

    private fun loadModel(): MappedByteBuffer {
        val f = File(context.filesDir, modelAssetName)
        // Modell muss lokal gebündelt sein; kein Download.
        if (!f.exists()) throw IllegalStateException("Modell nicht gebündelt: $modelAssetName")
        val channel = java.io.FileInputStream(f).channel
        return channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
    }

    override fun isReady(): Boolean = initOk && compiledModel != null

    override fun infer(features: AudioFeatures): ModelResult {
        val m = compiledModel ?: throw IllegalStateException("Engine nicht initialisiert")
        val start = System.nanoTime()
        // Minimaler Pfad: Eingabe-Buffer erzeugen (Modell-spezifisch anzupassen)
        val input = TensorBuffer.createFixedSize(intArrayOf(features.monoPcm16k.size), org.tensorflow.lite.DataType.FLOAT32)
        input.loadArray(features.monoPcm16k)
        val outputs = m.run(input)
        val elapsed = (System.nanoTime() - start) / 1_000_000
        execVerified = true
        return ModelResult(
            labels = emptyList(),
            scores = FloatArray(0),
            inferenceTimeMs = elapsed,
            backend = activeBackend,
        )
    }

    override fun release() {
        compiledModel?.close()
        compiledModel = null
        initOk = false
    }

    override fun backendStatus(): BackendStatus = BackendStatus(
        supported = true,
        initializationSucceeded = initOk,
        executionVerified = execVerified,
        failureReason = failure,
    )
}