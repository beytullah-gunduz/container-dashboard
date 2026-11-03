package com.containerdashboard.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.unit.sp
import com.containerdashboard.ui.theme.ThemeStyle
import com.containerdashboard.ui.theme.ThemeStyleState
import com.containerdashboard.ui.theme.appMonoFamily
import com.containerdashboard.ui.theme.retroChrome
import com.containerdashboard.ui.theme.retroPixelFamily
import com.containerdashboard.ui.theme.retroReadingFamily
import com.containerdashboard.ui.theme.retroTypography
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * [retroTypography], [appMonoFamily] and [retroChrome]. Does not wrap its content in
 * `ContainerDashboardTheme`: the typography is read directly, and the style is flipped through
 * [ThemeStyleState] only where the helper under test reads that global. None of it touches
 * `PreferenceRepository`; the global is reset after every case.
 */
@OptIn(ExperimentalTestApi::class)
class RetroTypographyTest {
    @BeforeTest
    fun setUp() {
        assertEquals(ThemeStyle.DEFAULT, ThemeStyleState.style, "another test leaked the Retro style")
    }

    @AfterTest
    fun restore() {
        ThemeStyleState.style = ThemeStyle.DEFAULT
    }

    // All 30 Typography slots as name -> style.
    private fun Typography.slots(): List<Pair<String, TextStyle>> =
        listOf(
            "displayLarge" to displayLarge,
            "displayMedium" to displayMedium,
            "displaySmall" to displaySmall,
            "headlineLarge" to headlineLarge,
            "headlineMedium" to headlineMedium,
            "headlineSmall" to headlineSmall,
            "titleLarge" to titleLarge,
            "titleMedium" to titleMedium,
            "titleSmall" to titleSmall,
            "bodyLarge" to bodyLarge,
            "bodyMedium" to bodyMedium,
            "bodySmall" to bodySmall,
            "labelLarge" to labelLarge,
            "labelMedium" to labelMedium,
            "labelSmall" to labelSmall,
            "displayLargeEmphasized" to displayLargeEmphasized,
            "displayMediumEmphasized" to displayMediumEmphasized,
            "displaySmallEmphasized" to displaySmallEmphasized,
            "headlineLargeEmphasized" to headlineLargeEmphasized,
            "headlineMediumEmphasized" to headlineMediumEmphasized,
            "headlineSmallEmphasized" to headlineSmallEmphasized,
            "titleLargeEmphasized" to titleLargeEmphasized,
            "titleMediumEmphasized" to titleMediumEmphasized,
            "titleSmallEmphasized" to titleSmallEmphasized,
            "bodyLargeEmphasized" to bodyLargeEmphasized,
            "bodyMediumEmphasized" to bodyMediumEmphasized,
            "bodySmallEmphasized" to bodySmallEmphasized,
            "labelLargeEmphasized" to labelLargeEmphasized,
            "labelMediumEmphasized" to labelMediumEmphasized,
            "labelSmallEmphasized" to labelSmallEmphasized,
        )

    @Test
    fun `Retro moves every slot to the reading face except the pixel headlines`() =
        runComposeUiTest {
            var slots: List<Pair<String, TextStyle>> = emptyList()
            var reading: FontFamily? = null
            var pixel: FontFamily? = null
            setContent {
                slots = retroTypography().slots()
                reading = retroReadingFamily()
                pixel = retroPixelFamily()
            }
            waitForIdle()

            assertEquals(30, slots.size)
            // The slot checks compare against retroReadingFamily() itself, so pin that it is a
            // face of its own: they would pass if both families were the same font.
            assertNotEquals(pixel, reading, "the reading face must not be the pixel face")
            assertNotEquals(FontFamily.Default, reading)
            assertNotEquals(FontFamily.Monospace, reading)

            val pixelNames = setOf("headlineLarge", "headlineMedium")
            for ((name, style) in slots) {
                if (name in pixelNames) continue
                assertEquals(reading, style.fontFamily, "$name should use the reading face in Retro")
                assertEquals(FontSynthesis.None, style.fontSynthesis, "$name should disable font synthesis in Retro")
            }
            val map = slots.toMap()
            assertEquals(pixel, map.getValue("headlineLarge").fontFamily)
            assertEquals(20.sp, map.getValue("headlineLarge").fontSize)
            assertEquals(pixel, map.getValue("headlineMedium").fontFamily)
            assertEquals(16.sp, map.getValue("headlineMedium").fontSize)
        }

    @Test
    fun `Retro keeps Material sizes and line heights on the other slots`() =
        runComposeUiTest {
            var retro: Map<String, TextStyle> = emptyMap()
            setContent { retro = retroTypography().slots().toMap() }
            waitForIdle()

            val base = Typography().slots().toMap()
            for ((name, style) in retro) {
                if (name == "headlineLarge" || name == "headlineMedium") continue
                assertEquals(base.getValue(name).fontSize, style.fontSize, "$name fontSize must be unchanged in Retro")
                assertEquals(base.getValue(name).lineHeight, style.lineHeight, "$name lineHeight must be unchanged in Retro")
            }
        }

    @Test
    fun `appMonoFamily follows the style`() {
        var defaultMono: FontFamily? = null
        runComposeUiTest {
            setContent { defaultMono = appMonoFamily() }
            waitForIdle()
        }
        assertEquals(FontFamily.Monospace, defaultMono, "Default mono is the platform monospace")

        ThemeStyleState.style = ThemeStyle.RETRO
        var retroMono: FontFamily? = null
        var reading: FontFamily? = null
        runComposeUiTest {
            setContent {
                retroMono = appMonoFamily()
                reading = retroReadingFamily()
            }
            waitForIdle()
        }
        assertEquals(reading, retroMono, "Retro mono is the reading face")
        assertNotEquals(FontFamily.Monospace, retroMono)
    }

    @Test
    fun `retroChrome is a no-op in Default and swaps in the pixel face in Retro`() {
        val base = TextStyle(fontSize = 14.sp)
        var defaultStyle: TextStyle? = null
        runComposeUiTest {
            setContent { defaultStyle = base.retroChrome(8.sp) }
            waitForIdle()
        }
        assertEquals(base, defaultStyle)

        ThemeStyleState.style = ThemeStyle.RETRO
        var retroStyle: TextStyle? = null
        var scaledStyle: TextStyle? = null
        var pixel: FontFamily? = null
        runComposeUiTest {
            setContent {
                retroStyle = base.retroChrome(8.sp)
                scaledStyle = base.retroChrome()
                pixel = retroPixelFamily()
            }
            waitForIdle()
        }
        assertEquals(pixel, retroStyle?.fontFamily)
        assertEquals(8.sp, retroStyle?.fontSize)
        assertEquals(14.sp * 0.72f, scaledStyle?.fontSize)
    }
}
