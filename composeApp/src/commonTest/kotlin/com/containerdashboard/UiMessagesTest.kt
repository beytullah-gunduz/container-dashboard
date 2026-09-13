package com.containerdashboard

import com.containerdashboard.ui.state.UiMessage
import com.containerdashboard.ui.state.UiMessages
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The app-wide feedback bus behind the snackbar host (UX audit U1.2).
 */
class UiMessagesTest {
    @Test
    fun `info and error are delivered in order with their kind`() =
        runTest {
            val received = mutableListOf<UiMessage>()
            val collector =
                launch {
                    UiMessages.events.take(2).toList(received)
                }
            // The bus has no replay: make sure the collector is subscribed first.
            testScheduler.runCurrent()

            UiMessages.info("Stopping web-1…")
            UiMessages.error("Failed to stop web-1")
            collector.join()

            assertEquals(listOf("Stopping web-1…", "Failed to stop web-1"), received.map { it.text })
            assertFalse(received[0].isError)
            assertTrue(received[1].isError)
        }

    @Test
    fun `posting with no collector neither blocks nor throws`() {
        UiMessages.info("nobody is listening")
        UiMessages.error("still nobody")
    }
}
