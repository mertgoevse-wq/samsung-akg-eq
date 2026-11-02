package de.sonora.app.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.sonora.app.ai.inference.NeuralAudioState
import de.sonora.app.ui.CardBorder
import de.sonora.app.ui.Copper
import de.sonora.app.ui.CopperSoft
import de.sonora.app.ui.GreenAccent
import de.sonora.app.ui.Ink
import de.sonora.app.ui.Muted
import de.sonora.app.ui.Paper
import de.sonora.app.ui.SectionCard
import de.sonora.app.ui.Surface1
import de.sonora.app.ui.Surface2
import de.sonora.app.ui.Warning

/**
 * Neural Audio Lab screen content — rebuilt to match the Sonora design system.
 *
 * Anti-slop rules applied:
 * - Flat surfaces only (no Card/elevation/shadows, no gradients).
 * - Sonora design tokens (Ink / Surface1 / Surface2 / Copper / Paper / Muted / Warning / GreenAccent).
 * - Colour is never the only status signal: state is conveyed through text + form + semantics.
 * - Every interactive target is at least 48 dp tall and carries a TalkBack label.
 * - Typographic scale uses sp units and Material-3 text styles; dynamic type scaling is respected.
 * - Reduced motion: no infinite decorative animation; the activity meter reflects real RMS only.
 * - Demo vs Live mode is clearly distinguished: a visual indicator is always present when the
 *   synthetic/test signal (Sound Lab) is driving the analysis.
 */
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
        // Header with classification toggle and demo/live indicator
        NeuralAudioHeader(
            isActive = state.isActive,
            isDemoMode = state.isDemoMode,
            backendName = state.backendName,
            inferenceTimeMs = state.inferenceTimeMs,
            audioActivityLevel = state.audioActivityLevel,
            onToggleClassification = onToggleClassification,
            onToggleDemoMode = onToggleDemoMode
        )

        Spacer(Modifier.height(14.dp))

        // Audio activity visualization — real RMS level only, no synthetic metrics
        AudioActivityMeter(level = state.audioActivityLevel)

        Spacer(Modifier.height(14.dp))

        // Status message (honest error reporting, never hidden)
        if (state.statusMessage != null) {
            StatusBanner(message = state.statusMessage!!)
            Spacer(Modifier.height(14.dp))
        }

        // Top-5 classification results
        ClassificationResults(
            results = state.topResults,
            inferenceTimeMs = state.inferenceTimeMs,
            backend = state.backendName
        )

        Spacer(Modifier.height(14.dp))

        // Demo mode warning — always visible when synthetic signal drives the analysis
        if (state.isDemoMode) {
            DemoModeIndicator()
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
    SectionCard(
        borderColor = if (isActive) GreenAccent.copy(alpha = 0.5f) else CardBorder
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
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Paper
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (isActive) "Audio-Klassifikation aktiv" else "Audio-Klassifikation pausiert",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Muted
                    )
                }
                Switch(
                    checked = isActive,
                    onCheckedChange = { onToggleClassification() },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics {
                            contentDescription = if (isActive)
                                "Audio-Klassifikation ist eingeschaltet"
                            else
                                "Audio-Klassifikation ist ausgeschaltet"
                        },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Ink,
                        checkedTrackColor = Copper,
                        uncheckedThumbColor = Muted,
                        uncheckedTrackColor = Surface2
                    )
                )
            }

            Spacer(Modifier.height(14.dp))

            // Backend, timing and activity info — flat info chips, no elevation
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                InfoChip(label = "Backend", value = backendName)
                InfoChip(label = "Latenz", value = "${inferenceTimeMs} ms")
                InfoChip(
                    label = "Aktivität",
                    value = "${(audioActivityLevel * 100).toInt()} %"
                )
            }

            Spacer(Modifier.height(14.dp))

            // Demo / Live mode toggle — always visible, clearly labelled
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(
                    text = if (isDemoMode) "Demo-Modus (Testsignal)" else "Live-Modus",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isDemoMode) Warning else Paper
                )
                Spacer(modifier = Modifier.weight(1f))
                Switch(
                    checked = isDemoMode,
                    onCheckedChange = { onToggleDemoMode() },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics {
                            contentDescription = if (isDemoMode)
                                "Demo-Modus ist eingeschaltet — lokales Testsignal wird verwendet"
                            else
                                "Live-Modus ist eingeschaltet — echte Audioeingabe wird verwendet"
                        },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Ink,
                        checkedTrackColor = Warning,
                        uncheckedThumbColor = Muted,
                        uncheckedTrackColor = Surface2
                    )
                )
            }
        }
    }
}

