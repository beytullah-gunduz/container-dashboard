package com.containerdashboard.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.containerdashboard.data.models.Container
import com.containerdashboard.data.repository.ContainerColumnWidths
import com.containerdashboard.data.repository.PreferenceRepository
import com.containerdashboard.ui.components.AppTooltip
import com.containerdashboard.ui.components.ConfirmActionDialog
import com.containerdashboard.ui.components.DeleteAllContainersDialog
import com.containerdashboard.ui.components.DeletingAllContainersDialog
import com.containerdashboard.ui.components.EmptyState
import com.containerdashboard.ui.components.EmptyStateAction
import com.containerdashboard.ui.components.ErrorStateCard
import com.containerdashboard.ui.components.ListRowSkeleton
import com.containerdashboard.ui.components.LogsPaneLayout
import com.containerdashboard.ui.components.SearchBar
import com.containerdashboard.ui.components.SectionHeader
import com.containerdashboard.ui.icons.automirrored.outlined.ViewSidebar
import com.containerdashboard.ui.icons.outlined.AutoAwesome
import com.containerdashboard.ui.icons.outlined.DeleteForever
import com.containerdashboard.ui.icons.outlined.Error
import com.containerdashboard.ui.icons.outlined.RemoveDone
import com.containerdashboard.ui.icons.outlined.SearchOff
import com.containerdashboard.ui.icons.outlined.Stop
import com.containerdashboard.ui.icons.outlined.ViewAgenda
import com.containerdashboard.ui.icons.outlined.ViewInAr
import com.containerdashboard.ui.screens.viewmodel.ContainerFilter
import com.containerdashboard.ui.screens.viewmodel.ContainersScreenViewModel
import com.containerdashboard.ui.shortcuts.LocalSearchFocusRequester
import com.containerdashboard.ui.theme.Spacing

internal enum class SortColumn {
    NAME,
    IMAGE,
    STATUS,
    PORTS,
}

internal enum class SortDirection {
    ASCENDING,
    DESCENDING,
}

