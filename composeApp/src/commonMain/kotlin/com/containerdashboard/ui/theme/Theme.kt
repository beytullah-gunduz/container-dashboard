package com.containerdashboard.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.dockerdashboard.composeapp.generated.resources.Res
import com.dockerdashboard.composeapp.generated.resources.departure_mono_regular
import com.dockerdashboard.composeapp.generated.resources.sixtyfour_regular
import org.jetbrains.compose.resources.Font

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
    val running: Color,
    val stopped: Color,
    val paused: Color,
    val warning: Color,
    val accent: Color,
    val accentSecondary: Color,
    val accentTertiary: Color,
    val textMuted: Color,
    val consoleForeground: Color,
    val consoleBackground: Color,
)

private val DarkExtendedColors =
    ExtendedColors(
        dividerStrong = Color(0xFF404040),
        warningSurface = Color(0xFFFFA726),
        warningSurfaceLight = Color(0x1AFFF3E0),
        successSurface = Color(0xFF4CAF50),
        infoSurface = Color(0xFF2196F3),
        running = AppColors.Running,
        stopped = AppColors.Stopped,
        paused = AppColors.Paused,
        warning = AppColors.Warning,
        accent = AppColors.AccentBlue,
        accentSecondary = AppColors.AccentBlueDark,
        accentTertiary = AppColors.AccentBlueLight,
        textMuted = AppColors.TextSecondary,
        consoleForeground = Color(0xFFD4D4D4),
        consoleBackground = Color(0xFF1A1A1A),
    )

