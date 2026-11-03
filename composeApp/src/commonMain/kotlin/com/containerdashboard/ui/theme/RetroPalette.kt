package com.containerdashboard.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Retro-dark: CRT arcade. Deep blue-black tube, cyan phosphor primary, coin-gold secondary.
// The full surfaceContainer ramp is set because SettingsSection's Surface reads `surfaceContainer`.
internal val RetroDarkColorScheme =
    darkColorScheme(
        primary = Color(0xFF7FD8EA),
        onPrimary = Color(0xFF00363F),
        primaryContainer = Color(0xFF1E4A56),
        onPrimaryContainer = Color(0xFFBDEBF7),
        secondary = Color(0xFFFFD23E),
        onSecondary = Color(0xFF241A00),
        secondaryContainer = Color(0xFF5C4A00),
        onSecondaryContainer = Color(0xFFFFE99C),
        tertiary = Color(0xFFFF77A8),
        onTertiary = Color(0xFF54082C),
        background = Color(0xFF12142B),
        surface = Color(0xFF1E2240),
        surfaceVariant = Color(0xFF272C50),
        onBackground = Color(0xFFE3E6F5),
        onSurface = Color(0xFFE3E6F5),
        onSurfaceVariant = Color(0xFFADB3D6),
        error = Color(0xFFFF5C7A),
        onError = Color(0xFF3B0010),
        outline = Color(0xFF3B4275),
        outlineVariant = Color(0xFF3B4275),
        surfaceContainerLowest = Color(0xFF0A0B1A),
        surfaceContainerLow = Color(0xFF191C38),
        surfaceContainer = Color(0xFF1E2240),
        surfaceContainerHigh = Color(0xFF272C50),
        surfaceContainerHighest = Color(0xFF313763),
        surfaceDim = Color(0xFF12142B),
        surfaceBright = Color(0xFF313763),
    )

// Retro-light: "paper terminal". Cream field, dark-brown ink, deep-teal primary, plum secondary.
internal val RetroLightColorScheme =
    lightColorScheme(
        primary = Color(0xFF00606B),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFCFE3E0),
        onPrimaryContainer = Color(0xFF00363B),
        secondary = Color(0xFF6B2F5B),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFEBD6E3),
        onSecondaryContainer = Color(0xFF2E0F27),
        tertiary = Color(0xFF6B4F1D),
        onTertiary = Color.White,
        background = Color(0xFFEFE7D2),
        surface = Color(0xFFF8F2E3),
        surfaceVariant = Color(0xFFE8DEC6),
        onBackground = Color(0xFF2B2418),
        onSurface = Color(0xFF2B2418),
        onSurfaceVariant = Color(0xFF5A4F3C),
        error = Color(0xFFA8231C),
        onError = Color.White,
        outline = Color(0xFFCBBE9E),
        outlineVariant = Color(0xFFCBBE9E),
        surfaceContainerLowest = Color(0xFFFFFBF2),
        surfaceContainerLow = Color(0xFFF8F2E3),
        surfaceContainer = Color(0xFFF2EAD6),
        surfaceContainerHigh = Color(0xFFEBE2CB),
        surfaceContainerHighest = Color(0xFFE3D8BC),
        surfaceDim = Color(0xFFE6DCC3),
        surfaceBright = Color(0xFFFFFBF2),
    )

internal val RetroDarkExtendedColors =
    ExtendedColors(
        dividerStrong = Color(0xFF3B4275),
        warningSurface = Color(0xFFFFA300),
        warningSurfaceLight = Color(0x1AFFA300),
        successSurface = Color(0xFF3CE66B),
        infoSurface = Color(0xFF5CBDFF),
        running = Color(0xFF3CE66B),
        stopped = Color(0xFFFF5C7A),
        paused = Color(0xFFFFD23E),
        warning = Color(0xFFFFA300),
        accent = Color(0xFF7FD8EA),
        accentSecondary = Color(0xFF5CBDFF),
        accentTertiary = Color(0xFFFFD23E),
        textMuted = Color(0xFFADB3D6),
        consoleForeground = Color(0xFFE3E6F5),
        consoleBackground = Color(0xFF12142B),
    )

internal val RetroLightExtendedColors =
    ExtendedColors(
        dividerStrong = Color(0xFFCBBE9E),
        warningSurface = Color(0xFF8F5300),
        warningSurfaceLight = Color(0x148F5300),
        successSurface = Color(0xFF2E6B2F),
        infoSurface = Color(0xFF1D5A9E),
        running = Color(0xFF2E6B2F),
        stopped = Color(0xFFA8231C),
        paused = Color(0xFF775E03),
        warning = Color(0xFF8F5300),
        accent = Color(0xFF00606B),
        accentSecondary = Color(0xFF1D5A9E),
        accentTertiary = Color(0xFF6B2F5B),
        textMuted = Color(0xFF5A4F3C),
        consoleForeground = Color(0xFF2B2418),
        consoleBackground = Color(0xFFEFE7D2),
    )
