package de.sonora.app.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import de.sonora.app.BuildConfig
import de.sonora.app.SonoraUiState
import de.sonora.app.SonoraViewModel
import de.sonora.app.ai.HearingPreference
import de.sonora.app.audio.EngineStatus
import de.sonora.app.audio.HeadphoneModelStatus
import de.sonora.app.audio.SonoraEngine
import de.sonora.app.core.BandCount
import de.sonora.app.core.bandFrequencies
import de.sonora.app.data.PresetCatalog
import de.sonora.app.data.SoundProfile
import java.util.Locale
import kotlin.math.abs

// ---------------------------------------------------------------- Bausteine & Cards

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    borderColor: Color = CardBorder,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color = Surface1,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
    ) {
        Column(Modifier.padding(18.dp), content = content)
    }
}

@Composable
fun DiagnosticRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = Muted
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = Paper,
            textAlign = TextAlign.End
        )
    }
}

private fun hasPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun environmentLabel(level: Int?): String = when (level) {
    0 -> "Leise Umgebung"
    1 -> "Mittlere Lautstärke"
    2 -> "Laute Umgebung"
    else -> "Wird ermittelt …"
}

private fun frequencyLabel(hz: Int): String = if (hz >= 1000) "${hz / 1000}k" else hz.toString()

private fun bandLabels(count: BandCount): List<String> {
    val frequencies = bandFrequencies(count)
    if (count == BandCount.TEN) return frequencies.map(::frequencyLabel)

    val labels = MutableList(frequencies.size) { "" }
    listOf(63, 250, 1_000, 4_000, 16_000).forEach { anchor ->
        val nearest = frequencies.indices.minByOrNull { index -> abs(frequencies[index] - anchor) }
        if (nearest != null) labels[nearest] = frequencyLabel(anchor)
    }
    return labels
}

// ---------------------------------------------------------------- Onboarding

