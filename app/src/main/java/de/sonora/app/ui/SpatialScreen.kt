package de.sonora.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.sonora.app.SonoraViewModel
import de.sonora.app.core.SpatialMode

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
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text("Raum", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Eigene Klangsimulationen von Sonora. Keine lizenzierten Fremdformate.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted
                )
            }
        }

        item {
            SpatialChoice(
                title = "Aus",
                description = "Unveränderter Originalklang ohne Raumklangeffekt.",
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
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Stärke", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${(state.spatialIntensity * 100).toInt()} %",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Slider(
                    value = state.spatialIntensity,
                    onValueChange = onIntensityChanged,
                    valueRange = 0f..1f,
                    steps = 19,
                    modifier = Modifier.semantics {
                        contentDescription = "Stärke, ${(state.spatialIntensity * 100).toInt()} Prozent"
                    }
                )
            }
        }

        spatialNoticeText(state)?.let { notice ->
            item { SpatialNotice(text = notice) }
        }

        item {
            Text(
                "Virtualisierung und Raumhall hängen von den Audioeffekten des Geräts ab. Mittenfokus und Dynamikkontrolle werden derzeit nicht separat verarbeitet.",
                style = MaterialTheme.typography.bodySmall,
                color = Muted
            )
        }
    }
}

@Composable
private fun SpatialNotice(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
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
        color = if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .semantics(mergeDescendants = true) {
                contentDescription = "$title. $description. ${if (selected) "Ausgewählt" else "Nicht ausgewählt"}."
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(description, style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            RadioButton(selected = selected, onClick = null)
        }
    }
}
