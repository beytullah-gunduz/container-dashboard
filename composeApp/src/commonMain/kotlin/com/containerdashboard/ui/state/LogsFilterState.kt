package com.containerdashboard.ui.state

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Process-lifetime holder for the logs pane filter bar.
 *
 * `filterText` and `selectedService` outlive the `LogsTabContent` composable,
 * so closing/reopening the logs pane (or switching to the console tab and
 * back) preserves the user's filter selection.
 *
 * Backed by [MutableStateFlow] so writes are thread-safe regardless of which
 * thread they happen on; UI consumers observe via `collectAsState()`.
 */
object LogsFilterState {
    val filterText = MutableStateFlow("")
    val selectedService = MutableStateFlow<String?>(null)
}
