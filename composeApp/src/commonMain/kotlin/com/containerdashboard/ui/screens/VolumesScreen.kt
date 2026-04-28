package com.containerdashboard.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.containerdashboard.data.models.Volume
import com.containerdashboard.data.repository.PreferenceRepository
import com.containerdashboard.ui.components.AppTooltip
import com.containerdashboard.ui.components.CompactCheckbox
import com.containerdashboard.ui.components.ConfirmActionDialog
import com.containerdashboard.ui.components.CreateResourceDialog
import com.containerdashboard.ui.components.DetailsTarget
import com.containerdashboard.ui.components.EmptyState
import com.containerdashboard.ui.components.EmptyStateAction
import com.containerdashboard.ui.components.ErrorStateCard
import com.containerdashboard.ui.components.ListRowSkeleton
import com.containerdashboard.ui.components.ResourceDetailsDialog
import com.containerdashboard.ui.components.SearchBar
import com.containerdashboard.ui.components.SectionHeader
import com.containerdashboard.ui.icons.filled.ArrowDownward
import com.containerdashboard.ui.icons.filled.ArrowUpward
import com.containerdashboard.ui.icons.outlined.Error
import com.containerdashboard.ui.icons.outlined.SearchOff
import com.containerdashboard.ui.icons.outlined.Storage
import com.containerdashboard.ui.screens.components.VolumeContextMenu
import com.containerdashboard.ui.screens.viewmodel.SortDirection
import com.containerdashboard.ui.screens.viewmodel.VolumeSortColumn
import com.containerdashboard.ui.screens.viewmodel.VolumesScreenViewModel
import com.containerdashboard.ui.theme.Radius
import com.containerdashboard.ui.theme.Spacing
import com.containerdashboard.ui.util.copyToClipboard
import com.containerdashboard.ui.util.hoverHighlight

