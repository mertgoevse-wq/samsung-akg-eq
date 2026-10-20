package de.sonora.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import de.sonora.app.BuildConfig
import de.sonora.app.SonoraUiState
import de.sonora.app.SonoraViewModel
import de.sonora.app.audio.EngineStatus
import de.sonora.app.audio.SonoraEngine
import de.sonora.app.data.Presets
import de.sonora.app.data.SoundProfile
import java.util.Locale

// ---------------------------------------------------------------- Bausteine

@Composable
private fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), content = content)
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = Muted
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.End
        )
    }
}

private fun hasPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun hasUsbHeadset(context: Context): Boolean {
    val manager = context.getSystemService(AudioManager::class.java) ?: return false
    return manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { device ->
        device.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
            device.type == AudioDeviceInfo.TYPE_USB_DEVICE
    }
}

private fun environmentLabel(level: Int?): String = when (level) {
    0 -> "leise"
    1 -> "mittel"
    2 -> "laut"
    else -> "wird gemessen"
}

private fun engineStatusText(status: EngineStatus): String = when (status) {
    is EngineStatus.Active -> "Systemausgang angefordert (${status.bands} Bänder)"
    is EngineStatus.Unavailable -> "Nicht verfügbar: ${status.reason}"
    EngineStatus.Inactive -> "Inaktiv"
}

private fun frequencyLabel(hz: Int): String = if (hz >= 1000) "${hz / 1000}k" else hz.toString()

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
    val usbConnected = remember { hasUsbHeadset(context) }
    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> micGranted = granted }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notificationGranted = granted }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Spacer(Modifier.height(32.dp))
        Text(
            text = "Sonora",
            style = MaterialTheme.typography.displaySmall,
            color = Copper
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Klang, der sich dir und deinem Tag anpasst.",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { index ->
                Box(
                    Modifier
                        .width(if (index == step) 22.dp else 8.dp)
                        .height(8.dp)
                        .background(
                            if (index <= step) Copper else MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape
                        )
                )
            }
        }
        Spacer(Modifier.height(24.dp))

        when (step) {
            0 -> {
                Text(
                    text = "Sonora stellt den Klang deines Galaxy A56 ein und reagiert auf deine Umgebung. Die Analyse passiert auf dem Handy.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Das Mikrofon bleibt auf dem Gerät. Es werden keine Audiodaten gesendet und keine Aufnahmen gespeichert.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted
                )
                Spacer(Modifier.height(28.dp))
                Button(
                    onClick = { step = 1 },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text("Weiter") }
            }

            1 -> {
                Text("Dein Hörer", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(14.dp))
                SectionCard {
                    Text(
                        text = if (usbConnected) {
                            "Samsung EO-IC100 erkannt"
                        } else {
                            "Kein USB-Hörer erkannt"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (usbConnected) {
                            "Der Hörer ist angeschlossen. Du kannst direkt starten."
                        } else {
                            "Stecke den Hörer ein, wenn du möchtest. Sonora läuft auch mit anderen Ausgängen; die Abstimmung ist dann weniger genau."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted
                    )
                }
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { step = 2 },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text("Weiter") }
                TextButton(onClick = { step = 0 }) { Text("Zurück") }
            }

            else -> {
                Text("Was Sonora darf", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(14.dp))
                PermissionCard(
                    title = "Mikrofon",
                    body = "Nur um die Lautstärke deiner Umgebung zu messen. Auf dem Gerät, nicht im Netz.",
                    granted = micGranted,
                    actionLabel = "Erlauben",
                    onAction = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                )
                Spacer(Modifier.height(10.dp))
                PermissionCard(
                    title = "Benachrichtigungen",
                    body = "Zeigt eine ruhige Zeile, solange Sonora im Hintergrund läuft.",
                    granted = notificationGranted,
                    actionLabel = "Erlauben",
                    onAction = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Du kannst alles später ändern. Sonora funktioniert auch ohne Mikrofon.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { vm.completeOnboarding() },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text("Sonora starten") }
                TextButton(onClick = { vm.completeOnboarding() }) {
                    Text("Ohne Mikrofon starten")
                }
                TextButton(onClick = { step = 1 }) { Text("Zurück") }
            }
        }
        Spacer(Modifier.height(32.dp))
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
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(body, style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            Spacer(Modifier.width(10.dp))
            if (granted) {
                Text("Erlaubt", color = Copper, style = MaterialTheme.typography.labelMedium)
            } else {
                TextButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

// ---------------------------------------------------------------- Jetzt

@Composable
fun HomeScreen(vm: SonoraViewModel) {
    val state = vm.uiState
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Jetzt",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (state.masterEnabled) "Sonora ist aktiv" else "Sonora ist aus",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.masterEnabled) Copper else Muted
                    )
                }
                Switch(
                    checked = state.masterEnabled,
                    onCheckedChange = vm::setMasterEnabled
                )
            }
        }
        item { ActiveProfileCard(state) }
        item { EnvironmentCard(state, vm::setAutoAdjust) }
        item { EngineCard(state, vm::retryEngine) }
        item {
            Column {
                Text("Schnellzugriff", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                QuickPresets(vm)
            }
        }
    }
}

