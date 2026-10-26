package de.sonora.app.ui.visualizer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.sonora.app.ui.CardBorder
import de.sonora.app.ui.Copper
import de.sonora.app.ui.CopperSoft
import de.sonora.app.ui.Ink
import de.sonora.app.ui.Muted
import de.sonora.app.ui.Paper
import de.sonora.app.ui.Surface1
import de.sonora.app.ui.Surface2
import de.sonora.app.ui.Warning
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Visuelle Modi des Sonora Partikelfelds.
 */
enum class ParticleMode(val title: String, val shortDesc: String) {
    CONSTELLATION("Konstellation", "Organisches elastisches Netz mit Verbindungslinien"),
    VORTEX("Wirbel", "Akkustischer Partikelwirbel um den Bass-Kern"),
    SPECTRA_WAVE("Spektralwelle", "Doppelschichtige harmonische Raumwelle")
}

/**
 * Internes Datenmodell fuer einen einzelnen Partikel (wiederverwendbar ohne Allokation).
 */
private class Particle(
    var x: Float = 0f,
    var y: Float = 0f,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var baseX: Float = 0f,
    var baseY: Float = 0f,
    var baseRadius: Float = 2.5f,
    var phase: Float = 0f,
    var bandIndex: Int = 0,
    var speed: Float = 1.0f
)

/**
 * Sonora Particle Field — ein interaktives, audioreaktives Partikel-System
 * mit nativer Jetpack-Compose-Canvas-Implementierung und Touch-to-Sound XY-Steuerung.
 *
 * @param spectrum Aktuelle 8-Band-Energien aus der FFT (0..1 je Band)
 * @param waveform Zeitbereichs-Wellenform fuer Spitzen- und Transientendetektion
 * @param activeMode Gewaehlter visueller Modus
 * @param onModeChange Callback beim Moduswechsel
 * @param xyControlEnabled Ob die Touch-Bewegung den internen DSP-Flanger steuern soll
 * @param onXyChange Callback mit (xNorm 0..1, yNorm 0..1, isTouching Boolean)
 */
