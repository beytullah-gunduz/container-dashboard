package com.containerdashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberDialogState
import androidx.compose.ui.window.rememberWindowState
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.ConsoleAppender
import com.containerdashboard.data.models.ContainerStats
import com.containerdashboard.data.repository.PreferenceRepository
import com.containerdashboard.di.AppModule
import com.containerdashboard.logging.InMemoryAppender
import com.containerdashboard.ui.navigation.Screen
import com.containerdashboard.ui.theme.ContainerDashboardTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
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

data class TrayStats(
    val runningContainers: Int = 0,
    val totalCpuPercent: Double = 0.0,
    val totalMemoryUsage: Long = 0,
    val totalMemoryLimit: Long = 0,
) {
    val memoryPercent: Double
        get() = if (totalMemoryLimit > 0) (totalMemoryUsage.toDouble() / totalMemoryLimit) * 100.0 else 0.0

    val isAlert: Boolean
        get() = totalCpuPercent >= 90.0 || memoryPercent >= 90.0
}

private fun trayStatsFlow(refreshRateSeconds: Int): Flow<TrayStats> =
    flow {
        while (true) {
            try {
                val repo = AppModule.dockerRepository
                val containers = repo.listContainersOnce(all = true)
                val running = containers.filter { it.isRunning }
                val stats = if (running.isNotEmpty()) {
                    repo.getContainerStatsOnce(running.map { it.id to it.displayName })
                } else {
                    emptyList()
                }
                val totalCpu = stats.sumOf { it.cpuPercent }
                val totalMemUsage = stats.sumOf { it.memoryUsage }
                val totalMemLimit = stats.maxOfOrNull { it.memoryLimit } ?: 0L
                emit(
                    TrayStats(
                        runningContainers = running.size,
                        totalCpuPercent = totalCpu,
                        totalMemoryUsage = totalMemUsage,
                        totalMemoryLimit = totalMemLimit,
                    ),
                )
            } catch (_: Exception) {
                emit(TrayStats())
            }
            delay(refreshRateSeconds * 1000L)
        }
    }.catch { emit(TrayStats()) }

fun main() {
    // Workaround for macOS accessibility crash (NPE in SemanticsOwnerAccessibility.onNodeRemoved)
    System.setProperty("compose.accessibility.enable", "false")

    configureLogging()

    val log = LoggerFactory.getLogger("com.containerdashboard.Main")
    log.info("Container Dashboard starting…")

    application {
        var isWindowVisible by remember { mutableStateOf(true) }
        var pendingRoute by remember { mutableStateOf<String?>(null) }
        var pendingAbout by remember { mutableStateOf(false) }

        DisposableEffect(Unit) {
            onDispose {
                AppModule.dockerRepository.close()
            }
        }

        val windowState =
            rememberWindowState(
                size = DpSize(1400.dp, 900.dp),
                position = WindowPosition(Alignment.Center),
            )

        val refreshRate by PreferenceRepository.trayRefreshRateSeconds()
            .collectAsState(initial = 5)

        var trayStats by remember { mutableStateOf(TrayStats()) }

        LaunchedEffect(refreshRate) {
            trayStatsFlow(refreshRate).collect { trayStats = it }
        }

        val appIcon = remember { AppIconPainter() }
        val alertIcon = remember { AppIconPainter(alertMode = true) }
        val currentIcon = if (trayStats.isAlert) alertIcon else appIcon

        val statsTooltip = buildString {
            append("Container Dashboard")
            append(" — ${trayStats.runningContainers} running")
            append(" | CPU: ${"%.1f".format(trayStats.totalCpuPercent)}%")
            append(" | Mem: ${"%.1f".format(trayStats.memoryPercent)}%")
        }

        Tray(
            icon = currentIcon,
            tooltip = statsTooltip,
            onAction = { isWindowVisible = !isWindowVisible },
            menu = {
                Item(
                    text = if (isWindowVisible) "Hide Dashboard" else "Show Dashboard",
                    onClick = { isWindowVisible = !isWindowVisible },
                )
                Separator()
                Item(
                    text = "\uD83D\uDCE6 ${trayStats.runningContainers} running container${if (trayStats.runningContainers != 1) "s" else ""}",
                    enabled = false,
                    onClick = {},
                )
                Item(
                    text = "\uD83D\uDCBB CPU: ${"%.1f".format(trayStats.totalCpuPercent)}%",
                    enabled = false,
                    onClick = {},
                )
                Item(
                    text = "\uD83D\uDCBE Mem: ${ContainerStats.formatBytes(trayStats.totalMemoryUsage)} / ${ContainerStats.formatBytes(trayStats.totalMemoryLimit)} (${"%.1f".format(trayStats.memoryPercent)}%)",
                    enabled = false,
                    onClick = {},
                )
                Separator()
                Item("Settings…", onClick = {
                    isWindowVisible = true
                    pendingRoute = Screen.Settings.route
                })
                Item("About Container Dashboard", onClick = {
                    isWindowVisible = true
                    pendingAbout = true
                })
                Separator()
                Item("Quit Container Dashboard", onClick = ::exitApplication)
            },
        )

        Window(
            onCloseRequest = { isWindowVisible = false },
            title = "Container Dashboard",
            state = windowState,
            icon = appIcon,
            visible = isWindowVisible,
        ) {
            App(
                navigateToRoute = pendingRoute,
                onNavigated = { pendingRoute = null },
                consoleContent = { containerId, isDark ->
                    com.containerdashboard.terminal.JediTermConsole(
                        containerId = containerId,
                        darkTheme = isDark,
                    )
                },
            )
        }

        if (pendingAbout) {
            DialogWindow(
                onCloseRequest = { pendingAbout = false },
                title = "About Container Dashboard",
                state = rememberDialogState(size = DpSize(360.dp, 200.dp)),
                resizable = false,
                icon = appIcon,
            ) {
                ContainerDashboardTheme(darkTheme = true) {
                    Surface {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "Container Dashboard",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Version 1.0.0",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "A desktop dashboard for managing containers, images, volumes, and networks.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
