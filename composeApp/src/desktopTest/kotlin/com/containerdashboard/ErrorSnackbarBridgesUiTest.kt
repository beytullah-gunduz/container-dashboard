package com.containerdashboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.containerdashboard.ui.components.AppSnackbarHost
import com.containerdashboard.ui.components.ErrorSnackbarBridges
import com.containerdashboard.util.DATA_DIR_PROPERTY
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The error-to-snackbar bridges in `App.kt`: an error present on the first frame is not lost
 * before the host subscribes, and one the Containers card already showed is not toasted again
 * on leaving the screen (review-2026-10 G10).
 */
@OptIn(ExperimentalTestApi::class)
class ErrorSnackbarBridgesUiTest {
    @BeforeTest
    fun setUp() {
        // Tests must never read or write the developer's real settings; the desktopTest task
        // points this property at build/.
        assertNotNull(System.getProperty(DATA_DIR_PROPERTY), "$DATA_DIR_PROPERTY must be set for tests")
    }

    @Test
    fun `an app error present on the first frame is shown`() =
        runComposeUiTest {
            var shown = 0

            // Bridges first, then the host, as in App.kt: the host subscribes one frame later.
            setContent {
                ErrorSnackbarBridges(
                    appError = "boom",
                    onAppErrorShown = { shown++ },
                    containersError = null,
                    onContainersErrorHandled = {},
                    onContainersScreen = false,
                )
                AppSnackbarHost()
            }

            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("boom").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(1, shown)
        }

    @Test
    fun `an error already shown inline is not toasted on leaving Containers`() =
        runComposeUiTest {
            var onContainersScreen by mutableStateOf(true)
            var containersError by mutableStateOf<String?>("inline")
            var handled = 0

            setContent {
                ErrorSnackbarBridges(
                    appError = null,
                    onAppErrorShown = {},
                    containersError = containersError,
                    onContainersErrorHandled = {
                        handled++
                        containersError = null
                    },
                    onContainersScreen = onContainersScreen,
                )
                AppSnackbarHost()
            }
            waitForIdle()
            onAllNodesWithText("inline").assertCountEquals(0)

            onContainersScreen = false
            waitForIdle()

            onAllNodesWithText("inline").assertCountEquals(0)
            assertEquals(1, handled)
        }

    @Test
    fun `an error raised off Containers is toasted`() =
        runComposeUiTest {
            var containersError by mutableStateOf<String?>("elsewhere")
            var handled = 0

            setContent {
                ErrorSnackbarBridges(
                    appError = null,
                    onAppErrorShown = {},
                    containersError = containersError,
                    onContainersErrorHandled = {
                        handled++
                        containersError = null
                    },
                    onContainersScreen = false,
                )
                AppSnackbarHost()
            }

            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("elsewhere").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(1, handled)
        }

    @Test
    fun `an error dismissed inline and raised again off Containers is toasted`() =
        runComposeUiTest {
            var onContainersScreen by mutableStateOf(true)
            var containersError by mutableStateOf<String?>("same-text")

            setContent {
                ErrorSnackbarBridges(
                    appError = null,
                    onAppErrorShown = {},
                    containersError = containersError,
                    onContainersErrorHandled = { containersError = null },
                    onContainersScreen = onContainersScreen,
                )
                AppSnackbarHost()
            }
            waitForIdle() // shown inline

            containersError = null // the card's X
            waitForIdle()

            onContainersScreen = false
            containersError = "same-text"

            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("same-text").fetchSemanticsNodes().isNotEmpty() }
        }
}
