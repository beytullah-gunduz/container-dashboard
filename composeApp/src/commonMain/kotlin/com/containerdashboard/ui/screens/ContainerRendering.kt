package com.containerdashboard.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.containerdashboard.data.models.Container
import com.containerdashboard.data.models.ContainerStats
import com.containerdashboard.data.repository.ContainerColumnWidths
import com.containerdashboard.ui.components.AppTooltip
import com.containerdashboard.ui.components.CompactCheckbox
import com.containerdashboard.ui.components.DetailsTarget
import com.containerdashboard.ui.components.ResourceDetailsDialog
import com.containerdashboard.ui.components.StatusBadge
import com.containerdashboard.ui.components.TruncatingText
import com.containerdashboard.ui.components.toContainerStatus
import com.containerdashboard.ui.screens.components.ContainerContextMenu
import com.containerdashboard.ui.theme.AppColors
import com.containerdashboard.ui.theme.Radius
import com.containerdashboard.ui.theme.Spacing
import com.containerdashboard.ui.util.copyToClipboard
import com.containerdashboard.ui.util.hoverHighlight

@Composable
internal fun ComposeProjectHeader(
    projectName: String,
    containerCount: Int,
    expanded: Boolean,
    hasRunning: Boolean,
    hasPaused: Boolean,
    onToggle: () -> Unit,
    allSelected: Boolean,
    onSelectAll: (Boolean) -> Unit,
    onViewGroupLogs: () -> Unit,
    onStartAll: () -> Unit,
    onStopAll: () -> Unit,
    onPauseAll: () -> Unit,
    onUnpauseAll: () -> Unit,
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
                .hoverHighlight()
                .clickable(onClick = onToggle)
                .padding(horizontal = 8.dp),
    ) {
        val showStats = maxWidth > 500.dp
        val showComposeBadge = maxWidth > 350.dp

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
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
            TruncatingText(
                text = projectName,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (showComposeBadge) {
                Surface(
                    shape = RoundedCornerShape(Radius.xs),
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
                        text = "Mem ${ContainerStats.formatBytes(it)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Group actions — packed tightly at far right to align with per-container action column
            Row(
                modifier = Modifier.width(108.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppTooltip(label = "View group logs") {
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
                }
                if (hasRunning) {
                    AppTooltip(label = "Pause all") {
                        IconButton(onClick = onPauseAll, modifier = Modifier.size(24.dp)) {
                            Icon(
                                Icons.Outlined.Pause,
                                contentDescription = "Pause all",
                                modifier = Modifier.size(14.dp),
                                tint = AppColors.Paused,
                            )
                        }
                    }
                } else if (hasPaused) {
                    AppTooltip(label = "Unpause all") {
                        IconButton(onClick = onUnpauseAll, modifier = Modifier.size(24.dp)) {
                            Icon(
                                Icons.Outlined.PlayArrow,
                                contentDescription = "Unpause all",
                                modifier = Modifier.size(14.dp),
                                tint = AppColors.Running,
                            )
                        }
                    }
                }
                if (hasRunning) {
                    AppTooltip(label = "Stop all") {
                        IconButton(onClick = onStopAll, modifier = Modifier.size(24.dp)) {
                            Icon(
                                Icons.Outlined.Stop,
                                contentDescription = "Stop all",
                                modifier = Modifier.size(14.dp),
                                tint = AppColors.Stopped,
                            )
                        }
                    }
                } else {
                    AppTooltip(label = "Start all") {
                        IconButton(onClick = onStartAll, modifier = Modifier.size(24.dp)) {
                            Icon(
                                Icons.Outlined.PlayArrow,
                                contentDescription = "Start all",
                                modifier = Modifier.size(14.dp),
                                tint = AppColors.Running,
                            )
                        }
                    }
                }
                AppTooltip(label = "Delete all") {
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
    }
}

@Composable
internal fun ComposeProjectCard(
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
    onPauseAll: (List<String>) -> Unit,
    onUnpauseAll: (List<String>) -> Unit,
    onRemoveAll: (List<String>) -> Unit,
) {
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val shape = RoundedCornerShape(Radius.lg)
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
            hasPaused = item.containers.any { it.isPaused },
            onToggle = onToggle,
            allSelected = item.containers.all { it.id in selectedContainerIds },
            onSelectAll = onSelectAll,
            onViewGroupLogs = { onViewGroupLogs(item.containers) },
            onStartAll = { onStartAll(item.containers.map { it.id }) },
            onStopAll = { onStopAll(item.containers.map { it.id }) },
            onPauseAll = { onPauseAll(item.containers.filter { it.isRunning }.map { it.id }) },
            onUnpauseAll = { onUnpauseAll(item.containers.filter { it.isPaused }.map { it.id }) },
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
internal fun ContainerRowByMode(
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
internal fun SortableHeaderCell(
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
                .clip(RoundedCornerShape(Radius.sm))
                .clickable { onSortChange(column) }
                .padding(vertical = Spacing.xs, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
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
internal fun CompactTableHeader(
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
                .padding(horizontal = Spacing.sm, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
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
internal fun CompactContainerRow(
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
                ).hoverHighlight()
                .clickable(enabled = !isPendingDelete) { onViewLogs() }
                .padding(horizontal = Spacing.sm, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        CompactCheckbox(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            enabled = !isPendingDelete,
        )

        // Container info (Name + Image below)
        Column(modifier = Modifier.weight(1f)) {
            TruncatingText(
                text = container.displayName,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
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
                        Icons.Outlined.MoreVert,
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
internal fun ExpandedTableHeader(
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
                    .padding(horizontal = Spacing.sm),
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
            Spacer(modifier = Modifier.width(Spacing.md))
            SortableHeaderCell(
                text = "IMAGE",
                column = SortColumn.IMAGE,
                currentSortColumn = sortColumn,
                sortDirection = sortDirection,
                onSortChange = onSortChange,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(Spacing.md))
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
internal fun ColumnResizeHandle(
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
internal fun ExpandedContainerRow(
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
                    ).hoverHighlight()
                    .clickable(enabled = !isPendingDelete) { onViewLogs() }
                    .padding(horizontal = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompactCheckbox(
                checked = isChecked,
                onCheckedChange = onCheckedChange,
                enabled = !isPendingDelete,
            )
            Spacer(modifier = Modifier.width(10.dp))

            // Name (displayName · shortId, single line) — elastic
            TruncatingText(
                text = "${container.displayName} · ${container.shortId}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(Spacing.md))

            // Image — elastic
            Text(
                text = container.image,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.width(Spacing.md))

            // Status
            Box(modifier = Modifier.width(columnWidths.status.dp)) {
                StatusBadge(status = container.state.toContainerStatus())
            }
            Spacer(modifier = Modifier.width(Spacing.md))

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
            Spacer(modifier = Modifier.width(Spacing.md))

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
                            tint = MaterialTheme.colorScheme.error,
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
