package com.containerdashboard.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.containerdashboard.ui.theme.ExtendedColors
import com.containerdashboard.ui.theme.RetroDarkColorScheme
import com.containerdashboard.ui.theme.RetroDarkExtendedColors
import com.containerdashboard.ui.theme.RetroLightColorScheme
import com.containerdashboard.ui.theme.RetroLightExtendedColors
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the Retro palette: the role table (so a retune is a conscious test edit) and the WCAG AA
 * floor (4.5:1) of every text-role colour on the three backgrounds text sits on.
 */
class RetroPaletteTest {
    private fun channel(c: Float): Double {
        val v = c.toDouble()
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(c: Color): Double = 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)

    private fun contrast(
        a: Color,
        b: Color,
    ): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun backgrounds(scheme: ColorScheme) =
        listOf(
            "background" to scheme.background,
            "surface" to scheme.surface,
            "surfaceVariant" to scheme.surfaceVariant,
        )

    private fun textRoles(ext: ExtendedColors) =
        listOf(
            "running" to ext.running,
            "stopped" to ext.stopped,
            "paused" to ext.paused,
            "warning" to ext.warning,
            "accent" to ext.accent,
            "accentSecondary" to ext.accentSecondary,
            "accentTertiary" to ext.accentTertiary,
            "textMuted" to ext.textMuted,
        )

    private fun assertReadable(
        label: String,
        scheme: ColorScheme,
        ext: ExtendedColors,
    ) {
        val foregrounds = textRoles(ext) + listOf("onSurface" to scheme.onSurface, "primary" to scheme.primary)
        for ((bgName, bg) in backgrounds(scheme)) {
            for ((fgName, fg) in foregrounds) {
                val ratio = contrast(fg, bg)
                assertTrue(ratio >= 4.5, "$label: $fgName on $bgName is ${"%.2f".format(ratio)}:1, below 4.5:1")
            }
        }
    }

    @Test
    fun `retro dark text roles reach AA on background surface and surfaceVariant`() {
        assertReadable("Retro dark", RetroDarkColorScheme, RetroDarkExtendedColors)
    }

    @Test
    fun `retro light text roles reach AA on background surface and surfaceVariant`() {
        assertReadable("Retro light", RetroLightColorScheme, RetroLightExtendedColors)
    }

    @Test
    fun `contrast helper matches known WCAG values`() {
        assertEquals(21.0, contrast(Color.Black, Color.White), 0.001)
        assertEquals(1.0, contrast(Color.White, Color.White), 0.001)
    }

    @Test
    fun `retro light paused keeps the measured contrast`() {
        val paused = RetroLightExtendedColors.paused
        assertEquals(Color(0xFF775E03), paused)
        assertEquals(5.03, contrast(paused, Color(0xFFEFE7D2)), 0.01)
        assertEquals(5.55, contrast(paused, Color(0xFFF8F2E3)), 0.01)
        assertEquals(4.63, contrast(paused, Color(0xFFE8DEC6)), 0.01)
    }

    @Test
    fun `retro extended colours match the role table`() {
        fun assertRoles(
            label: String,
            ext: ExtendedColors,
            expected: Map<String, Long>,
        ) {
            val actual =
                mapOf(
                    "dividerStrong" to ext.dividerStrong,
                    "warningSurface" to ext.warningSurface,
                    "warningSurfaceLight" to ext.warningSurfaceLight,
                    "successSurface" to ext.successSurface,
                    "infoSurface" to ext.infoSurface,
                    "running" to ext.running,
                    "stopped" to ext.stopped,
                    "paused" to ext.paused,
                    "warning" to ext.warning,
                    "accent" to ext.accent,
                    "accentSecondary" to ext.accentSecondary,
                    "accentTertiary" to ext.accentTertiary,
                    "textMuted" to ext.textMuted,
                    "consoleForeground" to ext.consoleForeground,
                    "consoleBackground" to ext.consoleBackground,
                )
            assertEquals(expected.keys, actual.keys, "$label: every field is pinned")
            for ((name, argb) in expected) {
                assertEquals(argb.toInt(), actual.getValue(name).toArgb(), "$label.$name")
            }
        }

        assertRoles(
            "dark",
            RetroDarkExtendedColors,
            mapOf(
                "dividerStrong" to 0xFF3B4275,
                "warningSurface" to 0xFFFFA300,
                "warningSurfaceLight" to 0x1AFFA300,
                "successSurface" to 0xFF3CE66B,
                "infoSurface" to 0xFF5CBDFF,
                "running" to 0xFF3CE66B,
                "stopped" to 0xFFFF5C7A,
                "paused" to 0xFFFFD23E,
                "warning" to 0xFFFFA300,
                "accent" to 0xFF7FD8EA,
                "accentSecondary" to 0xFF5CBDFF,
                "accentTertiary" to 0xFFFFD23E,
                "textMuted" to 0xFFADB3D6,
                "consoleForeground" to 0xFFE3E6F5,
                "consoleBackground" to 0xFF12142B,
            ),
        )
        assertRoles(
            "light",
            RetroLightExtendedColors,
            mapOf(
                "dividerStrong" to 0xFFCBBE9E,
                "warningSurface" to 0xFF8F5300,
                "warningSurfaceLight" to 0x148F5300,
                "successSurface" to 0xFF2E6B2F,
                "infoSurface" to 0xFF1D5A9E,
                "running" to 0xFF2E6B2F,
                "stopped" to 0xFFA8231C,
                "paused" to 0xFF775E03,
                "warning" to 0xFF8F5300,
                "accent" to 0xFF00606B,
                "accentSecondary" to 0xFF1D5A9E,
                "accentTertiary" to 0xFF6B2F5B,
                "textMuted" to 0xFF5A4F3C,
                "consoleForeground" to 0xFF2B2418,
                "consoleBackground" to 0xFFEFE7D2,
            ),
        )
    }

    @Test
    fun `retro schemes pin their backgrounds`() {
        assertEquals(Color(0xFF12142B), RetroDarkColorScheme.background)
        assertEquals(Color(0xFF1E2240), RetroDarkColorScheme.surface)
        assertEquals(Color(0xFF272C50), RetroDarkColorScheme.surfaceVariant)
        assertEquals(Color(0xFF7FD8EA), RetroDarkColorScheme.primary)
        assertEquals(Color(0xFFEFE7D2), RetroLightColorScheme.background)
        assertEquals(Color(0xFFF8F2E3), RetroLightColorScheme.surface)
        assertEquals(Color(0xFFE8DEC6), RetroLightColorScheme.surfaceVariant)
        assertEquals(Color(0xFF00606B), RetroLightColorScheme.primary)
    }
}
