package io.github.voidcast00.noisesleep.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Always dark: this app is used in a dark bedroom
private val Night = darkColorScheme(
    primary = Color(0xFFE3B7A0),
    onPrimary = Color(0xFF2B1A12),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFE8E3DC),
    surface = Color(0xFF0E1116),
    onSurface = Color(0xFFE8E3DC),
    surfaceVariant = Color(0xFF1C2129),
    onSurfaceVariant = Color(0xFF8E959E),
    secondaryContainer = Color(0xFF3A2C26),
    onSecondaryContainer = Color(0xFFF0D9CC),
    outline = Color(0xFF353B45),
)

object NoiseColors {
    val White = Color(0xFFF2F0EB)
    val Pink = Color(0xFFE8A0B4)
    val Brown = Color(0xFF9A6244)
    val Bright = Color(0xFFBFD3E6)
    val Deep = Color(0xFFB0703F)
}

@Composable
fun NoiseSleepTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Night, content = content)
}
