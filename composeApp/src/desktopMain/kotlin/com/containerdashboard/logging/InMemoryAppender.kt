package com.containerdashboard.logging

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import ch.qos.logback.core.encoder.Encoder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * A Logback appender that stores the last [maxEntries] log entries in memory.
 *
 * Log entries are stored as formatted strings (using the configured encoder)
 * and also exposed as structured [LogEntry] objects.
 *
 * The entries can be observed reactively via the [entriesFlow] StateFlow, or
 * retrieved as a snapshot via [getEntries].
 */
class InMemoryAppender : AppenderBase<ILoggingEvent>() {

    /** Maximum number of log entries to keep. Configurable from logback.xml. */
    var maxEntries: Int = 100

    /** Encoder set from logback.xml configuration (optional, for formatted output). */
    var encoder: Encoder<ILoggingEvent>? = null

    private val entries = ConcurrentLinkedDeque<LogEntry>()

    private val _entriesFlow = MutableStateFlow<List<LogEntry>>(emptyList())

    override fun start() {
        encoder?.start()
        super.start()
    }

    override fun stop() {
        super.stop()
        encoder?.stop()
    }

    override fun append(event: ILoggingEvent) {
        val formattedMessage = encoder?.let {
            String(it.encode(event)).trimEnd()
        } ?: "${event.formattedMessage}"

        val entry = LogEntry(
            timestamp = event.timeStamp,
            level = event.level.toString(),
            loggerName = event.loggerName,
            message = event.formattedMessage,
            formattedMessage = formattedMessage,
            threadName = event.threadName,
            throwable = event.throwableProxy?.message
        )

        entries.addLast(entry)
        while (entries.size > maxEntries) {
            entries.pollFirst()
        }

        _entriesFlow.value = entries.toList()

        // Push into the platform-independent AppLogStore so commonMain screens can observe
        AppLogStore.addEntry(
            AppLogEntry(
                timestamp = event.timeStamp,
                level = event.level.toString(),
                loggerName = event.loggerName,
                message = event.formattedMessage,
                formattedMessage = formattedMessage,
                threadName = event.threadName,
                throwable = event.throwableProxy?.message
            )
        )
    }

    /** Returns an immutable snapshot of the current log entries. */
    fun getEntries(): List<LogEntry> = entries.toList()

    /** Clears all stored log entries. */
    fun clear() {
        entries.clear()
        _entriesFlow.value = emptyList()
    }

    /** A [StateFlow] that emits the current list of log entries whenever a new entry is appended. */
    val entriesFlow: StateFlow<List<LogEntry>> = _entriesFlow.asStateFlow()

    companion object {
        /** Singleton reference set automatically when the appender starts. */
        @Volatile
        private var instance: InMemoryAppender? = null

        /**
         * Returns the active [InMemoryAppender] instance registered with Logback,
         * or `null` if it hasn't been initialised yet.
         */
        fun getInstance(): InMemoryAppender? = instance
    }

    init {
        instance = this
    }
}

/**
 * Represents a single log entry stored by [InMemoryAppender].
 */
data class LogEntry(
    val timestamp: Long,
    val level: String,
    val loggerName: String,
    val message: String,
    val formattedMessage: String,
    val threadName: String,
    val throwable: String? = null
) {
    /** Human-readable timestamp. */
    val formattedTimestamp: String
        get() = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(timestamp))
}
