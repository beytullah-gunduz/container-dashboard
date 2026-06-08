package com.containerdashboard

import com.containerdashboard.data.models.ContainerStats
import com.containerdashboard.ui.screens.viewmodel.MonitoringScreenViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Regression test for the Monitoring loading state: `derivedStats` must be seeded `null`
 * ("no snapshot yet") so the screen shows a spinner during the initial load, rather than seeded
 * `emptyList()`, which is indistinguishable from "no running containers" and flashed that message
 * on first open. Becomes a non-null list (possibly empty) only once a stats snapshot is delivered.
 *
 * The stats pipeline samples on a timer, so this drives virtual time rather than `advanceUntilIdle`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MonitoringLoadingStateTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun stats(id: String) =
        ContainerStats(containerId = id, containerName = id, cpuPercent = 1.0, memoryUsage = 1L, memoryLimit = 100L)

    @Test
    fun `derivedStats is null until the first snapshot, then reflects streamed stats`() =
        runTest {
            val statsSource = MutableSharedFlow<List<ContainerStats>>(replay = 1)
            val fake = FakeDockerRepository(containerStatsFlowOverride = statsSource)
            val vm = MonitoringScreenViewModel(repoProvider = { fake })
            vm.setRefreshRate(0.05f) // 50ms sample window keeps the test fast

            assertNull(vm.derivedStats.value, "seed must be null = no snapshot yet")

            val job = launch { vm.derivedStats.collect {} }
            advanceTimeBy(300)
            runCurrent()
            assertNull(vm.derivedStats.value, "stays null while no stats have streamed")

            statsSource.tryEmit(listOf(stats("c1"), stats("c2")))
            advanceTimeBy(300)
            runCurrent()
            assertEquals(2, vm.derivedStats.value?.size, "becomes a non-null list once a snapshot arrives")

            job.cancel()
        }
}
