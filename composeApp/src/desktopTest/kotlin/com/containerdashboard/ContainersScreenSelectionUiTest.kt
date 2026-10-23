package com.containerdashboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import com.containerdashboard.data.models.Container
import com.containerdashboard.ui.screens.ContainersScreen
import com.containerdashboard.ui.screens.viewmodel.ContainersScreenViewModel
import com.containerdashboard.util.DATA_DIR_PROPERTY
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The Containers screen keeps its selection to containers that exist (review-2026-10 G9): a
 * container removed elsewhere leaves the selection, and leaving and returning to the screen does
 * not clear it (its first frame shows an empty placeholder list).
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class ContainersScreenSelectionUiTest {
    @BeforeTest
    fun setUp() {
        // The screen reads the preferences DataStore (column widths). The desktopTest task points
        // this property at build/; refuse to run against the developer's real settings.
        assertNotNull(System.getProperty(DATA_DIR_PROPERTY), "$DATA_DIR_PROPERTY must be set for tests")
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun container(id: String) = Container(id = id, names = listOf("/$id"), image = "demo", state = "exited")

    @Test
    fun `selection drops removed containers and survives leaving the screen`() =
        runComposeUiTest {
            val list = MutableStateFlow(listOf(container("a"), container("b")))
            val fake = FakeDockerRepository(containersFlowOverride = list)
            val vm = ContainersScreenViewModel(repoProvider = { fake }, repoFlow = MutableStateFlow(fake))
            var shown by mutableStateOf(true)

            setContent { if (shown) ContainersScreen(viewModel = vm) }
            waitForIdle()
            vm.toggleContainerSelection("a", selected = true)
            vm.toggleContainerSelection("b", selected = true)

            list.value = listOf(container("a")) // "b" removed elsewhere
            waitForIdle()
            assertEquals(setOf("a"), vm.selectedContainerIds.value)

            shown = false
            waitForIdle()
            shown = true
            waitForIdle()
            assertEquals(setOf("a"), vm.selectedContainerIds.value, "returning to the screen keeps the selection")
        }
}
