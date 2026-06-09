package com.containerdashboard

import com.containerdashboard.data.models.Container
import com.containerdashboard.data.models.DockerVersion
import com.containerdashboard.data.repository.DockerRepository
import com.containerdashboard.ui.screens.viewmodel.ActionState
import com.containerdashboard.ui.screens.viewmodel.ConnectionTestState
import com.containerdashboard.ui.screens.viewmodel.SettingsScreenViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Covers the destructive Settings actions (S8.2): `pruneAll` error aggregation,
 * `stopAllContainers` against mixed running/stopped containers, and
 * `testConnection` through the injected repository factory seam.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsScreenViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeVm(
        fake: FakeDockerRepository,
        testRepoFactory: (String) -> DockerRepository = { fake },
    ): SettingsScreenViewModel =
        SettingsScreenViewModel(
            repoProvider = { fake },
            testRepoFactory = testRepoFactory,
        )

    private fun makeContainer(
        id: String,
        state: String,
    ) = Container(
        id = id,
        names = listOf("/$id-name"),
        image = "nginx:latest",
        state = state,
    )

    /** Collects every [ActionState] emitted by [vm] into a list (auto-cancelled by runTest). */
    private fun TestScope.recordActionStates(vm: SettingsScreenViewModel): List<ActionState> {
        val states = mutableListOf<ActionState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.actionState.toList(states)
        }
        return states
    }

    // -------------------------------------------------------------------------
    // pruneAll
    // -------------------------------------------------------------------------

    @Test
    fun `pruneAll full success transitions Idle - InProgress - Success`() =
        runTest {
            val fake = FakeDockerRepository()
            val vm = makeVm(fake)
            val states = recordActionStates(vm)

            vm.pruneAll()
            advanceUntilIdle()

            assertEquals(
                listOf(
                    ActionState.Idle,
                    ActionState.InProgress("Pruning unused resources..."),
                    ActionState.Success("All unused resources pruned"),
                ),
                states,
            )
        }

    @Test
    fun `pruneAll partial failure aggregates only the failed operations in order`() =
        runTest {
            val fake =
                FakeDockerRepository(
                    pruneImagesResult = Result.failure(RuntimeException("img boom")),
                    pruneVolumesResult = Result.failure(RuntimeException("vol boom")),
                )
            val vm = makeVm(fake)

            vm.pruneAll()
            advanceUntilIdle()

            // Successful ops (containers, networks) are absent; failures keep call order.
            assertEquals(
                ActionState.Error("Prune failed: images: img boom; volumes: vol boom"),
                vm.actionState.value,
            )
        }

    @Test
    fun `pruneAll total failure aggregates all four operations`() =
        runTest {
            val fake =
                FakeDockerRepository(
                    pruneContainersResult = Result.failure(RuntimeException("c")),
                    pruneImagesResult = Result.failure(RuntimeException("i")),
                    pruneVolumesResult = Result.failure(RuntimeException("v")),
                    pruneNetworksResult = Result.failure(RuntimeException("n")),
                )
            val vm = makeVm(fake)
            val states = recordActionStates(vm)

            vm.pruneAll()
            advanceUntilIdle()

            assertEquals(
                ActionState.Error("Prune failed: containers: c; images: i; volumes: v; networks: n"),
                vm.actionState.value,
            )
            assertTrue(states.any { it is ActionState.InProgress }, "should pass through InProgress")
        }

    @Test
    fun `dismissActionState resets to Idle`() =
        runTest {
            val fake = FakeDockerRepository(pruneImagesResult = Result.failure(RuntimeException("x")))
            val vm = makeVm(fake)

            vm.pruneAll()
            advanceUntilIdle()
            assertIs<ActionState.Error>(vm.actionState.value)

            vm.dismissActionState()
            assertEquals(ActionState.Idle, vm.actionState.value)
        }

    // -------------------------------------------------------------------------
    // stopAllContainers
    // -------------------------------------------------------------------------

    @Test
    fun `stopAllContainers stops only the running containers`() =
        runTest {
            val fake =
                FakeDockerRepository(
                    containers =
                        listOf(
                            makeContainer("run1", state = "running"),
                            makeContainer("stop1", state = "exited"),
                            makeContainer("run2", state = "running"),
                            makeContainer("pause1", state = "paused"),
                        ),
                )
            val vm = makeVm(fake)

            vm.stopAllContainers()
            advanceUntilIdle()

            assertEquals(listOf("run1", "run2"), fake.stoppedContainerIds)
            assertEquals(ActionState.Success("All containers stopped"), vm.actionState.value)
        }

    @Test
    fun `stopAllContainers partial failure reports the failed container but still stops the rest`() =
        runTest {
            val fake =
                FakeDockerRepository(
                    containers =
                        listOf(
                            makeContainer("run1", state = "running"),
                            makeContainer("run2", state = "running"),
                        ),
                    stopResultsById = mapOf("run1" to Result.failure(RuntimeException("stop boom"))),
                )
            val vm = makeVm(fake)

            vm.stopAllContainers()
            advanceUntilIdle()

            // run2 is still attempted after run1 fails.
            assertEquals(listOf("run1", "run2"), fake.stoppedContainerIds)
            // displayName strips the leading slash from names.
            assertEquals(
                ActionState.Error("Failed to stop: run1-name: stop boom"),
                vm.actionState.value,
            )
        }

    @Test
    fun `stopAllContainers with no running containers reports success without stop calls`() =
        runTest {
            val fake =
                FakeDockerRepository(
                    containers = listOf(makeContainer("stop1", state = "exited")),
                )
            val vm = makeVm(fake)

            vm.stopAllContainers()
            advanceUntilIdle()

            assertTrue(fake.stoppedContainerIds.isEmpty())
            assertEquals(ActionState.Success("All containers stopped"), vm.actionState.value)
        }

    // -------------------------------------------------------------------------
    // testConnection (through the factory seam)
    // -------------------------------------------------------------------------

    @Test
    fun `testConnection success reports the engine version and closes the test repo`() =
        runTest {
            val testRepo = FakeDockerRepository(dockerVersion = DockerVersion(version = "24.0.7"))
            val requestedHosts = mutableListOf<String>()
            val vm =
                makeVm(FakeDockerRepository()) { host ->
                    requestedHosts.add(host)
                    testRepo
                }

            vm.testConnection("unix:///tmp/test.sock")
            advanceUntilIdle()

            assertEquals(listOf("unix:///tmp/test.sock"), requestedHosts)
            assertEquals(
                ConnectionTestState.Success("Connected — Engine v24.0.7"),
                vm.connectionTestResult.value,
            )
            assertEquals(1, testRepo.closeCount, "test repository must be closed after the probe")
        }

    @Test
    fun `testConnection failure surfaces the error and still closes the test repo`() =
        runTest {
            val testRepo =
                FakeDockerRepository(
                    getVersionResult = Result.failure(RuntimeException("no daemon")),
                )
            val vm = makeVm(FakeDockerRepository()) { testRepo }

            vm.testConnection("unix:///tmp/test.sock")
            advanceUntilIdle()

            assertEquals(ConnectionTestState.Error("no daemon"), vm.connectionTestResult.value)
            assertEquals(1, testRepo.closeCount, "test repository must be closed even on failure")
        }

    @Test
    fun `testConnection factory exception is caught and reported`() =
        runTest {
            val vm = makeVm(FakeDockerRepository()) { throw IllegalStateException("bad host") }

            vm.testConnection("garbage")
            advanceUntilIdle()

            assertEquals(ConnectionTestState.Error("bad host"), vm.connectionTestResult.value)
        }

    @Test
    fun `testConnection passes through Testing then dismissTestResult resets to Idle`() =
        runTest {
            val vm = makeVm(FakeDockerRepository())
            val states = mutableListOf<ConnectionTestState>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                vm.connectionTestResult.toList(states)
            }

            vm.testConnection("unix:///tmp/test.sock")
            advanceUntilIdle()

            assertEquals(ConnectionTestState.Idle, states.first())
            assertTrue(states.contains(ConnectionTestState.Testing), "should pass through Testing")
            assertIs<ConnectionTestState.Success>(vm.connectionTestResult.value)

            vm.dismissTestResult()
            assertEquals(ConnectionTestState.Idle, vm.connectionTestResult.value)
        }
}