internal sealed interface ContainerListItem {
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ContainersScreen(
    onShowLogs: (Container) -> Unit = {},
    onShowGroupLogs: (List<Container>) -> Unit = {},
    currentLogsContainerId: String? = null,
    paneActionContainerId: String? = null,
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
    val expandedRunningProjects by viewModel.expandedRunningProjects.collectAsState()
    val expandedOtherProjects by viewModel.expandedOtherProjects.collectAsState()

    val toggleRunningGroup: (String) -> Unit = { project -> viewModel.toggleRunningProject(project) }
    val toggleOtherGroup: (String) -> Unit = { project -> viewModel.toggleOtherProject(project) }

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
    val effectiveActionInProgress = actionInProgress ?: paneActionContainerId
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
            modifier = Modifier.fillMaxSize().padding(Spacing.xl),
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

                val toolbarSpacing by animateDpAsState(
                    targetValue = if (iconOnly) 4.dp else 12.dp,
                    animationSpec = tween(durationMillis = 200),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(toolbarSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val hasSelection = selectedContainerIds.isNotEmpty()

                    // Connected button-group shapes. The visible set shifts with
                    // selection state, so leading/trailing are computed dynamically
                    // against whatever is actually showing right now.
                    val showClear = hasSelection
                    val showStop = hasSelection && runningSelectedCount > 0
                    val showDelSel = hasSelection
                    val showDelAll = containers.isNotEmpty()
                    val order =
                        buildList {
                            if (showClear) add("clear")
                            if (showStop) add("stop")
                            if (showDelSel) add("delSel")
                            if (showDelAll) add("delAll")
                        }
                    val solo = order.size <= 1
                    val leadingShape = ButtonGroupDefaults.connectedLeadingButtonShape
                    val trailingShape = ButtonGroupDefaults.connectedTrailingButtonShape
                    val middleShape = RoundedCornerShape(6.dp)
                    val soloShape = RoundedCornerShape(20.dp)
                    val shapeFor: (String) -> androidx.compose.ui.graphics.Shape = { name ->
                        when {
                            solo -> soloShape
                            order.firstOrNull() == name -> leadingShape
                            order.lastOrNull() == name -> trailingShape
                            else -> middleShape
                        }
                    }

                    ButtonGroup(
                        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                    ) {
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
                            AnimatedContent(
                                targetState = iconOnly,
                                transitionSpec = {
                                    (fadeIn(tween(200)) togetherWith fadeOut(tween(150)))
                                        .using(SizeTransform(clip = false))
                                },
                                label = "ClearButtonCompactToggle",
                            ) { compact ->
                                if (compact) {
                                    IconButton(onClick = { viewModel.clearSelection() }) {
                                        Icon(
                                            Icons.Outlined.RemoveDone,
                                            contentDescription = "Clear selection",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = { viewModel.clearSelection() },
                                        shape = shapeFor("clear"),
                                    ) {
                                        Icon(Icons.Outlined.RemoveDone, null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(Spacing.sm))
                                        Text("Clear")
                                    }
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
                            AnimatedContent(
                                targetState = iconOnly,
                                transitionSpec = {
                                    (fadeIn(tween(200)) togetherWith fadeOut(tween(150)))
                                        .using(SizeTransform(clip = false))
                                },
                                label = "StopButtonCompactToggle",
                            ) { compact ->
                                if (compact) {
                                    AppTooltip(label = "Stop $runningSelectedCount selected") {
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
                                        shape = shapeFor("stop"),
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
                                        Spacer(modifier = Modifier.width(Spacing.sm))
                                        Text("Stop $runningSelectedCount selected")
                                    }
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
                            AnimatedContent(
                                targetState = iconOnly,
                                transitionSpec = {
                                    (fadeIn(tween(200)) togetherWith fadeOut(tween(150)))
                                        .using(SizeTransform(clip = false))
                                },
                                label = "DeleteSelectedCompactToggle",
                            ) { compact ->
                                if (compact) {
                                    AppTooltip(label = "Delete ${selectedContainerIds.size} selected") {
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
                                                    Icons.Outlined.Delete,
                                                    contentDescription = "Delete ${selectedContainerIds.size} selected",
                                                    tint = MaterialTheme.colorScheme.error,
                                                )
                                            }
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
                                        shape = shapeFor("delSel"),
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
                                            Icon(Icons.Outlined.Delete, null, modifier = Modifier.size(18.dp))
                                        }
                                        Spacer(modifier = Modifier.width(Spacing.sm))
                                        Text("Delete ${selectedContainerIds.size} selected")
                                    }
                                }
                            }
                        }

                        // Delete All Button
                        if (containers.isNotEmpty()) {
                            AnimatedContent(
                                targetState = iconOnly,
                                transitionSpec = {
                                    (fadeIn(tween(200)) togetherWith fadeOut(tween(150)))
                                        .using(SizeTransform(clip = false))
                                },
                                label = "DeleteAllCompactToggle",
                            ) { compact ->
                                if (compact) {
                                    AppTooltip(label = "Delete all containers") {
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
                                                    Icons.Outlined.DeleteForever,
                                                    contentDescription = "Delete All",
                                                    tint = MaterialTheme.colorScheme.error,
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Button(
                                        onClick = { showDeleteAllDialog = true },
                                        enabled = !isDeletingAll && !isDeletingSelected,
                                        shape = shapeFor("delAll"),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    ) {
                                        if (isDeletingAll) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.onError,
                                            )
                                        } else {
                                            Icon(Icons.Outlined.DeleteForever, null, modifier = Modifier.size(18.dp))
                                        }
                                        Spacer(modifier = Modifier.width(Spacing.sm))
                                        Text("Delete All")
                                    }
                                }
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
                        AppTooltip(label = "Log panel layout") {
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
                    modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.lg),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Icon(Icons.Outlined.Error, null, tint = MaterialTheme.colorScheme.error)
                        Text(errorMessage, color = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.clearError() }) {
                            Icon(Icons.Outlined.Close, null)
                        }
                    }
                }
            }

            // Filters Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
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
                            { Icon(Icons.Outlined.Check, null, modifier = Modifier.size(16.dp)) }
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
                            { Icon(Icons.Outlined.Check, null, modifier = Modifier.size(16.dp)) }
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
                            { Icon(Icons.Outlined.Check, null, modifier = Modifier.size(16.dp)) }
                        } else {
                            null
                        },
                )
            }

            Spacer(modifier = Modifier.height(Spacing.lg))

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
                } else if (containerFilter != ContainerFilter.ALL) {
                    val label =
                        when (containerFilter) {
                            ContainerFilter.RUNNING -> "running"
                            ContainerFilter.STOPPED -> "stopped"
                            ContainerFilter.ALL -> "" // unreachable
                        }
                    EmptyState(
                        icon = Icons.Outlined.ViewInAr,
                        title = "No $label containers",
                        body = "No containers match the \"$label\" filter right now.",
                        action =
                            EmptyStateAction("Show all") {
                                containerFilter = ContainerFilter.ALL
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
                            SectionHeader(
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
                                                actionInProgress = effectiveActionInProgress,
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
                                                onPauseAll = { ids -> ids.forEach { viewModel.pauseContainer(it) } },
                                                onUnpauseAll = { ids -> ids.forEach { viewModel.unpauseContainer(it) } },
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
                                                isActionInProgress = effectiveActionInProgress == item.container.id,
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
                                Spacer(modifier = Modifier.height(Spacing.lg))
                            }
                            SectionHeader(
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
                                                actionInProgress = effectiveActionInProgress,
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
                                                onPauseAll = { ids -> ids.forEach { viewModel.pauseContainer(it) } },
                                                onUnpauseAll = { ids -> ids.forEach { viewModel.unpauseContainer(it) } },
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
                                                isActionInProgress = effectiveActionInProgress == item.container.id,
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
