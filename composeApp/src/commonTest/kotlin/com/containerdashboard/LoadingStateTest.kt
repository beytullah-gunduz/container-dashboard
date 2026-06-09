package com.containerdashboard

import com.containerdashboard.data.models.Container
import com.containerdashboard.data.models.DockerImage
import com.containerdashboard.ui.screens.viewmodel.AppViewModel
import com.containerdashboard.ui.screens.viewmodel.ContainersScreenViewModel
import com.containerdashboard.ui.screens.viewmodel.DashboardScreenViewModel
import com.containerdashboard.ui.screens.viewmodel.EngineConnectionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
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

/**
 * Regression tests for the two startup loading-state fixes:
 *  - `hasLoaded` must stay `false` until the repository delivers a real list (not flip true on the
 *    StateFlow seed, which caused a spurious "No containers" flash).
 *  - `connectionState` must seed `CHECKING` and only become `CONNECTED`/`UNAVAILABLE` once an
 *    availability result actually arrives (not seed a hard `UNAVAILABLE`).
 *
 * `WhileSubscribed` flows only run their upstream while collected, so each test launches a
 * throwaway collector to activate them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoadingStateTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun container(id: String = "c1") = Container(id = id, names = listOf("/$id"), image = "nginx:latest", state = "running")

    // -------------------------------------------------------------------------
    // hasLoaded
    // -------------------------------------------------------------------------

    @Test
    fun `Containers hasLoaded stays false until the first list is delivered`() =
        runTest {
            val source = MutableSharedFlow<List<Container>>(replay = 1)
            val fake = FakeDockerRepository(containersFlowOverride = source)
            val vm = ContainersScreenViewModel(repoProvider = { fake })

            assertEquals(false, vm.hasLoaded.value, "seed must be false")

            val job = launch { vm.hasLoaded.collect {} }
            advanceUntilIdle()
            assertEquals(false, vm.hasLoaded.value, "must stay false while the repo has not emitted")

            source.tryEmit(listOf(container()))
            advanceUntilIdle()
            assertEquals(true, vm.hasLoaded.value, "the first delivered list flips it true")

            job.cancel()
        }

    @Test
    fun `Dashboard hasLoaded treats an empty delivered list as loaded`() =
        runTest {
            val source = MutableSharedFlow<List<Container>>(replay = 1)
            val fake = FakeDockerRepository(containersFlowOverride = source)
            val vm = DashboardScreenViewModel(repoProvider = { fake }, repoFlow = MutableStateFlow(fake))

            assertEquals(false, vm.hasLoaded.value)

            val job = launch { vm.hasLoaded.collect {} }
            advanceUntilIdle()
            assertEquals(false, vm.hasLoaded.value)

            source.tryEmit(emptyList()) // empty, but actually delivered → loaded
            advanceUntilIdle()
            assertEquals(true, vm.hasLoaded.value, "an empty-but-delivered list still means loaded")

            job.cancel()
        }

    @Test
    fun `Dashboard per-card loaded flags resolve independently`() =
        runTest {
            // Containers and images are fetched by separate flows; the images card's loading flag
            // must not flip just because containers arrived (which flashed a premature "0 Images").
            val containersSource = MutableSharedFlow<List<Container>>(replay = 1)
            val imagesSource = MutableSharedFlow<List<DockerImage>>(replay = 1)
            val fake =
                FakeDockerRepository(
                    containersFlowOverride = containersSource,
                    imagesFlowOverride = imagesSource,
                )
            val vm = DashboardScreenViewModel(repoProvider = { fake }, repoFlow = MutableStateFlow(fake))

            val jobs =
                listOf(
                    launch { vm.hasLoaded.collect {} },
                    launch { vm.imagesLoaded.collect {} },
                )
            advanceUntilIdle()
            assertEquals(false, vm.hasLoaded.value)
            assertEquals(false, vm.imagesLoaded.value)

            containersSource.tryEmit(listOf(container()))
            advanceUntilIdle()
            assertEquals(true, vm.hasLoaded.value, "containers loaded")
            assertEquals(false, vm.imagesLoaded.value, "images card stays in skeleton until images arrive")

            imagesSource.tryEmit(emptyList())
            advanceUntilIdle()
            assertEquals(true, vm.imagesLoaded.value, "images loaded once delivered")

            jobs.forEach { it.cancel() }
        }

    // -------------------------------------------------------------------------
    // connectionState
    // -------------------------------------------------------------------------

    @Test
    fun `App connectionState is CHECKING until availability resolves, then maps it`() =
        runTest {
            val availability = MutableSharedFlow<Boolean>(replay = 1)
            val fake = FakeDockerRepository(availabilityFlowOverride = availability)
            val vm = AppViewModel(repoProvider = { fake }, repoFlow = MutableStateFlow(fake))

            assertEquals(EngineConnectionState.CHECKING, vm.connectionState.value, "seed must be CHECKING")

            val job = launch { vm.connectionState.collect {} }
            advanceUntilIdle()
            assertEquals(
                EngineConnectionState.CHECKING,
                vm.connectionState.value,
                "stays CHECKING before the first poll result",
            )

            availability.tryEmit(true)
            advanceUntilIdle()
            assertEquals(EngineConnectionState.CONNECTED, vm.connectionState.value)

            availability.tryEmit(false)
            advanceUntilIdle()
            assertEquals(EngineConnectionState.UNAVAILABLE, vm.connectionState.value)

            job.cancel()
        }

    @Test
    fun `Dashboard connectionState maps availability the same way`() =
        runTest {
            val availability = MutableSharedFlow<Boolean>(replay = 1)
            val fake = FakeDockerRepository(availabilityFlowOverride = availability)
            val vm = DashboardScreenViewModel(repoProvider = { fake }, repoFlow = MutableStateFlow(fake))

            assertEquals(EngineConnectionState.CHECKING, vm.connectionState.value)

            val job = launch { vm.connectionState.collect {} }
            advanceUntilIdle()
            assertEquals(EngineConnectionState.CHECKING, vm.connectionState.value)

            availability.tryEmit(true)
            advanceUntilIdle()
            assertEquals(EngineConnectionState.CONNECTED, vm.connectionState.value)

            job.cancel()
        }
}
