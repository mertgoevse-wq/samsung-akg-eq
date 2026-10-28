package de.sonora.app.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * UI state for the Neural Audio Lab screen.
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

@Composable
fun NeuralAudioLab(
    state: NeuralAudioState,
    onToggleClassification: () -> Unit,
    onToggleDemoMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        // Header with toggle
        NeuralAudioHeader(
            isActive = state.isActive,
            isDemoMode = state.isDemoMode,
            backendName = state.backendName,
            inferenceTimeMs = state.inferenceTimeMs,
            audioActivityLevel = state.audioActivityLevel,
            onToggleClassification = onToggleClassification,
            onToggleDemoMode = onToggleDemoMode
        )

        // Audio activity visualization
        AudioActivityMeter(level = state.audioActivityLevel)

        // Status message
        if (state.statusMessage != null) {
            StatusBanner(message = state.statusMessage)
        }

        // Top-5 classification results
        ClassificationResults(
            results = state.topResults,
            inferenceTimeMs = state.inferenceTimeMs,
            backend = state.backendName
        )

        // Warning / status
        if (state.isDemoMode) {
            DemoModeWarning()
        }
    }
}

@Composable
private fun NeuralAudioHeader(
    isActive: Boolean,
    isDemoMode: Boolean,
    backendName: String,
    inferenceTimeMs: Long,
    audioActivityLevel: Float,
    onToggleClassification: () -> Unit,
    onToggleDemoMode: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = if (isActive) {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        }
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "Neural Audio Lab",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isActive) "Audio classification active" else "Audio classification paused",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                </Column>

                Switch(
                    checked = isActive,
                    onCheckedChange = { onToggleClassification() },
                    colors = SwitchDefaults.colors(
                        checkedIconColor = MaterialTheme.colorScheme.primary,
                        uncheckedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Backend and timing info
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                InfoChip(
                    label = "Backend",
                    value = backendName
                )
                InfoChip(
                    label = "Latenz",
                    value = "${inferenceTimeMs}ms"
                )
                InfoChip(
                    label = "Aktivität",
                    value = "${(audioActivityLevel * 100).toInt()}%"
                )
            }

            // Demo mode toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(
                    text = if (isDemoMode) "Demo-Modus aktiv" else "Live-Modus",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
                Switch(
                    checked = isDemoMode,
                    onCheckedChange = { onToggleDemoMode() },
                    colors = SwitchDefaults.colors(
                        checkedIconColor = MaterialTheme.colorScheme.secondary,
                        uncheckedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}

@Composable
private fun InfoChip(label: String, value: String) {
    Card(
        modifier = Modifier
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(0.3f), RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun AudioActivityMeter(level: Float) {
    val barCount = 20
    val normalizedLevel = level.coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Canvas(modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
        ) {
            val barWidth = size.width / barCount
            val maxHeight = size.height

            for (i in 0 until barCount) {
                val barHeight = maxHeight * (normalizedLevel * (0.3f + 0.7f * (i.toFloat() / barCount)))
                val yPosition = maxHeight - barHeight

                val barColor = if (i < barCount * normalizedLevel) {
                    val hueProgress = i.toFloat() / barCount
                    if (hueProgress < 0.5f) {
                        Color(0xFF4CAF50) // green
                    } else if (hueProgress < 0.75f) {
                        Color(0xFFFFC107) // amber
                    } else {
                        Color(0xFFF44336) // red
                    }
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(0.1f)
                }

                if (barHeight > 0) {
                    drawRect(
                        color = barColor,
                        topLeft = Offset(i * barWidth, yPosition),
                        size = Size(barWidth - 2.dp.toPx(), barHeight)
                    )
                }
            }
        }
    }
}

@Composable
private fun ClassificationResults(
    results: List<Pair<String, Float>>,
    inferenceTimeMs: Long,
    backend: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Erkannte Ereignisse",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (results.isEmpty()) {
                Text(
                    text = "Noch keine Klassifikation verfügbar. Aktiviere die Audioanalyse.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                    textAlign = TextAlign.Center
                )
            } else {
                results.forEachIndexed { index, (label, score) ->
                    ClassificationRow(
                        rank = index + 1,
                        label = label,
                        score = score,
                        inferenceTimeMs = inferenceTimeMs,
                        backend = backend,
                        isTop = index == 0
                    )
                }
            }
        }
    }
}

@Composable
private fun ClassificationRow(
    rank: Int,
    label: String,
    score: Float,
    inferenceTimeMs: Long,
    backend: String,
    isTop: Boolean
) {
    val normalizedScore = score.coerceIn(0f, 1f)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // Rank badge
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(
                    if (isTop) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = rank.toString(),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = if (isTop) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Label
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isTop) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Score bar
        Box(
            modifier = Modifier
                .width(60.dp)
                .height(20.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(0.3f),
                    shape = RoundedCornerShape(4.dp)
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF4CAF50),
                                Color(0xFFFFC107),
                                Color(0xFFF44336)
                            )
                        ),
                        shape = RoundedCornerShape(4.dp)
                    )
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Score percentage
        Text(
            text = "${(normalizedScore * 100).toInt()}%",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun DemoModeWarning() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "🄳", fontSize = 20.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "Demo-Modus",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Verwendet ein lokales Testsignal. Schließe ein Mikrofon an oder erlaube den Zugriff für Live-Analyse.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatusBanner(message: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(0.2f)
        )
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(12.dp)
        )
    }
}
