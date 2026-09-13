package com.containerdashboard

import com.containerdashboard.data.models.Container
import com.containerdashboard.ui.screens.viewmodel.ContainersScreenViewModel
import com.containerdashboard.ui.screens.viewmodel.VolumesScreenViewModel
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Re-entrancy guards on bulk operations (UX audit U1.11): `enabled = false` is
 * presentation only — the desktop accessibility bridge fires `onClick` on disabled
 * controls, so the ViewModel is the real boundary.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReentrancyGuardTest {
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
    fun `deleteSelectedContainers ignores a re-entrant call`() =
        runTest {
            val fake = FakeDockerRepository()
            val vm = ContainersScreenViewModel(repoProvider = { fake }, repoFlow = MutableStateFlow(fake))
            vm.selectAllContainers(listOf("a", "b"))

            vm.deleteSelectedContainers()
            vm.deleteSelectedContainers()
            advanceUntilIdle()

            assertEquals(listOf("a", "b"), fake.removedContainerIds)
        }

    @Test
    fun `deleteSelectedContainers raises isDeletingSelected before the coroutine runs`() =
        runTest {
            val fake = FakeDockerRepository()
            val vm = ContainersScreenViewModel(repoProvider = { fake }, repoFlow = MutableStateFlow(fake))
            vm.selectAllContainers(listOf("a", "b"))

            vm.deleteSelectedContainers()
            assertTrue(vm.isDeletingSelected.value)

            advanceUntilIdle()
            assertFalse(vm.isDeletingSelected.value)
        }

    @Test
    fun `deleteSelectedContainers with an empty selection does nothing`() =
        runTest {
            val fake = FakeDockerRepository()
            val vm = ContainersScreenViewModel(repoProvider = { fake }, repoFlow = MutableStateFlow(fake))

            vm.deleteSelectedContainers()
            advanceUntilIdle()

            assertTrue(fake.removedContainerIds.isEmpty())
            assertFalse(vm.isDeletingSelected.value)
        }

    @Test
    fun `stopSelectedContainers ignores a re-entrant call`() =
        runTest {
            val fake = FakeDockerRepository()
            val vm = ContainersScreenViewModel(repoProvider = { fake }, repoFlow = MutableStateFlow(fake))

            vm.stopSelectedContainers(listOf("a"))
            vm.stopSelectedContainers(listOf("a"))
            advanceUntilIdle()

            assertEquals(listOf("a"), fake.stoppedContainerIds)
        }

    @Test
    fun `deleteAllContainers ignores a re-entrant call`() =
        runTest {
            val fake = FakeDockerRepository()
            val vm = ContainersScreenViewModel(repoProvider = { fake }, repoFlow = MutableStateFlow(fake))
            val list = listOf(makeContainer("a"), makeContainer("b"))

            vm.deleteAllContainers(list)
            vm.deleteAllContainers(list)
            advanceUntilIdle()

            assertEquals(listOf("a", "b"), fake.removedContainerIds)
        }

    @Test
    fun `deleteSelected ignores a re-entrant call`() =
        runTest {
            val fake = FakeDockerRepository()
            val vm = VolumesScreenViewModel(repoProvider = { fake }, repoFlow = MutableStateFlow(fake))
            vm.checkAll(listOf("v1", "v2"))

            vm.deleteSelectedVolumes()
            vm.deleteSelectedVolumes()
            advanceUntilIdle()

            assertEquals(listOf("v1", "v2"), fake.removedVolumeNames)
        }
}