@Composable
fun OnboardingScreen(vm: SonoraViewModel) {
    var step by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    var micGranted by remember {
        mutableStateOf(hasPermission(context, Manifest.permission.RECORD_AUDIO))
    }
    var notificationGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        )
    }
    val routing = vm.uiState.audioRouting
    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> micGranted = granted }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notificationGranted = granted }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Spacer(Modifier.height(32.dp))
        Text(
            text = "Sonora",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = Copper
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Präzisions-Klangbegleiter für das Galaxy A56",
            style = MaterialTheme.typography.titleLarge,
            color = Paper
        )

        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) { index ->
                Box(
                    Modifier
                        .width(if (index == step) 28.dp else 10.dp)
                        .height(6.dp)
                        .background(
                            if (index <= step) Copper else Surface2,
                            CircleShape
                        )
                )
            }
        }
        Spacer(Modifier.height(28.dp))

        when (step) {
            0 -> {
                SectionCard {
                    Text(
                        text = "Systemweiter No-Root Klang",
                        style = MaterialTheme.typography.titleMedium,
                        color = Paper
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Sonora passt den Klang dort an, wo Android den Audioeffekt freigibt. Klangvorschläge entstehen lokal aus festen Regeln; ein lernendes Modell sowie NPU-/GPU-Beschleunigung sind derzeit nicht eingebunden.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Muted
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Kein Konto, keine Telemetrie, kein Audioversand ins Netzwerk.",
                        style = MaterialTheme.typography.bodySmall,
                        color = CopperSoft
                    )
                }
                Spacer(Modifier.height(28.dp))
                Button(
                    onClick = { step = 1 },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Copper, contentColor = Ink)
                ) { Text("Weiter", fontWeight = FontWeight.Bold) }
            }

            1 -> {
                Text("Audioausgang & Hörer", style = MaterialTheme.typography.titleLarge, color = Paper)
                Spacer(Modifier.height(14.dp))
                SectionCard {
                    when (val status = routing.headphoneModel) {
                        is HeadphoneModelStatus.VerifiedModel -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(10.dp).background(GreenAccent, CircleShape))
                                Spacer(Modifier.width(8.dp))
                                Text(status.modelName, style = MaterialTheme.typography.titleMedium, color = Paper)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(status.details, style = MaterialTheme.typography.bodySmall, color = Muted)
                            Spacer(Modifier.height(6.dp))
                            Text("Modell erkannt. Die Presets sind nicht individuell auf diesen Hörer eingemessen.", style = MaterialTheme.typography.bodyMedium, color = Paper)
                        }
                        is HeadphoneModelStatus.GenericUsbOutput -> {
                            Text(status.message, style = MaterialTheme.typography.titleMedium, color = Paper)
                            Spacer(Modifier.height(6.dp))
                            Text("Ein USB-Audioausgang ist aktiv. Das genaue Modell konnte über Android nicht verifiziert werden; Sonora passt den Klang linear an.", style = MaterialTheme.typography.bodySmall, color = Muted)
                        }
                        HeadphoneModelStatus.BuiltInSpeaker -> {
                            Text("Telefon-Lautsprecher aktiv", style = MaterialTheme.typography.titleMedium, color = Paper)
                            Spacer(Modifier.height(6.dp))
                            Text("Für das beste Klangerlebnis empfehlen wir die kabelgebundenen EO-IC100 USB-C Hörer.", style = MaterialTheme.typography.bodySmall, color = Muted)
                        }
                        else -> {
                            Text("Kein USB-C Hörer erkannt", style = MaterialTheme.typography.titleMedium, color = Warning)
                            Spacer(Modifier.height(6.dp))
                            Text("Du kannst die Hörer jederzeit einstecken. Sonora aktualisiert die Erkennung automatisch in Echtzeit.", style = MaterialTheme.typography.bodySmall, color = Muted)
                        }
                    }
                }
                Spacer(Modifier.height(28.dp))
                Button(
                    onClick = { step = 2 },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Copper, contentColor = Ink)
                ) { Text("Weiter", fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { step = 0 }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Zurück", color = Muted) }
            }

            else -> {
                Text("Berechtigungen & Kontrolle", style = MaterialTheme.typography.titleLarge, color = Paper)
                Spacer(Modifier.height(14.dp))
                PermissionCard(
                    title = "Umgebungsmessung (Mikrofon)",
                    body = "Misst den Umgebungspegel zur EQ-Anpassung. Das ist keine aktive Geräuschunterdrückung.",
                    granted = micGranted,
                    actionLabel = "Freigeben",
                    onAction = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                )
                Spacer(Modifier.height(12.dp))
                PermissionCard(
                    title = "Benachrichtigungen",
                    body = "Ermöglicht den ausfallsicheren Android-Vordergrunddienst zur kontinuierlichen Klangsteuerung.",
                    granted = notificationGranted,
                    actionLabel = "Freigeben",
                    onAction = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Mikrofon ist optional: Der Equalizer und alle 14 Presets funktionieren auch ohne Mikrofonberechtigung uneingeschränkt.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted
                )
                Spacer(Modifier.height(28.dp))
                Button(
                    onClick = { vm.completeOnboarding() },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Copper, contentColor = Ink)
                ) { Text("Sonora starten", fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { step = 1 }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Zurück", color = Muted) }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    body: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    SectionCard(borderColor = if (granted) GreenAccent.copy(alpha = 0.5f) else CardBorder) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Paper)
                Spacer(Modifier.height(4.dp))
                Text(body, style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            Spacer(Modifier.width(12.dp))
            if (granted) {
                Text("Aktiv", color = GreenAccent, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            } else {
                OutlinedButton(
                    onClick = onAction,
                    modifier = Modifier.heightIn(min = 48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Copper)
                ) { Text(actionLabel) }
            }
        }
    }
}

@Composable
private fun PlaybackWaveformCard(
    state: SonoraUiState,
    onEnabledChange: (Boolean) -> Unit
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Live-Wellenform", style = MaterialTheme.typography.titleMedium, color = Paper)
                Text("Zeigt den hörbaren Systemausgang. Android braucht dafür die Mikrofonfreigabe.", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            val context = LocalContext.current
            Switch(
                checked = state.playbackVisualizationEnabled,
                enabled = state.playbackVisualizationEnabled || hasPermission(context, Manifest.permission.RECORD_AUDIO),
                onCheckedChange = onEnabledChange,
                modifier = Modifier.semantics {
                    role = Role.Switch
                    contentDescription = "Live-Wellenform für den Audioausgang"
                }
            )
        }
        Spacer(Modifier.height(10.dp))
        if (state.playbackVisualizationEnabled && state.playbackWaveformAvailable && state.playbackWaveform.isNotEmpty()) {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .animateContentSize()
                    .semantics { contentDescription = "Live-Wellenform der laufenden Wiedergabe" }
            ) {
                val samples = state.playbackWaveform
                val middle = size.height / 2f
                val path = Path()
                samples.forEachIndexed { index, sample ->
                    val x = size.width * index / (samples.size - 1).coerceAtLeast(1)
                    val y = middle - sample.coerceIn(-1f, 1f) * middle * 0.85f
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawLine(
                    color = DividerColor,
                    start = Offset(0f, middle),
                    end = Offset(size.width, middle),
                    strokeWidth = 1.dp.toPx()
                )
                drawPath(
                    path = path,
                    color = Copper,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            Text(
                if (state.playbackWaveform.any { kotlin.math.abs(it) > 0.02f }) "Systemausgang wird visualisiert" else "Warte auf hörbares Audiosignal …",
                style = MaterialTheme.typography.bodySmall,
                color = Muted
            )
        } else if (!state.playbackVisualizationEnabled) {
            val context = LocalContext.current
            Text(
                if (hasPermission(context, Manifest.permission.RECORD_AUDIO)) "Ausgeschaltet. Sonora liest kein Audiosignal mit, bis du die Visualisierung einschaltest." else "Mikrofonfreigabe fehlt. Du kannst sie unter Mehr freigeben; die Visualisierung bleibt optional.",
                style = MaterialTheme.typography.bodySmall,
                color = Muted
            )
        } else {
            Text(
                "Warte auf laufende Wiedergabe. Android gibt die Wellenform gegebenenfalls nicht frei.",
                style = MaterialTheme.typography.bodySmall,
                color = Muted
            )
        }
    }
}

// ---------------------------------------------------------------- Jetzt (Dashboard)

@Composable
fun HomeScreen(vm: SonoraViewModel) {
    val state = vm.uiState
    var showAiAdvisor by remember { mutableStateOf(false) }

    if (showAiAdvisor) {
        AiAdvisorDialog(
            state = state,
            onDismiss = { showAiAdvisor = false },
            onRequest = { pref -> vm.requestAiSuggestion(pref) },
            onApply = {
                vm.applyAiSuggestion()
                showAiAdvisor = false
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Header: Brand & Master Switch
        item {
            HeroHeader(
                masterEnabled = state.masterEnabled,
                onMasterChange = vm::setMasterEnabled
            )
        }

        // Section 1: Audio Routing Hero Card
        item {
            AudioRoutingHeroCard(
                routing = state.audioRouting,
                onRefresh = vm::refreshAudioRouting
            )
        }

        // Section 2: Audio engine status card
        item {
            EngineAndHardwareCard(
                engineStatus = state.engineStatus,
                hwState = state.hardwareState,
                onRetry = vm::retryEngine
            )
        }

        // Section 3: Active Profile Hero Card
        item {
            ActiveProfileHeroCard(
                state = state,
                onOpenAdvisor = { showAiAdvisor = true }
            )
        }

        // Section 4: AI Suggestion Banner if available
        state.aiSuggestion?.let { suggestion ->
            item {
                AiSuggestionBanner(
                    suggestion = suggestion,
                    onApply = vm::applyAiSuggestion,
                    onDismiss = vm::dismissAiSuggestion
                )
            }
        }

        // Section 5: Playback visualizer
        item {
            PlaybackWaveformCard(
                state = state,
                onEnabledChange = vm::setPlaybackVisualizationEnabled
            )
        }

        // Section 6: Ambient Environment
        item {
            EnvironmentHeroCard(
                state = state,
                onAutoAdjust = vm::setAutoAdjust
            )
        }

        // Section 7: Now Playing Card
        state.nowPlaying?.let { now ->
            item {
                SectionCard {
                    Text("Medien-Inhalt erkannt", style = MaterialTheme.typography.labelMedium, color = Copper)
                    Spacer(Modifier.height(4.dp))
                    Text(now.appLabel, style = MaterialTheme.typography.titleMedium, color = Paper)
                    now.title?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Muted) }
                }
            }
        }

        // Section 8: Hearing Safety Guard Reminder
        if (state.volumeState.reminderDue) {
            item {
                HearingGuardCard(
                    minutesLoud = state.volumeState.minutesLoud,
                    onAcknowledge = vm::acknowledgeVolumeReminder,
                    onDisable = { vm.setVolumeReminderEnabled(false) }
                )
            }
        }

        // Section 9: Quick Action Hub
        item {
            ActionHubCard(
                onStartTest = vm::startBlindTest,
                onStartInterview = vm::startInterview,
                onOpenAdvisor = { showAiAdvisor = true }
            )
        }
    }
}

@Composable
private fun HeroHeader(
    masterEnabled: Boolean,
    onMasterChange: (Boolean) -> Unit
) {
    SectionCard(
        borderColor = if (masterEnabled) Copper.copy(alpha = 0.6f) else CardBorder
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Sonora",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Paper
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(if (masterEnabled) GreenAccent else Muted, CircleShape)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (masterEnabled) "Klangoptimierung aktiv" else "Sonora pausiert",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (masterEnabled) Copper else Muted
                    )
                }
            }
            Switch(
                checked = masterEnabled,
                onCheckedChange = onMasterChange,
                modifier = Modifier
                    .semantics {
                        role = Role.Switch
                        contentDescription = if (masterEnabled) "Sonora Hauptschalter ist eingeschaltet" else "Sonora Hauptschalter ist ausgeschaltet"
                    }
                    .padding(end = 4.dp),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Ink,
                    checkedTrackColor = Copper,
                    uncheckedThumbColor = Muted,
                    uncheckedTrackColor = Surface2
                )
            )
        }
    }
}

