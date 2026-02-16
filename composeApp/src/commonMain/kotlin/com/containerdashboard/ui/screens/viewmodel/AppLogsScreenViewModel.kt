package com.containerdashboard.ui.screens.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.containerdashboard.logging.AppLogEntry
import com.containerdashboard.logging.AppLogStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class AppLogsScreenViewModel : ViewModel() {
    /** The raw log entries from the store. */
    private val allEntries: StateFlow<List<AppLogEntry>> = AppLogStore.entries

    /** Current search / filter query. */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** Currently selected log-level filter (null = show all). */
    private val _levelFilter = MutableStateFlow<String?>(null)
    val levelFilter: StateFlow<String?> = _levelFilter.asStateFlow()

    /** Whether auto-scroll to the latest entry is enabled. */
    private val _autoScroll = MutableStateFlow(true)
    val autoScroll: StateFlow<Boolean> = _autoScroll.asStateFlow()

    /** Filtered log entries exposed to the UI. */
    val entries: StateFlow<List<AppLogEntry>> =
        combine(allEntries, _searchQuery, _levelFilter) { logs, query, level ->
            logs.filter { entry ->
                val matchesLevel = level == null || entry.level == level
                val matchesQuery =
                    query.isBlank() ||
                        entry.message.contains(query, ignoreCase = true) ||
                        entry.loggerName.contains(query, ignoreCase = true) ||
                        entry.threadName.contains(query, ignoreCase = true)
                matchesLevel && matchesQuery
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Total (unfiltered) entry count. */
    val totalCount: StateFlow<Int> =
        allEntries
            .combine(_searchQuery) { logs, _ -> logs.size }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setLevelFilter(level: String?) {
        _levelFilter.value = level
    }

    fun toggleAutoScroll() {
        _autoScroll.value = !_autoScroll.value
    }

    fun clearLogs() {
        AppLogStore.clear()
    }
}
