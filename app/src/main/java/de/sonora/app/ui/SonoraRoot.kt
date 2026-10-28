package de.sonora.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import de.sonora.app.SonoraViewModel

enum class SonoraTab(val label: String, val symbol: String) {
    Now("Jetzt", "◉"),
    Neural("Neural", "◉"),
    Sound("Klang", "≋"),
    Spatial("Raum", "◈"),
    Profiles("Profile", "▤"),
    Settings("Mehr", "⚙")
}

@Composable
fun SonoraRoot(vm: SonoraViewModel) {
    val state = vm.uiState
    if (!state.onboardingDone) {
        OnboardingScreen(vm)
        return
    }
    if (state.interviewActive) {
        InterviewScreen(vm)
        return
    }
    if (state.blindTestActive) {
        BlindTestScreen(
            round = state.blindTestRound,
            ratingA = state.blindTestRatingA,
            ratingB = state.blindTestRatingB,
            ready = state.blindTestReady,
            sonoraAverage = vm.blindTestAverage(),
            originalAverage = vm.blindTestOriginalAverage(),
            feedbackSaved = state.blindTestFeedbackSaved,
            message = state.blindTestMessage,
            onPresent = vm::presentBlindTestRound,
            onRatingAChange = vm::updateBlindTestRatingA,
            onRatingBChange = vm::updateBlindTestRatingB,
            onRate = vm::rateBlindTest,
            onFinish = vm::finishBlindTest,
            onCancel = vm::cancelBlindTest
        )
        return
    }

    var selectedTab by rememberSaveable { mutableStateOf(SonoraTab.Now.name) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        val message = state.message
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            vm.clearMessage()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                SonoraTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab.name,
                        onClick = { selectedTab = tab.name },
                        icon = { Text(tab.symbol, fontSize = 18.sp) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (selectedTab) {
                SonoraTab.Now.name -> HomeScreen(vm)
                SonoraTab.Neural.name -> NeuralAudioScreen(vm)
                SonoraTab.Sound.name -> EqualizerScreen(vm)
                SonoraTab.Spatial.name -> SpatialScreen(vm)
                SonoraTab.Profiles.name -> ProfilesScreen(vm)
                else -> SettingsScreen(vm)
            }
        }
    }
}
