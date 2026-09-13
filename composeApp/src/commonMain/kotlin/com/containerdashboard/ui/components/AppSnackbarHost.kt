package com.containerdashboard.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.containerdashboard.ui.state.UiMessages
import kotlinx.coroutines.flow.collectLatest

/**
 * Renders [UiMessages] as Material snackbars (UX audit U1.2). A new message
 * replaces the one currently showing instead of queueing behind it, so a
 * burst of failures always ends on the latest one. Errors use the error
 * container colours and stay longer.
 */
@Composable
fun AppSnackbarHost(modifier: Modifier = Modifier) {
    val hostState = remember { SnackbarHostState() }
    LaunchedEffect(hostState) {
        // collectLatest cancels the in-flight showSnackbar (which releases the
        // host's mutex and clears currentSnackbarData) before showing the next
        // message, so a burst never queues — the latest message always wins.
        UiMessages.events.collectLatest { message ->
            hostState.showSnackbar(
                UiSnackbarVisuals(
                    message = message.text,
                    isError = message.isError,
                ),
            )
        }
    }
    SnackbarHost(hostState = hostState, modifier = modifier) { data ->
        val isError = (data.visuals as? UiSnackbarVisuals)?.isError == true
        if (isError) {
            Snackbar(
                snackbarData = data,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                dismissActionContentColor = MaterialTheme.colorScheme.onErrorContainer,
            )
        } else {
            Snackbar(snackbarData = data)
        }
    }
}

private data class UiSnackbarVisuals(
    override val message: String,
    val isError: Boolean,
) : SnackbarVisuals {
    override val actionLabel: String? = null
    override val withDismissAction: Boolean = true
    override val duration: SnackbarDuration =
        if (isError) SnackbarDuration.Long else SnackbarDuration.Short
}
