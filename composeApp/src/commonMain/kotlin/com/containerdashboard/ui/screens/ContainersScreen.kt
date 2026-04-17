package com.containerdashboard.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.material.icons.automirrored.outlined.ViewSidebar
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RemoveDone
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.containerdashboard.data.models.Container
import com.containerdashboard.data.models.ContainerStats
import com.containerdashboard.data.repository.ContainerColumnWidths
import com.containerdashboard.data.repository.PreferenceRepository
import com.containerdashboard.ui.components.CompactCheckbox
import com.containerdashboard.ui.components.ConfirmActionDialog
import com.containerdashboard.ui.components.DeleteAllContainersDialog
import com.containerdashboard.ui.components.DeletingAllContainersDialog
import com.containerdashboard.ui.components.DetailsTarget
import com.containerdashboard.ui.components.EmptyState
import com.containerdashboard.ui.components.EmptyStateAction
import com.containerdashboard.ui.components.ErrorStateCard
import com.containerdashboard.ui.components.ListRowSkeleton
import com.containerdashboard.ui.components.LogsPaneLayout
import com.containerdashboard.ui.components.ResourceDetailsDialog
import com.containerdashboard.ui.components.SearchBar
import com.containerdashboard.ui.components.StatusBadge
import com.containerdashboard.ui.components.toContainerStatus
import com.containerdashboard.ui.screens.components.ContainerContextMenu
import com.containerdashboard.ui.screens.viewmodel.ContainerFilter
import com.containerdashboard.ui.screens.viewmodel.ContainersScreenViewModel
import com.containerdashboard.ui.shortcuts.LocalSearchFocusRequester
import com.containerdashboard.ui.theme.AppColors
import com.containerdashboard.ui.util.copyToClipboard

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

private sealed interface ContainerListItem {
    data class ComposeGroupHeader(
        val projectName: String,
        val containers: List<Container>,
        val expanded: Boolean,
    ) : ContainerListItem

    data class ContainerItem(
        val container: Container,
        val indented: Boolean,
    ) : ContainerListItem
}

private fun groupContainers(
    containers: List<Container>,
    expandedProjects: Set<String>,
): List<ContainerListItem> {
    val (composeContainers, standalone) = containers.partition { it.composeProject != null }
    val groups = composeContainers.groupBy { it.composeProject!! }

    val items = mutableListOf<ContainerListItem>()
    for ((project, members) in groups.entries.sortedBy { it.key.lowercase() }) {
        val isExpanded = project in expandedProjects
        items.add(ContainerListItem.ComposeGroupHeader(project, members, isExpanded))
    }
    standalone.forEach { items.add(ContainerListItem.ContainerItem(it, indented = false)) }
    return items
}