@Composable
fun ParticleField(
    spectrum: List<Float>,
    waveform: List<Float> = emptyList(),
    modifier: Modifier = Modifier,
    activeMode: ParticleMode = ParticleMode.CONSTELLATION,
    onModeChange: (ParticleMode) -> Unit = {},
    xyControlEnabled: Boolean = true,
    onXyChange: (xNorm: Float, yNorm: Float, isTouching: Boolean) -> Unit = { _, _, _ -> }
) {
    val particleCount = 120
    val particles = remember {
        Array(particleCount) { index ->
            Particle(
                bandIndex = index % 8,
                phase = (index * (2f * PI / particleCount)).toFloat(),
                baseRadius = 1.8f + (index % 5) * 0.7f,
                speed = 0.6f + (index % 7) * 0.2f
            )
        }
    }

    var initialized by remember { mutableStateOf(false) }
    var touchPos by remember { mutableStateOf<Offset?>(null) }
    var canvasSize by remember { mutableStateOf(Offset.Zero) }

    // Berechnete Audio-Kennzahlen
    val bassEnergy = remember(spectrum) {
        if (spectrum.size >= 2) ((spectrum[0] * 0.6f + spectrum[1] * 0.4f) * 1.5f).coerceIn(0f, 1f) else 0f
    }
    val midEnergy = remember(spectrum) {
        if (spectrum.size >= 6) ((spectrum[2] + spectrum[3] + spectrum[4] + spectrum[5]) / 4f).coerceIn(0f, 1f) else 0f
    }
    val highEnergy = remember(spectrum) {
        if (spectrum.size >= 8) ((spectrum[6] + spectrum[7]) / 2f).coerceIn(0f, 1f) else 0f
    }

    // Live Tick fuer gleichmaessige 60fps-Physikintegration
    var frameTick by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var lastNanos = 0L
        while (true) {
            withFrameNanos { nanos ->
                if (lastNanos == 0L) lastNanos = nanos
                val dt = ((nanos - lastNanos) / 1_000_000_000f).coerceIn(0.005f, 0.033f)
                lastNanos = nanos
                frameTick += dt
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Surface1)
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(xyControlEnabled) {
                    if (!xyControlEnabled) return@pointerInput
                    detectTapGestures(
                        onPress = { offset ->
                            touchPos = offset
                            val xNorm = (offset.x / size.width).coerceIn(0f, 1f)
                            val yNorm = (1f - (offset.y / size.height)).coerceIn(0f, 1f)
                            onXyChange(xNorm, yNorm, true)
                            tryAwaitRelease()
                            touchPos = null
                            onXyChange(xNorm, yNorm, false)
                        }
                    )
                }
                .pointerInput(xyControlEnabled) {
                    if (!xyControlEnabled) return@pointerInput
                    detectDragGestures(
                        onDragStart = { offset ->
                            touchPos = offset
                            val xNorm = (offset.x / size.width).coerceIn(0f, 1f)
                            val yNorm = (1f - (offset.y / size.height)).coerceIn(0f, 1f)
                            onXyChange(xNorm, yNorm, true)
                        },
                        onDragEnd = {
                            touchPos = null
                            canvasSize.let { s ->
                                if (s.x > 0 && s.y > 0) {
                                    onXyChange(0.5f, 0.5f, false)
                                }
                            }
                        },
                        onDragCancel = {
                            touchPos = null
                            onXyChange(0.5f, 0.5f, false)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            touchPos = change.position
                            val xNorm = (change.position.x / size.width).coerceIn(0f, 1f)
                            val yNorm = (1f - (change.position.y / size.height)).coerceIn(0f, 1f)
                            onXyChange(xNorm, yNorm, true)
                        }
                    )
                }
        ) {
            val width = size.width
            val height = size.height
            canvasSize = Offset(width, height)
            val centerX = width / 2f
            val centerY = height / 2f

            // Partikelpositionen beim ersten Frame initialisieren
            if (!initialized && width > 0 && height > 0) {
                for (p in particles) {
                    val radiusSpread = (0.15f + (p.phase % 1f) * 0.7f) * (width.coerceAtMost(height) / 2f)
                    p.baseX = centerX + cos(p.phase) * radiusSpread
                    p.baseY = centerY + sin(p.phase) * radiusSpread
                    p.x = p.baseX
                    p.y = p.baseY
                }
                initialized = true
            }

            // Subtiler radialer Hintergrund-Glow (pulsiert mit Bass)
            val bassPulseRadius = (width.coerceAtMost(height) * 0.45f) * (1f + bassEnergy * 0.35f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Copper.copy(alpha = 0.08f + bassEnergy * 0.15f),
                        Surface2.copy(alpha = 0.04f + midEnergy * 0.06f),
                        Color.Transparent
                    ),
                    center = Offset(centerX, centerY),
                    radius = bassPulseRadius
                ),
                radius = bassPulseRadius,
                center = Offset(centerX, centerY)
            )

            // Physik- und Positions-Update je nach Modus
            val t = frameTick
            val currentTouch = touchPos

            for (i in particles.indices) {
                val p = particles[i]
                val bandVal = spectrum.getOrElse(p.bandIndex) { 0f }

                when (activeMode) {
                    ParticleMode.CONSTELLATION -> {
                        // Organische Schwebung um Basisposition
                        val driftX = cos(t * p.speed + p.phase) * (12f + bandVal * 24f)
                        val driftY = sin(t * (p.speed * 1.2f) + p.phase) * (10f + bandVal * 20f)
                        var targetX = p.baseX + driftX
                        var targetY = p.baseY + driftY

                        // Touch-Anziehung / Wirbel
                        if (currentTouch != null) {
                            val dx = currentTouch.x - p.x
                            val dy = currentTouch.y - p.y
                            val dist = hypot(dx, dy)
                            if (dist < 180f && dist > 1f) {
                                val pull = (1f - dist / 180f) * 45f
                                // Kombination aus Zug und leichter tangentialer Wirbelbewegung
                                targetX += (dx / dist) * pull - (dy / dist) * (pull * 0.6f)
                                targetY += (dy / dist) * pull + (dx / dist) * (pull * 0.6f)
                            }
                        }

                        // Sanfte Federdaempfung zur Zielposition
                        p.x += (targetX - p.x) * 0.15f
                        p.y += (targetY - p.y) * 0.15f
                    }

                    ParticleMode.VORTEX -> {
                        // Spiralfoermige Rotation um den Mittelpunkt
                        val angle = p.phase + t * (0.8f + midEnergy * 1.2f) * p.speed
                        val baseDist = 30f + (p.bandIndex * 14f) + (bassEnergy * 40f)
                        var targetX = centerX + cos(angle) * baseDist
                        var targetY = centerY + sin(angle) * (baseDist * 0.65f) // Elliptisch

                        if (currentTouch != null) {
                            val dx = currentTouch.x - p.x
                            val dy = currentTouch.y - p.y
                            val dist = hypot(dx, dy)
                            if (dist < 150f && dist > 1f) {
                                val factor = (1f - dist / 150f) * 35f
                                targetX += (dx / dist) * factor
                                targetY += (dy / dist) * factor
                            }
                        }

                        p.x += (targetX - p.x) * 0.2f
                        p.y += (targetY - p.y) * 0.2f
                    }

                    ParticleMode.SPECTRA_WAVE -> {
                        // Zwei horizontale Schichten, getrieben durch Frequenzbaender
                        val xRatio = (i.toFloat() / particleCount)
                        val waveX = 24f + xRatio * (width - 48f)
                        val layer = if (i % 2 == 0) 1f else -1f
                        val bandAmp = spectrum.getOrElse((xRatio * 7).toInt().coerceIn(0, 7)) { 0f }
                        val waveY = centerY + layer * (sin(xRatio * 4f * PI + t * 2f).toFloat() * (20f + bandAmp * 55f))

                        var targetX = waveX
                        var targetY = waveY

                        if (currentTouch != null) {
                            val dy = currentTouch.y - p.y
                            if (kotlin.math.abs(currentTouch.x - p.x) < 80f) {
                                targetY += dy * 0.35f
                            }
                        }

                        p.x += (targetX - p.x) * 0.2f
                        p.y += (targetY - p.y) * 0.2f
                    }
                }
            }

            // 1. Verbindungslinien zeichnen (Konstellations-Netzwerk)
            if (activeMode == ParticleMode.CONSTELLATION || activeMode == ParticleMode.SPECTRA_WAVE) {
                val maxDist = if (activeMode == ParticleMode.CONSTELLATION) 72f else 50f
                // Deterministischer Stride fuer O(N) Performance
                for (i in 0 until particleCount step 2) {
                    val p1 = particles[i]
                    // Pruefe nur benachbarte Indizes im Ring
                    for (offset in 1..4) {
                        val j = (i + offset) % particleCount
                        val p2 = particles[j]
                        val dist = hypot(p1.x - p2.x, p1.y - p2.y)
                        if (dist < maxDist) {
                            val alpha = ((1f - dist / maxDist) * (0.12f + bassEnergy * 0.28f + highEnergy * 0.15f)).coerceIn(0.02f, 0.5f)
                            drawLine(
                                color = Copper.copy(alpha = alpha),
                                start = Offset(p1.x, p1.y),
                                end = Offset(p2.x, p2.y),
                                strokeWidth = 1.0f + (bassEnergy * 0.8f)
                            )
                        }
                    }
                }
            }

            // 2. Partikel-Knoten zeichnen
            for (p in particles) {
                val bandVal = spectrum.getOrElse(p.bandIndex) { 0f }
                val currentRadius = p.baseRadius * (1.0f + bandVal * 1.4f + (if (p.bandIndex <= 1) bassEnergy * 0.8f else 0f))
                val alpha = (0.35f + bandVal * 0.55f + highEnergy * 0.25f).coerceIn(0.2f, 0.95f)
                val color = when {
                    p.bandIndex <= 1 -> Copper // Bass = kraeftiges Kupfer
                    p.bandIndex in 2..4 -> CopperSoft // Mitten = sanftes Kupfer
                    else -> Paper.copy(alpha = alpha) // Hoehen = glitzerndes Weiss/Cream
                }

                drawCircle(
                    color = color.copy(alpha = alpha),
                    radius = currentRadius,
                    center = Offset(p.x, p.y)
                )
            }

            // 3. Touch-Retikel und Live-Interaktions-Feedback
            if (currentTouch != null && xyControlEnabled) {
                val pulseRadius = 24f + sin(t * 8f) * 4f
                // Innerer Kern
                drawCircle(
                    color = Copper,
                    radius = 4f,
                    center = currentTouch
                )
                // Aeusserer pulsierender Ring
                drawCircle(
                    color = Copper.copy(alpha = 0.5f),
                    radius = pulseRadius,
                    center = currentTouch,
                    style = Stroke(width = 1.5f, cap = StrokeCap.Round)
                )
                // Horizontale / vertikale Achsenlinie (dezent)
                drawLine(
                    color = Copper.copy(alpha = 0.15f),
                    start = Offset(0f, currentTouch.y),
                    end = Offset(width, currentTouch.y),
                    strokeWidth = 1f
                )
                drawLine(
                    color = Copper.copy(alpha = 0.15f),
                    start = Offset(currentTouch.x, 0f),
                    end = Offset(currentTouch.x, height),
                    strokeWidth = 1f
                )
            }
        }

        // Overlay: Modus-Wahl und Statusanzeige oben
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Modus-Umschalter
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ParticleMode.entries.forEach { mode ->
                    val isSelected = mode == activeMode
                    Surface(
                        onClick = { onModeChange(mode) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) Copper.copy(alpha = 0.22f) else Surface2.copy(alpha = 0.6f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) Copper else Color.Transparent
                        )
                    ) {
                        Text(
                            text = mode.title,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) CopperSoft else Muted
                            )
                        )
                    }
                }
            }

            // Audio-Aktivitaets-Indikator
            val isAudioActive = spectrum.any { it > 0.05f } || waveform.any { kotlin.math.abs(it) > 0.02f }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (isAudioActive) Copper else Muted.copy(alpha = 0.4f))
                )
                Text(
                    text = if (isAudioActive) "Echtzeit-FFT" else "Bereit",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 10.sp,
                        color = if (isAudioActive) CopperSoft else Muted
                    )
                )
            }
        }

        // Overlay: XY Touch HUD (wenn der Nutzer den Finger bewegt)
        if (touchPos != null && xyControlEnabled && canvasSize.x > 0 && canvasSize.y > 0) {
            val touch = touchPos!!
            val xNorm = (touch.x / canvasSize.x).coerceIn(0f, 1f)
            val yNorm = (1f - (touch.y / canvasSize.y)).coerceIn(0f, 1f)
            val rateHz = 0.15f + xNorm * 3.85f
            val depthPercent = (0.2f + yNorm * 0.8f) * 100f
            val feedbackPercent = (yNorm * 85f)

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(20.dp),
                color = Ink.copy(alpha = 0.85f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Copper.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TOUCH DSP",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Copper,
                            fontSize = 10.sp
                        )
                    )
                    Text(
                        text = "LFO: %.1f Hz".format(rateHz),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Paper,
                            fontSize = 11.sp
                        )
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall.copy(color = Muted, fontSize = 10.sp)
                    )
                    Text(
                        text = "Tiefe: %d%%".format(depthPercent.toInt()),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Paper,
                            fontSize = 11.sp
                        )
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall.copy(color = Muted, fontSize = 10.sp)
                    )
                    Text(
                        text = "Feedback: %d%%".format(feedbackPercent.toInt()),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = CopperSoft,
                            fontSize = 11.sp
                        )
                    )
                }
            }
        }
    }
}