@Composable
private fun AudioRoutingHeroCard(
    routing: de.sonora.app.audio.AudioDeviceSnapshot,
    onRefresh: () -> Unit
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Audioausgang & Routing",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Paper,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onRefresh, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Prüfen", color = CopperSoft, style = MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = DividerColor)
        Spacer(Modifier.height(8.dp))

        // Output Status
        when (val model = routing.headphoneModel) {
            is HeadphoneModelStatus.VerifiedModel -> {
                DiagnosticRow("Erkannter Hörer", model.modelName)
                DiagnosticRow("Gerätedetails", model.details)
            }
            is HeadphoneModelStatus.GenericUsbOutput -> {
                DiagnosticRow("Erkannter Hörer", model.message)
            }
            HeadphoneModelStatus.BuiltInSpeaker -> {
                DiagnosticRow("Erkannter Hörer", "Lautsprecher (Intern)")
            }
            is HeadphoneModelStatus.OtherDevice -> {
                DiagnosticRow("Erkannter Hörer", model.name)
            }
            HeadphoneModelStatus.None -> {
                DiagnosticRow("Erkannter Hörer", "Kein Ausgang aktiv")
            }
        }

        // Active Routing
        routing.activeOutput?.let { active ->
            DiagnosticRow("Aktives Routing", "${active.typeName} (${active.safeAddress()})")
        } ?: DiagnosticRow("Aktives Routing", "Standard-Ausgabe")

        // Input Devices
        val micCount = routing.detectedInputs.size
        DiagnosticRow("Audioeingänge", if (micCount > 0) "$micCount verfügbar (kein Record-Zwang)" else "Kein Mikrofon")
    }
}

