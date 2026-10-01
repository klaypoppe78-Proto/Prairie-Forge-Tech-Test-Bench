package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = AgEmeraldGlow,
    onPrimary = Color(0xFF003822),
    primaryContainer = AgEmeraldDark,
    onPrimaryContainer = Color(0xFFA7F3D0),
    secondary = AgSkyPrecision,
    onSecondary = Color(0xFF003258),
    tertiary = AgAmberGold,
    error = AgCoralStop,
    background = AgDarkBackground,
    onBackground = Color(0xFFF1F5F9),
    surface = AgDarkSurface,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = AgDarkSurfaceVariant,
    onSurfaceVariant = Color(0xFF94A3B8)
)

private val LightColorScheme = lightColorScheme(
    primary = AgEmeraldPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1FAE5),
    onPrimaryContainer = Color(0xFF065F46),
    secondary = Color(0xFF0284C7),
    onSecondary = Color.White,
    tertiary = AgAmberGold,
    error = AgCoralStop,
    background = AgLightBackground,
    onBackground = Color(0xFF0F172A),
    surface = AgLightSurface,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = AgLightSurfaceVariant,
    onSurfaceVariant = Color(0xFF475569)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep high-contrast agricultural colors for cab readability
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