@Composable
fun VolumesScreen(
    modifier: Modifier = Modifier,
    viewModel: VolumesScreenViewModel = viewModel { VolumesScreenViewModel() },
) {
    val volumes by viewModel.volumes.collectAsState(listOf())
    val hasLoaded by viewModel.hasLoaded.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedVolume by viewModel.selectedVolumeName.collectAsState()
    val showCreateDialog by viewModel.showCreateDialog.collectAsState()
    val sortColumn by viewModel.sortColumn.collectAsState()
    val sortDirection by viewModel.sortDirection.collectAsState()
    val error by viewModel.error.collectAsState()
    val checkedVolumeNames by viewModel.checkedVolumeNames.collectAsState()
    val isDeletingSelected by viewModel.isDeletingSelected.collectAsState()

    // Resizable column weights (persisted)
    val persistedWeights by PreferenceRepository.volumeColumnWeights().collectAsState(
        initial = com.containerdashboard.data.repository.VolumeColumnWeights.Default,
    )
    var nameWeight by remember(persistedWeights) { mutableFloatStateOf(persistedWeights.name) }
    var driverWeight by remember(persistedWeights) { mutableFloatStateOf(persistedWeights.driver) }
    var mountpointWeight by remember(persistedWeights) { mutableFloatStateOf(persistedWeights.mountpoint) }
    val totalWeight = nameWeight + driverWeight + mountpointWeight
    LaunchedEffect(nameWeight, driverWeight, mountpointWeight) {
        kotlinx.coroutines.delay(300)
        PreferenceRepository.setVolumeColumnWeights(
            com.containerdashboard.data.repository
                .VolumeColumnWeights(nameWeight, driverWeight, mountpointWeight),
        )
    }

    val confirmBeforeDelete by PreferenceRepository.confirmBeforeDelete().collectAsState(initial = true)
    var pendingConfirmTitle by remember { mutableStateOf("") }
    var pendingConfirmBody by remember { mutableStateOf("") }
    var pendingConfirmAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    var namedVisible by remember { mutableStateOf(true) }
    var anonymousVisible by remember { mutableStateOf(true) }

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

    val filteredVolumes =
        volumes
            .filter { volume ->
                searchQuery.isEmpty() || volume.name.contains(searchQuery, ignoreCase = true)
            }.let { list ->
                val ascending = sortDirection == SortDirection.ASC
                when (sortColumn) {
                    VolumeSortColumn.NAME ->
                        if (ascending) {
                            list.sortedBy { it.name.lowercase() }
                        } else {
                            list.sortedByDescending {
                                it.name
                                    .lowercase()
                            }
                        }
                    VolumeSortColumn.DRIVER ->
                        if (ascending) {
                            list.sortedBy { it.driver.lowercase() }
                        } else {
                            list.sortedByDescending {
                                it.driver
                                    .lowercase()
                            }
                        }
                    VolumeSortColumn.MOUNTPOINT ->
                        if (ascending) {
                            list.sortedBy {
                                it.mountpoint.lowercase()
                            }
                        } else {
                            list.sortedByDescending { it.mountpoint.lowercase() }
                        }
                }
            }

    // Create Volume Dialog
    if (showCreateDialog) {
        var volumeName by remember { mutableStateOf("") }
        var submitAttempted by remember { mutableStateOf(false) }
        val showNameError = submitAttempted && volumeName.isBlank()
        CreateResourceDialog(
            title = "Create Volume",
            confirmEnabled = volumeName.isNotBlank(),
            onConfirm = {
                if (volumeName.isNotBlank()) {
                    viewModel.createVolume(volumeName)
                    viewModel.setShowCreateDialog(false)
                }
            },
            onDismiss = { viewModel.setShowCreateDialog(false) },
            onBlockedConfirm = { submitAttempted = true },
        ) {
            OutlinedTextField(
                value = volumeName,
                onValueChange = { volumeName = it },
                label = { Text("Volume Name") },
                singleLine = true,
                isError = showNameError,
                supportingText =
                    if (showNameError) {
                        { Text("Name is required") }
                    } else {
                        null
                    },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isCompactMode = maxWidth < COMPACT_THRESHOLD

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
                        text = "Volumes",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${volumes.size} volumes",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    if (checkedVolumeNames.isNotEmpty()) {
                        Button(
                            onClick = {
                                askConfirm(
                                    "Delete selected volumes?",
                                    "This will delete ${checkedVolumeNames.size} volume(s).",
                                ) { viewModel.deleteSelectedVolumes() }
                            },
                            enabled = !isDeletingSelected,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
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
                            Text("Delete ${checkedVolumeNames.size} selected")
                        }
                        OutlinedButton(onClick = { viewModel.clearChecked() }) {
                            Icon(Icons.Outlined.Close, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(Spacing.sm))
                            Text("Clear")
                        }
                    }
                    Button(onClick = { viewModel.setShowCreateDialog(true) }) {
                        Icon(Icons.Outlined.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Text("Create volume")
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

            // Search
            SearchBar(
                query = searchQuery,
                onQueryChange = { viewModel.setSearchQuery(it) },
                placeholder = "Search volumes...",
                modifier = if (isCompactMode) Modifier.fillMaxWidth() else Modifier.fillMaxWidth(0.4f),
                compact = isCompactMode,
            )

            Spacer(modifier = Modifier.height(Spacing.lg))

            if (!hasLoaded && volumes.isEmpty()) {
                if (error != null) {
                    ErrorStateCard(
                        message = error ?: "Could not load volumes",
                        onRetry = {
                            viewModel.clearError()
                            viewModel.refresh()
                        },
                    )
                } else {
                    ListRowSkeleton(rowCount = 6)
                }
            } else if (filteredVolumes.isEmpty()) {
                if (searchQuery.isNotEmpty()) {
                    EmptyState(
                        icon = Icons.Outlined.SearchOff,
                        title = "No matches",
                        body = "Nothing matched \"$searchQuery\".",
                        action =
                            EmptyStateAction("Clear search") {
                                viewModel.setSearchQuery("")
                            },
                    )
                } else {
                    EmptyState(
                        icon = Icons.Outlined.Storage,
                        title = "No volumes",
                        body = "Volumes persist container data. Create one to get started.",
                        action =
                            EmptyStateAction("Create volume") {
                                viewModel.setShowCreateDialog(true)
                            },
                    )
                }
            } else {
                val namedVolumes = filteredVolumes.filterNot { it.isAnonymous }
                val anonymousVolumes = filteredVolumes.filter { it.isAnonymous }
                val onResizeName: (Float) -> Unit = { delta ->
                    val newName = (nameWeight + delta).coerceIn(0.5f, totalWeight - 1f)
                    val newDriver = (driverWeight - delta).coerceIn(0.3f, totalWeight - 1f)
                    if (newName >= 0.5f && newDriver >= 0.3f) {
                        nameWeight = newName
                        driverWeight = newDriver
                    }
                }
                val onResizeDriver: (Float) -> Unit = { delta ->
                    val newDriver = (driverWeight + delta).coerceIn(0.3f, totalWeight - 1f)
                    val newMount = (mountpointWeight - delta).coerceIn(0.5f, totalWeight - 1f)
                    if (newDriver >= 0.3f && newMount >= 0.5f) {
                        driverWeight = newDriver
                        mountpointWeight = newMount
                    }
                }
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    volumeSection(
                        keyPrefix = "named",
                        title = "Named",
                        volumes = namedVolumes,
                        expanded = namedVisible,
                        onToggleExpanded = { namedVisible = !namedVisible },
                        checkedVolumeNames = checkedVolumeNames,
                        selectedVolume = selectedVolume,
                        sortColumn = sortColumn,
                        sortDirection = sortDirection,
                        onSort = { viewModel.toggleSort(it) },
                        onCheckAll = { selectAll ->
                            if (selectAll) {
                                viewModel.checkAll(
                                    (checkedVolumeNames + namedVolumes.map { it.name }).toList(),
                                )
                            } else {
                                namedVolumes.forEach { viewModel.toggleChecked(it.name, false) }
                            }
                        },
                        onCheckedChange = { id, checked -> viewModel.toggleChecked(id, checked) },
                        onSelect = { viewModel.setSelectedVolume(it) },
                        onRemove = { volume ->
                            askConfirm(
                                "Delete volume?",
                                "This will delete \"${volume.name}\".",
                            ) { viewModel.removeVolume(volume.name) }
                        },
                        nameWeight = nameWeight,
                        driverWeight = driverWeight,
                        mountpointWeight = mountpointWeight,
                        onResizeName = onResizeName,
                        onResizeDriver = onResizeDriver,
                        isCompactMode = isCompactMode,
                    )
                    volumeSection(
                        keyPrefix = "anonymous",
                        title = "Anonymous",
                        volumes = anonymousVolumes,
                        expanded = anonymousVisible,
                        onToggleExpanded = { anonymousVisible = !anonymousVisible },
                        checkedVolumeNames = checkedVolumeNames,
                        selectedVolume = selectedVolume,
                        sortColumn = sortColumn,
                        sortDirection = sortDirection,
                        onSort = { viewModel.toggleSort(it) },
                        onCheckAll = { selectAll ->
                            if (selectAll) {
                                viewModel.checkAll(
                                    (checkedVolumeNames + anonymousVolumes.map { it.name }).toList(),
                                )
                            } else {
                                anonymousVolumes.forEach { viewModel.toggleChecked(it.name, false) }
                            }
                        },
                        onCheckedChange = { id, checked -> viewModel.toggleChecked(id, checked) },
                        onSelect = { viewModel.setSelectedVolume(it) },
                        onRemove = { volume ->
                            askConfirm(
                                "Delete volume?",
                                "This will delete \"${volume.name}\".",
                            ) { viewModel.removeVolume(volume.name) }
                        },
                        nameWeight = nameWeight,
                        driverWeight = driverWeight,
                        mountpointWeight = mountpointWeight,
                        onResizeName = onResizeName,
                        onResizeDriver = onResizeDriver,
                        isCompactMode = isCompactMode,
                        precedingSpacer = namedVolumes.isNotEmpty(),
                    )
                }
            }
        }
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
}

@Composable
private fun VolumeTableHeader(
    allSelected: Boolean,
    onSelectAllChange: (Boolean) -> Unit,
    hasItems: Boolean,
    sortColumn: VolumeSortColumn,
    sortDirection: SortDirection,
    onSort: (VolumeSortColumn) -> Unit,
    nameWeight: Float,
    driverWeight: Float,
    mountpointWeight: Float,
    onResizeName: (Float) -> Unit,
    onResizeDriver: (Float) -> Unit,
    isCompactMode: Boolean,
    selectAllTooltip: String = "Select all",
) {
    Column {
        if (isCompactMode) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(horizontal = Spacing.sm, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AppTooltip(label = selectAllTooltip) {
                    CompactCheckbox(
                        checked = allSelected && hasItems,
                        onCheckedChange = onSelectAllChange,
                        enabled = hasItems,
                    )
                }
                VolumeSortableHeaderCell("VOLUME", VolumeSortColumn.NAME, sortColumn, sortDirection, onSort, Modifier.weight(1f))
                Spacer(modifier = Modifier.width(Spacing.xl))
            }
        } else {
            BoxWithConstraints(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            ) {
                val totalWeight = nameWeight + driverWeight + mountpointWeight
                val pxPerWeight = constraints.maxWidth / totalWeight

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AppTooltip(label = selectAllTooltip) {
                        CompactCheckbox(
                            checked = allSelected && hasItems,
                            onCheckedChange = onSelectAllChange,
                            enabled = hasItems,
                        )
                    }
                    VolumeSortableHeaderCell("NAME", VolumeSortColumn.NAME, sortColumn, sortDirection, onSort, Modifier.weight(nameWeight))
                    ColumnResizeHandle { delta -> onResizeName(delta / pxPerWeight) }
                    VolumeSortableHeaderCell(
                        "DRIVER",
                        VolumeSortColumn.DRIVER,
                        sortColumn,
                        sortDirection,
                        onSort,
                        Modifier.weight(driverWeight),
                    )
                    ColumnResizeHandle { delta -> onResizeDriver(delta / pxPerWeight) }
                    VolumeSortableHeaderCell(
                        "MOUNTPOINT",
                        VolumeSortColumn.MOUNTPOINT,
                        sortColumn,
                        sortDirection,
                        onSort,
                        Modifier.weight(mountpointWeight),
                    )
                    Spacer(modifier = Modifier.width(Spacing.xxl))
                }
            }
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
            thickness = 1.dp,
        )
    }
}

