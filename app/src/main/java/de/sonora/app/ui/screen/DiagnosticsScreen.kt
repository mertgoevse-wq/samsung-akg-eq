package de.sonora.app.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.sonora.app.ai.inference.NeuralAudioViewModel

/**
 * Diagnostics screen for Neural Audio Engine.
 *
 * Shows:
 * - Model loaded status, version, SHA-256
 * - CPU status
 * - GPU status (with execution verification)
 * - NPU hardware info (documented as unavailable on Exynos 1580)
 * - Inference latency p50/p95
 * - Memory usage
 * - Error messages
 */
@Composable
fun DiagnosticsScreen(
    modelInfo: NeuralAudioViewModel.ModelInfo,
    hardwareCapabilities: NeuralAudioViewModel.HardwareCapabilities,
    latencyP50: Long,
    latencyP95: Long,
    memoryUsageBytes: Long,
    errorMessage: String?,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text(
            text = "Neural Audio Diagnose",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Model Info Card
        DiagnosticCard(
            title = "Modell",
            color = if (modelInfo.loaded) Color(0xFF4CAF50) else Color(0xFFF44336)
        ) {
            DiagnosticRow("Geladen", if (modelInfo.loaded) "✅ Ja" else "❌ Nein")
            DiagnosticRow("Version", modelInfo.version)
            DiagnosticRow("SHA-256", if (modelInfo.sha256.isNotBlank())
                "${modelInfo.sha256}..." else "Nicht verifiziert")
            DiagnosticRow("Labels", "${modelInfo.labelCount}")
            DiagnosticRow("Input", "${modelInfo.inputSize} Samples")
            DiagnosticRow("Output", "${modelInfo.outputSize} Klassen")
        }

        // CPU Status
        DiagnosticCard(
            title = "CPU",
            color = Color(0xFF4CAF50)
        ) {
            BackendStatusRow(
                name = hardwareCapabilities.cpu.name,
                supported = hardwareCapabilities.cpu.supported,
                initialized = hardwareCapabilities.cpu.initializationSucceeded,
                verified = hardwareCapabilities.cpu.executionVerified
            )
        }

        // GPU Status
        val gpuColor = if (hardwareCapabilities.gpu.executionVerified) {
            Color(0xFF4CAF50)
        } else if (hardwareCapabilities.gpu.supported) {
            Color(0xFFFFC107)
        } else {
            Color(0xFFF44336)
        }
        DiagnosticCard(
            title = "GPU",
            color = gpuColor
        ) {
            BackendStatusRow(
                name = hardwareCapabilities.gpu.name,
                supported = hardwareCapabilities.gpu.supported,
                initialized = hardwareCapabilities.gpu.initializationSucceeded,
                verified = hardwareCapabilities.gpu.executionVerified,
                fallbackNote = "CPU-Fallback aktiv"
            )
            Text(
                text = "Hinweis: GPU-Verifikation erfordert echte Inferenz, nicht nur Modell-Ladung.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // NPU Status
        DiagnosticCard(
            title = "NPU",
            color = Color(0xFF9E9E9E)
        ) {
            BackendStatusRow(
                name = hardwareCapabilities.npu.name,
                supported = hardwareCapabilities.npu.supported,
                initialized = hardwareCapabilities.npu.initializationSucceeded,
                verified = hardwareCapabilities.npu.executionVerified,
                fallbackNote = "Exynos 1580: Keine NPU für Drittanbieter"
            )
        }

        // Latency
        DiagnosticCard(title = "Inferenz-Latenz", color = Color(0xFF2196F3)) {
            DiagnosticRow("p50", "${latencyP50} ms")
            DiagnosticRow("p95", "${latencyP95} ms")

            // Latency distribution visualization
            if (latencyP50 > 0 && latencyP95 > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Canvas(modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                    ) {
                        val barWidth = size.width / 2
                        val maxLatency = maxOf(latencyP50.toFloat(), latencyP95.toFloat()) * 1.2f

                        // p50 bar
                        val p50Height = (latencyP50.toFloat() / maxLatency) * size.height
                        drawRect(
                            color = Color(0xFF4CAF50),
                            topLeft = Offset(0f, size.height - p50Height),
                            size = androidx.compose.ui.geometry.Size(barWidth - 16.dp.toPx(), p50Height)
                        )

                        // p95 bar
                        val p95Height = (latencyP95.toFloat() / maxLatency) * size.height
                        drawRect(
                            color = Color(0xFFFFC107),
                            topLeft = Offset(barWidth, size.height - p95Height),
                            size = androidx.compose.ui.geometry.Size(barWidth - 16.dp.toPx(), p95Height)
                        )
                    }
                }
            }
        }

        // Memory
        val memoryMb = memoryUsageBytes / (1024 * 1024)
        DiagnosticCard(title = "Speicher", color = Color(0xFF9C27B0)) {
            DiagnosticRow("Modell + Engine", "${memoryMb} MB")
        }

        // Error Messages
        if (errorMessage != null) {
            DiagnosticCard(title = "Fehler", color = Color(0xFFF44336)) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun DiagnosticCard(
    title: String,
    color: Color,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            2.dp,
            color.copy(0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(color, shape = RoundedCornerShape(50))
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun BackendStatusRow(
    name: String,
    supported: Boolean,
    initialized: Boolean,
    verified: Boolean,
    fallbackNote: String? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        DiagnosticRow("Name", name)
        DiagnosticRow("Unterstützt", if (supported) "✅ Ja" else "❌ Nein")
        DiagnosticRow("Initialisiert", if (initialized) "✅ Ja" else "❌ Nein")
        DiagnosticRow("Verifiziert", if (verified) "✅ Ja (echte Inferenz)" else "❌ Nein (nur Modell-Ladung)")
        if (fallbackNote != null) {
            DiagnosticRow("Fallback", fallbackNote)
        }
    }
}
