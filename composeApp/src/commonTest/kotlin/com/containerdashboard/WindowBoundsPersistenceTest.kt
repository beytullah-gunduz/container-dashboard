package com.containerdashboard

import com.containerdashboard.data.repository.WindowBounds
import com.containerdashboard.data.repository.forPersistence
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A maximized window must not save its maximized rectangle as its size: after a relaunch, restore
 * would return to that same rectangle, and maximize and restore would seem to do nothing.
 */
class WindowBoundsPersistenceTest {
    private val floating = WindowBounds(x = 100, y = 80, width = 1200, height = 800, maximized = false)
    private val maximized = WindowBounds(x = 0, y = 25, width = 1920, height = 1055, maximized = true)

    @Test
    fun `a floating window saves its own bounds`() {
        assertEquals(floating, floating.forPersistence(lastFloating = null))
        assertEquals(floating, floating.forPersistence(lastFloating = floating.copy(x = 5)))
    }

    @Test
    fun `a maximized window keeps its last floating bounds and the maximized flag`() {
        assertEquals(floating.copy(maximized = true), maximized.forPersistence(lastFloating = floating))
    }

    @Test
    fun `a maximized window with no floating bounds known saves what it has`() {
        assertEquals(maximized, maximized.forPersistence(lastFloating = null))
    }
}
