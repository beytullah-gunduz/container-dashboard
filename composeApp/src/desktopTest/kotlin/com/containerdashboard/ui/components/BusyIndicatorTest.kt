package com.containerdashboard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.containerdashboard.ui.theme.ThemeStyle
import com.containerdashboard.ui.theme.ThemeStyleState
import kotlin.math.abs
import kotlin.math.floor
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val Backdrop = Color.White
private val Ink = Color.Red

/** (column, row) of the 8 cells of the 3 x 3 ring, clockwise from the top-left (the ring's own order). */
private val RingOrder = listOf(0 to 0, 1 to 0, 2 to 0, 2 to 1, 2 to 2, 1 to 2, 0 to 2, 0 to 1)

/**
 * [BusyIndicator]: the pure ring geometry, step and opacity helpers, and the drawn result at 1x
 * density over a white backdrop in a red ink. Default keeps Material's round spinner; Retro draws a
 * ring of square cells around an empty centre. The style is flipped through [ThemeStyleState] only
 * and reset after every case; nothing here touches `PreferenceRepository` or any real settings.
 */
@Suppress("DEPRECATION")
@OptIn(ExperimentalTestApi::class)
class BusyIndicatorTest {
    @BeforeTest
    fun setUp() {
        assertEquals(ThemeStyle.DEFAULT, ThemeStyleState.style, "another test leaked the Retro style")
    }

    @AfterTest
    fun restore() {
        ThemeStyleState.style = ThemeStyle.DEFAULT
    }

    private fun pin(style: ThemeStyle) {
        ThemeStyleState.style = style
    }

    private fun SkikoComposeUiTest.showSingle(
        style: ThemeStyle,
        boxDp: Int,
    ): PixelMap {
        pin(style)
        setContent {
            Box(Modifier.size(boxDp.dp).background(Backdrop)) {
                BusyIndicator(Modifier.size(boxDp.dp), color = Ink)
            }
        }
        waitForIdle()
        return captureToImage().toPixelMap()
    }

    private fun PixelMap.assertColor(
        x: Int,
        y: Int,
        expected: Color,
    ) {
        val actual = this[x, y]
        val close =
            abs(actual.red - expected.red) < 0.05f &&
                abs(actual.green - expected.green) < 0.05f &&
                abs(actual.blue - expected.blue) < 0.05f &&
                abs(actual.alpha - expected.alpha) < 0.05f
        assertTrue(close, "pixel ($x, $y) should be $expected but is $actual")
    }

    private fun PixelMap.isInk(
        x: Int,
        y: Int,
    ): Boolean {
        val actual = this[x, y]
        return abs(actual.red - Ink.red) < 0.05f &&
            abs(actual.green - Ink.green) < 0.05f &&
            abs(actual.blue - Ink.blue) < 0.05f &&
            abs(actual.alpha - Ink.alpha) < 0.05f
    }

    /** The index of the one fully lit cell of the 14 px ring whose left edge is [x0]; fails unless exactly one is lit. */
    private fun litCell(
        map: PixelMap,
        x0: Int,
    ): Int {
        val lit =
            RingOrder.indices.filter { index ->
                val (column, row) = RingOrder[index]
                map.isInk(x0 + column * 5 + 1, row * 5 + 1)
            }
        assertEquals(1, lit.size, "exactly one cell of the ring at x=$x0 should be fully lit, but lit cells are $lit")
        return lit.single()
    }

    @Test
    fun `busyStep advances every 100 ms and wraps after 8 steps`() {
        assertEquals(0, busyStep(0))
        assertEquals(0, busyStep(99))
        assertEquals(1, busyStep(100))
        assertEquals(7, busyStep(750))
        assertEquals(0, busyStep(800))
        assertEquals(1, busyStep(1_000_000_123L))
    }

    @Test
    fun `frameStep divides frame time by the step length and wraps at the step count`() {
        assertEquals(0, frameStep(0, 180L, 6))
        assertEquals(0, frameStep(179, 180L, 6))
        assertEquals(1, frameStep(180, 180L, 6))
        assertEquals(5, frameStep(5 * 180L + 10, 180L, 6))
        assertEquals(0, frameStep(6 * 180L, 180L, 6))
        assertEquals(2, frameStep(8 * 180L, 180L, 6))
        assertEquals(0, frameStep(123_456_789L, 100L, 1))
    }

    @Test
    fun `the 14 px ring is 8 crisp 4 px cells clockwise around an empty centre`() {
        assertEquals(
            listOf(
                BusyCell(0f, 0f, 4f),
                BusyCell(5f, 0f, 4f),
                BusyCell(10f, 0f, 4f),
                BusyCell(10f, 5f, 4f),
                BusyCell(10f, 10f, 4f),
                BusyCell(5f, 10f, 4f),
                BusyCell(0f, 10f, 4f),
                BusyCell(0f, 5f, 4f),
            ),
            busyRingCells(14f, 14f, 12f),
        )
    }

    @Test
    fun `a box of 12 px or less gets the 2x2 ring`() {
        assertEquals(
            listOf(BusyCell(0f, 0f, 4f), BusyCell(5f, 0f, 4f), BusyCell(5f, 5f, 4f), BusyCell(0f, 5f, 4f)),
            busyRingCells(10f, 10f, 12f),
        )
        assertEquals(4, busyRingCells(12f, 12f, 12f).size)
        assertEquals(8, busyRingCells(13f, 13f, 12f).size)
    }

