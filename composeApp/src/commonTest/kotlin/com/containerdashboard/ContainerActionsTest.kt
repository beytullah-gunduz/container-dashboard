package com.containerdashboard

import com.containerdashboard.data.models.Container
import com.containerdashboard.data.repository.DockerRepository
import com.containerdashboard.ui.screens.viewmodel.ContainersScreenViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Per-container action tracking and group removal (review-2026-10 B10/G12). */
@OptIn(ExperimentalCoroutinesApi::class)
class ContainerActionsTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeContainer(id: String) =
        Container(
            id = id,
            names = listOf("/$id"),
            image = "nginx:latest",
            state = "running",
        )

    @Test
    fun `concurrent actions on different containers are tracked independently`() =
        runTest {
            val fake = FakeDockerRepository(containersFlowOverride = flowOf(listOf(makeContainer("a"), makeContainer("b"))))
            val gates = mapOf("a" to CompletableDeferred<Unit>(), "b" to CompletableDeferred<Unit>())
            val repo: DockerRepository =
                object : DockerRepository by fake {
                    override suspend fun stopContainer(id: String): Result<Unit> {
                        gates.getValue(id).await()
                        return Result.success(Unit)
                    }
                }
            val vm = ContainersScreenViewModel(repoProvider = { repo }, repoFlow = MutableStateFlow(repo))

            vm.stopContainer("a")
            vm.stopContainer("b")
            advanceUntilIdle()
            assertEquals(setOf("a", "b"), vm.actionInProgress.value)

            // The stop path then waits for the list to catch up (up to a virtual 5 s) and 400 ms
            // before it releases the id; advanceUntilIdle covers both.
            gates.getValue("a").complete(Unit)
            advanceUntilIdle()
            assertEquals(setOf("b"), vm.actionInProgress.value)

            gates.getValue("b").complete(Unit)
            advanceUntilIdle()
            assertTrue(vm.actionInProgress.value.isEmpty())
        }

    @Test
    fun `removeContainers reports several failures in one message`() =
        runTest {
            val fake = FakeDockerRepository(removeContainerResult = Result.failure(IllegalStateException("No such container")))
            val vm = ContainersScreenViewModel(repoProvider = { fake }, repoFlow = MutableStateFlow(fake))

            vm.removeContainers(listOf("a", "b"))
            advanceUntilIdle()

            assertEquals(listOf("a", "b"), fake.removedContainerIds)
            assertEquals("Failed to delete 2 container(s)", vm.error.value)
            assertTrue(vm.pendingDeleteIds.value.isEmpty())
        }

    @Test
    fun `removeContainers reports a single failure as its own message`() =
        runTest {
            val fake = FakeDockerRepository(removeContainerResult = Result.failure(IllegalStateException("No such container")))
            val vm = ContainersScreenViewModel(repoProvider = { fake }, repoFlow = MutableStateFlow(fake))

            vm.removeContainers(listOf("a"))
            advanceUntilIdle()

            assertEquals("No such container", vm.error.value)
        }

    @Test
    fun `removeContainers without failures sets no error and marks ids pending until done`() =
        runTest {
            val fake = FakeDockerRepository()
            val vm = ContainersScreenViewModel(repoProvider = { fake }, repoFlow = MutableStateFlow(fake))

            vm.removeContainers(listOf("a", "b"))
            assertEquals(setOf("a", "b"), vm.pendingDeleteIds.value)

            advanceUntilIdle()
            assertNull(vm.error.value)
            assertTrue(vm.pendingDeleteIds.value.isEmpty())
        }
}
