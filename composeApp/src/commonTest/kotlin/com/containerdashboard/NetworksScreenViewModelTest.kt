package com.containerdashboard

import com.containerdashboard.ui.screens.viewmodel.NetworksScreenViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NetworksScreenViewModelTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeVm(fake: FakeDockerRepository = FakeDockerRepository()) = NetworksScreenViewModel(repoProvider = { fake })

    // -------------------------------------------------------------------------
    // Selection ops
    // -------------------------------------------------------------------------

    @Test
    fun `checkedNetworkIds is empty on construction`() {
        val vm = makeVm()
        assertTrue(vm.checkedNetworkIds.value.isEmpty())
    }

    @Test
    fun `toggleChecked true adds id`() {
        val vm = makeVm()
        vm.toggleChecked("net1", true)
        assertTrue("net1" in vm.checkedNetworkIds.value)
    }

    @Test
    fun `toggleChecked false removes id`() {
        val vm = makeVm()
        vm.toggleChecked("net1", true)
        vm.toggleChecked("net1", false)
        assertFalse("net1" in vm.checkedNetworkIds.value)
    }

    @Test
    fun `toggleChecked accumulates multiple ids`() {
        val vm = makeVm()
        vm.toggleChecked("net1", true)
        vm.toggleChecked("net2", true)
        assertEquals(setOf("net1", "net2"), vm.checkedNetworkIds.value)
    }

    @Test
    fun `checkAll replaces checked set with provided ids`() {
        val vm = makeVm()
        vm.toggleChecked("old", true)
        vm.checkAll(listOf("net1", "net2", "net3"))
        assertEquals(setOf("net1", "net2", "net3"), vm.checkedNetworkIds.value)
    }

    @Test
    fun `uncheckAll removes specified ids from checked set`() {
        val vm = makeVm()
        vm.checkAll(listOf("net1", "net2", "net3"))
        vm.uncheckAll(listOf("net1", "net3"))
        assertEquals(setOf("net2"), vm.checkedNetworkIds.value)
    }

    @Test
    fun `clearChecked empties the set`() {
        val vm = makeVm()
        vm.checkAll(listOf("net1", "net2"))
        vm.clearChecked()
        assertTrue(vm.checkedNetworkIds.value.isEmpty())
    }

    // -------------------------------------------------------------------------
    // deleteSelectedNetworks — success path
    // -------------------------------------------------------------------------

    @Test
    fun `deleteSelectedNetworks success clears checked set`() =
        runTest {
            val fake = FakeDockerRepository(removeNetworkResult = Result.success(Unit))
            val vm = makeVm(fake)
            vm.checkAll(listOf("net1", "net2"))

            vm.deleteSelectedNetworks()
            advanceUntilIdle()

            assertTrue(vm.checkedNetworkIds.value.isEmpty())
        }

    @Test
    fun `deleteSelectedNetworks success leaves isDeletingSelected false`() =
        runTest {
            val fake = FakeDockerRepository(removeNetworkResult = Result.success(Unit))
            val vm = makeVm(fake)
            vm.checkAll(listOf("net1"))

            vm.deleteSelectedNetworks()
            advanceUntilIdle()

            assertFalse(vm.isDeletingSelected.value)
        }

    @Test
    fun `deleteSelectedNetworks success leaves error null`() =
        runTest {
            val fake = FakeDockerRepository(removeNetworkResult = Result.success(Unit))
            val vm = makeVm(fake)
            vm.checkAll(listOf("net1"))

            vm.deleteSelectedNetworks()
            advanceUntilIdle()

            assertNull(vm.error.value)
        }

    // -------------------------------------------------------------------------
    // deleteSelectedNetworks — failure path
    // -------------------------------------------------------------------------

    @Test
    fun `deleteSelectedNetworks failure sets error with exact message`() =
        runTest {
            val fake =
                FakeDockerRepository(
                    removeNetworkResult = Result.failure(RuntimeException("docker error")),
                )
            val vm = makeVm(fake)
            vm.checkAll(listOf("net1", "net2"))

            vm.deleteSelectedNetworks()
            advanceUntilIdle()

            assertEquals("Failed to delete 2 network(s)", vm.error.value)
        }

    @Test
    fun `deleteSelectedNetworks failure still clears checked set`() =
        runTest {
            val fake =
                FakeDockerRepository(
                    removeNetworkResult = Result.failure(RuntimeException("docker error")),
                )
            val vm = makeVm(fake)
            vm.checkAll(listOf("net1", "net2"))

            vm.deleteSelectedNetworks()
            advanceUntilIdle()

            assertTrue(vm.checkedNetworkIds.value.isEmpty())
        }

    @Test
    fun `deleteSelectedNetworks failure leaves isDeletingSelected false`() =
        runTest {
            val fake =
                FakeDockerRepository(
                    removeNetworkResult = Result.failure(RuntimeException("docker error")),
                )
            val vm = makeVm(fake)
            vm.checkAll(listOf("net1"))

            vm.deleteSelectedNetworks()
            advanceUntilIdle()

            assertFalse(vm.isDeletingSelected.value)
        }

    @Test
    fun `deleteSelectedNetworks single failure produces singular message`() =
        runTest {
            val fake =
                FakeDockerRepository(
                    removeNetworkResult = Result.failure(RuntimeException("oops")),
                )
            val vm = makeVm(fake)
            vm.checkAll(listOf("net1"))

            vm.deleteSelectedNetworks()
            advanceUntilIdle()

            assertEquals("Failed to delete 1 network(s)", vm.error.value)
        }

    // -------------------------------------------------------------------------
    // setSearchQuery
    // -------------------------------------------------------------------------

    @Test
    fun `setSearchQuery updates searchQuery`() {
        val vm = makeVm()
        vm.setSearchQuery("bridge")
        assertEquals("bridge", vm.searchQuery.value)
    }

    @Test
    fun `setSearchQuery with empty string clears query`() {
        val vm = makeVm()
        vm.setSearchQuery("bridge")
        vm.setSearchQuery("")
        assertEquals("", vm.searchQuery.value)
    }

    // -------------------------------------------------------------------------
    // setSelectedNetwork
    // -------------------------------------------------------------------------

    @Test
    fun `setSelectedNetwork updates selectedNetworkId`() {
        val vm = makeVm()
        vm.setSelectedNetwork("net-abc")
        assertEquals("net-abc", vm.selectedNetworkId.value)
    }

    @Test
    fun `setSelectedNetwork null clears selection`() {
        val vm = makeVm()
        vm.setSelectedNetwork("net-abc")
        vm.setSelectedNetwork(null)
        assertNull(vm.selectedNetworkId.value)
    }
}