    @Test
    fun `cells are whole pixels and centred`() {
        val large = busyRingCells(80f, 80f, 24f)
        assertEquals(8, large.size)
        assertTrue(large.all { it.side == 23f }, "every cell of the 80 px ring should be 23 px: $large")
        assertEquals(BusyCell(0f, 0f, 23f), large.first())
        assertEquals(BusyCell(0f, 28f, 23f), large.last())

        assertEquals(BusyCell(3f, 0f, 4f), busyRingCells(20f, 14f, 12f).first())

        for (cells in listOf(busyRingCells(14.6f, 14.6f, 12f), busyRingCells(96f, 96f, 24f))) {
            assertTrue(cells.isNotEmpty())
            cells.forEach { cell ->
                assertEquals(floor(cell.left), cell.left, "left of $cell")
                assertEquals(floor(cell.top), cell.top, "top of $cell")
                assertEquals(floor(cell.side), cell.side, "side of $cell")
            }
        }
    }

    @Test
    fun `a box too small for a 1 px cell draws nothing`() {
        assertTrue(busyRingCells(1f, 1f, 12f).isEmpty())
        assertTrue(busyRingCells(0f, 0f, 12f).isEmpty())
        assertTrue(busyRingCells(Float.NaN, Float.NaN, 12f).isEmpty())
        assertTrue(busyRingCells(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, 12f).isEmpty())
    }

    @Test
    fun `trail and resting opacities`() {
        assertEquals(1f, busyCellAlpha(head = 3, index = 3, cellCount = 8))
        assertEquals(0.55f, busyCellAlpha(head = 3, index = 2, cellCount = 8))
        assertEquals(0.3f, busyCellAlpha(head = 3, index = 1, cellCount = 8))
        assertEquals(0.14f, busyCellAlpha(head = 3, index = 0, cellCount = 8))
        assertEquals(0.14f, busyCellAlpha(head = 3, index = 4, cellCount = 8))

        assertEquals(0.55f, busyCellAlpha(head = 0, index = 7, cellCount = 8))
        assertEquals(0.3f, busyCellAlpha(head = 0, index = 6, cellCount = 8))

        assertEquals(1f, busyCellAlpha(head = 1, index = 1, cellCount = 4))
        assertEquals(0.55f, busyCellAlpha(head = 1, index = 0, cellCount = 4))
        assertEquals(0.14f, busyCellAlpha(head = 1, index = 3, cellCount = 4))
        assertEquals(0.14f, busyCellAlpha(head = 1, index = 2, cellCount = 4))
    }

    @Test
    fun `both styles expose indeterminate progress`() =
        runSkikoComposeUiTest(size = Size(40f, 40f), density = Density(1f)) {
            pin(ThemeStyle.DEFAULT)
            setContent { BusyIndicator() }
            onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertExists()

            pin(ThemeStyle.RETRO)
            waitForIdle()
            onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertExists()
            onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertCountEquals(1)
        }

    @Test
    fun `Retro draws square cells in the given colour around an empty centre`() =
        runSkikoComposeUiTest(size = Size(14f, 14f), density = Density(1f)) {
            val map = showSingle(ThemeStyle.RETRO, boxDp = 14)
            map.assertColor(1, 1, Ink)
            map.assertColor(6, 6, Backdrop)
            map.assertColor(4, 1, Backdrop)
            map.assertColor(1, 6, Color(1f, 0.45f, 0.45f))
            map.assertColor(1, 11, Color(1f, 0.7f, 0.7f))
            map.assertColor(6, 1, Color(1f, 0.86f, 0.86f))
        }

    @Test
    fun `the 2x2 ring fills a 10 px box`() =
        runSkikoComposeUiTest(size = Size(10f, 10f), density = Density(1f)) {
            val map = showSingle(ThemeStyle.RETRO, boxDp = 10)
            map.assertColor(1, 1, Ink)
            map.assertColor(6, 1, Color(1f, 0.86f, 0.86f))
            map.assertColor(1, 6, Color(1f, 0.45f, 0.45f))
            map.assertColor(4, 4, Backdrop)
        }

    @Test
    fun `Retro rings step clockwise in phase with the frame clock`() =
        runSkikoComposeUiTest(size = Size(34f, 14f), density = Density(1f)) {
            pin(ThemeStyle.RETRO)
            mainClock.autoAdvance = false
            setContent {
                Row(Modifier.size(34.dp, 14.dp).background(Backdrop)) {
                    BusyIndicator(Modifier.size(14.dp), color = Ink)
                    Spacer(Modifier.width(6.dp))
                    BusyIndicator(Modifier.size(14.dp), color = Ink)
                }
            }
            mainClock.advanceTimeByFrame()
            waitForIdle()
            val first = captureToImage().toPixelMap()
            val h0 = litCell(first, 0)
            assertEquals(h0, litCell(first, 20), "the second ring should be in phase with the first")

            var next = h0
            var map = first
            var steps = 0
            while (steps < 20 && next == h0) {
                mainClock.advanceTimeByFrame()
                waitForIdle()
                map = captureToImage().toPixelMap()
                next = litCell(map, 0)
                steps++
            }
            assertTrue(next != h0, "the lit cell should move within 20 frames, but stayed on cell $h0")
            assertEquals((h0 + 1) % 8, next, "the lit cell should step one cell clockwise")
            assertEquals(next, litCell(map, 20), "the second ring should still be in phase after the step")
        }

    @Test
    fun `Default keeps Material's round spinner`() =
        runSkikoComposeUiTest(size = Size(14f, 14f), density = Density(1f)) {
            val map = showSingle(ThemeStyle.DEFAULT, boxDp = 14)
            map.assertColor(1, 1, Backdrop)
        }
}
