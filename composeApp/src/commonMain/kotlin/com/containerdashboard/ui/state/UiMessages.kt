package com.containerdashboard.ui.state

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first

/** A transient, app-wide message shown as a snackbar (UX audit U1.2). */
data class UiMessage(
    val text: String,
    val isError: Boolean,
)

/**
 * Process-lifetime bus for transient feedback that has no inline home on the
 * current screen: detail-pane actions, palette actions fired from another
 * screen, file downloads and log export. Rendered by `AppSnackbarHost`.
 *
 * Same singleton pattern as [LogsFilterState]. No replay: a message posted
 * while nothing is collecting is dropped rather than shown late. Safe to call
 * from any thread.
 */
object UiMessages {
    private val _events =
        MutableSharedFlow<UiMessage>(
            extraBufferCapacity = 16,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val events: SharedFlow<UiMessage> = _events.asSharedFlow()

    fun info(text: String) {
        _events.tryEmit(UiMessage(text = text, isError = false))
    }

    fun error(text: String) {
        _events.tryEmit(UiMessage(text = text, isError = true))
    }

    /**
     * Suspends until the snackbar host collects. A message posted before that is dropped (no
     * replay), and the host subscribes one frame after the effects that post (review-2026-10 G10).
     */
    suspend fun awaitCollector() {
        _events.subscriptionCount.first { it > 0 }
    }
}
