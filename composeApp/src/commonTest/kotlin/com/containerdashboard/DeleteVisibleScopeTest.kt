package com.containerdashboard

import com.containerdashboard.ui.screens.TYPED_CONFIRM_THRESHOLD
import com.containerdashboard.ui.screens.deleteVisibleLabel
import com.containerdashboard.ui.screens.deleteVisibleTitle
import com.containerdashboard.ui.screens.requiresTypedConfirmation
import com.containerdashboard.ui.screens.typedCountMatches
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Scope wording and typed-confirmation threshold for the Containers screen's
 * bulk delete (UX audit U1.4).
 */
class DeleteVisibleScopeTest {
    @Test
    fun `label says all when nothing is narrowed`() {
        assertEquals("Delete all 12 containers…", deleteVisibleLabel(visible = 12, total = 12))
        assertEquals("Delete the only container…", deleteVisibleLabel(visible = 1, total = 1))
        assertEquals("Delete containers…", deleteVisibleLabel(visible = 0, total = 0))
    }

    @Test
    fun `label says matching when a search or filter narrowed the set`() {
        assertEquals("Delete 5 matching containers…", deleteVisibleLabel(visible = 5, total = 12))
        assertEquals("Delete 1 matching container…", deleteVisibleLabel(visible = 1, total = 12))
    }

    @Test
    fun `title mirrors the scope`() {
        assertEquals("Delete all 12 containers?", deleteVisibleTitle(count = 12, isSubset = false))
        assertEquals("Delete the only container?", deleteVisibleTitle(count = 1, isSubset = false))
        assertEquals("Delete 3 matching containers?", deleteVisibleTitle(count = 3, isSubset = true))
        assertEquals("Delete 1 matching container?", deleteVisibleTitle(count = 1, isSubset = true))
    }

    @Test
    fun `typed confirmation is required strictly above the threshold`() {
        assertEquals(10, TYPED_CONFIRM_THRESHOLD)
        assertFalse(requiresTypedConfirmation(0))
        assertFalse(requiresTypedConfirmation(TYPED_CONFIRM_THRESHOLD))
        assertTrue(requiresTypedConfirmation(TYPED_CONFIRM_THRESHOLD + 1))
    }

    @Test
    fun `typed count must equal the current count exactly`() {
        assertTrue(typedCountMatches(" 11 ", 11))
        assertFalse(typedCountMatches("11", 12))
        assertFalse(typedCountMatches("", 11))
        assertFalse(typedCountMatches("011", 11))
    }
}