private fun de.sonora.app.audio.AudioOutputInfo.safeAddress(): String =
    if (address.isNotBlank() && address != "–") address else "Port $id"

@Composable
private fun EngineAndHardwareCard(
    engineStatus: EngineStatus,
    hwState: de.sonora.app.audio.dsp.DspHardwareState,
    onRetry: () -> Unit
) {
    SectionCard {
        Text(
            text = "Klang-Engine",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Paper
        )
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = DividerColor)
        Spacer(Modifier.height(8.dp))

        // System output effect status
        when (engineStatus) {
            is EngineStatus.Active -> {
                DiagnosticRow("Systemausgang (Session 0)", "Bestätigt (${engineStatus.bands} Bänder)")
                DiagnosticRow("Verarbeitung angefordert", if (engineStatus.processingRequested) "Ja" else "Nein")
                DiagnosticRow("Audioeffekt-Kontrolle", if (engineStatus.processingAvailable) "Freigegeben" else "Verweigert")
                Text("Effekt-Session ist aktiv; eine hörbare Wirkung in allen Apps ist dadurch nicht garantiert.", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            is EngineStatus.Unavailable -> {
                DiagnosticRow("Systemausgang", "Nicht verfügbar")
                Text(
                    text = engineStatus.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = Warning,
                    modifier = Modifier.padding(top = 4.dp)
                )
                TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Session 0 erneut anfordern", color = Copper)
                }
            }
            EngineStatus.Inactive -> {
                DiagnosticRow("Systemausgang", "Inaktiv")
                TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Initialisieren", color = Copper)
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = DividerColor)
        Spacer(Modifier.height(4.dp))

        DiagnosticRow("Lokale Berechnung", hwState.backendName)
        Text(
            text = hwState.details,
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun ActiveProfileHeroCard(
    state: SonoraUiState,
    onOpenAdvisor: () -> Unit
) {
    val activeProfile = state.profiles.firstOrNull { it.id == state.activeProfileId }
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Aktives Klangprofil", style = MaterialTheme.typography.labelMedium, color = Copper)
                Spacer(Modifier.height(2.dp))
                Text(state.activeProfileName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Paper)
            }
            OutlinedButton(
                onClick = onOpenAdvisor,
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Copper)
            ) {
                Text("KI-Berater")
            }
        }

        Spacer(Modifier.height(6.dp))
        activeProfile?.let { prof ->
            DiagnosticRow("Vorverstärkung (Preamp)", String.format(Locale.GERMAN, "%+.1f dB", state.preampDb))
            prof.origin?.let { DiagnosticRow("Herkunft", it) }
            prof.uncertainty?.let { DiagnosticRow("Genauigkeit", it) }
        }

        if (state.dirty) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "● Nicht gespeicherte manuelle Regleranpassungen aktiv",
                style = MaterialTheme.typography.bodySmall,
                color = Warning
            )
        }
    }
}

@Composable
private fun AiSuggestionBanner(
    suggestion: de.sonora.app.ai.AiSuggestion,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    SectionCard(borderColor = Copper) {
        Text("Neuer KI-Profilvorschlag", style = MaterialTheme.typography.labelMedium, color = Copper)
        Spacer(Modifier.height(4.dp))
        Text(suggestion.profileName, style = MaterialTheme.typography.titleMedium, color = Paper)
        Spacer(Modifier.height(4.dp))
        Text(suggestion.explanation, style = MaterialTheme.typography.bodyMedium, color = Muted)
        Spacer(Modifier.height(8.dp))
        DiagnosticRow("Preamp / Headroom", String.format(Locale.GERMAN, "%.1f dB", suggestion.preampDb))
        DiagnosticRow("Erstellt mit", suggestion.backendUsed)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onApply,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Copper, contentColor = Ink)
            ) { Text("Übernehmen") }
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
            ) { Text("Verwerfen") }
        }
    }
}

@Composable
private fun EnvironmentHeroCard(
    state: SonoraUiState,
    onAutoAdjust: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val microphoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onAutoAdjust(true)
    }
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Umgebungsanpassung", style = MaterialTheme.typography.labelMedium, color = Muted)
                Spacer(Modifier.height(2.dp))
                AnimatedContent(
                    targetState = environmentLabel(state.environment?.level),
                    label = "Umgebungspegel"
                ) { label ->
                    Text(label, style = MaterialTheme.typography.titleMedium, color = Paper)
                }
                Text(
                    text = if (state.autoAdjust) "EQ passt den Klang an die gemessene Umgebungslautstärke an. Keine Geräuschunterdrückung." else "EQ bleibt unverändert.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted
                )
            }
            Switch(checked = state.autoAdjust, onCheckedChange = { enabled ->
                if (enabled && !hasPermission(context, Manifest.permission.RECORD_AUDIO)) {
                    microphoneLauncher.launch(Manifest.permission.RECORD_AUDIO)
                } else {
                    onAutoAdjust(enabled)
                }
            })
        }
    }
}

