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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun BlindTestScreen(
    round: Int,
    ratingA: Int,
    ratingB: Int,
    ready: Boolean,
    sonoraAverage: Double?,
    originalAverage: Double?,
    feedbackSaved: Boolean,
    message: String?,
    onPresent: () -> Unit,
    onRatingAChange: (Int) -> Unit,
    onRatingBChange: (Int) -> Unit,
    onRate: () -> Unit,
    onFinish: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) { Text("Abbrechen") }
            Text("Verblindeter Hörtest", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Zehn Durchläufe desselben laufenden Titels. Die Reihenfolge A/B ist zufällig und ausgeglichen.",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "Bewerte beide Abschnitte getrennt. Sonora kann andere Apps nicht fernsteuern; Android oder der Hersteller kann globale Effekte ignorieren.",
                style = MaterialTheme.typography.bodySmall,
                color = Muted
            )
        }
        if (sonoraAverage == null) {
            item {
                Text("Durchlauf ${round + 1} von 10", style = MaterialTheme.typography.titleLarge)
                Button(
                    onClick = onPresent,
                    enabled = !ready,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) {
                    Text(if (ready) "Beide Abschnitte gehört" else "Vergleich starten")
                }
            }
            item {
                Text(
                    message ?: if (ready) "Bewerte A und B unabhängig (1 = schwach, 10 = sehr gut)." else "Der Klang wechselt zweimal automatisch, je drei Sekunden. Lass denselben Titel weiterlaufen.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (!ready && round > 0) {
                item {
                    Text("Noch ${round} von 10 Bewertungen", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }
            if (ready) {
                item { BlindRating("A", ratingA, onRatingAChange) }
                item { BlindRating("B", ratingB, onRatingBChange) }
                item {
                    Button(onClick = onRate, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Text("Bewertung sichern")
                    }
                }
            }
        } else {
            item {
                Text(
                    if (feedbackSaved) "Mittelwert Sonora: ${"%.1f".format(java.util.Locale.GERMAN, sonoraAverage)} von 10"
                    else "Alle zehn Durchläufe bewertet.",
                    style = MaterialTheme.typography.titleLarge
                )
                originalAverage?.let {
                    Text("Mittelwert Original: ${"%.1f".format(java.util.Locale.GERMAN, it)} von 10", style = MaterialTheme.typography.bodyLarge)
                }
                Text("Die Ergebnisse bleiben lokal und stehen im Diagnosebericht.", style = MaterialTheme.typography.bodySmall, color = Muted)
                Spacer(Modifier.height(8.dp))
                Button(onClick = onFinish, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text("Im Diagnosebericht sichern")
                }
            }
        }
    }
}

@Composable
private fun BlindRating(label: String, rating: Int, onRatingChange: (Int) -> Unit) {
    Column {
        Text("Abschnitt $label: $rating von 10", style = MaterialTheme.typography.titleMedium)
        Slider(
            value = rating.toFloat(),
            onValueChange = { onRatingChange(it.toInt().coerceIn(1, 10)) },
            valueRange = 1f..10f,
            steps = 8,
            modifier = Modifier.semantics {
                contentDescription = "Bewertung für Abschnitt $label, $rating von 10"
            }
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("1 · schwach", style = MaterialTheme.typography.bodySmall, color = Muted)
            Text("10 · sehr gut", style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}
