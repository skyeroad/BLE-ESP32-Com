package com.skyeroad.ble_esp32comjpc.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF0A84FF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6F0FF),
    onPrimaryContainer = Color(0xFF00254D),
    secondary = Color(0xFF3A3A3C),
    onSecondary = Color.White,
    background = Color(0xFFF8F9FB),
    onBackground = Color(0xFF111111),
    surface = Color.White,
    onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFEAEAEA),
    onSurfaceVariant = Color(0xFF3A3A3C)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7DB7FF),
    onPrimary = Color(0xFF00254D),
    primaryContainer = Color(0xFF0A84FF),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFD1D1D6),
    onSecondary = Color(0xFF1C1C1E),
    background = Color(0xFF0F1115),
    onBackground = Color(0xFFE6E6E6),
    surface = Color(0xFF1C1C1E),
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFFD1D1D6)
)

@Composable
fun BLEESP32ComJPCTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (useDarkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
