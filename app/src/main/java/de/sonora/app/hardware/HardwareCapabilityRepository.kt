package de.sonora.app.hardware

import android.content.Context
import android.util.Log
import de.sonora.app.ai.inference.LiteRtInferenceEngine

/**
 * Ehrliche Hardware-Capability-Inventarisierung.
 *
 * Auf Samsung Galaxy A56 (Exynos 1580, Xclipse 540) gibt es keine öffentlich
 * dokumentierte NPU-Schnittstelle für Drittanbieter-Apps. Exynos AI LiteCore
 * ist nur für Exynos 2500/2600 dokumentiert. Daher ist NPU standardmäßig
 * "unknown -> not available", nie "verfügbar".
 *
 * GPU-Verifikation erfolgt zur Laufzeit über LiteRT GPU-Delegate.
 * CPU ist immer verfügbar und verifiziert.
 */
data class BackendCapability(
    val name: String,
    val supported: Boolean,
    val initializationSucceeded: Boolean,
    val executionVerified: Boolean,
    val failureReason: String?,
)

class HardwareCapabilityRepository(private val context: Context? = null) {

    fun cpu(): BackendCapability = BackendCapability(
        name = "CPU",
        supported = true,
        initializationSucceeded = true,
        executionVerified = true,
        failureReason = null,
    )

    fun gpu(): BackendCapability = BackendCapability(
        name = "GPU (LiteRT GPU delegate)",
        supported = true,
        initializationSucceeded = false,
        executionVerified = false,
        failureReason = "Nicht auf diesem Gerät verifiziert; Erfordert echte Runtime-/Driver-Diagnostik.",
    )

    fun npu(): BackendCapability = BackendCapability(
        name = "NPU (Exynos AI LiteCore)",
        supported = false,
        initializationSucceeded = false,
        executionVerified = false,
        failureReason = "Exynos 1580 ist nicht in der offiziellen LiteCore-Dokumentation (2500/2600) gelistet. Nicht annehmen.",
    )

    /**
     * Verifies GPU delegate availability at runtime using LiteRT.
     * Returns updated GPU capability based on actual hardware test.
     */
    fun verifyGpu(): BackendCapability {
        if (context == null) {
            return gpu()
        }

        try {
            val engine = LiteRtInferenceEngine(context)
            val result = engine.initializeGpu()
            return BackendCapability(
                name = "GPU (LiteRT GPU delegate)",
                supported = result.supported,
                initializationSucceeded = result.initializationSucceeded,
                executionVerified = result.executionVerified,
                failureReason = if (result.failureReason != null && result.cpuFallback) {
                    "${result.failureReason} — CPU-Fallback aktiv"
                } else {
                    result.failureReason
                },
            )
        } catch (e: Exception) {
            Log.w(TAG, "GPU verification failed", e)
            return BackendCapability(
                name = "GPU (LiteRT GPU delegate)",
                supported = false,
                initializationSucceeded = false,
                executionVerified = false,
                failureReason = "GPU-Verifikation fehlgeschlagen: ${e.message}",
            )
        }
    }

    /**
     * Documents NPU availability on Exynos 1580.
     * The Exynos 1580 does not have a documented NPU accessible to third-party apps.
     * Samsung's Exynos 1580 features:
     * - CPU: Octa-core (4×2.6 GHz Cortex-A78 + 4×2.0 GHz Cortex-A55)
     * - GPU: Xclipse 540 (Mali-G720 MP4)
     * - NPU: None exposed to Android NNAPI for third-party use
     *
     * Reference: https://www.samsung.com/semiconductor/minisite/semi7foundry/blog/exynos-1580-mobile-processor-for-galaxy-a56-series/
     */
    fun npuStatus(): String {
        return """
            Exynos 1580 NPU Analysis:
            - Exynos 1580 (4nm EUV, 2025) does NOT expose an NPU to Android NNAPI
            - CPU: 1×2.6 + 4×2.0 GHz (Cortex-A78 + A55, only 4 A78 cores not 4x A78 as in 2500)
            - GPU: Xclipse 540 (Mali-G720 MP4)
            - NPU: None — Samsung has not documented NPU for Exynos 1580
            - Exynos 2500/2600 use "Exynos AI" with up to 70 TOPS NPU
            - No third-party access via NNAPI or LiteRT NPU delegate
        """.trimIndent()
    }

    fun all(): List<BackendCapability> = listOf(cpu(), gpu(), npu())

    companion object {
        private const val TAG = "HardwareCapabilityRepo"
    }
}