@Composable
private fun ActiveProfileCard(state: SonoraUiState) {
    SectionCard {
        Text("Aktives Profil", style = MaterialTheme.typography.labelMedium, color = Muted)
        Spacer(Modifier.height(4.dp))
        Text(state.activeProfileName, style = MaterialTheme.typography.headlineSmall)
        if (state.dirty) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Angepasst, noch nicht gespeichert",
                style = MaterialTheme.typography.bodySmall,
                color = Warning
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (state.masterEnabled) {
                "Die Kurve wirkt auf den Systemausgang. Im Klang-Bereich kannst du sie hören und vergleichen."
            } else {
                "Sonora ist aus. Schalte den Hauptschalter ein, um zu hören."
            },
            style = MaterialTheme.typography.bodySmall,
            color = Muted
        )
    }
}

@Composable
private fun EnvironmentCard(state: SonoraUiState, onAutoAdjust: (Boolean) -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Umgebung", style = MaterialTheme.typography.labelMedium, color = Muted)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = environmentLabel(state.environment?.level),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = if (state.autoAdjust) {
                        "Sonora passt den Klang automatisch an."
                    } else {
                        "Automatische Anpassung ist aus."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted
                )
            }
            Switch(checked = state.autoAdjust, onCheckedChange = onAutoAdjust)
        }
        Spacer(Modifier.height(10.dp))
        EnvironmentMeter(state.environment?.level)
        if (state.autoAdjust && state.environment == null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Mikrofon wird vorbereitet …",
                style = MaterialTheme.typography.bodySmall,
                color = Muted
            )
        }
    }
}

@Composable
private fun EnvironmentMeter(level: Int?) {
    val labels = listOf("leise", "mittel", "laut")
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        labels.forEachIndexed { index, label ->
            val active = level != null && level >= index
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .width(58.dp)
                        .height((14 + index * 8).dp)
                        .background(
                            if (active) Copper else MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(6.dp)
                        )
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (active) Copper else Muted
                )
            }
        }
    }
}

@Composable
private fun EngineCard(state: SonoraUiState, onRetry: () -> Unit) {
    SectionCard {
        Text("Systemausgang", style = MaterialTheme.typography.labelMedium, color = Muted)
        Spacer(Modifier.height(4.dp))
        when (val status = state.engineStatus) {
            is EngineStatus.Active -> {
                Text("Klangänderung angefordert", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Das Gerät stellt ${status.bands} Effektbänder bereit. Sonora bildet die zehn Regler darauf ab.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted
                )
            }

            is EngineStatus.Unavailable -> {
                Text(
                    text = "Nicht verfügbar",
                    style = MaterialTheme.typography.titleMedium,
                    color = Warning
                )
                Spacer(Modifier.height(4.dp))
                Text(status.reason, style = MaterialTheme.typography.bodySmall, color = Muted)
                TextButton(onClick = onRetry) { Text("Erneut prüfen") }
            }

            EngineStatus.Inactive -> {
                Text("Dienst nicht gestartet", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Sonora konnte den Systemausgang noch nicht öffnen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted
                )
                TextButton(onClick = onRetry) { Text("Erneut prüfen") }
            }
        }
    }
}

