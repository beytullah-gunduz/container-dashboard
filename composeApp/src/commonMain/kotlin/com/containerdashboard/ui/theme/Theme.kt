package com.containerdashboard.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

// Application color palette
object AppColors {
    // Primary blue
    val AccentBlue = Color(0xFF0DB7ED)
    val AccentBlueDark = Color(0xFF086DD7)
    val AccentBlueLight = Color(0xFF4FC3F7)

    // Backgrounds
    val DarkBackground = Color(0xFF1E1E1E)
    val DarkSurface = Color(0xFF252526)
    val DarkSurfaceVariant = Color(0xFF34343A)
    val DarkSurfaceElevated = Color(0xFF3A3A40)

    // Light theme
    val LightBackground = Color(0xFFF5F5F5)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceVariant = Color(0xFFDDDDDD)

    // Status colors
    val Running = Color(0xFF4CAF50)
    val Stopped = Color(0xFFEF5350)
    val Paused = Color(0xFFFFCA3A)
    val Warning = Color(0xFFFF9800)

    // Text
    val TextPrimary = Color(0xFFE0E0E0)
    val TextSecondary = Color(0xFF9E9E9E)

    // Extended semantic surfaces (dark theme values)
    val DividerStrong = Color(0xFF404040)

    val WarningSurface = Color(0xFFFFA726)
    val WarningSurfaceLight = Color(0x1AFFF3E0)
    val SuccessSurface = Color(0xFF4CAF50)
    val InfoSurface = Color(0xFF2196F3)
}

data class ExtendedColors(
    val dividerStrong: Color,
    val warningSurface: Color,
    val warningSurfaceLight: Color,
    val successSurface: Color,
    val infoSurface: Color,
)

private val DarkExtendedColors =
    ExtendedColors(
        dividerStrong = Color(0xFF404040),
        warningSurface = Color(0xFFFFA726),
        warningSurfaceLight = Color(0x1AFFF3E0),
        successSurface = Color(0xFF4CAF50),
        infoSurface = Color(0xFF2196F3),
    )

private val LightExtendedColors =
    ExtendedColors(
        dividerStrong = Color(0xFFBDBDBD),
        warningSurface = Color(0xFFF57C00),
        warningSurfaceLight = Color(0x14FFF3E0),
        successSurface = Color(0xFF388E3C),
        infoSurface = Color(0xFF1976D2),
    )

val LocalAppColors = staticCompositionLocalOf<ExtendedColors> { DarkExtendedColors }

enum class ThemeMode {
    AUTO,
    DARK,
    LIGHT,
}

object AppTheme {
    val extended: ExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAppColors.current
}

val Typography.monospaceMedium: TextStyle
    get() = bodySmall.copy(fontFamily = FontFamily.Monospace, lineHeight = 18.sp)

val Typography.monospaceSmall: TextStyle
    get() = labelSmall.copy(fontFamily = FontFamily.Monospace, lineHeight = 16.sp)

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
        outlineVariant = Color(0xFF3A3A3A),
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
    val extended = if (darkTheme) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(LocalAppColors provides extended) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography(),
            content = content,
        )
    }
}
