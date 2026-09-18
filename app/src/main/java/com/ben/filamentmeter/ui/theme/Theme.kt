package com.ben.filamentmeter.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ObsidianBg = Color(0xFF0A0D10)
private val CardSurface = Color(0xFF11161B)
private val InputSurface = Color(0xFF131920)
private val BorderColor = Color(0xFF1E2630)
private val NeonGreen = Color(0xFF00E676)
private val NeonGreenSoft = Color(0xFF69F0AE)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFF8A99A8)

private val Colors = darkColorScheme(
    primary = NeonGreen,
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF0A2E1A),
    onPrimaryContainer = NeonGreen,
    secondary = NeonGreenSoft,
    secondaryContainer = Color(0xFF173B2B),
    onSecondaryContainer = NeonGreenSoft,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = CardSurface,
    onSurface = TextPrimary,
    surfaceVariant = InputSurface,
    onSurfaceVariant = TextSecondary,
    outline = BorderColor
)

@Composable
fun FilamentMeterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Colors,
        content = content
    )
}
