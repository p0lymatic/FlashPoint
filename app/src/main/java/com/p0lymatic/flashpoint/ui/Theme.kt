package com.p0lymatic.flashpoint.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF00E676),
    onPrimary = Color(0xFF003300),
    primaryContainer = Color(0xFF004D25),
    secondary = Color(0xFF00BCD4),
    background = Color(0xFF0A0A0A),
    surface = Color(0xFF121212),
    surfaceVariant = Color(0xFF1E1E1E),
    onBackground = Color(0xFFE0E0E0),
    onSurface = Color(0xFFE0E0E0),
    error = Color(0xFFFF1744),
)

@Composable
fun FlashPointTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content,
    )
}
