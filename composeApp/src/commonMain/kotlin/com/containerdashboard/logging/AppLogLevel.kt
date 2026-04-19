package com.containerdashboard.logging

/**
 * Application-log severity levels surfaced in the `AppLogsScreen` filter chips.
 *
 * The entries here are the single source of truth for what the UI offers as a
 * filter — adding a new level means adding one entry here and the chip list
 * plus color mapping follow automatically. `TRACE` is intentionally omitted
 * because it is not routed into `AppLogStore` by the current appender
 * configuration; if that changes, add the entry and the chip appears.
 */
enum class AppLogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
    ;

    companion object {
        /** Parse a raw logger-emitted level string (case-insensitive), or null if unknown. */
        fun fromString(raw: String): AppLogLevel? = entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
    }
}