@Composable
private fun HearingGuardCard(
    minutesLoud: Int,
    onAcknowledge: () -> Unit,
    onDisable: () -> Unit
) {
    SectionCard(borderColor = Warning) {
        Text("Hörschutz-Erinnerung", style = MaterialTheme.typography.titleMedium, color = Warning)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Du hörst seit $minutesLoud Minuten bei hohem Ausgangspegel. Zum Schutz deiner Ohren empfiehlt Sonora eine kurze Pause. Sonora senkt die Lautstärke niemals ungefragt.",
            style = MaterialTheme.typography.bodyMedium,
            color = Paper
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onAcknowledge, modifier = Modifier.heightIn(min = 48.dp)) { Text("Verstanden") }
            OutlinedButton(onClick = onDisable, modifier = Modifier.heightIn(min = 48.dp)) { Text("Erinnerung aus") }
        }
    }
}

@Composable
private fun ActionHubCard(
    onStartTest: () -> Unit,
    onStartInterview: () -> Unit,
    onOpenAdvisor: () -> Unit
) {
    SectionCard {
        Text("Assistenten & Werkzeuge", style = MaterialTheme.typography.titleMedium, color = Paper)
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onOpenAdvisor,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Surface2, contentColor = Paper)
            ) {
                Text("KI-Berater", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onStartInterview,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Surface2, contentColor = Paper)
            ) {
                Text("Interview", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onStartTest,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Surface2, contentColor = Paper)
            ) {
                Text("Hörtest", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ---------------------------------------------------------------- KI Advisor Dialog

@Composable
fun AiAdvisorDialog(
    state: SonoraUiState,
    onDismiss: () -> Unit,
    onRequest: (HearingPreference) -> Unit,
    onApply: () -> Unit
) {
    var genre by remember { mutableStateOf("Rock") }
    var bass by remember { mutableStateOf("Ausgewogen") }
    var speech by remember { mutableStateOf("Normal") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Klangvorschlag", color = Paper) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Ein paar Antworten helfen Sonora, einen passenden EQ-Vorschlag aus festen Klangregeln zu erstellen. Das ist kein lernendes Modell.", style = MaterialTheme.typography.bodySmall, color = Muted)
                Spacer(Modifier.height(12.dp))

                Text("Genre:", style = MaterialTheme.typography.labelMedium, color = Copper)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Rock", "Metal", "Hip-Hop", "Elektronisch", "Klassik", "Podcast").forEach { item ->
                        FilterChip(
                            selected = genre == item,
                            onClick = { genre = item },
                            label = { Text(item) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text("Tieftonabstimmung:", style = MaterialTheme.typography.labelMedium, color = Copper)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Kräftig", "Ausgewogen", "Trocken", "Zurückhaltend").forEach { item ->
                        FilterChip(
                            selected = bass == item,
                            onClick = { bass = item },
                            label = { Text(item) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text("Sprachverständlichkeit:", style = MaterialTheme.typography.labelMedium, color = Copper)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Normal", "Hervorgehoben", "Sanft").forEach { item ->
                        FilterChip(
                            selected = speech == item,
                            onClick = { speech = item },
                            label = { Text(item) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text("Alles bleibt auf dem Gerät. Sonora zeichnet hier kein Audio auf.", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onRequest(HearingPreference(genre, bass, speech, "Ruhig"))
                },
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Copper, contentColor = Ink)
            ) {
                Text("Kurve berechnen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Schließen", color = Muted) }
        }
    )
}

// ---------------------------------------------------------------- Klang (Equalizer Screen)

@Composable
fun EqualizerScreen(vm: SonoraViewModel) {
    val state = vm.uiState
    val frequencies = remember(state.bandCount) { bandFrequencies(state.bandCount) }
    val labels = remember(state.bandCount) { bandLabels(state.bandCount) }
    var showSave by remember { mutableStateOf(false) }

    if (showSave) {
        SaveProfileDialog(
            onDismiss = { showSave = false },
            onSave = { name ->
                vm.saveCurrentAs(name)
                showSave = false
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Klang",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Paper
                )
                Text(
                    text = "${state.bandCount.size} Regler (−12 bis +12 dB) · Präzisions-EQ",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BandCount.entries.forEach { count ->
                    FilterChip(
                        selected = state.bandCount == count,
                        onClick = { vm.setBandCount(count) },
                        modifier = Modifier.heightIn(min = 48.dp),
                        label = { Text(if (count == BandCount.SIXTY_FOUR) "64+" else count.size.toString()) }
                    )
                }
            }
        }

        item {
            SectionCard {
                Text("Vorverstärkung & Limiter", style = MaterialTheme.typography.titleSmall, color = Paper)
                Spacer(Modifier.height(4.dp))
                Slider(
                    value = state.preampDb,
                    onValueChange = vm::setPreamp,
                    valueRange = -12f..12f,
                    steps = 47,
                    modifier = Modifier.semantics {
                        contentDescription = "Vorverstärkung: ${String.format(Locale.GERMAN, "%.1f", state.preampDb)} Dezibel"
                    },
                    colors = SliderDefaults.colors(thumbColor = Copper, activeTrackColor = Copper)
                )
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        text = String.format(Locale.GERMAN, "Preamp: %+.1f dB", state.preampDb),
                        style = MaterialTheme.typography.labelMedium,
                        color = Muted,
                        modifier = Modifier.weight(1f)
                    )
                    if (state.clipWarning) {
                        Text(
                            text = "Limiter aktiv (Übersteuerung verhindert)",
                            style = MaterialTheme.typography.bodySmall,
                            color = Warning
                        )
                    }
                }
            }
        }

        items(state.eqCurve.levelsDb.size) { index ->
            BandRow(
                hz = frequencies[index],
                index = index,
                label = labels[index],
                value = state.eqCurve.levelsDb[index],
                onChange = vm::setBand
            )
        }

        item { AbCompare(vm) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = vm::resetLevels,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) { Text("Zurücksetzen") }
                Button(
                    onClick = { showSave = true },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Copper, contentColor = Ink)
                ) { Text("Profil speichern") }
            }
        }
    }
}

