package com.containerdashboard

import com.containerdashboard.data.models.Container
import com.containerdashboard.ui.screens.viewmodel.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelErrorTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeContainer(id: String = "abc123") =
        Container(
            id = id,
            names = listOf("/$id"),
            image = "nginx:latest",
            state = "running",
        )

    private fun makeVmWithFake(fake: FakeDockerRepository): AppViewModel =
        AppViewModel(
            repoProvider = { fake },
            repoFlow = MutableStateFlow(fake),
        )

    // -------------------------------------------------------------------------
    // pauseContainer failure → error StateFlow becomes non-null
    // -------------------------------------------------------------------------

    @Test
    fun `pauseLogsContainer failure populates error`() =
        runTest {
            val fake =
                FakeDockerRepository(
                    pauseResult = Result.failure(RuntimeException("pause failed")),
                )
            val vm = makeVmWithFake(fake)

            // Prime the logs pane with a container so the action has an id to work with.
            val container = makeContainer()
            vm.setLogsPaneStateForTest(container)

            vm.pauseLogsContainer()
            advanceUntilIdle()

            assertNotNull(vm.error.value, "error should be non-null after a failed pause")
        }

    // -------------------------------------------------------------------------
    // restartContainer failure → error StateFlow becomes non-null
    // -------------------------------------------------------------------------

    @Test
    fun `restartLogsContainer failure populates error`() =
        runTest {
            val fake =
                FakeDockerRepository(
                    restartResult = Result.failure(RuntimeException("restart failed")),
                )
            val vm = makeVmWithFake(fake)
            val container = makeContainer()
            vm.setLogsPaneStateForTest(container)

            vm.restartLogsContainer()
            advanceUntilIdle()

            assertNotNull(vm.error.value, "error should be non-null after a failed restart")
        }

    // -------------------------------------------------------------------------
    // removeContainer failure → error StateFlow becomes non-null
    // -------------------------------------------------------------------------

    @Test
    fun `removeLogsContainer failure populates error`() =
        runTest {
            val fake =
                FakeDockerRepository(
                    removeContainerResult = Result.failure(RuntimeException("remove failed")),
                )
            val vm = makeVmWithFake(fake)
            val container = makeContainer()
            vm.setLogsPaneStateForTest(container)

            vm.removeLogsContainer()
            advanceUntilIdle()

            assertNotNull(vm.error.value, "error should be non-null after a failed remove")
        }

    // -------------------------------------------------------------------------
    // clearError() resets the error back to null
    // -------------------------------------------------------------------------

    @Test
    fun `clearError resets error to null`() =
        runTest {
            val fake =
                FakeDockerRepository(
                    pauseResult = Result.failure(RuntimeException("pause failed")),
                )
            val vm = makeVmWithFake(fake)
            val container = makeContainer()
            vm.setLogsPaneStateForTest(container)

            vm.pauseLogsContainer()
            advanceUntilIdle()
            assertNotNull(vm.error.value)

            vm.clearError()
            assertNull(vm.error.value, "error should be null after clearError()")
        }
}

/**
 * Reaches into [AppViewModel]'s private [LogsPaneState] just enough to seed
 * the container id for logs-pane actions.  We use [AppViewModel.showContainerLogs]
 * which is the public API for this — it also starts a follow job, but the fake
 * repo's `followContainerLogs` returns an empty flow, so that's fine.
 */
private fun AppViewModel.setLogsPaneStateForTest(container: Container) {
    showContainerLogs(container)
}
