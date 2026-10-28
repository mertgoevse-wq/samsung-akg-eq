package de.sonora.app.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import de.sonora.app.SonoraViewModel
import de.sonora.app.ai.inference.NeuralAudioState

/**
 * Neural Audio Lab screen — integrates with the SonoraViewModel's neural audio support.
 *
 * Shows:
 * - Currently detected audio events (top-5 from YAMNet)
 * - Audio activity status
 * - Active backend (CPU/GPU with execution verification)
 * - Inference time
 * - Toggle for classification
 * - Clearly marked demo mode indicator
 */
@Composable
fun NeuralAudioScreen(
    vm: SonoraViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by remember { mutableStateOf(vm.neuralAudioState) }
    val micPermission = rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        micPermission.value = hasPermission
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            micPermission.value = granted
            if (granted) {
                vm.startNeuralClassification()
            }
        }
    )

    Box(modifier = modifier.fillMaxSize()) {
        if (vm.neuralAudioReady) {
            NeuralAudioLab(
                state = vm.neuralAudioState,
                onToggleClassification = {
                    if (vm.neuralAudioState.isActive) {
                        vm.stopNeuralClassification()
                    } else {
                        if (vm.neuralAudioState.isDemoMode) {
                            vm.startNeuralClassification()
                        } else {
                            if (!micPermission.value) {
                                launcher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                vm.startNeuralClassification()
                            }
                        }
                    }
                },
                onToggleDemoMode = { vm.toggleNeuralDemoMode() }
            )
        } else {
            // Loading / not initialized
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (vm.neuralAudioInitError != null) {
                        "Modell konnte nicht initialisiert werden: ${vm.neuralAudioInitError}"
                    } else {
                        "Initialisiere Neural Audio Engine..."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