@Composable
private fun BandRow(index: Int, hz: Int, label: String, value: Float, onChange: (Int, Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            modifier = Modifier.width(48.dp),
            style = MaterialTheme.typography.labelMedium,
            color = Muted
        )
        Slider(
            value = value,
            onValueChange = { newValue -> onChange(index, newValue) },
            valueRange = -12f..12f,
            steps = 47,
            modifier = Modifier.weight(1f).semantics {
                contentDescription = "$hz Hertz: ${String.format(Locale.GERMAN, "%.1f", value)} Dezibel"
            },
            colors = SliderDefaults.colors(thumbColor = Copper, activeTrackColor = Copper)
        )
        Text(
            text = String.format(Locale.GERMAN, "%+.1f dB", value),
            modifier = Modifier.width(68.dp),
            style = MaterialTheme.typography.labelMedium,
            color = Paper,
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun AbCompare(vm: SonoraViewModel) {
    val state = vm.uiState
    var pressed by remember { mutableStateOf(false) }

    SectionCard {
        Text("A/B Direktvergleich", style = MaterialTheme.typography.titleMedium, color = Paper)
        Spacer(Modifier.height(4.dp))
        Text("Halte den Taster gedrückt, um das unbearbeitete Originalsignal im Vergleich zu hören.", style = MaterialTheme.typography.bodySmall, color = Muted)
        Spacer(Modifier.height(10.dp))
        Surface(
            color = if (pressed) Copper else Surface2,
            contentColor = if (pressed) Ink else Paper,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            pressed = true
                            vm.beginBypass()
                            tryAwaitRelease()
                            pressed = false
                            vm.endBypass()
                        }
                    )
                }
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (pressed) "Originalklang (Bypass aktiv)" else "Gedrückt halten für Originalklang",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Profile Screen

@Composable
fun ProfilesScreen(vm: SonoraViewModel) {
    val state = vm.uiState
    val clipboard = LocalClipboardManager.current
    var showSave by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }
    var importText by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf<String?>(null) }
    var selectedPresetTab by remember { mutableIntStateOf(0) } // 0: Eigene, 1: Presets (14)

    if (showRenameDialog != null) {
        val targetId = showRenameDialog!!
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = { Text("Profil umbenennen", color = Paper) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Neuer Profilname") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    vm.renameProfile(targetId, renameText)
                    showRenameDialog = null
                }, enabled = renameText.isNotBlank()) { Text("Speichern") }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = null }) { Text("Abbrechen") } }
        )
    }

    if (showImport) {
        AlertDialog(
            onDismissRequest = { showImport = false; importError = null },
            title = { Text("Profil importieren (JSON)", color = Paper) },
            text = {
                Column {
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it; importError = null },
                        label = { Text("JSON-Text einfügen") },
                        minLines = 4,
                        maxLines = 8,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Copper)
                    )
                    importError?.let { Text(it, color = Warning, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (vm.importProfile(importText)) {
                        showImport = false
                        importText = ""
                        importError = null
                    } else {
                        importError = vm.uiState.message
                    }
                }, enabled = importText.isNotBlank()) { Text("Importieren") }
            },
            dismissButton = { TextButton(onClick = { showImport = false }) { Text("Abbrechen") } }
        )
    }

    if (showSave) {
        SaveProfileDialog(
            onDismiss = { showSave = false },
            onSave = { name ->
                vm.saveCurrentAs(name)
                showSave = false
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Profile & Presets", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Paper)
                    Text("Aktives Profil: ${state.activeProfileName}", style = MaterialTheme.typography.bodySmall, color = Copper)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showImport = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Import") }
                    Button(onClick = { showSave = true }, modifier = Modifier.heightIn(min = 48.dp), colors = ButtonDefaults.buttonColors(containerColor = Copper, contentColor = Ink)) { Text("Sichern") }
                }
            }
        }

        // Toggle Tabs: Eigene Profile vs. Sonora 14-Preset Bibliothek
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(
                    selected = selectedPresetTab == 0,
                    onClick = { selectedPresetTab = 0 },
                    label = { Text("Eigene Profile (${state.profiles.filter { it.preset == null }.size})") },
                    modifier = Modifier.heightIn(min = 48.dp)
                )
                FilterChip(
                    selected = selectedPresetTab == 1,
                    onClick = { selectedPresetTab = 1 },
                    label = { Text("14 Studio-Presets") },
                    modifier = Modifier.heightIn(min = 48.dp)
                )
            }
        }

        if (selectedPresetTab == 0) {
            val userProfiles = state.profiles.filter { it.preset == null }
            if (userProfiles.isEmpty()) {
                item {
                    SectionCard {
                        Text("Noch keine eigenen Profile gespeichert", style = MaterialTheme.typography.titleMedium, color = Paper)
                        Spacer(Modifier.height(4.dp))
                        Text("Passe den Klang im Bereich „Klang“ an und speichere deine Kurve oder dupliziere eines der Presets.", style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                }
            } else {
                items(userProfiles, key = { it.id }) { prof ->
                    UserProfileCard(
                        profile = prof,
                        isActive = prof.id == state.activeProfileId,
                        onApply = { vm.selectProfile(prof.id) },
                        onDuplicate = { vm.duplicateProfile(prof.id) },
                        onRename = {
                            renameText = prof.name
                            showRenameDialog = prof.id
                        },
                        onDelete = { vm.deleteProfile(prof.id) },
                        onExport = {
                            vm.exportProfile(prof.id)?.let { clipboard.setText(AnnotatedString(it)) }
                        }
                    )
                }
            }
        } else {
            // 14 Presets from PresetCatalog
            items(PresetCatalog.presets, key = { it.id }) { preset ->
                PresetDetailCard(
                    preset = preset,
                    isActive = preset.id == state.activeProfileId,
                    onApply = {
                        val matchingProfile = state.profiles.firstOrNull { it.preset == preset.id }
                            ?: preset.toSoundProfile()
                        vm.applyPreset(matchingProfile)
                    },
                    onDuplicate = {
                        val matchingProfile = state.profiles.firstOrNull { it.preset == preset.id }
                            ?: preset.toSoundProfile()
                        vm.duplicateProfile(matchingProfile.id)
                    }
                )
            }
        }
    }
}

