package com.containerdashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.containerdashboard.data.models.Container
import com.containerdashboard.ui.components.LogsTabContent
import com.containerdashboard.ui.state.LogsPaneState
import com.containerdashboard.util.DATA_DIR_PROPERTY
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Switching the logs pane from one container to another starts at the new container's last line
 * and follows it, even when the previous container was scrolled to the top. Reported as a bug
 * (the pane "keeps the previous scroll position"); it does not happen, and this pins that
 * (review-2026-10 G7).
 */
@OptIn(ExperimentalTestApi::class)
class LogsTabContentUiTest {
    @BeforeTest
    fun setUp() {
        // LogsTabContent reads the preferences DataStore (word wrap, max lines). The desktopTest
        // task points this property at build/; refuse to run against the developer's real settings.
        assertNotNull(System.getProperty(DATA_DIR_PROPERTY), "$DATA_DIR_PROPERTY must be set for tests")
    }

    private fun container(id: String) = Container(id = id, names = listOf("/$id"), image = "demo", state = "running")

    private fun lines(prefix: String) = (1..200).map { "$prefix-line-$it" }

    @Test
    fun `switching containers lands on the last line and follows`() =
        runComposeUiTest {
            val a = container("a")
            val b = container("b")
            var state by mutableStateOf(LogsPaneState(containers = listOf(a), logs = lines("a"), isFollowing = true))

            setContent {
                Box(Modifier.height(300.dp)) {
                    LogsTabContent(state = state, onRefresh = {})
                }
            }
            waitForIdle()

            // Only the lazy list has a scroll-to-index action (the horizontal scroller may match
            // hasScrollAction() when word wrap is off). The swipe is a real gesture: the pane
            // drops its follow flag when a gesture ends away from the bottom, which a
            // scroll-to-index alone does not trigger.
            onNode(hasScrollToIndexAction()).performScrollToIndex(0)
            onNode(hasScrollToIndexAction()).performTouchInput { swipeDown() }
            waitForIdle()
            onAllNodesWithText("a-line-1").assertCountEquals(1)
            onAllNodesWithText("a-line-200").assertCountEquals(0)

            // Every switch goes through isLoading, as in the view model.
            state = LogsPaneState(containers = listOf(b), isLoading = true)
            waitForIdle()
            state = LogsPaneState(containers = listOf(b), logs = lines("b"), isFollowing = true)
            waitForIdle()
            onAllNodesWithText("b-line-200").assertCountEquals(1)

            state = state.copy(logs = state.logs + "b-line-201")
            waitForIdle()
            onAllNodesWithText("b-line-201").assertCountEquals(1)
        }
}
