package com.containerdashboard.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Process-lifetime holder for the logs pane filter bar.
 *
 * `filterText` and `selectedService` outlive the `LogsTabContent` composable,
 * so closing/reopening the logs pane (or switching to the console tab and
 * back) preserves the user's filter selection.
 */
object LogsFilterState {
    var filterText: String by mutableStateOf("")
    var selectedService: String? by mutableStateOf(null)
}
