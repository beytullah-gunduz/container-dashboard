package com.containerdashboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.containerdashboard.ui.shortcuts.CommandPalette
import com.containerdashboard.ui.shortcuts.PaletteAction
import com.containerdashboard.util.DATA_DIR_PROPERTY
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * The command palette lists the actions it is given now, not the ones it opened with: an action
 * list that changes while the palette is open (a container stopped) must not leave a stale
 * "Stop: X" row that 409s when run (review-2026-10 G6).
 */
@OptIn(ExperimentalTestApi::class)
class CommandPaletteUiTest {
    @BeforeTest
    fun setUp() {
        // Tests must never read or write the developer's real settings; the desktopTest task
        // points this property at build/.
        assertNotNull(System.getProperty(DATA_DIR_PROPERTY), "$DATA_DIR_PROPERTY must be set for tests")
    }

    private fun action(
        id: String,
        label: String,
    ) = PaletteAction(id = id, label = label, section = "Containers", onRun = {})

    @Test
    fun `the palette follows its action list while open`() =
        runComposeUiTest {
            var actions by mutableStateOf(listOf(action("a", "Stop: alpha")))

            setContent { CommandPalette(actions = actions, onClose = {}) }
            waitForIdle()
            onAllNodesWithText("Stop: alpha").assertCountEquals(1)

            actions = listOf(action("b", "Start: alpha"))
            waitForIdle()

            onAllNodesWithText("Start: alpha").assertCountEquals(1)
            onAllNodesWithText("Stop: alpha").assertCountEquals(0)
        }
}
