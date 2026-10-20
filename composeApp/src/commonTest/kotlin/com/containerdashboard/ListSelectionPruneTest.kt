package com.containerdashboard

import com.containerdashboard.ui.screens.viewmodel.ImagesScreenViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Checked keys follow the live list (review-2026-10 G9). */
@OptIn(ExperimentalCoroutinesApi::class)
class ListSelectionPruneTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeVm(): ImagesScreenViewModel {
        val fake = FakeDockerRepository()
        return ImagesScreenViewModel(repoProvider = { fake }, repoFlow = MutableStateFlow(fake))
    }

    @Test
    fun `retainChecked drops keys that are gone`() {
        val vm = makeVm()
        vm.checkAll(listOf("aaa", "bbb"))
        vm.retainChecked(setOf("aaa"))
        assertEquals(setOf("aaa"), vm.checkedKeys.value)
    }

    @Test
    fun `retainChecked keeps checked keys that are all still live`() {
        val vm = makeVm()
        vm.checkAll(listOf("aaa", "bbb"))
        vm.retainChecked(setOf("aaa", "bbb", "ccc"))
        assertEquals(setOf("aaa", "bbb"), vm.checkedKeys.value)
    }
}