@Composable
private fun QuickPresets(vm: SonoraViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Presets.defaults.forEach { profile ->
            AssistChip(
                onClick = { vm.applyPreset(profile) },
                label = { Text(profile.name) }
            )
        }
    }
}

// ---------------------------------------------------------------- Klang

@Composable
fun EqualizerScreen(vm: SonoraViewModel) {
    val state = vm.uiState
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
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Klang",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Zehn Regler, −12 bis +12 dB.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted
                )
            }
        }
        item { QuickPresets(vm) }
        item { MappingNote(state) }
        items(10) { index ->
            BandRow(
                index = index,
                value = state.levelsDb.getOrElse(index) { 0f },
                onChange = vm::setBand
            )
        }
        item { AbCompare(vm) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = vm::resetLevels, modifier = Modifier.weight(1f)) {
                    Text("Zurücksetzen")
                }
                Button(onClick = { showSave = true }, modifier = Modifier.weight(1f)) {
                    Text("Profil speichern")
                }
            }
        }
        item {
            Text(
                text = if (state.masterEnabled) {
                    "Änderungen sind sofort hörbar. Ein Profil speichert die aktuelle Kurve."
                } else {
                    "Sonora ist aus. Die Regler werden gespeichert, aber nicht angewendet."
                },
                style = MaterialTheme.typography.bodySmall,
                color = Muted
            )
        }
    }
}

@Composable
private fun MappingNote(state: SonoraUiState) {
    val bands = (state.engineStatus as? EngineStatus.Active)?.bands
    Text(
        text = if (bands != null) {
            "Dein Gerät stellt $bands Bänder bereit. Die zehn Regler werden auf die nächsten Bänder abgebildet."
        } else {
            "Der Systemausgang ist nicht verfügbar. Die Kurve wird gespeichert, aber nicht angewendet."
        },
        style = MaterialTheme.typography.bodySmall,
        color = if (bands != null) Muted else Warning
    )
}

@Composable
private fun BandRow(index: Int, value: Float, onChange: (Int, Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = frequencyLabel(SonoraEngine.uiFrequencies[index]),
            modifier = Modifier.width(48.dp),
            style = MaterialTheme.typography.labelMedium,
            color = Muted
        )
        Slider(
            value = value,
            onValueChange = { newValue -> onChange(index, newValue) },
            valueRange = -12f..12f,
            steps = 47,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = String.format(Locale.GERMAN, "%+.1f dB", value),
            modifier = Modifier.width(68.dp),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun AbCompare(vm: SonoraViewModel) {
    var pressed by remember { mutableStateOf(false) }
    SectionCard {
        Text("A/B", style = MaterialTheme.typography.labelMedium, color = Muted)
        Spacer(Modifier.height(2.dp))
        Text(
            text = "Halte gedrückt, um das Original zu hören.",
            style = MaterialTheme.typography.bodySmall,
            color = Muted
        )
        Spacer(Modifier.height(10.dp))
        Surface(
            color = if (pressed) Copper else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (pressed) Ink else MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            pressed = true
                            vm.beginBypass()
                            tryAwaitRelease()
                            vm.endBypass()
                            pressed = false
                        }
                    )
                }
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (pressed) "Original" else "Original halten",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun SaveProfileDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Profil speichern") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onSave(name) },
                enabled = name.isNotBlank()
            ) { Text("Speichern") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

// ---------------------------------------------------------------- Profile

@Composable
fun ProfilesScreen(vm: SonoraViewModel) {
    val state = vm.uiState
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
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Profile",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${state.profiles.size} gespeichert · ${state.activeProfileName} aktiv",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted
                    )
                }
                Button(onClick = { showSave = true }) { Text("Sichern") }
            }
        }
        items(items = state.profiles, key = { it.id }) { profile ->
            ProfileRow(
                profile = profile,
                active = profile.id == state.activeProfileId,
                onApply = { vm.selectProfile(profile.id) },
                onDelete = { vm.deleteProfile(profile.id) }
            )
        }
        item {
            Text(
                text = "Umbenennen, Duplizieren und Export folgen in Phase 2. Presets sind als „von Sonora“ markiert.",
                style = MaterialTheme.typography.bodySmall,
                color = Muted
            )
        }
    }
}

