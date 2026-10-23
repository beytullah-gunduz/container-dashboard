package com.containerdashboard

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.containerdashboard.ui.components.RawJsonTab
import kotlin.test.Test

/**
 * The Inspect dialog's Raw JSON tab hides secret env values like the Environment tab does
 * (review-2026-10, S5.3). The dialog itself only opens from a right-click menu, so the tab is
 * rendered on its own.
 */
@OptIn(ExperimentalTestApi::class)
class RawJsonTabUiTest {
    private val json =
        """
        {
          "Config" : {
            "Env" : [ "PATH=/usr/bin", "POSTGRES_PASSWORD=hunter2" ]
          }
        }
        """.trimIndent()

    @Test
    fun `secret env values stay masked until revealed`() =
        runComposeUiTest {
            setContent { RawJsonTab(json) }

            onAllNodes(hasText("hunter2", substring = true)).assertCountEquals(0)
            onAllNodes(hasText("POSTGRES_PASSWORD=••••••••", substring = true)).assertCountEquals(1)
            onAllNodes(hasText("PATH=/usr/bin", substring = true)).assertCountEquals(1)

            onNodeWithContentDescription("Reveal secret values").performClick()
            onAllNodes(hasText("POSTGRES_PASSWORD=hunter2", substring = true)).assertCountEquals(1)

            onNodeWithContentDescription("Hide secret values").performClick()
            onAllNodes(hasText("hunter2", substring = true)).assertCountEquals(0)
        }

    @Test
    fun `no reveal toggle when there is nothing to hide`() =
        runComposeUiTest {
            setContent { RawJsonTab("""{ "Env" : [ "PATH=/usr/bin" ] }""") }

            onAllNodesWithContentDescription("Reveal secret values").assertCountEquals(0)
        }
}