@Composable
private fun InfoChip(label: String, value: String) {
    Surface(
        color = Surface2,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, CardBorder.copy(alpha = 0.6f), RoundedCornerShape(12.dp)
        ),
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = Muted
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    fontWeight = FontWeight.Medium,
                    color = Paper,
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

    SectionCard {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Audio-Aktivität",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Paper
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${(normalizedLevel * 100).toInt()} %",
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = CopperSoft
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(Surface2, RoundedCornerShape(8.dp)),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(barCount) { index ->
                    val active = normalizedLevel > index.toFloat() / barCount
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .background(
                                if (active) Copper else Muted.copy(alpha = 0.25f),
                                RoundedCornerShape(2.dp)
                            )
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
    SectionCard {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "Erkannte Ereignisse",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Paper,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (results.isEmpty()) {
                Text(
                    text = "Noch keine Klassifikation verfügbar. Aktiviere die Audioanalyse.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                    modifier = Modifier.padding(vertical = 16.dp),
                    textAlign = TextAlign.Center
                )
            } else {
                results.forEachIndexed { index, (label, score) ->
                    ClassificationRow(
                        rank = index + 1,
                        label = label,
                        score = score,
                        isTop = index == 0
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DiagnosticChip("Inferenzzeit", "${inferenceTimeMs} ms")
                    DiagnosticChip("Backend", backend)
                }
            }
        }
    }
}

@Composable
private fun RowScope.DiagnosticChip(label: String, value: String) {
    Surface(
        color = Surface2,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            1.dp, CardBorder.copy(alpha = 0.6f)
        ),
        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = Muted)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    fontWeight = FontWeight.Medium,
                    color = Paper,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun ClassificationRow(
    rank: Int,
    label: String,
    score: Float,
    isTop: Boolean
) {
    val normalizedScore = score.coerceIn(0f, 1f)
    val accent = if (isTop) CopperSoft else Paper

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .semantics {
                contentDescription = "Platz $rank: $label, ${(normalizedScore * 100).toInt()} Prozent"
            }
    ) {
        // Rank badge — flat circle, colour + text (never colour alone)
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(if (isTop) Copper else Surface2, CircleShape)
                .border(
                    if (isTop) 0.dp else 1.dp,
                    CardBorder.copy(alpha = 0.6f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = rank.toString(),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = if (isTop) Ink else Muted
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Label
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isTop) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.weight(1f),
            color = accent,
            maxLines = 1
        )

        Spacer(modifier = Modifier.width(10.dp))

        // Score bar — flat, single colour (no decorative gradient)
        Box(
            modifier = Modifier
                .width(64.dp)
                .height(20.dp)
                .background(Surface2, RoundedCornerShape(10.dp))
        ) {
            Box(
                modifier = Modifier
                    .width((normalizedScore * 56f).coerceIn(0f, 56f).dp)
                    .height(20.dp)
                    .background(if (isTop) Copper else Surface2, RoundedCornerShape(10.dp))
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Score percentage
        Text(
            text = "${(normalizedScore * 100).toInt()} %",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            fontWeight = FontWeight.Medium,
            color = if (isTop) CopperSoft else Muted
        )
    }
}

/**
 * Demo-mode indicator. Always visible when the synthetic/test signal drives the analysis.
 * This satisfies the requirement that synthetic/test signals are visually distinguished from
 * live measurements — colour is never the only signal; text + form + semantics are all used.
 */
@Composable
private fun DemoModeIndicator() {
    SectionCard(borderColor = Warning.copy(alpha = 0.6f)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(Warning, CircleShape)
                    .semantics { contentDescription = "Demo-Modus" }
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Demo-Modus aktiv",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Paper
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Die Analyse verwendet ein lokales Testsignal (Sound Lab). Keine echte Audioeingabe wird gemessen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted
                )
            }
        }
    }
}

@Composable
private fun StatusBanner(message: String) {
    SectionCard(borderColor = Warning.copy(alpha = 0.6f)) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = Warning,
            modifier = Modifier.padding(16.dp)
        )
    }
}

// Helper removed — inline clamping used instead
