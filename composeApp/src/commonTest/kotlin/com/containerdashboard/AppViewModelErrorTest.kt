package com.containerdashboard

import com.containerdashboard.data.models.Container
import com.containerdashboard.ui.screens.viewmodel.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
    // A list refresh failure reported by the repository → error StateFlow
    // -------------------------------------------------------------------------

    @Test
    fun `a list refresh failure reaches the app error`() =
        runTest {
            val failures = MutableSharedFlow<String>(extraBufferCapacity = 1)
            val fake = FakeDockerRepository(listRefreshFailuresFlowOverride = failures)
            val vm = makeVmWithFake(fake)
            // The app always observes the connection state (App.kt); without a collector it stays CHECKING.
            val connection = launch { vm.connectionState.collect {} }
            advanceUntilIdle()

            val message = "Couldn't refresh the container list (x); showing the last one received."
            failures.tryEmit(message)
            advanceUntilIdle()

            assertEquals(message, vm.error.value)
            connection.cancel()
        }

    @Test
    fun `a list refresh failure while disconnected is not kept for after the reconnect`() =
        runTest {
            val failures = MutableSharedFlow<String>(extraBufferCapacity = 1)
            val fake =
                FakeDockerRepository(
                    listRefreshFailuresFlowOverride = failures,
                    availabilityFlowOverride = flowOf(false),
                )
            val vm = makeVmWithFake(fake)
            val connection = launch { vm.connectionState.collect {} }
            advanceUntilIdle()

            failures.tryEmit("Couldn't refresh the container list (x); showing the last one received.")
            advanceUntilIdle()

            assertNull(vm.error.value, "the snackbar only exists while connected; a pending message would show up stale")
            connection.cancel()
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
