package com.p0lymatic.flashpoint.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ExpressiveDarkColorScheme = darkColorScheme(
    primary = Color(0xFF00E599),
    onPrimary = Color(0xFF003822),
    primaryContainer = Color(0xFF005234),
    onPrimaryContainer = Color(0xFF66FFBA),
    secondary = Color(0xFF38BDF8),
    onSecondary = Color(0xFF003549),
    secondaryContainer = Color(0xFF004D69),
    onSecondaryContainer = Color(0xFFBCE9FF),
    background = Color(0xFF0D0F12),
    onBackground = Color(0xFFE2E4E9),
    surface = Color(0xFF13161C),
    onSurface = Color(0xFFE2E4E9),
    surfaceVariant = Color(0xFF1B1F27),
    onSurfaceVariant = Color(0xFF9BA3B4),
    surfaceContainerLowest = Color(0xFF08090C),
    surfaceContainerLow = Color(0xFF111419),
    surfaceContainer = Color(0xFF171B22),
    surfaceContainerHigh = Color(0xFF1E232C),
    surfaceContainerHighest = Color(0xFF272D38),
    outline = Color(0xFF2E3646),
    outlineVariant = Color(0xFF1D232E),
    error = Color(0xFFFF5252),
    onError = Color(0xFF410002)
)

@Composable
fun FlashPointTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ExpressiveDarkColorScheme,
        content = content,
    )
}