@Composable
private fun ColumnResizeHandle(onDrag: (Float) -> Unit) {
    Box(
        modifier =
            Modifier
                .width(8.dp)
                .height(24.dp)
                .pointerHoverIcon(PointerIcon.Hand)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x)
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(1.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)),
        )
    }
}

@Composable
private fun VolumeSortableHeaderCell(
    label: String,
    column: VolumeSortColumn,
    currentSort: VolumeSortColumn,
    sortDirection: SortDirection,
    onSort: (VolumeSortColumn) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isActive = currentSort == column
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(Radius.sm))
                .clickable { onSort(column) }
                .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
        if (isActive) {
            Icon(
                imageVector = if (sortDirection == SortDirection.ASC) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                contentDescription = if (sortDirection == SortDirection.ASC) "Ascending" else "Descending",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun VolumeRow(
    volume: Volume,
    isSelected: Boolean,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    nameWeight: Float,
    driverWeight: Float,
    mountpointWeight: Float,
    isCompactMode: Boolean,
) {
    var ctxMenuExpanded by remember { mutableStateOf(false) }
    var pressOffset by remember { mutableStateOf(DpOffset.Zero) }
    var inspectOpen by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    Column(
        modifier =
            Modifier.pointerInput(volume.name) {
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
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(if (isCompactMode) Modifier else Modifier.height(30.dp))
                    .background(
                        when {
                            isChecked ->
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                            isSelected ->
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
                            else -> MaterialTheme.colorScheme.surface
                        },
                    ).hoverHighlight()
                    .clickable(onClick = onClick)
                    .padding(horizontal = Spacing.sm, vertical = if (isCompactMode) 6.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CompactCheckbox(checked = isChecked, onCheckedChange = onCheckedChange)
            if (isCompactMode) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Storage,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = volume.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = volume.driver,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                    )
                    Text(
                        text = volume.mountpoint,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                // Name
                Row(
                    modifier = Modifier.weight(nameWeight),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Outlined.Storage,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = volume.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Driver
                Text(
                    text = volume.driver,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(driverWeight),
                )

                // Mountpoint
                Text(
                    text = volume.mountpoint,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(mountpointWeight),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Actions
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
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
            thickness = 0.5.dp,
        )

        VolumeContextMenu(
            expanded = ctxMenuExpanded,
            onDismiss = { ctxMenuExpanded = false },
            offset = pressOffset,
            onInspect = { inspectOpen = true },
            onCopyName = { copyToClipboard(volume.name) },
            onRemove = onRemove,
        )

        if (inspectOpen) {
            ResourceDetailsDialog(
                target = DetailsTarget.VolumeTarget(volume.name),
                onDismiss = { inspectOpen = false },
            )
        }
    }
}

private fun LazyListScope.volumeSection(
    keyPrefix: String,
    title: String,
    volumes: List<Volume>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    checkedVolumeNames: Set<String>,
    selectedVolume: String?,
    sortColumn: VolumeSortColumn,
    sortDirection: SortDirection,
    onSort: (VolumeSortColumn) -> Unit,
    onCheckAll: (Boolean) -> Unit,
    onCheckedChange: (String, Boolean) -> Unit,
    onSelect: (String) -> Unit,
    onRemove: (Volume) -> Unit,
    nameWeight: Float,
    driverWeight: Float,
    mountpointWeight: Float,
    onResizeName: (Float) -> Unit,
    onResizeDriver: (Float) -> Unit,
    isCompactMode: Boolean,
    precedingSpacer: Boolean = false,
) {
    if (volumes.isEmpty()) return
    item(key = "$keyPrefix-header") {
        if (precedingSpacer) {
            Spacer(modifier = Modifier.height(Spacing.lg))
        }
        SectionHeader(
            title = title,
            count = volumes.size,
            expanded = expanded,
            onToggle = onToggleExpanded,
        )
    }
    item(key = "$keyPrefix-table-header") {
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            VolumeTableHeader(
                allSelected = volumes.all { it.name in checkedVolumeNames },
                onSelectAllChange = onCheckAll,
                hasItems = true,
                sortColumn = sortColumn,
                sortDirection = sortDirection,
                onSort = onSort,
                nameWeight = nameWeight,
                driverWeight = driverWeight,
                mountpointWeight = mountpointWeight,
                onResizeName = onResizeName,
                onResizeDriver = onResizeDriver,
                isCompactMode = isCompactMode,
                selectAllTooltip = "Select all $title",
            )
        }
    }
    items(volumes, key = { "$keyPrefix-${it.name}" }) { volume ->
        Column(
            modifier =
                Modifier.animateItem(
                    fadeInSpec = tween(600),
                    fadeOutSpec = tween(800),
                    placementSpec = tween(500),
                ),
        ) {
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                VolumeRow(
                    volume = volume,
                    isSelected = selectedVolume == volume.name,
                    isChecked = volume.name in checkedVolumeNames,
                    onCheckedChange = { onCheckedChange(volume.name, it) },
                    onClick = { onSelect(volume.name) },
                    onRemove = { onRemove(volume) },
                    nameWeight = nameWeight,
                    driverWeight = driverWeight,
                    mountpointWeight = mountpointWeight,
                    isCompactMode = isCompactMode,
                )
            }
        }
    }
}
