package com.containerdashboard

import com.containerdashboard.ui.screens.viewmodel.ContainersScreenViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ContainersSelectionTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeVm() = ContainersScreenViewModel(repoProvider = { FakeDockerRepository() })

    // -------------------------------------------------------------------------
    // Initial state
    // -------------------------------------------------------------------------

    @Test
    fun `selectedContainerIds is empty on construction`() {
        val vm = makeVm()
        assertTrue(vm.selectedContainerIds.value.isEmpty())
    }

    // -------------------------------------------------------------------------
    // toggleContainerSelection
    // -------------------------------------------------------------------------

    @Test
    fun `toggleContainerSelection selected=true adds id`() {
        val vm = makeVm()
        vm.toggleContainerSelection("aaa", selected = true)
        assertTrue("aaa" in vm.selectedContainerIds.value)
    }

    @Test
    fun `toggleContainerSelection selected=false removes id`() {
        val vm = makeVm()
        vm.toggleContainerSelection("aaa", selected = true)
        vm.toggleContainerSelection("aaa", selected = false)
        assertFalse("aaa" in vm.selectedContainerIds.value)
    }

    @Test
    fun `toggleContainerSelection accumulates multiple ids`() {
        val vm = makeVm()
        vm.toggleContainerSelection("aaa", selected = true)
        vm.toggleContainerSelection("bbb", selected = true)
        assertEquals(setOf("aaa", "bbb"), vm.selectedContainerIds.value)
    }

    // -------------------------------------------------------------------------
    // selectAllContainers
    // -------------------------------------------------------------------------

    @Test
    fun `selectAllContainers replaces selection with provided ids`() {
        val vm = makeVm()
        vm.toggleContainerSelection("old", selected = true)
        vm.selectAllContainers(listOf("aaa", "bbb", "ccc"))
        assertEquals(setOf("aaa", "bbb", "ccc"), vm.selectedContainerIds.value)
    }

    @Test
    fun `selectAllContainers with empty list clears selection`() {
        val vm = makeVm()
        vm.toggleContainerSelection("aaa", selected = true)
        vm.selectAllContainers(emptyList())
        assertTrue(vm.selectedContainerIds.value.isEmpty())
    }

    // -------------------------------------------------------------------------
    // clearSelection
    // -------------------------------------------------------------------------

    @Test
    fun `clearSelection empties the set`() {
        val vm = makeVm()
        vm.toggleContainerSelection("aaa", selected = true)
        vm.toggleContainerSelection("bbb", selected = true)
        vm.clearSelection()
        assertTrue(vm.selectedContainerIds.value.isEmpty())
    }

    // -------------------------------------------------------------------------
    // toggleContainerSelection is idempotent for deselect on absent id
    // -------------------------------------------------------------------------

    @Test
    fun `deselecting an id that was never selected leaves set unchanged`() {
        val vm = makeVm()
        vm.toggleContainerSelection("aaa", selected = true)
        vm.toggleContainerSelection("zzz", selected = false) // was never in the set
        assertEquals(setOf("aaa"), vm.selectedContainerIds.value)
    }
}
