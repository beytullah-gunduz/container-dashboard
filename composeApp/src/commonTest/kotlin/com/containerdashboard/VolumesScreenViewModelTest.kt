package com.containerdashboard

import com.containerdashboard.ui.screens.viewmodel.SortDirection
import com.containerdashboard.ui.screens.viewmodel.VolumeSortColumn
import com.containerdashboard.ui.screens.viewmodel.VolumesScreenViewModel
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
class VolumesScreenViewModelTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeVm(fake: FakeDockerRepository = FakeDockerRepository()) = VolumesScreenViewModel(repoProvider = { fake })

    // -------------------------------------------------------------------------
    // Selection ops
    // -------------------------------------------------------------------------

    @Test
    fun `checkedVolumeNames is empty on construction`() {
        val vm = makeVm()
        assertTrue(vm.checkedVolumeNames.value.isEmpty())
    }

    @Test
    fun `toggleChecked true adds name`() {
        val vm = makeVm()
        vm.toggleChecked("vol1", true)
        assertTrue("vol1" in vm.checkedVolumeNames.value)
    }

    @Test
    fun `toggleChecked false removes name`() {
        val vm = makeVm()
        vm.toggleChecked("vol1", true)
        vm.toggleChecked("vol1", false)
        assertFalse("vol1" in vm.checkedVolumeNames.value)
    }

    @Test
    fun `toggleChecked accumulates multiple names`() {
        val vm = makeVm()
        vm.toggleChecked("vol1", true)
        vm.toggleChecked("vol2", true)
        assertEquals(setOf("vol1", "vol2"), vm.checkedVolumeNames.value)
    }

    @Test
    fun `checkAll replaces checked set with provided names`() {
        val vm = makeVm()
        vm.toggleChecked("old", true)
        vm.checkAll(listOf("vol1", "vol2", "vol3"))
        assertEquals(setOf("vol1", "vol2", "vol3"), vm.checkedVolumeNames.value)
    }

    @Test
    fun `uncheckAll removes specified names from checked set`() {
        val vm = makeVm()
        vm.checkAll(listOf("vol1", "vol2", "vol3"))
        vm.uncheckAll(listOf("vol1", "vol3"))
        assertEquals(setOf("vol2"), vm.checkedVolumeNames.value)
    }

    @Test
    fun `clearChecked empties the set`() {
        val vm = makeVm()
        vm.checkAll(listOf("vol1", "vol2"))
        vm.clearChecked()
        assertTrue(vm.checkedVolumeNames.value.isEmpty())
    }

    // -------------------------------------------------------------------------
    // deleteSelectedVolumes — success path
    // -------------------------------------------------------------------------

    @Test
    fun `deleteSelectedVolumes success clears checked set`() =
        runTest {
            val fake = FakeDockerRepository(removeVolumeResult = Result.success(Unit))
            val vm = makeVm(fake)
            vm.checkAll(listOf("vol1", "vol2"))

            vm.deleteSelectedVolumes()
            advanceUntilIdle()

            assertTrue(vm.checkedVolumeNames.value.isEmpty())
        }

    @Test
    fun `deleteSelectedVolumes success leaves isDeletingSelected false`() =
        runTest {
            val fake = FakeDockerRepository(removeVolumeResult = Result.success(Unit))
            val vm = makeVm(fake)
            vm.checkAll(listOf("vol1"))

            vm.deleteSelectedVolumes()
            advanceUntilIdle()

            assertFalse(vm.isDeletingSelected.value)
        }

    @Test
    fun `deleteSelectedVolumes success leaves error null`() =
        runTest {
            val fake = FakeDockerRepository(removeVolumeResult = Result.success(Unit))
            val vm = makeVm(fake)
            vm.checkAll(listOf("vol1"))

            vm.deleteSelectedVolumes()
            advanceUntilIdle()

            assertNull(vm.error.value)
        }

    // -------------------------------------------------------------------------
    // deleteSelectedVolumes — failure path
    // -------------------------------------------------------------------------

    @Test
    fun `deleteSelectedVolumes failure sets error with exact message`() =
        runTest {
            val fake =
                FakeDockerRepository(
                    removeVolumeResult = Result.failure(RuntimeException("docker error")),
                )
            val vm = makeVm(fake)
            vm.checkAll(listOf("vol1", "vol2"))

            vm.deleteSelectedVolumes()
            advanceUntilIdle()

            assertEquals("Failed to delete 2 volume(s)", vm.error.value)
        }

    @Test
    fun `deleteSelectedVolumes failure still clears checked set`() =
        runTest {
            val fake =
                FakeDockerRepository(
                    removeVolumeResult = Result.failure(RuntimeException("docker error")),
                )
            val vm = makeVm(fake)
            vm.checkAll(listOf("vol1", "vol2"))

            vm.deleteSelectedVolumes()
            advanceUntilIdle()

            assertTrue(vm.checkedVolumeNames.value.isEmpty())
        }

    @Test
    fun `deleteSelectedVolumes failure leaves isDeletingSelected false`() =
        runTest {
            val fake =
                FakeDockerRepository(
                    removeVolumeResult = Result.failure(RuntimeException("docker error")),
                )
            val vm = makeVm(fake)
            vm.checkAll(listOf("vol1"))

            vm.deleteSelectedVolumes()
            advanceUntilIdle()

            assertFalse(vm.isDeletingSelected.value)
        }

    @Test
    fun `deleteSelectedVolumes single failure produces singular message`() =
        runTest {
            val fake =
                FakeDockerRepository(
                    removeVolumeResult = Result.failure(RuntimeException("oops")),
                )
            val vm = makeVm(fake)
            vm.checkAll(listOf("vol1"))

            vm.deleteSelectedVolumes()
            advanceUntilIdle()

            assertEquals("Failed to delete 1 volume(s)", vm.error.value)
        }

    // -------------------------------------------------------------------------
    // setSearchQuery
    // -------------------------------------------------------------------------

    @Test
    fun `setSearchQuery updates searchQuery`() {
        val vm = makeVm()
        vm.setSearchQuery("my-data")
        assertEquals("my-data", vm.searchQuery.value)
    }

    @Test
    fun `setSearchQuery with empty string clears query`() {
        val vm = makeVm()
        vm.setSearchQuery("my-data")
        vm.setSearchQuery("")
        assertEquals("", vm.searchQuery.value)
    }

    // -------------------------------------------------------------------------
    // setSelectedVolume
    // -------------------------------------------------------------------------

    @Test
    fun `setSelectedVolume updates selectedVolumeName`() {
        val vm = makeVm()
        vm.setSelectedVolume("my-volume")
        assertEquals("my-volume", vm.selectedVolumeName.value)
    }

    @Test
    fun `setSelectedVolume null clears selection`() {
        val vm = makeVm()
        vm.setSelectedVolume("my-volume")
        vm.setSelectedVolume(null)
        assertNull(vm.selectedVolumeName.value)
    }

    // -------------------------------------------------------------------------
    // toggleSort
    // -------------------------------------------------------------------------

    @Test
    fun `toggleSort new column sets that column with ASC`() {
        val vm = makeVm()
        vm.toggleSort(VolumeSortColumn.DRIVER)
        assertEquals(VolumeSortColumn.DRIVER, vm.sortColumn.value)
        assertEquals(SortDirection.ASC, vm.sortDirection.value)
    }

    @Test
    fun `toggleSort same column flips ASC to DESC`() {
        val vm = makeVm()
        // Default column is NAME with ASC
        vm.toggleSort(VolumeSortColumn.NAME)
        assertEquals(VolumeSortColumn.NAME, vm.sortColumn.value)
        assertEquals(SortDirection.DESC, vm.sortDirection.value)
    }

    @Test
    fun `toggleSort same column flips DESC back to ASC`() {
        val vm = makeVm()
        vm.toggleSort(VolumeSortColumn.NAME) // ASC → DESC
        vm.toggleSort(VolumeSortColumn.NAME) // DESC → ASC
        assertEquals(SortDirection.ASC, vm.sortDirection.value)
    }

    @Test
    fun `toggleSort different column resets direction to ASC`() {
        val vm = makeVm()
        vm.toggleSort(VolumeSortColumn.NAME) // ASC → DESC
        vm.toggleSort(VolumeSortColumn.MOUNTPOINT) // different column → ASC
        assertEquals(VolumeSortColumn.MOUNTPOINT, vm.sortColumn.value)
        assertEquals(SortDirection.ASC, vm.sortDirection.value)
    }

    @Test
    fun `default sortColumn is NAME`() {
        val vm = makeVm()
        assertEquals(VolumeSortColumn.NAME, vm.sortColumn.value)
    }

    @Test
    fun `default sortDirection is ASC`() {
        val vm = makeVm()
        assertEquals(SortDirection.ASC, vm.sortDirection.value)
    }
}
