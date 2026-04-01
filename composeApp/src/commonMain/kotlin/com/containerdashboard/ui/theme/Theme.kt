package com.containerdashboard.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Application color palette
object AppColors {
    // Primary blue
    val AccentBlue = Color(0xFF0DB7ED)
    val AccentBlueDark = Color(0xFF086DD7)
    val AccentBlueLight = Color(0xFF4FC3F7)

    // Backgrounds
    val DarkBackground = Color(0xFF1E1E1E)
    val DarkSurface = Color(0xFF252526)
    val DarkSurfaceVariant = Color(0xFF2D2D30)
    val DarkSurfaceElevated = Color(0xFF333337)

    // Light theme
    val LightBackground = Color(0xFFF5F5F5)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceVariant = Color(0xFFE8E8E8)

    // Status colors
    val Running = Color(0xFF4CAF50)
    val Stopped = Color(0xFFEF5350)
    val Paused = Color(0xFFFFB74D)
    val Warning = Color(0xFFFF9800)

    // Text
    val TextPrimary = Color(0xFFE0E0E0)
    val TextSecondary = Color(0xFF9E9E9E)
    val TextMuted = Color(0xFF757575)
}

private val DarkColorScheme =
    darkColorScheme(
        primary = AppColors.AccentBlue,
        onPrimary = Color.White,
        primaryContainer = AppColors.AccentBlueDark,
        onPrimaryContainer = Color.White,
        secondary = AppColors.AccentBlueLight,
        onSecondary = Color.Black,
        background = AppColors.DarkBackground,
        onBackground = AppColors.TextPrimary,
        surface = AppColors.DarkSurface,
        onSurface = AppColors.TextPrimary,
        surfaceVariant = AppColors.DarkSurfaceVariant,
        onSurfaceVariant = AppColors.TextSecondary,
        outline = Color(0xFF404040),
        outlineVariant = Color(0xFF303030),
    )

private val LightColorScheme =
    lightColorScheme(
        primary = AppColors.AccentBlueDark,
        onPrimary = Color.White,
        primaryContainer = AppColors.AccentBlueLight,
        onPrimaryContainer = Color.Black,
        secondary = AppColors.AccentBlue,
        onSecondary = Color.White,
        background = AppColors.LightBackground,
        onBackground = Color(0xFF1A1A1A),
        surface = AppColors.LightSurface,
        onSurface = Color(0xFF1A1A1A),
        surfaceVariant = AppColors.LightSurfaceVariant,
        onSurfaceVariant = Color(0xFF424242),
        outline = Color(0xFFBDBDBD),
        outlineVariant = Color(0xFFE0E0E0),
    )

@Composable
fun ContainerDashboardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content,
    )
}
