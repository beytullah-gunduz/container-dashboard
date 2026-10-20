package com.containerdashboard.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.containerdashboard.ui.state.UiMessages

/**
 * Shows view-model errors that have no inline home as snackbars (UX audit U1.2). An error the
 * Containers screen already showed in its inline card is cleared, not toasted again, when the user
 * leaves that screen (review-2026-10 G10).
 */
@Composable
internal fun ErrorSnackbarBridges(
    appError: String?,
    onAppErrorShown: () -> Unit,
    containersError: String?,
    onContainersErrorHandled: () -> Unit,
    onContainersScreen: Boolean,
) {
    LaunchedEffect(appError) {
        val message = appError ?: return@LaunchedEffect
        UiMessages.awaitCollector()
        UiMessages.error(message)
        onAppErrorShown()
    }
    var shownInline by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(containersError, onContainersScreen) {
        val message = containersError
        if (message == null) {
            shownInline = null // dismissed inline, or already handled: nothing is "already shown"
            return@LaunchedEffect
        }
        if (onContainersScreen) {
            shownInline = message
        } else {
            if (message != shownInline) {
                UiMessages.awaitCollector()
                UiMessages.error(message)
            }
            shownInline = null
            onContainersErrorHandled()
        }
    }
}
