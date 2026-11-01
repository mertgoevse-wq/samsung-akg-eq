package de.sonora.app.ai.inference

/**
 * UI state for the Neural Audio Lab screen.
 *
 * Defined outside the UI layer to avoid circular dependencies between
 * ViewModel and Composable code.
 */
data class NeuralAudioState(
    val isActive: Boolean = false,
    val isDemoMode: Boolean = true,
    val backendName: String = "CPU",
    val inferenceTimeMs: Long = 0L,
    val topResults: List<Pair<String, Float>> = emptyList(),
    val audioActivityLevel: Float = 0f,
    val statusMessage: String? = null
) {
    val isRunning: Boolean get() = isActive && !isDemoMode
    val demoIndicatorVisible: Boolean get() = isDemoMode
}
