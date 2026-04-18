package com.containerdashboard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.containerdashboard.ui.state.ConsoleSessionRegistry
import com.containerdashboard.ui.state.LogsPaneState
import com.containerdashboard.ui.theme.AppColors
import com.containerdashboard.ui.theme.Radius
import com.containerdashboard.ui.theme.Spacing

@Composable
fun ContainerExtraPane(
    logsState: LogsPaneState,
    onRefreshLogs: () -> Unit,
    onClose: () -> Unit,
    onSaveLogs: () -> Unit,
    onPauseContainer: () -> Unit,
    onUnpauseContainer: () -> Unit,
    onRestartContainer: () -> Unit,
    onRemoveContainer: () -> Unit,
    consoleContent: @Composable () -> Unit,
    onConsoleTabSelected: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val container = logsState.container
    var selectedTab by remember(container?.id) { mutableStateOf(0) }
    var consoleEverOpened by remember(container?.id) { mutableStateOf(false) }
    LaunchedEffect(selectedTab) {
        if (selectedTab == 1) consoleEverOpened = true
    }
    val activeConsoleSessions by ConsoleSessionRegistry.activeSessions.collectAsState()
    val isConsoleAlive = container?.id?.let { it in activeConsoleSessions } == true

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Action toolbar: container name + actions + close
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (logsState.displayName.isNotEmpty()) {
                    Text(
                        text = logsState.displayName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (logsState.isGroupMode) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(Radius.xs),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        ) {
                            Text(
                                text = "${logsState.containers.size} services",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                if (logsState.isSavingLogs) {
                    Box(
                        modifier = Modifier.size(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    PaneActionButton(
                        icon = Icons.Outlined.Download,
                        contentDescription = "Save all logs",
                        enabled = container != null,
                        onClick = onSaveLogs,
                    )
                }
                if (logsState.isPauseActionInProgress) {
                    Box(
                        modifier = Modifier.size(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color =
                                if (container?.isPaused == true) {
                                    AppColors.Running
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                        )
                    }
                } else if (container?.isPaused == true) {
                    PaneActionButton(
                        icon = Icons.Outlined.PlayArrow,
                        contentDescription = "Resume container",
                        tint = AppColors.Running,
                        enabled = true,
                        onClick = onUnpauseContainer,
                    )
                } else {
                    PaneActionButton(
                        icon = Icons.Outlined.Pause,
                        contentDescription = "Pause container",
                        enabled = container?.isRunning == true,
                        onClick = onPauseContainer,
                    )
                }
                PaneActionButton(
                    icon = Icons.Outlined.RestartAlt,
                    contentDescription = "Restart container",
                    enabled = container != null,
                    onClick = onRestartContainer,
                )
                PaneActionButton(
                    icon = Icons.Outlined.Delete,
                    contentDescription = "Delete container",
                    tint = MaterialTheme.colorScheme.error,
                    enabled = container != null,
                    onClick = onRemoveContainer,
                )

                Spacer(modifier = Modifier.width(Spacing.sm))

                AppTooltip(label = "Close logs pane", shortcut = "Esc") {
                    IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "Close",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            if (logsState.isGroupMode) {
                // Group mode has no meaningful console target — show logs only.
                LogsTabContent(
                    state = logsState,
                    onRefresh = onRefreshLogs,
                )
            } else {
                // Tabs row
                @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
                SecondaryTabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary,
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.Article,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Logs",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = {
                            selectedTab = 1
                            onConsoleTabSelected()
                        },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box {
                                    Icon(
                                        Icons.Outlined.Terminal,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    if (isConsoleAlive) {
                                        Box(
                                            modifier =
                                                Modifier
                                                    .size(7.dp)
                                                    .align(Alignment.TopEnd)
                                                    .offset(x = 3.dp, y = (-2).dp)
                                                    .clip(CircleShape)
                                                    .background(AppColors.Running),
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Console",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        },
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                Box(modifier = Modifier.fillMaxSize()) {
                    // Console stays composed once first opened so the exec session
                    // survives tab switches. Shrunk to 0 when not active because
                    // SwingPanel can't be reliably hidden via Compose layering.
                    if (consoleEverOpened) {
                        Box(
                            modifier =
                                if (selectedTab == 1) {
                                    Modifier.fillMaxSize()
                                } else {
                                    Modifier.size(0.dp)
                                },
                        ) {
                            consoleContent()
                        }
                    }
                    if (selectedTab == 0) {
                        LogsTabContent(
                            state = logsState,
                            onRefresh = onRefreshLogs,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun PaneActionButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    AppTooltip(label = contentDescription) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(18.dp),
                tint = if (enabled) tint else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            )
        }
    }
}
