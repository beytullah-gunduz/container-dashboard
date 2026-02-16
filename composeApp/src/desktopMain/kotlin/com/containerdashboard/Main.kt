package com.containerdashboard

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.ConsoleAppender
import com.containerdashboard.data.DockerClientRepository
import com.containerdashboard.di.AppModule
import com.containerdashboard.logging.InMemoryAppender
import org.slf4j.LoggerFactory

/**
 * Configures Logback programmatically so we don't depend on logback.xml
 * being discovered on the classpath (which can be unreliable in
 * Compose Multiplatform Desktop packaging).
 */
private fun configureLogging() {
    val loggerContext = LoggerFactory.getILoggerFactory() as LoggerContext
    loggerContext.reset()

    // ── Console appender ────────────────────────────────────
    val consoleEncoder =
        PatternLayoutEncoder().apply {
            context = loggerContext
            pattern = "%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%thread] %-36.36logger{36} - %msg%n%throwable"
            start()
        }
    val consoleAppender =
        ConsoleAppender<ILoggingEvent>().apply {
            context = loggerContext
            name = "CONSOLE"
            encoder = consoleEncoder
            start()
        }

    // ── In-Memory appender ──────────────────────────────────
    val memoryEncoder =
        PatternLayoutEncoder().apply {
            context = loggerContext
            pattern = "%d{HH:mm:ss.SSS} %-5level [%thread] %logger{24} - %msg%throwable"
            start()
        }
    val inMemoryAppender =
        InMemoryAppender().apply {
            context = loggerContext
            name = "IN_MEMORY"
            maxEntries = 100
            encoder = memoryEncoder
            start()
        }

    // ── Root logger ─────────────────────────────────────────
    loggerContext.getLogger(Logger.ROOT_LOGGER_NAME).apply {
        level = Level.INFO
        addAppender(consoleAppender)
        addAppender(inMemoryAppender)
    }

    // ── Application logger at DEBUG ─────────────────────────
    loggerContext.getLogger("com.containerdashboard").level = Level.DEBUG

    // ── Noisy libraries at WARN ─────────────────────────────
    loggerContext.getLogger("com.github.dockerjava").level = Level.WARN
    loggerContext.getLogger("org.apache.hc").level = Level.WARN
}

fun main() {
    // Configure logging BEFORE anything else
    configureLogging()

    val log = LoggerFactory.getLogger("com.containerdashboard.Main")
    log.info("Container Dashboard starting…")

    application {
        // Initialize the Docker repository
        val dockerRepository =
            remember {
                DockerClientRepository("unix:///var/run/docker.sock")
            }

        DisposableEffect(Unit) {
            AppModule.initialize(dockerRepository)
            onDispose {
                dockerRepository.close()
            }
        }

        val windowState =
            rememberWindowState(
                size = DpSize(1400.dp, 900.dp),
                position = WindowPosition(Alignment.Center),
            )

        // Create the app icon
        val appIcon = remember { AppIconPainter() }

        Window(
            onCloseRequest = ::exitApplication,
            title = "Container Dashboard",
            state = windowState,
            icon = appIcon,
        ) {
            App()
        }
    }
}