private val LightExtendedColors =
    ExtendedColors(
        dividerStrong = Color(0xFFBDBDBD),
        warningSurface = Color(0xFFF57C00),
        warningSurfaceLight = Color(0x14FFF3E0),
        successSurface = Color(0xFF388E3C),
        infoSurface = Color(0xFF1976D2),
        running = AppColors.Running,
        stopped = AppColors.Stopped,
        paused = AppColors.Paused,
        warning = AppColors.Warning,
        accent = AppColors.AccentBlue,
        accentSecondary = AppColors.AccentBlueDark,
        accentTertiary = AppColors.AccentBlueLight,
        textMuted = AppColors.TextSecondary,
        consoleForeground = Color(0xFF1A1A1A),
        consoleBackground = Color(0xFFF5F5F5),
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

/**
 * Bundled retro reading font: Departure Mono Regular. SIL OFL 1.1, see
 * `composeResources/font/departure-mono-OFL.txt`. Retro's voice for everything that is not
 * [retroPixelFamily] chrome: body text, data, buttons, fields and code. Single weight: Compose
 * desktop never synthesizes bold, so heavier weight requests render Regular.
 */
@Composable
fun retroReadingFamily(): FontFamily {
    val regular = Font(Res.font.departure_mono_regular, weight = FontWeight.Normal, style = FontStyle.Normal)
    return remember(regular) { FontFamily(regular) }
}

/**
 * Bundled pixel-chrome font: Sixtyfour Regular. SIL OFL 1.1, see
 * `composeResources/font/sixtyfour-OFL.txt`. Sixtyfour is a variable font (axes SCAN and BLED);
 * this loads the default instance, both axes at 0, with no variation settings applied. Retro
 * chrome only: headings, section labels and table column headers, never resource data, body text,
 * table cells or logs, which use [retroReadingFamily].
 */
@Composable
fun retroPixelFamily(): FontFamily {
    val regular = Font(Res.font.sixtyfour_regular, weight = FontWeight.Normal, style = FontStyle.Normal)
    return remember(regular) { FontFamily(regular) }
}

/** The app's monospace face: the platform monospace in Default, Departure Mono in Retro. */
@Composable
fun appMonoFamily(): FontFamily = if (ThemeStyleState.isRetro) retroReadingFamily() else FontFamily.Monospace

/**
 * 8x8 home-computer pixel faces like Sixtyfour fill the em square, so they render noticeably
 * larger optically than the system face at an equal sp size. Chrome call sites that don't pin an
 * explicit retro size (see [retroChrome]) scale down by this factor instead.
 */
const val RETRO_TYPE_SCALE = 0.72f

/**
 * Chrome-only pixel voice: unchanged outside Retro; in Retro swaps in the pixel face at [size] (or
 * [RETRO_TYPE_SCALE] x the base size), Normal weight, zero tracking. Line height is kept so rows
 * never shift. Never use on resource data, body text, table cells or logs.
 */
@Composable
fun TextStyle.retroChrome(size: TextUnit = TextUnit.Unspecified): TextStyle {
    if (!ThemeStyleState.isRetro) return this
    return copy(
        fontFamily = retroPixelFamily(),
        fontSize =
            when {
                size.isSpecified -> size

                // An unspecified base size cannot be scaled (TextUnit arithmetic throws on it).
                fontSize.isSpecified -> fontSize * RETRO_TYPE_SCALE

                else -> fontSize
            },
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
    )
}

/** Fixed chrome in caps in Retro, like the column headers; unchanged in Default. */
fun String.retroCaps(): String = if (ThemeStyleState.isRetro) uppercase() else this

/**
 * Retro typography: all 30 slots move to [retroReadingFamily] with font synthesis off, except
 * headlineLarge/Medium, which take the [retroPixelFamily] chrome voice. headlineSmall stays on the
 * reading face on purpose: it renders resource names. Sizes and line heights are unchanged, so
 * rows never shift.
 */
@Composable
internal fun retroTypography(): Typography {
    val base = Typography()
    val readingFace = retroReadingFamily()
    val pixel = retroPixelFamily()

    fun TextStyle.reading() = copy(fontFamily = readingFace, fontSynthesis = FontSynthesis.None)
    return base.copy(
        displayLarge = base.displayLarge.reading(),
        displayMedium = base.displayMedium.reading(),
        displaySmall = base.displaySmall.reading(),
        headlineLarge =
            base.headlineLarge.copy(
                fontFamily = pixel,
                fontSize = 20.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.sp,
                fontSynthesis = FontSynthesis.None,
            ),
        headlineMedium =
            base.headlineMedium.copy(
                fontFamily = pixel,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.sp,
                fontSynthesis = FontSynthesis.None,
            ),
        headlineSmall = base.headlineSmall.reading(),
        titleLarge = base.titleLarge.reading(),
        titleMedium = base.titleMedium.reading(),
        titleSmall = base.titleSmall.reading(),
        bodyLarge = base.bodyLarge.reading(),
        bodyMedium = base.bodyMedium.reading(),
        bodySmall = base.bodySmall.reading(),
        labelLarge = base.labelLarge.reading(),
        labelMedium = base.labelMedium.reading(),
        labelSmall = base.labelSmall.reading(),
        displayLargeEmphasized = base.displayLargeEmphasized.reading(),
        displayMediumEmphasized = base.displayMediumEmphasized.reading(),
        displaySmallEmphasized = base.displaySmallEmphasized.reading(),
        headlineLargeEmphasized = base.headlineLargeEmphasized.reading(),
        headlineMediumEmphasized = base.headlineMediumEmphasized.reading(),
        headlineSmallEmphasized = base.headlineSmallEmphasized.reading(),
        titleLargeEmphasized = base.titleLargeEmphasized.reading(),
        titleMediumEmphasized = base.titleMediumEmphasized.reading(),
        titleSmallEmphasized = base.titleSmallEmphasized.reading(),
        bodyLargeEmphasized = base.bodyLargeEmphasized.reading(),
        bodyMediumEmphasized = base.bodyMediumEmphasized.reading(),
        bodySmallEmphasized = base.bodySmallEmphasized.reading(),
        labelLargeEmphasized = base.labelLargeEmphasized.reading(),
        labelMediumEmphasized = base.labelMediumEmphasized.reading(),
        labelSmallEmphasized = base.labelSmallEmphasized.reading(),
    )
}

// All eight shape slots squared.
private val RetroShapes =
    Shapes(
        extraSmall = RoundedCornerShape(0.dp),
        small = RoundedCornerShape(0.dp),
        medium = RoundedCornerShape(0.dp),
        large = RoundedCornerShape(0.dp),
        extraLarge = RoundedCornerShape(0.dp),
        largeIncreased = RoundedCornerShape(0.dp),
        extraLargeIncreased = RoundedCornerShape(0.dp),
        extraExtraLarge = RoundedCornerShape(0.dp),
    )

// The library default, held once so Default doesn't build a fresh Shapes() on every recomposition.
private val DefaultShapes = Shapes()

val Typography.monospaceMedium: TextStyle
    @Composable get() = bodySmall.copy(fontFamily = appMonoFamily(), lineHeight = 18.sp)

val Typography.monospaceSmall: TextStyle
    @Composable get() = labelSmall.copy(fontFamily = appMonoFamily(), lineHeight = 16.sp)

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
    style: ThemeStyle = ThemeStyle.DEFAULT,
    content: @Composable () -> Unit,
) {
    val retro = style == ThemeStyle.RETRO
    val colorScheme =
        when {
            retro -> if (darkTheme) RetroDarkColorScheme else RetroLightColorScheme
            else -> if (darkTheme) DarkColorScheme else LightColorScheme
        }
    val extended =
        when {
            retro -> if (darkTheme) RetroDarkExtendedColors else RetroLightExtendedColors
            else -> if (darkTheme) DarkExtendedColors else LightExtendedColors
        }

    CompositionLocalProvider(LocalAppColors provides extended) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = if (retro) retroTypography() else Typography(),
            shapes = if (retro) RetroShapes else DefaultShapes,
            content = content,
        )
    }
}
