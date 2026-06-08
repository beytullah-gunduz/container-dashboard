package com.containerdashboard.terminal

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.containerdashboard.data.repository.DesktopDockerRepository
import com.containerdashboard.di.AppModule
import com.containerdashboard.ui.state.ConsoleSessionRegistry
import com.containerdashboard.ui.theme.AppColors
import com.jediterm.terminal.ui.JediTermWidget
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import javax.swing.JComponent
import javax.swing.JTextArea

private val logger = LoggerFactory.getLogger("com.containerdashboard.terminal.JediTermConsole")

@Composable
fun JediTermConsole(
    containerId: String,
    darkTheme: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var connector by remember(containerId) { mutableStateOf<DockerExecTtyConnector?>(null) }
    var isConnecting by remember(containerId) { mutableStateOf(true) }
    var error by remember(containerId) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun connect() {
        scope.launch {
            isConnecting = true
            error = null
            try {
                val conn =
                    withContext(Dispatchers.IO) {
                        DockerExecTtyConnector(
                            dockerClient = (AppModule.dockerRepository as DesktopDockerRepository).client,
                            containerId = containerId,
                        ).also { it.start() }
                    }
                connector = conn
                ConsoleSessionRegistry.register(containerId)
            } catch (e: Exception) {
                error = e.message ?: "Failed to connect"
            } finally {
                isConnecting = false
            }
        }
    }

    DisposableEffect(containerId) {
        connect()
        onDispose {
            connector?.close()
            connector = null
            ConsoleSessionRegistry.unregister(containerId)
        }
    }

    if (error != null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = error!!, color = AppColors.Stopped)
                Button(onClick = { connect() }) { Text("Retry") }
            }
        }
    } else {
        Box(modifier = modifier.fillMaxSize()) {
            if (connector != null) {
                val conn = connector!!
                SwingPanel(
                    background = Color.Black,
                    modifier = Modifier.fillMaxSize(),
                    factory = {
                        // This runs on the Swing/Compose render thread. An uncaught throwable
                        // here is fatal to the whole window: Compose's default exception handler
                        // shows a bare "Unknown error" dialog and tears the app down. Contain it
                        // and degrade to an inline message so a terminal failure stays contained
                        // to the Console tab.
                        try {
                            createTerminalWidget(conn, darkTheme)
                        } catch (throwable: Throwable) {
                            logger.error("Failed to create terminal widget for container {}", containerId, throwable)
                            terminalErrorComponent(throwable)
                        }
                    },
                )
            }

            // Loading overlay on top of terminal (or black background)
            AnimatedVisibility(
                visible = isConnecting,
                enter = fadeIn(animationSpec = tween(durationMillis = 200)),
                exit = fadeOut(animationSpec = tween(durationMillis = 300)),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = AppColors.AccentBlue,
                            strokeWidth = 3.dp,
                        )
                        Text(
                            text = "Connecting to container...",
                            color = Color.White.copy(alpha = 0.6f),
                        )
                    }
                }
            }
        }
    }
}

private fun createTerminalWidget(
    connector: DockerExecTtyConnector,
    darkTheme: Boolean,
): JComponent {
    val settings =
        object : DefaultSettingsProvider() {
            @Suppress("OVERRIDE_DEPRECATION")
            override fun getDefaultStyle(): com.jediterm.terminal.TextStyle =
                if (darkTheme) {
                    com.jediterm.terminal.TextStyle(
                        com.jediterm.terminal.TerminalColor(com.jediterm.core.Color(0xD4, 0xD4, 0xD4)),
                        com.jediterm.terminal.TerminalColor(com.jediterm.core.Color(0x1A, 0x1A, 0x1A)),
                    )
                } else {
                    com.jediterm.terminal.TextStyle(
                        com.jediterm.terminal.TerminalColor(com.jediterm.core.Color(0x1A, 0x1A, 0x1A)),
                        com.jediterm.terminal.TerminalColor(com.jediterm.core.Color(0xF5, 0xF5, 0xF5)),
                    )
                }

            @Suppress("DEPRECATION")
            override fun useAntialiasing(): Boolean = true

            override fun scrollToBottomOnTyping(): Boolean = true
        }

    val widget = JediTermWidget(settings)
    widget.setTtyConnector(connector)
    widget.start()
    return widget.component
}

/**
 * Fallback shown in the Console tab when the terminal widget can't be created
 * (e.g. a JediTerm initialization failure). Replaces an uncaught render-thread
 * throwable — which would otherwise crash the whole window — with a contained,
 * readable message.
 */
private fun terminalErrorComponent(throwable: Throwable): JComponent {
    val detail =
        throwable.message
            ?: throwable.cause?.message
            ?: throwable::class.java.simpleName
    return JTextArea("Couldn't open the console.\n\n$detail").apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        background = java.awt.Color(0x1A, 0x1A, 0x1A)
        foreground = java.awt.Color(0xE0, 0x6C, 0x6C)
        margin = java.awt.Insets(16, 16, 16, 16)
    }
}
