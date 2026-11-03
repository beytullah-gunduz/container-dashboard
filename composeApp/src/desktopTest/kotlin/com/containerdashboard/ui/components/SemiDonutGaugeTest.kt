package com.containerdashboard.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

/** [litCells] and the segment count of the Retro [SemiDonutGauge]. */
class SemiDonutGaugeTest {
    @Test
    fun `nothing is lit for zero, negative or NaN`() {
        assertEquals(0, litCells(0f, GAUGE_SEGMENTS))
        assertEquals(0, litCells(-0.5f, GAUGE_SEGMENTS))
        assertEquals(0, litCells(Float.NaN, GAUGE_SEGMENTS))
    }

    @Test
    fun `any tiny positive value lights at least one segment`() {
        assertEquals(1, litCells(0.001f, GAUGE_SEGMENTS))
        assertEquals(1, litCells(Float.MIN_VALUE, GAUGE_SEGMENTS))
    }

    @Test
    fun `a full gauge lights all 18 segments and larger values clamp`() {
        assertEquals(18, GAUGE_SEGMENTS)
        assertEquals(18, litCells(1f, GAUGE_SEGMENTS))
        assertEquals(18, litCells(7f, GAUGE_SEGMENTS))
        assertEquals(18, litCells(Float.POSITIVE_INFINITY, GAUGE_SEGMENTS))
    }

    @Test
    fun `values in between round to the nearest segment`() {
        assertEquals(9, litCells(0.5f, GAUGE_SEGMENTS))
        assertEquals(17, litCells(0.95f, GAUGE_SEGMENTS))
    }
}
