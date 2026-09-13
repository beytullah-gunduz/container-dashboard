package com.containerdashboard

import com.containerdashboard.data.models.Container
import com.containerdashboard.ui.state.LogsPaneState
import com.containerdashboard.ui.state.paneDeleteConfirmation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Texts and policy for the detail-pane delete confirmation (UX audit U1.1).
 */
class PaneDeleteConfirmationTest {
    private fun container(
        id: String,
        name: String,
        project: String? = null,
    ) = Container(
        id = id,
        names = listOf("/$name"),
        image = "example/image:latest",
        state = "running",
        labels = if (project != null) mapOf("com.docker.compose.project" to project) else emptyMap(),
    )

    @Test
    fun `single container names the container and honours the preference`() {
        val state = LogsPaneState(containers = listOf(container("a1", "web-1")))

        val confirmation = paneDeleteConfirmation(state)

        assertEquals("Delete container?", confirmation.title)
        assertEquals("This will force-stop and remove \"web-1\".", confirmation.body)
        assertEquals("Delete", confirmation.confirmLabel)
        assertFalse(confirmation.alwaysConfirm)
    }

    @Test
    fun `group mode names the project and count and always confirms`() {
        val state =
            LogsPaneState(
                containers =
                    listOf(
                        container("a1", "example-stack-web-1", project = "example-stack"),
                        container("b2", "example-stack-db-1", project = "example-stack"),
                        container("c3", "example-stack-cache-1", project = "example-stack"),
                    ),
            )

        val confirmation = paneDeleteConfirmation(state)

        assertEquals("Delete all containers?", confirmation.title)
        assertEquals(
            "This will force-stop and remove all 3 containers in \"example-stack\".",
            confirmation.body,
        )
        assertEquals("Delete all", confirmation.confirmLabel)
        assertTrue(confirmation.alwaysConfirm)
    }

    @Test
    fun `group mode without a project label falls back to the Group display name`() {
        val state =
            LogsPaneState(
                containers = listOf(container("a1", "web-1"), container("b2", "db-1")),
            )

        val confirmation = paneDeleteConfirmation(state)

        assertEquals(
            "This will force-stop and remove all 2 containers in \"Group\".",
            confirmation.body,
        )
        assertTrue(confirmation.alwaysConfirm)
    }
}
