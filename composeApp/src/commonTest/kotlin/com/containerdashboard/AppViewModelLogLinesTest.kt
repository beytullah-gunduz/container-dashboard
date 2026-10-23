package com.containerdashboard

import com.containerdashboard.data.models.Container
import com.containerdashboard.ui.screens.viewmodel.AppViewModel
import com.containerdashboard.ui.screens.viewmodel.logTextLines
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

/** One-shot log fetches keep blank lines, like the follow view does (review-2026-10 B7). */
@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelLogLinesTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `logTextLines keeps blank lines and drops only the piece after the final newline`() {
        assertEquals(listOf("a", "", "b"), logTextLines("a\n\nb\n"))
        assertEquals(listOf("a", "", "b"), logTextLines("a\n\nb"))
    }

    @Test
    fun `logTextLines of an empty text is empty`() {
        assertEquals(emptyList(), logTextLines(""))
    }

    @Test
    fun `logTextLines handles CRLF line endings`() {
        assertEquals(listOf("a", "b"), logTextLines("a\r\nb"))
    }

    @Test
    fun `a stopped container's fetched logs keep their blank lines`() =
        runTest {
            val fake = FakeDockerRepository(containerLogs = "a\n\nb\n")
            val vm = AppViewModel(repoProvider = { fake }, repoFlow = MutableStateFlow(fake))
            val stopped = Container(id = "c1", names = listOf("/c1"), image = "nginx:latest", state = "exited")

            vm.showContainerLogs(stopped)
            advanceUntilIdle()

            assertEquals(listOf("a", "", "b"), vm.logsPaneState.value.logs)
        }
}
