package com.containerdashboard

import com.containerdashboard.ui.screens.viewmodel.NetworkSortColumn
import com.containerdashboard.ui.screens.viewmodel.NetworksScreenViewModel
import com.containerdashboard.ui.screens.viewmodel.SortDirection
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
import kotlin.test.assertNotNull
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

    // -------------------------------------------------------------------------
    // toggleSort
    // -------------------------------------------------------------------------

    @Test
    fun `toggleSort new column sets that column with ASC`() {
        val vm = makeVm()
        vm.toggleSort(NetworkSortColumn.DRIVER)
        assertEquals(NetworkSortColumn.DRIVER, vm.sortColumn.value)
        assertEquals(SortDirection.ASC, vm.sortDirection.value)
    }

    @Test
    fun `toggleSort same column flips ASC to DESC`() {
        val vm = makeVm()
        // Default column is NAME with ASC
        vm.toggleSort(NetworkSortColumn.NAME)
        assertEquals(NetworkSortColumn.NAME, vm.sortColumn.value)
        assertEquals(SortDirection.DESC, vm.sortDirection.value)
    }

    @Test
    fun `toggleSort same column flips DESC back to ASC`() {
        val vm = makeVm()
        vm.toggleSort(NetworkSortColumn.NAME) // ASC → DESC
        vm.toggleSort(NetworkSortColumn.NAME) // DESC → ASC
        assertEquals(SortDirection.ASC, vm.sortDirection.value)
    }

    @Test
    fun `toggleSort different column resets direction to ASC`() {
        val vm = makeVm()
        vm.toggleSort(NetworkSortColumn.NAME) // ASC → DESC
        vm.toggleSort(NetworkSortColumn.DRIVER) // different column → ASC
        assertEquals(NetworkSortColumn.DRIVER, vm.sortColumn.value)
        assertEquals(SortDirection.ASC, vm.sortDirection.value)
    }

    @Test
    fun `default sortColumn is NAME`() {
        val vm = makeVm()
        assertEquals(NetworkSortColumn.NAME, vm.sortColumn.value)
    }

    @Test
    fun `default sortDirection is ASC`() {
        val vm = makeVm()
        assertEquals(SortDirection.ASC, vm.sortDirection.value)
    }

    // -------------------------------------------------------------------------
    // createNetwork
    // -------------------------------------------------------------------------

    @Test
    fun `createNetwork success leaves error null`() =
        runTest {
            val fake = FakeDockerRepository()
            val vm = makeVm(fake)

            vm.createNetwork("my-net", "bridge")
            advanceUntilIdle()

            assertNull(vm.error.value)
        }

    @Test
    fun `createNetwork failure sets error`() =
        runTest {
            val fake =
                FakeDockerRepository(
                    createNetworkResult = Result.failure(RuntimeException("create failed")),
                )
            val vm = makeVm(fake)

            vm.createNetwork("bad-net", "bridge")
            advanceUntilIdle()

            assertNotNull(vm.error.value)
            assertEquals("create failed", vm.error.value)
        }

    // -------------------------------------------------------------------------
    // removeNetwork
    // -------------------------------------------------------------------------

    @Test
    fun `removeNetwork success leaves error null`() =
        runTest {
            val fake = FakeDockerRepository(removeNetworkResult = Result.success(Unit))
            val vm = makeVm(fake)

            vm.removeNetwork("net1")
            advanceUntilIdle()

            assertNull(vm.error.value)
        }

    @Test
    fun `removeNetwork failure sets error`() =
        runTest {
            val fake =
                FakeDockerRepository(
                    removeNetworkResult = Result.failure(RuntimeException("remove failed")),
                )
            val vm = makeVm(fake)

            vm.removeNetwork("net1")
            advanceUntilIdle()

            assertNotNull(vm.error.value)
            assertEquals("remove failed", vm.error.value)
        }
}
