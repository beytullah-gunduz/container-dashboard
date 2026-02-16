package com.containerdashboard.logging

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Represents a single application log entry.
 */
data class AppLogEntry(
    val timestamp: Long,
    val level: String,
    val loggerName: String,
    val message: String,
    val formattedMessage: String,
    val threadName: String,
    val throwable: String? = null,
) {
    /** Short logger name (last segment after the last dot). */
    val shortLoggerName: String
        get() = loggerName.substringAfterLast('.')
}

/**
 * A platform-independent log store that holds the most recent log entries
 * and exposes them as a [StateFlow] for reactive observation from the UI layer.
 *
 * The desktop [InMemoryAppender] pushes entries here so that common-main screens
 * can display them without depending on Logback directly.
 */
object AppLogStore {
    /** Maximum number of entries to keep in memory. */
    var maxEntries: Int = 100

    private val _entries = MutableStateFlow<List<AppLogEntry>>(emptyList())

    /** A [StateFlow] that emits the current list of log entries whenever a new entry arrives. */
    val entries: StateFlow<List<AppLogEntry>> = _entries.asStateFlow()

    /** Appends a new log entry, evicting the oldest if the store is full. */
    fun addEntry(entry: AppLogEntry) {
        val updated = (_entries.value + entry).takeLast(maxEntries)
        _entries.value = updated
    }

    /** Clears all stored log entries. */
    fun clear() {
        _entries.value = emptyList()
    }
}
