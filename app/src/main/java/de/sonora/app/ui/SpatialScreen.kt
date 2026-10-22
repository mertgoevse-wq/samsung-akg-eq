package de.sonora.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.sonora.app.SonoraViewModel
import de.sonora.app.core.DspStageState
import de.sonora.app.core.SpatialMode
import de.sonora.app.core.SpatialModeCatalog

@Immutable
internal data class SpatialScreenState(
    val masterEnabled: Boolean,
    val spatialMode: SpatialMode?,
    val spatialIntensity: Float,
    val spatialAvailable: Boolean,
    val availabilityMessage: String?
)

internal fun spatialNoticeText(state: SpatialScreenState): String? = when {
    !state.masterEnabled ->
        "Sonora ist ausgeschaltet. Schalte Sonora im Bereich „Jetzt“ ein, um Raumklang anzuwenden."
    state.spatialMode != null && !state.spatialAvailable ->
        state.availabilityMessage
            ?: "Dieses Gerät kann nicht alle Klanganteile dieses Modus verarbeiten. Der Equalizer bleibt aktiv; verfügbare Raumklangeffekte können teilweise wirken."
    else -> null
}

@Composable
fun SpatialScreen(vm: SonoraViewModel, modifier: Modifier = Modifier) {
    val state = vm.uiState
    SpatialScreenContent(
        state = SpatialScreenState(
            masterEnabled = state.masterEnabled,
            spatialMode = state.spatialMode,
            spatialIntensity = state.spatialIntensity,
            spatialAvailable = state.spatialAvailable,
            availabilityMessage = state.spatialAvailabilityMessage
        ),
        onModeSelected = vm::setSpatialMode,
        onIntensityChanged = vm::setSpatialIntensity,
        modifier = modifier
    )
}

@Composable
internal fun SpatialScreenContent(
    state: SpatialScreenState,
    onModeSelected: (SpatialMode?) -> Unit,
    onIntensityChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Ink),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text("Raumklang-Studio", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Paper)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Eigene, mathematisch getrennte DSP-Klangsimulationen. Keine geschützten Fremdcodecs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted
                )
            }
        }

        item {
            SpatialChoice(
                title = "Aus",
                description = "Originalklang ohne Raumklang- oder Virtualisierungseffekte.",
                selected = state.spatialMode == null,
                onClick = { onModeSelected(null) }
            )
        }

        items(SpatialMode.entries, key = { it.id }, contentType = { "spatial-mode" }) { mode ->
            SpatialChoice(
                title = mode.title,
                description = mode.description,
                selected = state.spatialMode == mode,
                onClick = { onModeSelected(mode) }
            )
        }

        item {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Raumklang-Stärke", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Paper)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${(state.spatialIntensity * 100).toInt()} %",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Copper
                    )
                }
                Slider(
                    value = state.spatialIntensity,
                    onValueChange = onIntensityChanged,
                    valueRange = 0f..1f,
                    steps = 19,
                    modifier = Modifier.semantics {
                        contentDescription = "Raumklang-Intensität: ${(state.spatialIntensity * 100).toInt()} Prozent"
                    },
                    colors = SliderDefaults.colors(thumbColor = Copper, activeTrackColor = Copper)
                )
            }
        }

        // Detailed DSP Stage Status Evaluation
        state.spatialMode?.let { selectedMode ->
            item {
                val eval = SpatialModeCatalog.evaluateDspStages(
                    mode = selectedMode,
                    virtualizerSupported = state.spatialAvailable,
                    reverbSupported = state.spatialAvailable,
                    intensity = state.spatialIntensity
                )
                SectionCard {
                    Text("DSP-Stufen für „${selectedMode.title}“", style = MaterialTheme.typography.titleMedium, color = Paper)
                    Spacer(Modifier.height(6.dp))
                    Text(eval.summaryMessage, style = MaterialTheme.typography.bodySmall, color = Muted)
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = DividerColor)
                    Spacer(Modifier.height(8.dp))

                    eval.stages.forEach { stage ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val dotColor = when (stage.state) {
                                DspStageState.ACTIVE -> GreenAccent
                                DspStageState.PARTIALLY_ACTIVE -> Warning
                                DspStageState.UNAVAILABLE -> Muted
                                DspStageState.PENDING -> Copper
                            }
                            Box(Modifier.size(8.dp).background(dotColor, CircleShape))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stage.stageName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Paper)
                                Text(stage.detail, style = MaterialTheme.typography.bodySmall, color = Muted)
                            }
                        }
                    }
                }
            }
        }

        spatialNoticeText(state)?.let { notice ->
            item { SpatialNotice(text = notice) }
        }

        item {
            Text(
                "Hinweis: Virtualisierung und Raumhall werden über Hardware-Effekte abgebildet. Crossfeed und Dynamikkontrolle werden über die lokale DSP-Engine berechnet.",
                style = MaterialTheme.typography.bodySmall,
                color = Muted
            )
        }
    }
}

@Composable
private fun SpatialNotice(text: String) {
    SectionCard(borderColor = Warning) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = Warning
        )
    }
}

@Composable
private fun SpatialChoice(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) SelectedCardSurface else Surface1,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .border(1.dp, if (selected) Copper else CardBorder, RoundedCornerShape(16.dp))
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .semantics(mergeDescendants = true) {
                contentDescription = "$title: $description. ${if (selected) "Ausgewählt" else "Nicht ausgewählt"}."
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Paper)
                Spacer(Modifier.height(2.dp))
                Text(description, style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            RadioButton(
                selected = selected,
                onClick = null,
                colors = RadioButtonDefaults.colors(selectedColor = Copper, unselectedColor = Muted)
            )
        }
    }
}