@Composable
private fun UserProfileCard(
    profile: SoundProfile,
    isActive: Boolean,
    onApply: () -> Unit,
    onDuplicate: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit
) {
    SectionCard(borderColor = if (isActive) Copper else CardBorder) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(profile.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Paper)
                Spacer(Modifier.height(2.dp))
                Text("Preamp: ${profile.preampDb} dB · ${profile.bandCount} Bänder", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            if (isActive) {
                Text("Aktiv", color = Copper, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = onApply, enabled = !isActive, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (isActive) "Aktiv" else "Aktivieren")
            }
            OutlinedButton(onClick = onDuplicate, modifier = Modifier.heightIn(min = 48.dp)) { Text("Duplizieren") }
            OutlinedButton(onClick = onRename, modifier = Modifier.heightIn(min = 48.dp)) { Text("Umbenennen") }
            OutlinedButton(onClick = onExport, modifier = Modifier.heightIn(min = 48.dp)) { Text("Exportieren") }
            TextButton(onClick = onDelete, modifier = Modifier.heightIn(min = 48.dp)) { Text("Löschen", color = Warning) }
        }
    }
}

@Composable
private fun PresetDetailCard(
    preset: de.sonora.app.data.PresetDefinition,
    isActive: Boolean,
    onApply: () -> Unit,
    onDuplicate: () -> Unit
) {
    SectionCard(borderColor = if (isActive) Copper else CardBorder) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${preset.name} ${preset.comparison}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Paper)
                Spacer(Modifier.height(2.dp))
                Text(preset.description, style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            if (isActive) {
                Text("Aktiv", color = Copper, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(Modifier.height(8.dp))
        DiagnosticRow("Preamp-Vorgabe", "${preset.preampDb} dB")
        DiagnosticRow("Herkunft", preset.origin)
        DiagnosticRow("Unsicherheit", preset.uncertainty)
        DiagnosticRow("Grenzen", preset.boundaryLimits)

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onApply,
                enabled = !isActive,
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Copper, contentColor = Ink)
            ) { Text(if (isActive) "Aktiviert" else "Aktivieren") }
            OutlinedButton(onClick = onDuplicate, modifier = Modifier.heightIn(min = 48.dp)) { Text("Kopie bearbeiten") }
        }
    }
}

// ---------------------------------------------------------------- Einstellungen (Mehr)

