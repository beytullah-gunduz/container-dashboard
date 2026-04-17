package com.containerdashboard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VerticalAlignBottom
import androidx.compose.material.icons.outlined.WrapText
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.containerdashboard.data.repository.PreferenceRepository
import com.containerdashboard.ui.state.ConsoleSessionRegistry
import com.containerdashboard.ui.state.LogsPaneState
import com.containerdashboard.ui.theme.AppColors
import kotlinx.coroutines.launch

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
    val hasLogs = logsState.logs.isNotBlank()
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
                        .padding(horizontal = 12.dp, vertical = 4.dp),
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
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(3.dp),
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
                if (container?.isPaused == true) {
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

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

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
                                        modifier = Modifier
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

@Composable
private fun PaneActionButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
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

@Composable
private fun LogsTabContent(
    state: LogsPaneState,
    onRefresh: () -> Unit,
) {
    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()
    val wordWrap by PreferenceRepository.logsWordWrap().collectAsState(initial = true)
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(state.logs) {
        verticalScrollState.scrollTo(verticalScrollState.maxValue)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = AppColors.AccentBlue,
                        strokeWidth = 3.dp,
                    )
                }
            }
            state.error != null -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = state.error,
                        color = AppColors.Stopped,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = onRefresh) { Text("Retry") }
                }
            }
            state.logs.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No logs available",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            else -> {
                var filterText by remember { mutableStateOf("") }
                var selectedService by remember { mutableStateOf<String?>(null) }
                var serviceDropdownExpanded by remember { mutableStateOf(false) }

                val serviceNames = remember(state.containers) {
                    state.containers.map { it.composeService ?: it.displayName }.distinct()
                }

                val displayedLogs = remember(state.logs, filterText, selectedService) {
                    state.logs.lineSequence()
                        .filter { line ->
                            (selectedService == null || line.startsWith("[$selectedService]")) &&
                                (filterText.isBlank() || line.contains(filterText, ignoreCase = true))
                        }
                        .joinToString("\n")
                }

                LaunchedEffect(selectedService, filterText) {
                    verticalScrollState.scrollTo(verticalScrollState.maxValue)
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    // Filter bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .height(28.dp)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(6.dp),
                            )
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Service dropdown (group mode only)
                        if (state.isGroupMode) {
                            Box {
                                Row(
                                    modifier = Modifier
                                        .clickable { serviceDropdownExpanded = true }
                                        .padding(end = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = selectedService ?: "All",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selectedService != null) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                    )
                                    Icon(
                                        Icons.Filled.ArrowDropDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                DropdownMenu(
                                    expanded = serviceDropdownExpanded,
                                    onDismissRequest = { serviceDropdownExpanded = false },
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "All",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = if (selectedService == null) {
                                                    androidx.compose.ui.text.font.FontWeight.Bold
                                                } else {
                                                    androidx.compose.ui.text.font.FontWeight.Normal
                                                },
                                            )
                                        },
                                        onClick = {
                                            selectedService = null
                                            serviceDropdownExpanded = false
                                        },
                                    )
                                    serviceNames.forEach { name ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    name,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = if (selectedService == name) {
                                                        androidx.compose.ui.text.font.FontWeight.Bold
                                                    } else {
                                                        androidx.compose.ui.text.font.FontWeight.Normal
                                                    },
                                                )
                                            },
                                            onClick = {
                                                selectedService = name
                                                serviceDropdownExpanded = false
                                            },
                                        )
                                    }
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(16.dp)
                                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }

                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        BasicTextField(
                            value = filterText,
                            onValueChange = { filterText = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            decorationBox = { innerTextField ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (filterText.isEmpty()) {
                                        Text(
                                            "Filter logs...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        )
                                    }
                                    innerTextField()
                                }
                            },
                        )
                        if (filterText.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = { filterText = "" },
                                modifier = Modifier.size(18.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Clear,
                                    contentDescription = "Clear filter",
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    SelectionContainer(modifier = Modifier.weight(1f)) {
                        Text(
                            text = displayedLogs,
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .verticalScroll(verticalScrollState)
                                    .then(
                                        if (!wordWrap) {
                                            Modifier.horizontalScroll(horizontalScrollState)
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .padding(12.dp),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            softWrap = wordWrap,
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "${state.logs.lines().size} lines",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            IconButton(
                                onClick = {
                                    scope.launch { PreferenceRepository.setLogsWordWrap(!wordWrap) }
                                },
                                modifier = Modifier.size(20.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.WrapText,
                                    contentDescription = if (wordWrap) "Disable word wrap" else "Enable word wrap",
                                    modifier = Modifier.size(14.dp),
                                    tint = if (wordWrap) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        verticalScrollState.animateScrollTo(verticalScrollState.maxValue)
                                    }
                                },
                                modifier = Modifier.size(20.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.VerticalAlignBottom,
                                    contentDescription = "Scroll to bottom",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (state.isFollowing) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(AppColors.Running, androidx.compose.foundation.shape.CircleShape),
                                )
                                Text(
                                    text = "Live",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AppColors.Running,
                                )
                            }
                        } else {
                            Text(
                                text = "Last 500 lines",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