@Composable
private fun ProfileRow(
    profile: SoundProfile,
    active: Boolean,
    onApply: () -> Unit,
    onDelete: () -> Unit
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(profile.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (profile.preset != null) "von Sonora" else "von dir",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted
                )
            }
            if (active) {
                Text("Aktiv", color = Copper, style = MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onApply, enabled = !active) {
                Text(if (active) "Aktiv" else "Anwenden")
            }
            if (profile.id != "flat") {
                TextButton(onClick = onDelete) { Text("Löschen") }
            }
        }
    }
}

// ---------------------------------------------------------------- Einstellungen

@Composable
fun SettingsScreen(vm: SonoraViewModel) {
    val state = vm.uiState
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Mehr",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Mikrofon ${if (micGranted) "erlaubt" else "nicht erlaubt"} · Benachrichtigungen ${if (notificationGranted) "erlaubt" else "nicht erlaubt"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted
                )
            }
        }
        item {
            RuntimePermissionCard(
                title = "Mikrofon",
                body = "Für die Lautstärkemessung. Die Daten bleiben auf dem Gerät.",
                permission = Manifest.permission.RECORD_AUDIO,
                onGrantedChange = { micGranted = it }
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            item {
                RuntimePermissionCard(
                    title = "Benachrichtigungen",
                    body = "Für die ruhige Zeile im Hintergrund.",
                    permission = Manifest.permission.POST_NOTIFICATIONS,
                    onGrantedChange = { notificationGranted = it }
                )
            }
        }
        item {
            SectionCard {
                Text("Datenschutz", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Das Mikrofon wird nur für die Lautstärkemessung genutzt. Die Messung wird nicht gespeichert und nicht gesendet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Profile liegen lokal auf dem Gerät. Ein Export ist in Phase 2 vorgesehen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted
                )
            }
        }
        item {
            SectionCard {
                Text("Diagnose", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Spacer(Modifier.height(6.dp))
                DiagnosticRow("Klangdienst", engineStatusText(state.engineStatus))
                DiagnosticRow(
                    "Gerätebänder",
                    (state.engineStatus as? EngineStatus.Active)?.bands?.toString() ?: "—"
                )
                DiagnosticRow(
                    "Pegelbereich",
                    "${SonoraEngine.bandLevelRange.first / 100} bis ${SonoraEngine.bandLevelRange.last / 100} dB"
                )
                DiagnosticRow(
                    "Umgebung",
                    state.environment?.let { reading ->
                        String.format(
                            Locale.GERMAN,
                            "%.0f dBFS · %s",
                            reading.rmsDb,
                            environmentLabel(reading.level)
                        )
                    } ?: "nicht aktiv"
                )
                DiagnosticRow("Version", BuildConfig.VERSION_NAME)
                if (state.engineStatus is EngineStatus.Unavailable) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = vm::retryEngine) { Text("Erneut prüfen") }
                }
            }
        }
        item {
            SectionCard {
                Text("Hintergrund", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Der Dienst hält den Klang aktiv, während du andere Apps nutzt.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = vm::startService) { Text("Starten") }
                    OutlinedButton(onClick = vm::stopService) { Text("Beenden") }
                }
            }
        }
        item {
            Text(
                text = "Sonora sendet nichts, solange du keine Verbindung einschaltest. Diese Version nutzt kein Netzwerk.",
                style = MaterialTheme.typography.bodySmall,
                color = Muted
            )
        }
    }
}

@Composable
private fun RuntimePermissionCard(
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
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(body, style = MaterialTheme.typography.bodySmall, color = Muted)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (granted) "Status: erlaubt" else "Status: nicht erlaubt",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (granted) Copper else Warning
                )
            }
            TextButton(
                onClick = {
                    if (granted) {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + context.packageName)
                            )
                        )
                    } else {
                        launcher.launch(permission)
                    }
                }
            ) {
                Text(if (granted) "Einstellungen" else "Erlauben")
            }
        }
    }
}