@Composable
fun SettingsScreen(vm: SonoraViewModel) {
    val state = vm.uiState
    val context = LocalContext.current
    var micGranted by remember { mutableStateOf(hasPermission(context, Manifest.permission.RECORD_AUDIO)) }
    var notificationGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text("Mehr & Diagnose", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Paper)
                Text("Gerät, Mikrofonfreigabe und Audioausgang", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        }

        item {
            SectionCard {
                Text("Was macht Sonora?", style = MaterialTheme.typography.titleMedium, color = Paper)
                Spacer(Modifier.height(6.dp))
                Text("Sonora passt Equalizer und verfügbare Audioeffekte an. Es leitet Musik nicht durch einen eigenen Player.", style = MaterialTheme.typography.bodySmall, color = Muted)
                Spacer(Modifier.height(4.dp))
                Text("Umgebungsanpassung verändert den EQ nach einer Lautstärkemessung. Sie entfernt keine Geräusche wie ANC-Kopfhörer.", style = MaterialTheme.typography.bodySmall, color = Muted)
                Spacer(Modifier.height(4.dp))
                Text("Umgebungsanpassung und Live-Wellenform sind optionale Funktionen. Sie brauchen Mikrofonfreigabe und lassen sich jederzeit wieder ausschalten.", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        }

        // Hardware details
        item {
            SectionCard {
                Text("Berechnung auf diesem Gerät", style = MaterialTheme.typography.titleMedium, color = Paper)
                Spacer(Modifier.height(6.dp))
                DiagnosticRow("Aktueller Rechenweg", state.hardwareState.backendName)
                DiagnosticRow("Hardware-Beschleunigung", if (state.hardwareState.isHardwareAccelerated) "Verifiziert aktiv" else "Nicht eingebunden")
                DiagnosticRow("Berechnung gemessen", if (state.hardwareState.latencyMicros > 0) "${state.hardwareState.latencyMicros} µs" else "Noch keine Messung")
                Text(state.hardwareState.details, style = MaterialTheme.typography.bodySmall, color = Muted)
                Spacer(Modifier.height(8.dp))
                Text("Der Klangberater verwendet feste Regeln: kein lernendes KI-Modell und keine NPU-/GPU-Verarbeitung.", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        }

        // Audio Routing Diagnose
        item {
            SectionCard {
                Text("Geräteerkennung & Routing-Protokoll", style = MaterialTheme.typography.titleMedium, color = Paper)
                Spacer(Modifier.height(6.dp))
                DiagnosticRow("Ausgänge physisch", "${state.audioRouting.detectedOutputs.size} erkannt")
                state.audioRouting.detectedOutputs.forEach { out ->
                    DiagnosticRow("Ausgang #${out.id}", "${out.typeName} [${out.productName}] (${out.address})")
                }
                DiagnosticRow("Eingänge physisch", "${state.audioRouting.detectedInputs.size} erkannt")
                state.audioRouting.detectedInputs.forEach { inp ->
                    DiagnosticRow("Eingang #${inp.id}", "${inp.typeName} [${inp.productName}] (${inp.address})")
                }
                state.audioRouting.lastEvent?.let { DiagnosticRow("Letztes Ereignis", it) }
            }
        }

        // Berechtigungen
        item {
            RuntimePermissionCard(
                title = "Umgebungsmessung (Mikrofon)",
                body = "Misst den Umgebungspegel zur EQ-Anpassung. Das ist keine aktive Geräuschunterdrückung.",
                permission = Manifest.permission.RECORD_AUDIO,
                onGrantedChange = { micGranted = it }
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            item {
                RuntimePermissionCard(
                    title = "Benachrichtigungen",
                    body = "Vordergrunddienst-Benachrichtigung für kontinuierliche Systemeffekte.",
                    permission = Manifest.permission.POST_NOTIFICATIONS,
                    onGrantedChange = { notificationGranted = it }
                )
            }
        }

        // Hörschutz & Hintergrunddienst
        item {
            SectionCard {
                Text("Hintergrunddienst", style = MaterialTheme.typography.titleMedium, color = Paper)
                Spacer(Modifier.height(6.dp))
                Text("Hält den verfügbaren Android-Audioeffekt im Hintergrund aktiv.", style = MaterialTheme.typography.bodySmall, color = Muted)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = vm::startService, modifier = Modifier.heightIn(min = 48.dp)) { Text("Starten") }
                    OutlinedButton(onClick = vm::stopService, modifier = Modifier.heightIn(min = 48.dp)) { Text("Beenden") }
                }
            }
        }

        // Diagnosebericht
        item {
            SectionCard {
                Text("Diagnoseprotokoll kopieren", style = MaterialTheme.typography.titleMedium, color = Paper)
                Spacer(Modifier.height(4.dp))
                Text("Exportiert alle System- und Routing-Ereignisse zur lokalen Fehlerprüfung.", style = MaterialTheme.typography.bodySmall, color = Muted)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(ClipboardManager::class.java)
                            clipboard?.setPrimaryClip(ClipData.newPlainText("Sonora-Diagnose", vm.diagnosticReport()))
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Copper, contentColor = Ink)
                    ) {
                        Text("Bericht kopieren")
                    }
                    OutlinedButton(onClick = vm::clearDiagnosticEvents, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Leeren")
                    }
                }
            }
        }
    }
}

@Composable
fun SaveProfileDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Profil speichern", color = Paper) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Profilname") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Copper)
            )
        },
        confirmButton = {
            Button(onClick = { onSave(name) }, enabled = name.isNotBlank(), modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Abbrechen") }
        }
    )
}

@Composable
fun RuntimePermissionCard(
    title: String,
    body: String,
    permission: String,
    onGrantedChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasPermission(context, permission)) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { result ->
        granted = result
        onGrantedChange(result)
    }
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Paper)
                Spacer(Modifier.height(4.dp))
                Text(body, style = MaterialTheme.typography.bodySmall, color = Muted)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (granted) "Status: Erlaubt" else "Status: Nicht freigegeben",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (granted) GreenAccent else Warning
                )
            }
            if (!granted) {
                OutlinedButton(
                    onClick = { launcher.launch(permission) },
                    modifier = Modifier.heightIn(min = 48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Copper)
                ) { Text("Freigeben") }
            }
        }
    }
}
