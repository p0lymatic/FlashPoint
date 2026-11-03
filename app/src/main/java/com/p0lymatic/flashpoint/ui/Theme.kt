package com.p0lymatic.flashpoint.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

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

private val ExpressiveLightColorScheme = lightColorScheme(
    primary = Color(0xFF006D44),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF66FFBA),
    onPrimaryContainer = Color(0xFF002112),
    secondary = Color(0xFF006688),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFBCE9FF),
    onSecondaryContainer = Color(0xFF001F2B),
    background = Color(0xFFF9F9FB),
    onBackground = Color(0xFF191C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF191C1E),
    surfaceVariant = Color(0xFFDEE3EB),
    onSurfaceVariant = Color(0xFF42474E),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F3F7),
    surfaceContainer = Color(0xFFEDEEF2),
    surfaceContainerHigh = Color(0xFFE7E8EC),
    surfaceContainerHighest = Color(0xFFE1E2E6),
    outline = Color(0xFF72777F),
    outlineVariant = Color(0xFFC2C7CF),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF)
)

@Composable
fun FlashPointTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> ExpressiveDarkColorScheme
        else -> ExpressiveLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
