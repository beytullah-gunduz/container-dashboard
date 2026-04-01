package com.containerdashboard.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.containerdashboard.data.models.Container
import com.containerdashboard.ui.components.DeleteAllContainersDialog
import com.containerdashboard.ui.components.DeletingAllContainersDialog
import com.containerdashboard.ui.components.SearchBar
import com.containerdashboard.ui.components.StatusBadge
import com.containerdashboard.ui.components.toContainerStatus
import com.containerdashboard.ui.screens.viewmodel.ContainerFilter
import com.containerdashboard.ui.screens.viewmodel.ContainersScreenViewModel
import com.containerdashboard.ui.theme.AppColors

// Threshold for switching between compact and expanded layouts
private val COMPACT_THRESHOLD = 700.dp
private val COMPACT_BUTTONS_THRESHOLD = 900.dp

private enum class SortColumn {
    NAME,
    IMAGE,
    STATUS,
    PORTS,
}

private enum class SortDirection {
    ASCENDING,
    DESCENDING,
}

@Composable
fun ContainersScreen(
    onShowLogs: (Container) -> Unit = {},
    currentLogsContainerId: String? = null,
    modifier: Modifier = Modifier,
    viewModel: ContainersScreenViewModel = viewModel { ContainersScreenViewModel() },
) {
    var searchQuery by remember { mutableStateOf("") }
    var containerFilter by remember { mutableStateOf(ContainerFilter.ALL) }
    var sortColumn by remember { mutableStateOf(SortColumn.NAME) }
    var sortDirection by remember { mutableStateOf(SortDirection.ASCENDING) }
    var runningVisible by remember { mutableStateOf(true) }
    var otherVisible by remember { mutableStateOf(true) }

    val onSortChange: (SortColumn) -> Unit = { column ->
        if (sortColumn == column) {
            sortDirection =
                if (sortDirection == SortDirection.ASCENDING) SortDirection.DESCENDING else SortDirection.ASCENDING
        } else {
            sortColumn = column
            sortDirection = SortDirection.ASCENDING
        }
    }

    val containers by viewModel.containers.collectAsState(listOf())
    val error by viewModel.error.collectAsState()
    val actionInProgress by viewModel.actionInProgress.collectAsState()
    val selectedContainerIds by viewModel.selectedContainerIds.collectAsState()
    val isDeletingSelected by viewModel.isDeletingSelected.collectAsState()
    val isDeletingAll by viewModel.isDeletingAll.collectAsState()
    val isStoppingSelected by viewModel.isStoppingSelected.collectAsState()

    // State for delete all confirmation dialog
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    val filteredContainers =
        remember(containers, searchQuery, containerFilter, sortColumn, sortDirection) {
            containers
                .filter { container ->
                    val matchesSearch =
                        searchQuery.isEmpty() ||
                            container.displayName.contains(searchQuery, ignoreCase = true) ||
                            container.image.contains(searchQuery, ignoreCase = true)
                    val matchesFilter =
                        when (containerFilter) {
                            ContainerFilter.ALL -> true
                            ContainerFilter.RUNNING -> container.isRunning
                            ContainerFilter.STOPPED -> !container.isRunning
                        }
                    matchesSearch && matchesFilter
                }.let { list ->
                    val comparator =
                        when (sortColumn) {
                            SortColumn.NAME -> compareBy<Container> { it.displayName.lowercase() }
                            SortColumn.IMAGE -> compareBy { it.image.lowercase() }
                            SortColumn.STATUS -> compareBy { it.state.lowercase() }
                            SortColumn.PORTS -> compareBy { it.ports.firstOrNull()?.displayString ?: "" }
                        }
                    if (sortDirection == SortDirection.DESCENDING) {
                        list.sortedWith(comparator.reversed())
                    } else {
                        list.sortedWith(comparator)
                    }
                }
        }

    // Check if all filtered containers are selected
    val allFilteredSelected =
        filteredContainers.isNotEmpty() &&
            filteredContainers.all { it.id in selectedContainerIds }

    // Check how many selected containers can be deleted (not running) vs running
    val deletableSelectedCount =
        remember(selectedContainerIds, containers) {
            selectedContainerIds.count { id ->
                containers.find { it.id == id }?.isRunning == false
            }
        }
    val runningSelectedCount =
        remember(selectedContainerIds, containers) {
            selectedContainerIds.count { id ->
                containers.find { it.id == id }?.isRunning == true
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
            onDismiss = { showDeleteAllDialog = false },
        )
    }

    // Deleting All Progress Modal
    if (isDeletingAll) {
        DeletingAllContainersDialog()
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isCompactMode = maxWidth < COMPACT_THRESHOLD
        val iconOnly = maxWidth < COMPACT_BUTTONS_THRESHOLD

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Containers",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${containers.count { it.isRunning }} running of ${containers.size} total",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(if (iconOnly) 4.dp else 12.dp)) {
                    if (selectedContainerIds.isNotEmpty()) {
                        // Stop button for running containers
                        if (runningSelectedCount > 0) {
                            if (iconOnly) {
                                IconButton(
                                    onClick = {
                                        val runningIds =
                                            selectedContainerIds.filter { id ->
                                                containers.find { it.id == id }?.isRunning == true
                                            }
                                        viewModel.stopSelectedContainers(runningIds)
                                    },
                                    enabled = !isStoppingSelected,
                                ) {
                                    if (isStoppingSelected) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                        )
                                    } else {
                                        Icon(
                                            Icons.Outlined.Stop,
                                            contentDescription = "Stop $runningSelectedCount selected",
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            } else {
                                Button(
                                    onClick = {
                                        val runningIds =
                                            selectedContainerIds.filter { id ->
                                                containers.find { it.id == id }?.isRunning == true
                                            }
                                        viewModel.stopSelectedContainers(runningIds)
                                    },
                                    enabled = !isStoppingSelected,
                                    colors =
                                        ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error,
                                        ),
                                ) {
                                    if (isStoppingSelected) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onError,
                                        )
                                    } else {
                                        Icon(Icons.Outlined.Stop, null, modifier = Modifier.size(18.dp))
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Stop $runningSelectedCount selected")
                                }
                            }
                        }

                        // Delete button (force-deletes running containers)
                        if (iconOnly) {
                            IconButton(
                                onClick = { viewModel.deleteSelectedContainers() },
                                enabled = !isDeletingSelected,
                            ) {
                                if (isDeletingSelected) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete ${selectedContainerIds.size} selected",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        } else {
                            Button(
                                onClick = { viewModel.deleteSelectedContainers() },
                                enabled = !isDeletingSelected,
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                    ),
                            ) {
                                if (isDeletingSelected) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onError,
                                    )
                                } else {
                                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Delete ${selectedContainerIds.size} selected")
                            }
                        }

                        // Clear selection button
                        if (iconOnly) {
                            IconButton(onClick = { viewModel.clearSelection() }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Clear selection",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            OutlinedButton(onClick = { viewModel.clearSelection() }) {
                                Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Clear")
                            }
                        }
                    }

                    // Delete All Button
                    if (containers.isNotEmpty()) {
                        if (iconOnly) {
                            IconButton(
                                onClick = { showDeleteAllDialog = true },
                                enabled = !isDeletingAll && !isDeletingSelected,
                            ) {
                                if (isDeletingAll) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.DeleteForever,
                                        contentDescription = "Delete All",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        } else {
                            Button(
                                onClick = { showDeleteAllDialog = true },
                                enabled = !isDeletingAll && !isDeletingSelected,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            ) {
                                if (isDeletingAll) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onError,
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
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Error message
            error?.let { errorMessage ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "Search containers...",
                    modifier = Modifier.weight(1f),
                )

                FilterChip(
                    selected = containerFilter == ContainerFilter.ALL,
                    onClick = { containerFilter = ContainerFilter.ALL },
                    label = { Text("All") },
                    leadingIcon =
                        if (containerFilter == ContainerFilter.ALL) {
                            { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                        } else {
                            null
                        },
                )

                FilterChip(
                    selected = containerFilter == ContainerFilter.RUNNING,
                    onClick = { containerFilter = ContainerFilter.RUNNING },
                    label = { Text("Running") },
                    leadingIcon =
                        if (containerFilter == ContainerFilter.RUNNING) {
                            { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                        } else {
                            null
                        },
                )

                FilterChip(
                    selected = containerFilter == ContainerFilter.STOPPED,
                    onClick = { containerFilter = ContainerFilter.STOPPED },
                    label = { Text("Stopped") },
                    leadingIcon =
                        if (containerFilter == ContainerFilter.STOPPED) {
                            { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                        } else {
                            null
                        },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            val runningContainers = filteredContainers.filter { it.isRunning }
            val otherContainers = filteredContainers.filter { !it.isRunning }

            if (filteredContainers.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No containers found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    // Running containers section
                    if (runningContainers.isNotEmpty()) {
                        item(key = "running-header") {
                            ContainerSectionHeader(
                                title = "Running",
                                count = runningContainers.size,
                                expanded = runningVisible,
                                onToggle = { runningVisible = !runningVisible },
                            )
                        }

                        item(key = "running-table-header") {
                            val allRunningSelected =
                                runningContainers.isNotEmpty() &&
                                    runningContainers.all { it.id in selectedContainerIds }
                            AnimatedVisibility(
                                visible = runningVisible,
                                enter = expandVertically(),
                                exit = shrinkVertically(),
                            ) {
                                if (isCompactMode) {
                                    CompactTableHeader(
                                        allSelected = allRunningSelected,
                                        onSelectAllChange = { selectAll ->
                                            if (selectAll) {
                                                viewModel.selectAllContainers(
                                                    (selectedContainerIds + runningContainers.map { it.id }).toList(),
                                                )
                                            } else {
                                                runningContainers.forEach {
                                                    viewModel.toggleContainerSelection(it.id, false)
                                                }
                                            }
                                        },
                                        hasItems = true,
                                        sortColumn = sortColumn,
                                        sortDirection = sortDirection,
                                        onSortChange = onSortChange,
                                    )
                                } else {
                                    ExpandedTableHeader(
                                        allSelected = allRunningSelected,
                                        onSelectAllChange = { selectAll ->
                                            if (selectAll) {
                                                viewModel.selectAllContainers(
                                                    (selectedContainerIds + runningContainers.map { it.id }).toList(),
                                                )
                                            } else {
                                                runningContainers.forEach {
                                                    viewModel.toggleContainerSelection(it.id, false)
                                                }
                                            }
                                        },
                                        hasItems = true,
                                        sortColumn = sortColumn,
                                        sortDirection = sortDirection,
                                        onSortChange = onSortChange,
                                    )
                                }
                            }
                        }

                        items(runningContainers, key = { "running-${it.id}" }) { container ->
                            AnimatedVisibility(
                                visible = runningVisible,
                                enter = expandVertically(),
                                exit = shrinkVertically(),
                            ) {
                                ContainerRowByMode(
                                    isCompactMode = isCompactMode,
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
                                    onViewLogs = { onShowLogs(container) },
                                )
                            }
                        }
                    }

                    // Other containers section
                    if (otherContainers.isNotEmpty()) {
                        item(key = "other-header") {
                            if (runningContainers.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                            ContainerSectionHeader(
                                title = "Stopped / Other",
                                count = otherContainers.size,
                                expanded = otherVisible,
                                onToggle = { otherVisible = !otherVisible },
                            )
                        }

                        item(key = "other-table-header") {
                            val allOtherSelected =
                                otherContainers.isNotEmpty() &&
                                    otherContainers.all { it.id in selectedContainerIds }
                            AnimatedVisibility(
                                visible = otherVisible,
                                enter = expandVertically(),
                                exit = shrinkVertically(),
                            ) {
                                if (isCompactMode) {
                                    CompactTableHeader(
                                        allSelected = allOtherSelected,
                                        onSelectAllChange = { selectAll ->
                                            if (selectAll) {
                                                viewModel.selectAllContainers(
                                                    (selectedContainerIds + otherContainers.map { it.id }).toList(),
                                                )
                                            } else {
                                                otherContainers.forEach {
                                                    viewModel.toggleContainerSelection(it.id, false)
                                                }
                                            }
                                        },
                                        hasItems = true,
                                        sortColumn = sortColumn,
                                        sortDirection = sortDirection,
                                        onSortChange = onSortChange,
                                    )
                                } else {
                                    ExpandedTableHeader(
                                        allSelected = allOtherSelected,
                                        onSelectAllChange = { selectAll ->
                                            if (selectAll) {
                                                viewModel.selectAllContainers(
                                                    (selectedContainerIds + otherContainers.map { it.id }).toList(),
                                                )
                                            } else {
                                                otherContainers.forEach {
                                                    viewModel.toggleContainerSelection(it.id, false)
                                                }
                                            }
                                        },
                                        hasItems = true,
                                        sortColumn = sortColumn,
                                        sortDirection = sortDirection,
                                        onSortChange = onSortChange,
                                    )
                                }
                            }
                        }

                        items(otherContainers, key = { "other-${it.id}" }) { container ->
                            AnimatedVisibility(
                                visible = otherVisible,
                                enter = expandVertically(),
                                exit = shrinkVertically(),
                            ) {
                                ContainerRowByMode(
                                    isCompactMode = isCompactMode,
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
                                    onViewLogs = { onShowLogs(container) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContainerSectionHeader(
    title: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onToggle)
                .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
            contentDescription = if (expanded) "Collapse" else "Expand",
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun ContainerRowByMode(
    isCompactMode: Boolean,
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
    onViewLogs: () -> Unit,
) {
    if (isCompactMode) {
        CompactContainerRow(
            container = container,
            isChecked = isChecked,
            isViewingLogs = isViewingLogs,
            onCheckedChange = onCheckedChange,
            isActionInProgress = isActionInProgress,
            onStart = onStart,
            onStop = onStop,
            onPause = onPause,
            onUnpause = onUnpause,
            onRemove = onRemove,
            onViewLogs = onViewLogs,
        )
    } else {
        ExpandedContainerRow(
            container = container,
            isChecked = isChecked,
            isViewingLogs = isViewingLogs,
            onCheckedChange = onCheckedChange,
            isActionInProgress = isActionInProgress,
            onStart = onStart,
            onStop = onStop,
            onPause = onPause,
            onUnpause = onUnpause,
            onRemove = onRemove,
            onViewLogs = onViewLogs,
        )
    }
}

// ============== SORTABLE HEADER ==============

@Composable
private fun SortableHeaderCell(
    text: String,
    column: SortColumn,
    currentSortColumn: SortColumn,
    sortDirection: SortDirection,
    onSortChange: (SortColumn) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isActive = currentSortColumn == column
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable { onSortChange(column) }
                .padding(vertical = 4.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color =
                if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
        if (isActive) {
            Icon(
                imageVector =
                    if (sortDirection == SortDirection.ASCENDING) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// ============== COMPACT MODE COMPONENTS ==============

@Composable
private fun CompactTableHeader(
    allSelected: Boolean,
    onSelectAllChange: (Boolean) -> Unit,
    hasItems: Boolean,
    sortColumn: SortColumn,
    sortDirection: SortDirection,
    onSortChange: (SortColumn) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = allSelected,
            onCheckedChange = onSelectAllChange,
            enabled = hasItems,
            modifier = Modifier.padding(end = 8.dp),
        )

        SortableHeaderCell(
            text = "CONTAINER",
            column = SortColumn.NAME,
            currentSortColumn = sortColumn,
            sortDirection = sortDirection,
            onSortChange = onSortChange,
            modifier = Modifier.weight(1f),
        )

        SortableHeaderCell(
            text = "STATUS",
            column = SortColumn.STATUS,
            currentSortColumn = sortColumn,
            sortDirection = sortDirection,
            onSortChange = onSortChange,
            modifier = Modifier.width(100.dp),
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
    onViewLogs: () -> Unit,
) {
    var showActionsMenu by remember { mutableStateOf(false) }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(
                    when {
                        isViewingLogs -> AppColors.AccentBlue.copy(alpha = 0.15f)
                        isChecked -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                        else -> MaterialTheme.colorScheme.surface
                    },
                ).clickable { onViewLogs() }
                .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.padding(end = 8.dp),
        )

        // Container info (Name + Image below)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = container.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = container.image,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = container.shortId,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontFamily = FontFamily.Monospace,
                )
            }
            container.ports.firstOrNull()?.displayString?.let { port ->
                Text(
                    text = port,
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.AccentBlue,
                    fontFamily = FontFamily.Monospace,
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
                    strokeWidth = 2.dp,
                )
            } else {
                IconButton(
                    onClick = { showActionsMenu = true },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Actions",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                DropdownMenu(
                    expanded = showActionsMenu,
                    onDismissRequest = { showActionsMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("View Logs") },
                        onClick = {
                            showActionsMenu = false
                            onViewLogs()
                        },
                        leadingIcon = {
                            Icon(
                                if (isViewingLogs) Icons.AutoMirrored.Filled.Article else Icons.AutoMirrored.Outlined.Article,
                                contentDescription = null,
                                tint =
                                    if (isViewingLogs) {
                                        AppColors.AccentBlue
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                            )
                        },
                    )

                    HorizontalDivider()

                    when {
                        container.isRunning -> {
                            DropdownMenuItem(
                                text = { Text("Pause") },
                                onClick = {
                                    showActionsMenu = false
                                    onPause()
                                },
                                leadingIcon = { Icon(Icons.Outlined.Pause, null) },
                            )
                            DropdownMenuItem(
                                text = { Text("Stop") },
                                onClick = {
                                    showActionsMenu = false
                                    onStop()
                                },
                                leadingIcon = { Icon(Icons.Outlined.Stop, null, tint = AppColors.Stopped) },
                            )
                        }
                        container.isPaused -> {
                            DropdownMenuItem(
                                text = { Text("Resume") },
                                onClick = {
                                    showActionsMenu = false
                                    onUnpause()
                                },
                                leadingIcon = { Icon(Icons.Outlined.PlayArrow, null, tint = AppColors.Running) },
                            )
                        }
                        else -> {
                            DropdownMenuItem(
                                text = { Text("Start") },
                                onClick = {
                                    showActionsMenu = false
                                    onStart()
                                },
                                leadingIcon = { Icon(Icons.Outlined.PlayArrow, null, tint = AppColors.Running) },
                            )
                        }
                    }

                    HorizontalDivider()

                    DropdownMenuItem(
                        text = {
                            Text(
                                "Delete",
                                color =
                                    if (!container.isRunning) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    },
                            )
                        },
                        onClick = {
                            showActionsMenu = false
                            onRemove()
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Delete,
                                null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
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
    hasItems: Boolean,
    sortColumn: SortColumn,
    sortDirection: SortDirection,
    onSortChange: (SortColumn) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = allSelected,
            onCheckedChange = onSelectAllChange,
            enabled = hasItems,
            modifier = Modifier.padding(end = 8.dp),
        )

        SortableHeaderCell(
            text = "NAME",
            column = SortColumn.NAME,
            currentSortColumn = sortColumn,
            sortDirection = sortDirection,
            onSortChange = onSortChange,
            modifier = Modifier.weight(1.5f),
        )
        SortableHeaderCell(
            text = "IMAGE",
            column = SortColumn.IMAGE,
            currentSortColumn = sortColumn,
            sortDirection = sortDirection,
            onSortChange = onSortChange,
            modifier = Modifier.weight(1.5f),
        )
        SortableHeaderCell(
            text = "STATUS",
            column = SortColumn.STATUS,
            currentSortColumn = sortColumn,
            sortDirection = sortDirection,
            onSortChange = onSortChange,
            modifier = Modifier.weight(1f),
        )
        SortableHeaderCell(
            text = "PORTS",
            column = SortColumn.PORTS,
            currentSortColumn = sortColumn,
            sortDirection = sortDirection,
            onSortChange = onSortChange,
            modifier = Modifier.weight(1.5f),
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
    onViewLogs: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(
                    when {
                        isViewingLogs -> AppColors.AccentBlue.copy(alpha = 0.15f)
                        isChecked -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                        else -> MaterialTheme.colorScheme.surface
                    },
                ).clickable { onViewLogs() }
                .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.padding(end = 8.dp),
        )

        // Name
        Column(modifier = Modifier.weight(1.5f)) {
            Text(
                text = container.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = container.shortId,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
            )
        }

        // Image
        Text(
            text = container.image,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1.5f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
            modifier = Modifier.weight(1.5f),
        )

        // Actions
        Row(
            modifier = Modifier.width(160.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            if (isActionInProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                // Logs button
                IconButton(
                    onClick = onViewLogs,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        if (isViewingLogs) Icons.AutoMirrored.Filled.Article else Icons.AutoMirrored.Outlined.Article,
                        contentDescription = "View Logs",
                        modifier = Modifier.size(18.dp),
                        tint =
                            if (isViewingLogs) {
                                AppColors.AccentBlue
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }

                when {
                    container.isRunning -> {
                        IconButton(onClick = onPause, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Outlined.Pause,
                                contentDescription = "Pause",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = onStop, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Outlined.Stop,
                                contentDescription = "Stop",
                                modifier = Modifier.size(18.dp),
                                tint = AppColors.Stopped,
                            )
                        }
                    }
                    container.isPaused -> {
                        IconButton(onClick = onUnpause, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Outlined.PlayArrow,
                                contentDescription = "Resume",
                                modifier = Modifier.size(18.dp),
                                tint = AppColors.Running,
                            )
                        }
                    }
                    else -> {
                        IconButton(onClick = onStart, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Outlined.PlayArrow,
                                contentDescription = "Start",
                                modifier = Modifier.size(18.dp),
                                tint = AppColors.Running,
                            )
                        }
                    }
                }
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Delete",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
