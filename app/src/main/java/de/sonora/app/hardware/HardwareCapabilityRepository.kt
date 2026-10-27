package de.sonora.app.hardware

/**
 * Ehrliche Hardware-Capability-Inventarisierung.
 *
 * Auf Samsung Galaxy A56 (Exynos 1580, Xclipse 540) gibt es keine öffentlich
 * dokumentierte NPU-Schnittstelle für Drittanbieter-Apps. Exynos AI LiteCore
 * ist nur für Exynos 2500/2600 dokumentiert. Daher ist NPU standardmäßig
 * "unknown -> not available", nie "verfügbar".
 */
data class BackendCapability(
    val name: String,
    val supported: Boolean,
    val initializationSucceeded: Boolean,
    val executionVerified: Boolean,
    val failureReason: String?,
)

class HardwareCapabilityRepository {
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

    fun all(): List<BackendCapability> = listOf(cpu(), gpu(), npu())
}