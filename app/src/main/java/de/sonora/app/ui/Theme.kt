package de.sonora.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// High-contrast, premium hero palette for Sonora
val Ink = Color(0xFF0E1013)
val Surface1 = Color(0xFF171A1F)
val Surface2 = Color(0xFF232830)
val SelectedCardSurface = Color(0xFF282F3B)
val Copper = Color(0xFFC98B5E)
val CopperSoft = Color(0xFFE3B591)
val Paper = Color(0xFFF2EDE7)
val Muted = Color(0xFF9AA0A6)
val Warning = Color(0xFFE0A458)
val GreenAccent = Color(0xFF4BB574)
val DividerColor = Color(0xFF222832)
val CardBorder = Color(0xFF262E3B)

private val SonoraColors = darkColorScheme(
    primary = Copper,
    onPrimary = Ink,
    secondary = CopperSoft,
    onSecondary = Ink,
    background = Ink,
    onBackground = Paper,
    surface = Surface1,
    onSurface = Paper,
    surfaceVariant = Surface2,
    onSurfaceVariant = Muted,
    error = Warning,
    onError = Ink
)

private val SonoraTypography = Typography(
    displaySmall = TextStyle(
        fontWeight = FontWeight.Light,
        fontSize = 40.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 28.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.4.sp
    )
)

@Composable
fun SonoraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SonoraColors,
        typography = SonoraTypography,
        content = content
    )
}
