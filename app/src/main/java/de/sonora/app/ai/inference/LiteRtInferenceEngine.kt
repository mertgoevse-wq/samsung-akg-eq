package de.sonora.app.ai.inference

import android.content.Context
import java.io.File

/**
 * LiteRT 2.3.0 basierte Inferenz — AKTUELL PLATZHALTER.
 *
 * STATUS: Die genauen CompiledModel-API-Signaturen (create/run/TensorBuffer)
 * sind nicht gegen die offizielle LiteRT 2.3.0-Dokumentation verifiziert.
 * Diese Klasse behauptet KEINEN GPU- oder NPU-Erfolg.
 *
 * Vor echter Aktivierung:
 * 1. LiteRT 2.3.0 AAR herunterladen und API-Signaturen prüfen.
 * 2. Modell (yamnet.tflite) in assets/ bündeln.
 * 3. Tensor-Vertrag validieren (Input: 15600 Float32, Output: 521 Float32).
 * 4. Auf echtem Gerät (Galaxy A56) ausführen und executionVerified setzen.
 *
 * Bis dahin: CPU-only-Fallback, keine Inferenz, ehrlicher Status.
 */
class LiteRtInferenceEngine(
    private val context: Context,
    private val modelAssetName: String = "yamnet.tflite",
) : AiInferenceEngine {
    override val backendName: String = "LiteRT (nicht verifiziert)"

    private var initOk: Boolean = false
    private var execVerified: Boolean = false
    private var failure: String? = "LiteRT-API nicht verifiziert; kein Modell gebündelt"

    override fun initialize(): Boolean {
        val modelFile = File(context.filesDir, modelAssetName)
        if (!modelFile.exists()) {
            failure = "Modell nicht gebündelt: $modelAssetName (assets/ fehlt)"
            initOk = false
            return false
        }
        // TODO: LiteRT CompiledModel.create() erst nach API-Verifikation aktivieren
        failure = "LiteRT-CompiledModel-API noch nicht verifiziert (siehe docs/NEURAL_ENGINE.md)"
        initOk = false
        return false
    }

    override fun isReady(): Boolean = false

    override fun infer(features: AudioFeatures): ModelResult {
        throw IllegalStateException("LiteRT-Inferenz nicht aktiviert: API nicht verifiziert")
    }

    override fun release() {
        initOk = false
        execVerified = false
    }

    override fun backendStatus(): BackendStatus = BackendStatus(
        supported = false,
        initializationSucceeded = initOk,
        executionVerified = execVerified,
        failureReason = failure,
    )
}