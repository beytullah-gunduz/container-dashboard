package com.containerdashboard

import com.containerdashboard.ui.screens.viewmodel.ImageSortColumn
import com.containerdashboard.ui.screens.viewmodel.ImagesScreenViewModel
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ImagesScreenViewModelTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeVm(fake: FakeDockerRepository = FakeDockerRepository()) = ImagesScreenViewModel(repoProvider = { fake })

    // -------------------------------------------------------------------------
    // Selection ops
    // -------------------------------------------------------------------------

    @Test
    fun `checkedImageIds is empty on construction`() {
        val vm = makeVm()
        assertTrue(vm.checkedImageIds.value.isEmpty())
    }

    @Test
    fun `toggleChecked true adds id`() {
        val vm = makeVm()
        vm.toggleChecked("img1", true)
        assertTrue("img1" in vm.checkedImageIds.value)
    }

    @Test
    fun `toggleChecked false removes id`() {
        val vm = makeVm()
        vm.toggleChecked("img1", true)
        vm.toggleChecked("img1", false)
        assertFalse("img1" in vm.checkedImageIds.value)
    }

    @Test
    fun `toggleChecked accumulates multiple ids`() {
        val vm = makeVm()
        vm.toggleChecked("img1", true)
        vm.toggleChecked("img2", true)
        assertEquals(setOf("img1", "img2"), vm.checkedImageIds.value)
    }

    @Test
    fun `checkAll replaces checked set with provided ids`() {
        val vm = makeVm()
        vm.toggleChecked("old", true)
        vm.checkAll(listOf("img1", "img2", "img3"))
        assertEquals(setOf("img1", "img2", "img3"), vm.checkedImageIds.value)
    }

    @Test
    fun `uncheckAll removes specified ids from checked set`() {
        val vm = makeVm()
        vm.checkAll(listOf("img1", "img2", "img3"))
        vm.uncheckAll(listOf("img1", "img3"))
        assertEquals(setOf("img2"), vm.checkedImageIds.value)
    }

    @Test
    fun `clearChecked empties the set`() {
        val vm = makeVm()
        vm.checkAll(listOf("img1", "img2"))
        vm.clearChecked()
        assertTrue(vm.checkedImageIds.value.isEmpty())
    }

    // -------------------------------------------------------------------------
    // deleteSelectedImages — success path
    // -------------------------------------------------------------------------

    @Test
    fun `deleteSelectedImages success clears checked set`() =
        runTest {
            val fake = FakeDockerRepository(removeImageResult = Result.success(Unit))
            val vm = makeVm(fake)
            vm.checkAll(listOf("img1", "img2"))

            vm.deleteSelectedImages()
            advanceUntilIdle()

            assertTrue(vm.checkedImageIds.value.isEmpty())
        }

    @Test
    fun `deleteSelectedImages success leaves isDeletingSelected false`() =
        runTest {
            val fake = FakeDockerRepository(removeImageResult = Result.success(Unit))
            val vm = makeVm(fake)
            vm.checkAll(listOf("img1"))

            vm.deleteSelectedImages()
            advanceUntilIdle()

            assertFalse(vm.isDeletingSelected.value)
        }

    @Test
    fun `deleteSelectedImages success leaves error null`() =
        runTest {
            val fake = FakeDockerRepository(removeImageResult = Result.success(Unit))
            val vm = makeVm(fake)
            vm.checkAll(listOf("img1"))

            vm.deleteSelectedImages()
            advanceUntilIdle()

            assertNull(vm.error.value)
        }

    // -------------------------------------------------------------------------
    // deleteSelectedImages — failure path
    // -------------------------------------------------------------------------

    @Test
    fun `deleteSelectedImages failure sets error with exact message`() =
        runTest {
            val fake =
                FakeDockerRepository(
                    removeImageResult = Result.failure(RuntimeException("docker error")),
                )
            val vm = makeVm(fake)
            vm.checkAll(listOf("img1", "img2"))

            vm.deleteSelectedImages()
            advanceUntilIdle()

            assertEquals("Failed to delete 2 image(s)", vm.error.value)
        }

    @Test
    fun `deleteSelectedImages failure still clears checked set`() =
        runTest {
            val fake =
                FakeDockerRepository(
                    removeImageResult = Result.failure(RuntimeException("docker error")),
                )
            val vm = makeVm(fake)
            vm.checkAll(listOf("img1", "img2"))

            vm.deleteSelectedImages()
            advanceUntilIdle()

            assertTrue(vm.checkedImageIds.value.isEmpty())
        }

    @Test
    fun `deleteSelectedImages failure leaves isDeletingSelected false`() =
        runTest {
            val fake =
                FakeDockerRepository(
                    removeImageResult = Result.failure(RuntimeException("docker error")),
                )
            val vm = makeVm(fake)
            vm.checkAll(listOf("img1"))

            vm.deleteSelectedImages()
            advanceUntilIdle()

            assertFalse(vm.isDeletingSelected.value)
        }

    @Test
    fun `deleteSelectedImages single failure produces singular message`() =
        runTest {
            val fake =
                FakeDockerRepository(
                    removeImageResult = Result.failure(RuntimeException("oops")),
                )
            val vm = makeVm(fake)
            vm.checkAll(listOf("img1"))

            vm.deleteSelectedImages()
            advanceUntilIdle()

            assertEquals("Failed to delete 1 image(s)", vm.error.value)
        }

    // -------------------------------------------------------------------------
    // setSearchQuery
    // -------------------------------------------------------------------------

    @Test
    fun `setSearchQuery updates searchQuery`() {
        val vm = makeVm()
        vm.setSearchQuery("alpine")
        assertEquals("alpine", vm.searchQuery.value)
    }

    @Test
    fun `setSearchQuery with empty string clears query`() {
        val vm = makeVm()
        vm.setSearchQuery("alpine")
        vm.setSearchQuery("")
        assertEquals("", vm.searchQuery.value)
    }

    // -------------------------------------------------------------------------
    // setSelectedImage
    // -------------------------------------------------------------------------

    @Test
    fun `setSelectedImage updates selectedImageId`() {
        val vm = makeVm()
        vm.setSelectedImage("sha256:abc123")
        assertEquals("sha256:abc123", vm.selectedImageId.value)
    }

    @Test
    fun `setSelectedImage null clears selection`() {
        val vm = makeVm()
        vm.setSelectedImage("sha256:abc123")
        vm.setSelectedImage(null)
        assertNull(vm.selectedImageId.value)
    }

    // -------------------------------------------------------------------------
    // toggleSort
    // -------------------------------------------------------------------------

    @Test
    fun `toggleSort new column sets that column with ASC`() {
        val vm = makeVm()
        vm.toggleSort(ImageSortColumn.SIZE)
        assertEquals(ImageSortColumn.SIZE, vm.sortColumn.value)
        assertEquals(SortDirection.ASC, vm.sortDirection.value)
    }

    @Test
    fun `toggleSort same column flips ASC to DESC`() {
        val vm = makeVm()
        // Default column is REPOSITORY with ASC
        vm.toggleSort(ImageSortColumn.REPOSITORY)
        assertEquals(ImageSortColumn.REPOSITORY, vm.sortColumn.value)
        assertEquals(SortDirection.DESC, vm.sortDirection.value)
    }

    @Test
    fun `toggleSort same column flips DESC back to ASC`() {
        val vm = makeVm()
        vm.toggleSort(ImageSortColumn.REPOSITORY) // ASC → DESC
        vm.toggleSort(ImageSortColumn.REPOSITORY) // DESC → ASC
        assertEquals(SortDirection.ASC, vm.sortDirection.value)
    }

    @Test
    fun `toggleSort different column resets direction to ASC`() {
        val vm = makeVm()
        vm.toggleSort(ImageSortColumn.REPOSITORY) // ASC → DESC
        vm.toggleSort(ImageSortColumn.TAG) // different column → ASC
        assertEquals(ImageSortColumn.TAG, vm.sortColumn.value)
        assertEquals(SortDirection.ASC, vm.sortDirection.value)
    }

    @Test
    fun `default sortColumn is REPOSITORY`() {
        val vm = makeVm()
        assertEquals(ImageSortColumn.REPOSITORY, vm.sortColumn.value)
    }

    @Test
    fun `default sortDirection is ASC`() {
        val vm = makeVm()
        assertEquals(SortDirection.ASC, vm.sortDirection.value)
    }
}
