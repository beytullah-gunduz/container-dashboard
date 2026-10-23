package com.containerdashboard.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

/** [skeletonRowAlpha]: the Retro skeleton lights one row at a time. */
class ListRowSkeletonTest {
    @Test
    fun `only the row under the step is bright`() {
        assertEquals(0.8f, skeletonRowAlpha(step = 2, row = 2, rowCount = 6))
        assertEquals(0.4f, skeletonRowAlpha(step = 2, row = 1, rowCount = 6))
        assertEquals(0.4f, skeletonRowAlpha(step = 2, row = 3, rowCount = 6))
        assertEquals(1, (0 until 6).count { skeletonRowAlpha(step = 4, row = it, rowCount = 6) == 0.8f })
    }

    @Test
    fun `the bright row wraps with the step`() {
        assertEquals(0.8f, skeletonRowAlpha(step = 6, row = 0, rowCount = 6))
        assertEquals(0.8f, skeletonRowAlpha(step = 8, row = 2, rowCount = 6))
    }

    @Test
    fun `no rows or a negative step do not throw`() {
        assertEquals(0.4f, skeletonRowAlpha(step = 3, row = 0, rowCount = 0))
        assertEquals(0.8f, skeletonRowAlpha(step = -1, row = 5, rowCount = 6))
    }
}