@Composable
fun ContainersScreen(
    onShowLogs: (Container) -> Unit = {},
    onShowGroupLogs: (List<Container>) -> Unit = {},
    currentLogsContainerId: String? = null,
    logsPaneLayout: LogsPaneLayout = LogsPaneLayout.AUTO,
    onLogsPaneLayoutChange: (LogsPaneLayout) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ContainersScreenViewModel = viewModel { ContainersScreenViewModel() },
) {
    var searchQuery by remember { mutableStateOf("") }
    var containerFilter by remember { mutableStateOf(ContainerFilter.ALL) }
    var sortColumn by remember { mutableStateOf(SortColumn.NAME) }
    var sortDirection by remember { mutableStateOf(SortDirection.ASCENDING) }
    var runningVisible by remember { mutableStateOf(true) }
    var otherVisible by remember { mutableStateOf(true) }
    var expandedRunningProjects by remember { mutableStateOf(setOf<String>()) }
    var expandedOtherProjects by remember { mutableStateOf(setOf<String>()) }

    val toggleRunningGroup: (String) -> Unit = { project ->
        expandedRunningProjects =
            if (project in expandedRunningProjects) {
                expandedRunningProjects - project
            } else {
                expandedRunningProjects + project
            }
    }
    val toggleOtherGroup: (String) -> Unit = { project ->
        expandedOtherProjects =
            if (project in expandedOtherProjects) {
                expandedOtherProjects - project
            } else {
                expandedOtherProjects + project
            }
    }

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
    val hasLoaded by viewModel.hasLoaded.collectAsState()
    val error by viewModel.error.collectAsState()
    val actionInProgress by viewModel.actionInProgress.collectAsState()
    val selectedContainerIds by viewModel.selectedContainerIds.collectAsState()
    val pendingDeleteIds by viewModel.pendingDeleteIds.collectAsState()
    val persistedColumnWidths by viewModel.columnWidths.collectAsState(initial = ContainerColumnWidths.Default)
    var columnWidths by remember(persistedColumnWidths) { mutableStateOf(persistedColumnWidths) }
    val isDeletingSelected by viewModel.isDeletingSelected.collectAsState()
    val isDeletingAll by viewModel.isDeletingAll.collectAsState()
    val isStoppingSelected by viewModel.isStoppingSelected.collectAsState()
    val statsById by viewModel.statsById.collectAsState(emptyMap())

    // State for delete all confirmation dialog
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    val confirmBeforeDelete by PreferenceRepository.confirmBeforeDelete().collectAsState(initial = true)
    var pendingConfirmTitle by remember { mutableStateOf("") }
    var pendingConfirmBody by remember { mutableStateOf("") }
    var pendingConfirmAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun askConfirm(
        title: String,
        body: String,
        action: () -> Unit,
    ) {
        if (confirmBeforeDelete) {
            pendingConfirmTitle = title
            pendingConfirmBody = body
            pendingConfirmAction = action
        } else {
            action()
        }
    }

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

    pendingConfirmAction?.let { action ->
        ConfirmActionDialog(
            title = pendingConfirmTitle,
            body = pendingConfirmBody,
            onConfirm = {
                action()
                pendingConfirmAction = null
            },
            onDismiss = { pendingConfirmAction = null },
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

                Row(
                    horizontalArrangement = Arrangement.spacedBy(if (iconOnly) 4.dp else 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val hasSelection = selectedContainerIds.isNotEmpty()

                    // Clear selection button (leftmost, animates last)
                    AnimatedVisibility(
                        visible = hasSelection,
                        enter =
                            expandHorizontally(
                                animationSpec = tween(durationMillis = 200, delayMillis = 100),
                                expandFrom = Alignment.Start,
                            ) + fadeIn(animationSpec = tween(durationMillis = 200, delayMillis = 100)),
                        exit =
                            shrinkHorizontally(
                                animationSpec = tween(durationMillis = 150),
                                shrinkTowards = Alignment.Start,
                            ) + fadeOut(animationSpec = tween(durationMillis = 150)),
                    ) {
                        if (iconOnly) {
                            IconButton(onClick = { viewModel.clearSelection() }) {
                                Icon(
                                    Icons.Default.RemoveDone,
                                    contentDescription = "Clear selection",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            OutlinedButton(onClick = { viewModel.clearSelection() }) {
                                Icon(Icons.Default.RemoveDone, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Clear")
                            }
                        }
                    }

                    // Stop button for running containers (middle, animates second)
                    AnimatedVisibility(
                        visible = hasSelection && runningSelectedCount > 0,
                        enter =
                            expandHorizontally(
                                animationSpec = tween(durationMillis = 200, delayMillis = 50),
                                expandFrom = Alignment.Start,
                            ) + fadeIn(animationSpec = tween(durationMillis = 200, delayMillis = 50)),
                        exit =
                            shrinkHorizontally(
                                animationSpec = tween(durationMillis = 150),
                                shrinkTowards = Alignment.Start,
                            ) + fadeOut(animationSpec = tween(durationMillis = 150)),
                    ) {
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

                    // Delete button (rightmost selection button, animates first)
                    AnimatedVisibility(
                        visible = hasSelection,
                        enter =
                            expandHorizontally(
                                animationSpec = tween(durationMillis = 200),
                                expandFrom = Alignment.Start,
                            ) + fadeIn(animationSpec = tween(durationMillis = 200)),
                        exit =
                            shrinkHorizontally(
                                animationSpec = tween(durationMillis = 150),
                                shrinkTowards = Alignment.Start,
                            ) + fadeOut(animationSpec = tween(durationMillis = 150)),
                    ) {
                        if (iconOnly) {
                            IconButton(
                                onClick = {
                                    askConfirm(
                                        "Delete selected containers?",
                                        "This will force-stop and delete ${selectedContainerIds.size} container(s).",
                                    ) { viewModel.deleteSelectedContainers() }
                                },
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
                                onClick = {
                                    askConfirm(
                                        "Delete selected containers?",
                                        "This will force-stop and delete ${selectedContainerIds.size} container(s).",
                                    ) { viewModel.deleteSelectedContainers() }
                                },
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

                    // Layout toggle
                    VerticalDivider(
                        modifier = Modifier.height(24.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Box {
                        var showLayoutMenu by remember { mutableStateOf(false) }
                        val layoutIcon =
                            when (logsPaneLayout) {
                                LogsPaneLayout.RIGHT -> Icons.AutoMirrored.Outlined.ViewSidebar
                                LogsPaneLayout.BOTTOM -> Icons.Outlined.ViewAgenda
                                LogsPaneLayout.AUTO -> Icons.Outlined.AutoAwesome
                            }
                        IconButton(
                            onClick = { showLayoutMenu = true },
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    layoutIcon,
                                    contentDescription = "Log panel layout",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = showLayoutMenu,
                            onDismissRequest = { showLayoutMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Right") },
                                onClick = {
                                    onLogsPaneLayoutChange(LogsPaneLayout.RIGHT)
                                    showLayoutMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.AutoMirrored.Outlined.ViewSidebar,
                                        null,
                                        tint =
                                            if (logsPaneLayout == LogsPaneLayout.RIGHT) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Bottom") },
                                onClick = {
                                    onLogsPaneLayoutChange(LogsPaneLayout.BOTTOM)
                                    showLayoutMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.ViewAgenda,
                                        null,
                                        tint =
                                            if (logsPaneLayout == LogsPaneLayout.BOTTOM) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Auto") },
                                onClick = {
                                    onLogsPaneLayoutChange(LogsPaneLayout.AUTO)
                                    showLayoutMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.AutoAwesome,
                                        null,
                                        tint =
                                            if (logsPaneLayout == LogsPaneLayout.AUTO) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                    )
                                },
                            )
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
                    placeholder = if (isCompactMode) "Search..." else "Search containers...",
                    modifier = Modifier.weight(1f),
                    compact = isCompactMode,
                    focusRequester = LocalSearchFocusRequester.current,
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

            val runningGrouped =
                remember(runningContainers, expandedRunningProjects) {
                    groupContainers(runningContainers, expandedRunningProjects)
                }
            val otherGrouped =
                remember(otherContainers, expandedOtherProjects) {
                    groupContainers(otherContainers, expandedOtherProjects)
                }

            if (!hasLoaded && containers.isEmpty()) {
                if (error != null) {
                    ErrorStateCard(
                        message = error ?: "Could not load containers",
                        onRetry = {
                            viewModel.clearError()
                            viewModel.refresh()
                        },
                    )
                } else {
                    ListRowSkeleton(rowCount = 6)
                }
            } else if (filteredContainers.isEmpty()) {
                if (searchQuery.isNotEmpty()) {
                    EmptyState(
                        icon = Icons.Outlined.SearchOff,
                        title = "No matches",
                        body = "Nothing matched \"$searchQuery\".",
                        action =
                            EmptyStateAction("Clear search") {
                                searchQuery = ""
                            },
                    )
                } else {
                    EmptyState(
                        icon = Icons.Outlined.ViewInAr,
                        title = "No containers",
                        body = "Run a container from the Images tab to see it here.",
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
                                        columnWidths = columnWidths,
                                        onColumnWidthsChange = { columnWidths = it },
                                        onColumnWidthsChangeFinished = { viewModel.setColumnWidths(columnWidths) },
                                    )
                                }
                            }
                        }

                        items(
                            runningGrouped,
                            key = { item ->
                                when (item) {
                                    is ContainerListItem.ComposeGroupHeader -> "running-group-${item.projectName}"
                                    is ContainerListItem.ContainerItem -> "running-${item.container.id}"
                                }
                            },
                        ) { item ->
                            Column(
                                modifier =
                                    Modifier.animateItem(
                                        fadeInSpec = tween(600),
                                        fadeOutSpec = tween(800),
                                        placementSpec = tween(500),
                                    ),
                            ) {
                                AnimatedVisibility(
                                    visible = runningVisible,
                                    enter = expandVertically(),
                                    exit = shrinkVertically(),
                                ) {
                                    when (item) {
                                        is ContainerListItem.ComposeGroupHeader -> {
                                            ComposeProjectCard(
                                                item = item,
                                                sectionPrefix = "running",
                                                isCompactMode = isCompactMode,
                                                selectedContainerIds = selectedContainerIds,
                                                pendingDeleteIds = pendingDeleteIds,
                                                columnWidths = columnWidths,
                                                currentLogsContainerId = currentLogsContainerId,
                                                actionInProgress = actionInProgress,
                                                statsById = statsById,
                                                onToggle = { toggleRunningGroup(item.projectName) },
                                                onSelectAll = { selectAll ->
                                                    if (selectAll) {
                                                        viewModel.selectAllContainers(
                                                            (selectedContainerIds + item.containers.map { it.id }).toList(),
                                                        )
                                                    } else {
                                                        item.containers.forEach {
                                                            viewModel.toggleContainerSelection(it.id, false)
                                                        }
                                                    }
                                                },
                                                onCheckedChange = { id, checked ->
                                                    viewModel.toggleContainerSelection(id, checked)
                                                },
                                                onStart = { viewModel.startContainer(it) },
                                                onStop = { viewModel.stopContainer(it) },
                                                onPause = { viewModel.pauseContainer(it) },
                                                onUnpause = { viewModel.unpauseContainer(it) },
                                                onRemove = { viewModel.removeContainer(it) },
                                                onViewLogs = { onShowLogs(it) },
                                                onViewGroupLogs = { onShowGroupLogs(it) },
                                                onStartAll = { ids -> ids.forEach { viewModel.startContainer(it) } },
                                                onStopAll = { ids -> ids.forEach { viewModel.stopContainer(it) } },
                                                onRemoveAll = { ids -> ids.forEach { viewModel.removeContainer(it) } },
                                            )
                                        }
                                        is ContainerListItem.ContainerItem -> {
                                            ContainerRowByMode(
                                                isCompactMode = isCompactMode,
                                                container = item.container,
                                                isChecked = item.container.id in selectedContainerIds,
                                                isViewingLogs = currentLogsContainerId == item.container.id,
                                                onCheckedChange = { checked ->
                                                    viewModel.toggleContainerSelection(item.container.id, checked)
                                                },
                                                isActionInProgress = actionInProgress == item.container.id,
                                                isPendingDelete = item.container.id in pendingDeleteIds,
                                                columnWidths = columnWidths,
                                                onStart = { viewModel.startContainer(item.container.id) },
                                                onStop = { viewModel.stopContainer(item.container.id) },
                                                onRestart = { viewModel.restartContainer(item.container.id) },
                                                onPause = { viewModel.pauseContainer(item.container.id) },
                                                onUnpause = { viewModel.unpauseContainer(item.container.id) },
                                                onRemove = {
                                                    askConfirm(
                                                        "Delete container?",
                                                        "This will force-stop and remove \"${item.container.displayName}\".",
                                                    ) { viewModel.removeContainer(item.container.id) }
                                                },
                                                onViewLogs = { onShowLogs(item.container) },
                                            )
                                        }
                                    }
                                }
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
                                        columnWidths = columnWidths,
                                        onColumnWidthsChange = { columnWidths = it },
                                        onColumnWidthsChangeFinished = { viewModel.setColumnWidths(columnWidths) },
                                    )
                                }
                            }
                        }

                        items(
                            otherGrouped,
                            key = { item ->
                                when (item) {
                                    is ContainerListItem.ComposeGroupHeader -> "other-group-${item.projectName}"
                                    is ContainerListItem.ContainerItem -> "other-${item.container.id}"
                                }
                            },
                        ) { item ->
                            Column(
                                modifier =
                                    Modifier.animateItem(
                                        fadeInSpec = tween(600),
                                        fadeOutSpec = tween(800),
                                        placementSpec = tween(500),
                                    ),
                            ) {
                                AnimatedVisibility(
                                    visible = otherVisible,
                                    enter = expandVertically(),
                                    exit = shrinkVertically(),
                                ) {
                                    when (item) {
                                        is ContainerListItem.ComposeGroupHeader -> {
                                            ComposeProjectCard(
                                                item = item,
                                                sectionPrefix = "other",
                                                isCompactMode = isCompactMode,
                                                selectedContainerIds = selectedContainerIds,
                                                pendingDeleteIds = pendingDeleteIds,
                                                columnWidths = columnWidths,
                                                currentLogsContainerId = currentLogsContainerId,
                                                actionInProgress = actionInProgress,
                                                statsById = statsById,
                                                onToggle = { toggleOtherGroup(item.projectName) },
                                                onSelectAll = { selectAll ->
                                                    if (selectAll) {
                                                        viewModel.selectAllContainers(
                                                            (selectedContainerIds + item.containers.map { it.id }).toList(),
                                                        )
                                                    } else {
                                                        item.containers.forEach {
                                                            viewModel.toggleContainerSelection(it.id, false)
                                                        }
                                                    }
                                                },
                                                onCheckedChange = { id, checked ->
                                                    viewModel.toggleContainerSelection(id, checked)
                                                },
                                                onStart = { viewModel.startContainer(it) },
                                                onStop = { viewModel.stopContainer(it) },
                                                onPause = { viewModel.pauseContainer(it) },
                                                onUnpause = { viewModel.unpauseContainer(it) },
                                                onRemove = { viewModel.removeContainer(it) },
                                                onViewLogs = { onShowLogs(it) },
                                                onViewGroupLogs = { onShowGroupLogs(it) },
                                                onStartAll = { ids -> ids.forEach { viewModel.startContainer(it) } },
                                                onStopAll = { ids -> ids.forEach { viewModel.stopContainer(it) } },
                                                onRemoveAll = { ids -> ids.forEach { viewModel.removeContainer(it) } },
                                            )
                                        }
                                        is ContainerListItem.ContainerItem -> {
                                            ContainerRowByMode(
                                                isCompactMode = isCompactMode,
                                                container = item.container,
                                                isChecked = item.container.id in selectedContainerIds,
                                                isViewingLogs = currentLogsContainerId == item.container.id,
                                                onCheckedChange = { checked ->
                                                    viewModel.toggleContainerSelection(item.container.id, checked)
                                                },
                                                isActionInProgress = actionInProgress == item.container.id,
                                                isPendingDelete = item.container.id in pendingDeleteIds,
                                                columnWidths = columnWidths,
                                                onStart = { viewModel.startContainer(item.container.id) },
                                                onStop = { viewModel.stopContainer(item.container.id) },
                                                onRestart = { viewModel.restartContainer(item.container.id) },
                                                onPause = { viewModel.pauseContainer(item.container.id) },
                                                onUnpause = { viewModel.unpauseContainer(item.container.id) },
                                                onRemove = {
                                                    askConfirm(
                                                        "Delete container?",
                                                        "This will force-stop and remove \"${item.container.displayName}\".",
                                                    ) { viewModel.removeContainer(item.container.id) }
                                                },
                                                onViewLogs = { onShowLogs(item.container) },
                                            )
                                        }
                                    }
                                }
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
private fun ComposeProjectHeader(
    projectName: String,
    containerCount: Int,
    expanded: Boolean,
    hasRunning: Boolean,
    onToggle: () -> Unit,
    allSelected: Boolean,
    onSelectAll: (Boolean) -> Unit,
    onViewGroupLogs: () -> Unit,
    onStartAll: () -> Unit,
    onStopAll: () -> Unit,
    onRemoveAll: () -> Unit,
    cpuPercent: Double? = null,
    memoryUsage: Long? = null,
) {
    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(30.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
                .clickable(onClick = onToggle)
                .padding(horizontal = 8.dp),
    ) {
        val showStats = maxWidth > 500.dp
        val showComposeBadge = maxWidth > 350.dp

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CompactCheckbox(
                checked = allSelected,
                onCheckedChange = onSelectAll,
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                contentDescription = if (expanded) "Collapse" else "Expand",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = projectName,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (showComposeBadge) {
                Surface(
                    shape = RoundedCornerShape(3.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                ) {
                    Text(
                        text = "Compose",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
            ) {
                Text(
                    text = containerCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            if (showStats && (cpuPercent != null || memoryUsage != null)) {
                cpuPercent?.let {
                    Text(
                        text = "CPU %.1f%%".format(it),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                memoryUsage?.let {
                    Text(
                        text = "MEM ${ContainerStats.formatBytes(it)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Group actions — pushed to far right
            IconButton(
                onClick = onViewGroupLogs,
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.Article,
                    contentDescription = "View group logs",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (hasRunning) {
                IconButton(onClick = onStopAll, modifier = Modifier.size(24.dp)) {
                    Icon(
                        Icons.Outlined.Stop,
                        contentDescription = "Stop all",
                        modifier = Modifier.size(14.dp),
                        tint = AppColors.Stopped,
                    )
                }
            } else {
                IconButton(onClick = onStartAll, modifier = Modifier.size(24.dp)) {
                    Icon(
                        Icons.Outlined.PlayArrow,
                        contentDescription = "Start all",
                        modifier = Modifier.size(14.dp),
                        tint = AppColors.Running,
                    )
                }
            }
            IconButton(onClick = onRemoveAll, modifier = Modifier.size(24.dp)) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Delete all",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ComposeProjectCard(
    item: ContainerListItem.ComposeGroupHeader,
    sectionPrefix: String,
    isCompactMode: Boolean,
    selectedContainerIds: Set<String>,
    pendingDeleteIds: Set<String>,
    columnWidths: ContainerColumnWidths,
    currentLogsContainerId: String?,
    actionInProgress: String?,
    statsById: Map<String, ContainerStats>,
    onToggle: () -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onCheckedChange: (String, Boolean) -> Unit,
    onStart: (String) -> Unit,
    onStop: (String) -> Unit,
    onPause: (String) -> Unit,
    onUnpause: (String) -> Unit,
    onRemove: (String) -> Unit,
    onViewLogs: (Container) -> Unit,
    onViewGroupLogs: (List<Container>) -> Unit,
    onStartAll: (List<String>) -> Unit,
    onStopAll: (List<String>) -> Unit,
    onRemoveAll: (List<String>) -> Unit,
) {
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val shape = RoundedCornerShape(12.dp)
    val animatedPadding by animateDpAsState(
        targetValue = if (item.expanded) 6.dp else 0.dp,
        animationSpec = tween(durationMillis = 250),
        label = "composeGroupPadding",
    )
    val animatedElevation by animateDpAsState(
        targetValue = if (item.expanded) 2.dp else 0.dp,
        animationSpec = tween(durationMillis = 250),
        label = "composeGroupElevation",
    )

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = animatedPadding)
                .then(
                    if (item.expanded) {
                        Modifier.border(1.dp, borderColor, shape)
                    } else {
                        Modifier
                    },
                ),
        shape = shape,
        elevation =
            CardDefaults.cardElevation(
                defaultElevation = animatedElevation,
            ),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (item.expanded) {
                        MaterialTheme.colorScheme.surface
                    } else {
                        MaterialTheme.colorScheme.background
                    },
            ),
    ) {
        val groupStats = item.containers.mapNotNull { statsById[it.id] }
        val totalCpu = groupStats.sumOf { it.cpuPercent }
        val totalMem = groupStats.sumOf { it.memoryUsage }

        ComposeProjectHeader(
            projectName = item.projectName,
            containerCount = item.containers.size,
            expanded = item.expanded,
            hasRunning = item.containers.any { it.isRunning },
            onToggle = onToggle,
            allSelected = item.containers.all { it.id in selectedContainerIds },
            onSelectAll = onSelectAll,
            onViewGroupLogs = { onViewGroupLogs(item.containers) },
            onStartAll = { onStartAll(item.containers.map { it.id }) },
            onStopAll = { onStopAll(item.containers.map { it.id }) },
            onRemoveAll = { onRemoveAll(item.containers.map { it.id }) },
            cpuPercent = if (groupStats.isNotEmpty()) totalCpu else null,
            memoryUsage = if (groupStats.isNotEmpty()) totalMem else null,
        )
        AnimatedVisibility(
            visible = item.expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column {
                item.containers.forEach { container ->
                    ContainerRowByMode(
                        isCompactMode = isCompactMode,
                        container = container,
                        isChecked = container.id in selectedContainerIds,
                        isViewingLogs = currentLogsContainerId == container.id,
                        onCheckedChange = { checked -> onCheckedChange(container.id, checked) },
                        isActionInProgress = actionInProgress == container.id,
                        isPendingDelete = container.id in pendingDeleteIds,
                        columnWidths = columnWidths,
                        onStart = { onStart(container.id) },
                        onStop = { onStop(container.id) },
                        onPause = { onPause(container.id) },
                        onUnpause = { onUnpause(container.id) },
                        onRemove = { onRemove(container.id) },
                        onViewLogs = { onViewLogs(container) },
                    )
                }
            }
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
    isPendingDelete: Boolean,
    columnWidths: ContainerColumnWidths,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onPause: () -> Unit,
    onUnpause: () -> Unit,
    onRemove: () -> Unit,
    onViewLogs: () -> Unit,
    onRestart: () -> Unit = {},
) {
    var ctxMenuExpanded by remember { mutableStateOf(false) }
    var pressOffset by remember { mutableStateOf(DpOffset.Zero) }
    var inspectOpen by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    Box(
        modifier =
            Modifier.pointerInput(container.id) {
                awaitEachGesture {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull()
                    if (change != null && event.buttons.isSecondaryPressed && change.changedToDown()) {
                        change.consume()
                        pressOffset =
                            with(density) {
                                DpOffset(change.position.x.toDp(), change.position.y.toDp())
                            }
                        ctxMenuExpanded = true
                    }
                }
            },
    ) {
        if (isCompactMode) {
            CompactContainerRow(
                container = container,
                isChecked = isChecked,
                isViewingLogs = isViewingLogs,
                onCheckedChange = onCheckedChange,
                isActionInProgress = isActionInProgress,
                isPendingDelete = isPendingDelete,
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
                isPendingDelete = isPendingDelete,
                columnWidths = columnWidths,
                onStart = onStart,
                onStop = onStop,
                onPause = onPause,
                onUnpause = onUnpause,
                onRemove = onRemove,
                onViewLogs = onViewLogs,
            )
        }

        ContainerContextMenu(
            container = container,
            expanded = ctxMenuExpanded,
            onDismiss = { ctxMenuExpanded = false },
            isViewingLogs = isViewingLogs,
            offset = pressOffset,
            onViewLogs = onViewLogs,
            onStart = onStart,
            onStop = onStop,
            onRestart = onRestart,
            onPause = onPause,
            onUnpause = onUnpause,
            onInspect = { inspectOpen = true },
            onCopyId = { copyToClipboard(container.id) },
            onRemove = onRemove,
        )

        if (inspectOpen) {
            ResourceDetailsDialog(
                target = DetailsTarget.ContainerTarget(container.id, container.displayName),
                onDismiss = { inspectOpen = false },
            )
        }
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
                .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CompactCheckbox(
            checked = allSelected,
            onCheckedChange = onSelectAllChange,
            enabled = hasItems,
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

        Spacer(modifier = Modifier.width(24.dp))
    }
}

@Composable
private fun CompactContainerRow(
    container: Container,
    isChecked: Boolean,
    isViewingLogs: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isActionInProgress: Boolean,
    isPendingDelete: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onPause: () -> Unit,
    onUnpause: () -> Unit,
    onRemove: () -> Unit,
    onViewLogs: () -> Unit,
) {
    var showActionsMenu by remember { mutableStateOf(false) }
    val rowAlpha by animateFloatAsState(
        targetValue = if (isPendingDelete) 0.45f else 1f,
        animationSpec = tween(200),
        label = "containerRowAlpha",
    )

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = rowAlpha }
                .background(
                    when {
                        isPendingDelete -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        isViewingLogs -> AppColors.AccentBlue.copy(alpha = 0.15f)
                        isChecked -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                        else -> MaterialTheme.colorScheme.surface
                    },
                ).clickable(enabled = !isPendingDelete) { onViewLogs() }
                .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CompactCheckbox(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            enabled = !isPendingDelete,
        )

        // Container info (Name + Image below)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = container.displayName,
                style = MaterialTheme.typography.bodySmall,
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
                    style = MaterialTheme.typography.labelSmall,
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
            if (isPendingDelete || isActionInProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color =
                        if (isPendingDelete) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                )
            } else {
                IconButton(
                    onClick = { showActionsMenu = true },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Actions",
                        modifier = Modifier.size(14.dp),
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
    columnWidths: ContainerColumnWidths,
    onColumnWidthsChange: (ContainerColumnWidths) -> Unit,
    onColumnWidthsChangeFinished: () -> Unit,
) {
    Column {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompactCheckbox(
                checked = allSelected && hasItems,
                onCheckedChange = onSelectAllChange,
                enabled = hasItems,
            )
            Spacer(modifier = Modifier.width(10.dp))

            SortableHeaderCell(
                text = "NAME",
                column = SortColumn.NAME,
                currentSortColumn = sortColumn,
                sortDirection = sortDirection,
                onSortChange = onSortChange,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(12.dp))
            SortableHeaderCell(
                text = "IMAGE",
                column = SortColumn.IMAGE,
                currentSortColumn = sortColumn,
                sortDirection = sortDirection,
                onSortChange = onSortChange,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(12.dp))
            SortableHeaderCell(
                text = "STATUS",
                column = SortColumn.STATUS,
                currentSortColumn = sortColumn,
                sortDirection = sortDirection,
                onSortChange = onSortChange,
                modifier = Modifier.width(columnWidths.status.dp),
            )
            ColumnResizeHandle(
                onResize = { deltaDp ->
                    onColumnWidthsChange(
                        columnWidths.copy(
                            status = (columnWidths.status + deltaDp).coerceAtLeast(ContainerColumnWidths.MIN),
                        ),
                    )
                },
                onResizeFinished = onColumnWidthsChangeFinished,
            )
            SortableHeaderCell(
                text = "PORTS",
                column = SortColumn.PORTS,
                currentSortColumn = sortColumn,
                sortDirection = sortDirection,
                onSortChange = onSortChange,
                modifier = Modifier.width(200.dp),
            )
            Spacer(modifier = Modifier.width(108.dp))
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
            thickness = 1.dp,
        )
    }
}

@Composable
private fun ColumnResizeHandle(
    onResize: (Float) -> Unit,
    onResizeFinished: () -> Unit,
) {
    val density = LocalDensity.current
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val currentOnResize by rememberUpdatedState(onResize)
    val currentOnFinished by rememberUpdatedState(onResizeFinished)
    Box(
        modifier =
            Modifier
                .width(12.dp)
                .height(24.dp)
                .hoverable(interactionSource)
                .pointerHoverIcon(PointerIcon.Crosshair)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = { currentOnFinished() },
                        onDragCancel = { currentOnFinished() },
                    ) { change, dragAmount ->
                        change.consume()
                        val deltaDp = with(density) { dragAmount.toDp().value }
                        currentOnResize(deltaDp)
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .width(if (isHovered) 4.dp else 2.dp)
                    .height(20.dp)
                    .background(
                        if (isHovered) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                        },
                    ),
        )
    }
}

@Composable
private fun ExpandedContainerRow(
    container: Container,
    isChecked: Boolean,
    isViewingLogs: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isActionInProgress: Boolean,
    isPendingDelete: Boolean,
    columnWidths: ContainerColumnWidths,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onPause: () -> Unit,
    onUnpause: () -> Unit,
    onRemove: () -> Unit,
    onViewLogs: () -> Unit,
) {
    val rowAlpha by animateFloatAsState(
        targetValue = if (isPendingDelete) 0.45f else 1f,
        animationSpec = tween(200),
        label = "containerRowAlpha",
    )
    Column {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .graphicsLayer { alpha = rowAlpha }
                    .background(
                        when {
                            isPendingDelete -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            isViewingLogs -> AppColors.AccentBlue.copy(alpha = 0.15f)
                            isChecked -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                            else -> MaterialTheme.colorScheme.surface
                        },
                    ).clickable(enabled = !isPendingDelete) { onViewLogs() }
                    .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompactCheckbox(
                checked = isChecked,
                onCheckedChange = onCheckedChange,
                enabled = !isPendingDelete,
            )
            Spacer(modifier = Modifier.width(10.dp))

            // Name (displayName · shortId, single line) — elastic
            Text(
                text = "${container.displayName} · ${container.shortId}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(12.dp))

            // Image — elastic
            Text(
                text = container.image,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.width(12.dp))

            // Status
            Box(modifier = Modifier.width(columnWidths.status.dp)) {
                StatusBadge(status = container.state.toContainerStatus())
            }
            Spacer(modifier = Modifier.width(12.dp))

            // Ports — fixed, just wide enough for typical port strings
            Text(
                text = container.ports.firstOrNull()?.displayString ?: "-",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(200.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.width(12.dp))

            // Actions
            Row(
                modifier = Modifier.width(108.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isPendingDelete || isActionInProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color =
                            if (isPendingDelete) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                    )
                } else {
                    // Logs button
                    IconButton(
                        onClick = onViewLogs,
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            if (isViewingLogs) Icons.AutoMirrored.Filled.Article else Icons.AutoMirrored.Outlined.Article,
                            contentDescription = "View Logs",
                            modifier = Modifier.size(14.dp),
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
                            IconButton(onClick = onPause, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    Icons.Outlined.Pause,
                                    contentDescription = "Pause",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = onStop, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    Icons.Outlined.Stop,
                                    contentDescription = "Stop",
                                    modifier = Modifier.size(14.dp),
                                    tint = AppColors.Stopped,
                                )
                            }
                        }
                        container.isPaused -> {
                            IconButton(onClick = onUnpause, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    Icons.Outlined.PlayArrow,
                                    contentDescription = "Resume",
                                    modifier = Modifier.size(14.dp),
                                    tint = AppColors.Running,
                                )
                            }
                        }
                        else -> {
                            IconButton(onClick = onStart, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    Icons.Outlined.PlayArrow,
                                    contentDescription = "Start",
                                    modifier = Modifier.size(14.dp),
                                    tint = AppColors.Running,
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "Delete",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
            thickness = 0.5.dp,
        )
    }
}
