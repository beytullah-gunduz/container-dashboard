package com.containerdashboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.containerdashboard.data.models.Container
import com.containerdashboard.ui.screens.viewmodel.ContainerFilter
import com.containerdashboard.ui.screens.viewmodel.ContainersScreenViewModel
import com.containerdashboard.ui.components.DeleteAllContainersDialog
import com.containerdashboard.ui.components.DeletingAllContainersDialog
import com.containerdashboard.ui.components.SearchBar
import com.containerdashboard.ui.components.StatusBadge
import com.containerdashboard.ui.components.toContainerStatus
import com.containerdashboard.ui.theme.DockerColors

// Threshold for switching between compact and expanded layouts
private val COMPACT_THRESHOLD = 700.dp

@Composable
fun ContainersScreen(
    onShowLogs: (Container) -> Unit = {},
    currentLogsContainerId: String? = null,
    modifier: Modifier = Modifier,
    viewModel: ContainersScreenViewModel = viewModel { ContainersScreenViewModel() }
) {
    var searchQuery by remember { mutableStateOf("") }
    var containerFilter by remember { mutableStateOf(ContainerFilter.ALL) }

    val containers by viewModel.containers.collectAsState(listOf())
    val error by viewModel.error.collectAsState()
    val actionInProgress by viewModel.actionInProgress.collectAsState()
    val selectedContainerIds by viewModel.selectedContainerIds.collectAsState()
    val isDeletingSelected by viewModel.isDeletingSelected.collectAsState()
    val isDeletingAll by viewModel.isDeletingAll.collectAsState()

    // State for delete all confirmation dialog
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    val filteredContainers = remember(containers, searchQuery, containerFilter) {
        containers.filter { container ->
            val matchesSearch = searchQuery.isEmpty() ||
                    container.displayName.contains(searchQuery, ignoreCase = true) ||
                    container.image.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (containerFilter) {
                ContainerFilter.ALL -> true
                ContainerFilter.RUNNING -> container.isRunning
                ContainerFilter.STOPPED -> !container.isRunning
            }
            matchesSearch && matchesFilter
        }
    }

    // Check if all filtered containers are selected
    val allFilteredSelected = filteredContainers.isNotEmpty() &&
        filteredContainers.all { it.id in selectedContainerIds }

    // Check how many selected containers can be deleted (not running)
    val deletableSelectedCount = remember(selectedContainerIds, containers) {
        selectedContainerIds.count { id ->
            containers.find { it.id == id }?.isRunning == false
        }
    }

    // Delete All Confirmation Dialog
    if (showDeleteAllDialog) {
        DeleteAllContainersDialog(
            containerCount = containers.size,
            onConfirm = {
                showDeleteAllDialog = false
                viewModel.deleteAllContainers(containers)
            },
            onDismiss = { showDeleteAllDialog = false }
        )
    }

    // Deleting All Progress Modal
    if (isDeletingAll) {
        DeletingAllContainersDialog()
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isCompactMode = maxWidth < COMPACT_THRESHOLD

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Containers",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${containers.count { it.isRunning }} running of ${containers.size} total",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Delete Selected Button
                    if (selectedContainerIds.isNotEmpty()) {
                        Button(
                            onClick = { viewModel.deleteSelectedContainers() },
                            enabled = deletableSelectedCount > 0 && !isDeletingSelected,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            if (isDeletingSelected) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onError
                                )
                            } else {
                                Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (deletableSelectedCount < selectedContainerIds.size)
                                    "Delete ${deletableSelectedCount} of ${selectedContainerIds.size}"
                                else
                                    "Delete ${selectedContainerIds.size} selected"
                            )
                        }

                        // Clear selection button
                        OutlinedButton(
                            onClick = { viewModel.clearSelection() }
                        ) {
                            Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Clear")
                        }
                    }

                    // Delete All Button
                    if (containers.isNotEmpty()) {
                        Button(
                            onClick = { showDeleteAllDialog = true },
                            enabled = !isDeletingAll && !isDeletingSelected,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            if (isDeletingAll) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onError
                                )
                            } else {
                                Icon(Icons.Default.DeleteForever, null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Delete All")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Error message
            error?.let { errorMessage ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error)
                        Text(errorMessage, color = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.clearError() }) {
                            Icon(Icons.Default.Close, null)
                        }
                    }
                }
            }

            // Filters Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "Search containers...",
                    modifier = Modifier.weight(1f)
                )

                FilterChip(
                    selected = containerFilter == ContainerFilter.ALL,
                    onClick = { containerFilter = ContainerFilter.ALL },
                    label = { Text("All") },
                    leadingIcon = if (containerFilter == ContainerFilter.ALL) {
                        { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                    } else null
                )

                FilterChip(
                    selected = containerFilter == ContainerFilter.RUNNING,
                    onClick = { containerFilter = ContainerFilter.RUNNING },
                    label = { Text("Running") },
                    leadingIcon = if (containerFilter == ContainerFilter.RUNNING) {
                        { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                    } else null
                )

                FilterChip(
                    selected = containerFilter == ContainerFilter.STOPPED,
                    onClick = { containerFilter = ContainerFilter.STOPPED },
                    label = { Text("Stopped") },
                    leadingIcon = if (containerFilter == ContainerFilter.STOPPED) {
                        { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Table Header - changes based on mode
            if (isCompactMode) {
                CompactTableHeader(
                    allSelected = allFilteredSelected,
                    onSelectAllChange = { selectAll ->
                        if (selectAll) {
                            viewModel.selectAllContainers(filteredContainers.map { it.id })
                        } else {
                            viewModel.clearSelection()
                        }
                    },
                    hasItems = filteredContainers.isNotEmpty()
                )
            } else {
                ExpandedTableHeader(
                    allSelected = allFilteredSelected,
                    onSelectAllChange = { selectAll ->
                        if (selectAll) {
                            viewModel.selectAllContainers(filteredContainers.map { it.id })
                        } else {
                            viewModel.clearSelection()
                        }
                    },
                    hasItems = filteredContainers.isNotEmpty()
                )
            }

            if (filteredContainers.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No containers found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Container List
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(filteredContainers, key = { it.id }) { container ->
                        if (isCompactMode) {
                            CompactContainerRow(
                                container = container,
                                isChecked = container.id in selectedContainerIds,
                                isViewingLogs = currentLogsContainerId == container.id,
                                onCheckedChange = { checked ->
                                    viewModel.toggleContainerSelection(container.id, checked)
                                },
                                isActionInProgress = actionInProgress == container.id,
                                onStart = { viewModel.startContainer(container.id) },
                                onStop = { viewModel.stopContainer(container.id) },
                                onPause = { viewModel.pauseContainer(container.id) },
                                onUnpause = { viewModel.unpauseContainer(container.id) },
                                onRemove = { viewModel.removeContainer(container.id) },
                                onViewLogs = { onShowLogs(container) }
                            )
                        } else {
                            ExpandedContainerRow(
                                container = container,
                                isChecked = container.id in selectedContainerIds,
                                isViewingLogs = currentLogsContainerId == container.id,
                                onCheckedChange = { checked ->
                                    viewModel.toggleContainerSelection(container.id, checked)
                                },
                                isActionInProgress = actionInProgress == container.id,
                                onStart = { viewModel.startContainer(container.id) },
                                onStop = { viewModel.stopContainer(container.id) },
                                onPause = { viewModel.pauseContainer(container.id) },
                                onUnpause = { viewModel.unpauseContainer(container.id) },
                                onRemove = { viewModel.removeContainer(container.id) },
                                onViewLogs = { onShowLogs(container) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============== COMPACT MODE COMPONENTS ==============

@Composable
private fun CompactTableHeader(
    allSelected: Boolean,
    onSelectAllChange: (Boolean) -> Unit,
    hasItems: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = allSelected,
            onCheckedChange = onSelectAllChange,
            enabled = hasItems,
            modifier = Modifier.padding(end = 8.dp)
        )

        Text(
            text = "CONTAINER",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = "STATUS",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(100.dp)
        )

        Spacer(modifier = Modifier.width(48.dp))
    }
}

@Composable
private fun CompactContainerRow(
    container: Container,
    isChecked: Boolean,
    isViewingLogs: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isActionInProgress: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onPause: () -> Unit,
    onUnpause: () -> Unit,
    onRemove: () -> Unit,
    onViewLogs: () -> Unit
) {
    var showActionsMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    isViewingLogs -> DockerColors.DockerBlue.copy(alpha = 0.15f)
                    isChecked -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                    else -> MaterialTheme.colorScheme.surface
                }
            )
            .clickable { onViewLogs() }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.padding(end = 8.dp)
        )

        // Container info (Name + Image below)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = container.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = container.image,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = container.shortId,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontFamily = FontFamily.Monospace
                )
            }
            container.ports.firstOrNull()?.displayString?.let { port ->
                Text(
                    text = port,
                    style = MaterialTheme.typography.labelSmall,
                    color = DockerColors.DockerBlue,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Box(modifier = Modifier.width(100.dp)) {
            StatusBadge(status = container.state.toContainerStatus())
        }

        // Actions Menu
        Box {
            if (isActionInProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp).padding(4.dp),
                    strokeWidth = 2.dp
                )
            } else {
                IconButton(
                    onClick = { showActionsMenu = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Actions",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DropdownMenu(
                    expanded = showActionsMenu,
                    onDismissRequest = { showActionsMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("View Logs") },
                        onClick = {
                            showActionsMenu = false
                            onViewLogs()
                        },
                        leadingIcon = {
                            Icon(
                                if (isViewingLogs) Icons.Filled.Article else Icons.Outlined.Article,
                                contentDescription = null,
                                tint = if (isViewingLogs) DockerColors.DockerBlue
                                    else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    )

                    HorizontalDivider()

                    when {
                        container.isRunning -> {
                            DropdownMenuItem(
                                text = { Text("Pause") },
                                onClick = { showActionsMenu = false; onPause() },
                                leadingIcon = { Icon(Icons.Outlined.Pause, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Stop") },
                                onClick = { showActionsMenu = false; onStop() },
                                leadingIcon = { Icon(Icons.Outlined.Stop, null, tint = DockerColors.Stopped) }
                            )
                        }
                        container.isPaused -> {
                            DropdownMenuItem(
                                text = { Text("Resume") },
                                onClick = { showActionsMenu = false; onUnpause() },
                                leadingIcon = { Icon(Icons.Outlined.PlayArrow, null, tint = DockerColors.Running) }
                            )
                        }
                        else -> {
                            DropdownMenuItem(
                                text = { Text("Start") },
                                onClick = { showActionsMenu = false; onStart() },
                                leadingIcon = { Icon(Icons.Outlined.PlayArrow, null, tint = DockerColors.Running) }
                            )
                        }
                    }

                    HorizontalDivider()

                    DropdownMenuItem(
                        text = {
                            Text(
                                "Delete",
                                color = if (!container.isRunning) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        },
                        onClick = { showActionsMenu = false; onRemove() },
                        enabled = !container.isRunning,
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Delete, null,
                                tint = if (!container.isRunning) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        }
                    )
                }
            }
        }
    }
}

// ============== EXPANDED MODE COMPONENTS ==============

@Composable
private fun ExpandedTableHeader(
    allSelected: Boolean,
    onSelectAllChange: (Boolean) -> Unit,
    hasItems: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = allSelected,
            onCheckedChange = onSelectAllChange,
            enabled = hasItems,
            modifier = Modifier.padding(end = 8.dp)
        )

        Text(
            text = "NAME",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1.5f)
        )
        Text(
            text = "IMAGE",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1.5f)
        )
        Text(
            text = "STATUS",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "PORTS",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1.5f)
        )
        Spacer(modifier = Modifier.width(160.dp))
    }
}

@Composable
private fun ExpandedContainerRow(
    container: Container,
    isChecked: Boolean,
    isViewingLogs: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isActionInProgress: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onPause: () -> Unit,
    onUnpause: () -> Unit,
    onRemove: () -> Unit,
    onViewLogs: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    isViewingLogs -> DockerColors.DockerBlue.copy(alpha = 0.15f)
                    isChecked -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                    else -> MaterialTheme.colorScheme.surface
                }
            )
            .clickable { onViewLogs() }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.padding(end = 8.dp)
        )

        // Name
        Column(modifier = Modifier.weight(1.5f)) {
            Text(
                text = container.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = container.shortId,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace
            )
        }

        // Image
        Text(
            text = container.image,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1.5f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Status
        Box(modifier = Modifier.weight(1f)) {
            StatusBadge(status = container.state.toContainerStatus())
        }

        // Ports
        Text(
            text = container.ports.firstOrNull()?.displayString ?: "-",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1.5f)
        )

        // Actions
        Row(
            modifier = Modifier.width(160.dp),
            horizontalArrangement = Arrangement.End
        ) {
            if (isActionInProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                // Logs button
                IconButton(
                    onClick = onViewLogs,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        if (isViewingLogs) Icons.Filled.Article else Icons.Outlined.Article,
                        contentDescription = "View Logs",
                        modifier = Modifier.size(18.dp),
                        tint = if (isViewingLogs) DockerColors.DockerBlue
                            else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                when {
                    container.isRunning -> {
                        IconButton(onClick = onPause, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Outlined.Pause,
                                contentDescription = "Pause",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onStop, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Outlined.Stop,
                                contentDescription = "Stop",
                                modifier = Modifier.size(18.dp),
                                tint = DockerColors.Stopped
                            )
                        }
                    }
                    container.isPaused -> {
                        IconButton(onClick = onUnpause, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Outlined.PlayArrow,
                                contentDescription = "Resume",
                                modifier = Modifier.size(18.dp),
                                tint = DockerColors.Running
                            )
                        }
                    }
                    else -> {
                        IconButton(onClick = onStart, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Outlined.PlayArrow,
                                contentDescription = "Start",
                                modifier = Modifier.size(18.dp),
                                tint = DockerColors.Running
                            )
                        }
                    }
                }
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(32.dp),
                    enabled = !container.isRunning
                ) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Delete",
                        modifier = Modifier.size(18.dp),
                        tint = if (!container.isRunning)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}
