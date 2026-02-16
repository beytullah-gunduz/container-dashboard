package com.containerdashboard.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Docker-inspired color palette
object DockerColors {
    // Primary Docker blue
    val DockerBlue = Color(0xFF0DB7ED)
    val DockerBlueDark = Color(0xFF086DD7)
    val DockerBlueLight = Color(0xFF4FC3F7)

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
        primary = DockerColors.DockerBlue,
        onPrimary = Color.White,
        primaryContainer = DockerColors.DockerBlueDark,
        onPrimaryContainer = Color.White,
        secondary = DockerColors.DockerBlueLight,
        onSecondary = Color.Black,
        background = DockerColors.DarkBackground,
        onBackground = DockerColors.TextPrimary,
        surface = DockerColors.DarkSurface,
        onSurface = DockerColors.TextPrimary,
        surfaceVariant = DockerColors.DarkSurfaceVariant,
        onSurfaceVariant = DockerColors.TextSecondary,
        outline = Color(0xFF404040),
        outlineVariant = Color(0xFF303030),
    )

private val LightColorScheme =
    lightColorScheme(
        primary = DockerColors.DockerBlueDark,
        onPrimary = Color.White,
        primaryContainer = DockerColors.DockerBlueLight,
        onPrimaryContainer = Color.Black,
        secondary = DockerColors.DockerBlue,
        onSecondary = Color.White,
        background = DockerColors.LightBackground,
        onBackground = Color(0xFF1A1A1A),
        surface = DockerColors.LightSurface,
        onSurface = Color(0xFF1A1A1A),
        surfaceVariant = DockerColors.LightSurfaceVariant,
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
