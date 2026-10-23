package com.containerdashboard.ui

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.containerdashboard.ui.theme.ThemeStyle
import com.containerdashboard.ui.theme.ThemeStyleState
import com.containerdashboard.ui.theme.appCorner
import com.containerdashboard.ui.theme.appCorners
import com.containerdashboard.ui.theme.appRoundShape
import com.containerdashboard.ui.theme.appStrokeCap
import com.containerdashboard.ui.theme.retroCaps
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * The style-aware shape, stroke and text helpers: unchanged in Default, square, flat and upper-case
 * in Retro. They read the global [ThemeStyleState], which every case resets.
 */
class ThemeStyleTokensTest {
    private val density = Density(1f)

    @BeforeTest
    fun setUp() {
        assertEquals(ThemeStyle.DEFAULT, ThemeStyleState.style, "another test leaked the Retro style")
    }

    @AfterTest
    fun restore() {
        ThemeStyleState.style = ThemeStyle.DEFAULT
    }

    private fun RoundedCornerShape.radii(): List<Float> = listOf(topStart, topEnd, bottomEnd, bottomStart).map { it.toPx(100f, density) }

    private fun CornerSize.toPx(
        side: Float,
        density: Density,
    ): Float = toPx(Size(side, side), density)

    @Test
    fun `appCorner keeps its radius in Default and is square in Retro`() {
        assertEquals(RoundedCornerShape(8.dp), 8.dp.appCorner)
        assertEquals(listOf(8f, 8f, 8f, 8f), 8.dp.appCorner.radii())

        ThemeStyleState.style = ThemeStyle.RETRO
        assertEquals(RoundedCornerShape(0.dp), 8.dp.appCorner)
        assertEquals(listOf(0f, 0f, 0f, 0f), 8.dp.appCorner.radii())
    }

    @Test
    fun `appCorners keeps each corner in Default and is square in Retro`() {
        assertEquals(
            RoundedCornerShape(topStart = 12.dp, topEnd = 4.dp, bottomEnd = 0.dp, bottomStart = 8.dp),
            appCorners(topStart = 12.dp, topEnd = 4.dp, bottomStart = 8.dp),
        )

        ThemeStyleState.style = ThemeStyle.RETRO
        assertEquals(RoundedCornerShape(0.dp), appCorners(topStart = 12.dp, topEnd = 4.dp, bottomStart = 8.dp))
    }

    @Test
    fun `appRoundShape is a full circle in Default and square in Retro`() {
        // 50% corners are Compose's CircleShape.
        assertEquals(listOf(50f, 50f, 50f, 50f), appRoundShape.let { (it as RoundedCornerShape).radii() })

        ThemeStyleState.style = ThemeStyle.RETRO
        assertEquals(listOf(0f, 0f, 0f, 0f), (appRoundShape as RoundedCornerShape).radii())
    }

    @Test
    fun `appStrokeCap is Round in Default and Butt in Retro`() {
        assertEquals(StrokeCap.Round, appStrokeCap)
        ThemeStyleState.style = ThemeStyle.RETRO
        assertEquals(StrokeCap.Butt, appStrokeCap)
    }

    @Test
    fun `retroCaps upper-cases only in Retro`() {
        assertEquals("Containers", "Containers".retroCaps())
        ThemeStyleState.style = ThemeStyle.RETRO
        assertEquals("CONTAINERS", "Containers".retroCaps())
    }

    @Test
    fun `isRetro follows the style`() {
        assertEquals(false, ThemeStyleState.isRetro)
        ThemeStyleState.style = ThemeStyle.RETRO
        assertEquals(true, ThemeStyleState.isRetro)
        assertNotEquals(ThemeStyle.DEFAULT, ThemeStyleState.style)
    }
}
