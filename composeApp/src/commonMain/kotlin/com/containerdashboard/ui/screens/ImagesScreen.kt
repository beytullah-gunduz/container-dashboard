package com.containerdashboard.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.containerdashboard.data.models.DockerImage
import com.containerdashboard.ui.components.CompactCheckbox
import com.containerdashboard.ui.components.InspectDialog
import com.containerdashboard.ui.components.SearchBar
import com.containerdashboard.ui.screens.components.ImageContextMenu
import com.containerdashboard.ui.screens.viewmodel.ImageSortColumn
import com.containerdashboard.ui.screens.viewmodel.ImagesScreenViewModel
import com.containerdashboard.ui.screens.viewmodel.SortDirection
import com.containerdashboard.ui.util.copyToClipboard
import kotlinx.serialization.encodeToString

// Threshold for switching between compact and expanded layouts.
// Kept in sync with ContainersScreen.COMPACT_THRESHOLD.
private val COMPACT_THRESHOLD = 700.dp

@Composable
fun ImagesScreen(
    modifier: Modifier = Modifier,
    viewModel: ImagesScreenViewModel = viewModel { ImagesScreenViewModel() },
) {
    val images by viewModel.images.collectAsState(listOf())
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedImage by viewModel.selectedImageId.collectAsState()
    val sortColumn by viewModel.sortColumn.collectAsState()
    val sortDirection by viewModel.sortDirection.collectAsState()
    val error by viewModel.error.collectAsState()
    val checkedImageIds by viewModel.checkedImageIds.collectAsState()
    val isDeletingSelected by viewModel.isDeletingSelected.collectAsState()

    var namedImagesVisible by remember { mutableStateOf(true) }
    var unnamedImagesVisible by remember { mutableStateOf(true) }

    val filteredImages =
        images
            .filter { image ->
                searchQuery.isEmpty() ||
                    image.repository.contains(searchQuery, ignoreCase = true) ||
                    image.tag.contains(searchQuery, ignoreCase = true)
            }.let { list: List<DockerImage> ->
                val ascending = sortDirection == SortDirection.ASC
                when (sortColumn) {
                    ImageSortColumn.REPOSITORY ->
                        if (ascending) {
                            list.sortedBy {
                                it.repository.lowercase()
                            }
                        } else {
                            list.sortedByDescending { it.repository.lowercase() }
                        }
                    ImageSortColumn.TAG ->
                        if (ascending) {
                            list.sortedBy {
                                it.tag.lowercase()
                            }
                        } else {
                            list.sortedByDescending { it.tag.lowercase() }
                        }
                    ImageSortColumn.IMAGE_ID ->
                        if (ascending) {
                            list.sortedBy {
                                it.shortId.lowercase()
                            }
                        } else {
                            list.sortedByDescending {
                                it.shortId
                                    .lowercase()
                            }
                        }
                    ImageSortColumn.SIZE -> if (ascending) list.sortedBy { it.size } else list.sortedByDescending { it.size }
                }
            }

    val namedImages = filteredImages.filter { it.repository != "<none>" }
    val unnamedImages = filteredImages.filter { it.repository == "<none>" }

    val totalSize = images.sumOf { it.size }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isCompactMode = maxWidth < COMPACT_THRESHOLD

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
                        text = "Images",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${images.size} images, ${formatBytes(totalSize)} total",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (checkedImageIds.isNotEmpty()) {
                        Button(
                            onClick = { viewModel.deleteSelectedImages() },
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
                                Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Delete ${checkedImageIds.size} selected")
                        }
                        OutlinedButton(onClick = { viewModel.clearChecked() }) {
                            Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Clear")
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

            // Search
            SearchBar(
                query = searchQuery,
                onQueryChange = { viewModel.setSearchQuery(it) },
                placeholder = "Search images...",
                modifier = if (isCompactMode) Modifier.fillMaxWidth() else Modifier.fillMaxWidth(0.4f),
                compact = isCompactMode,
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (filteredImages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No images found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    // Named images section
                    if (namedImages.isNotEmpty()) {
                        item(key = "named-header") {
                            ImageSectionHeader(
                                title = "Images",
                                count = namedImages.size,
                                expanded = namedImagesVisible,
                                onToggle = { namedImagesVisible = !namedImagesVisible },
                            )
                        }

                        item(key = "named-table-header") {
                            AnimatedVisibility(
                                visible = namedImagesVisible,
                                enter = expandVertically(),
                                exit = shrinkVertically(),
                            ) {
                                ImageTableHeader(
                                    sortColumn = sortColumn,
                                    sortDirection = sortDirection,
                                    onSort = { viewModel.toggleSort(it) },
                                    allSelected = namedImages.isNotEmpty() && namedImages.all { it.id in checkedImageIds },
                                    onSelectAllChange = { selected ->
                                        namedImages.forEach { viewModel.toggleChecked(it.id, selected) }
                                    },
                                    hasItems = namedImages.isNotEmpty(),
                                    isCompactMode = isCompactMode,
                                )
                            }
                        }

                        items(namedImages, key = { "named-${it.id}" }) { image ->
                            AnimatedVisibility(
                                visible = namedImagesVisible,
                                enter = expandVertically(),
                                exit = shrinkVertically(),
                            ) {
                                ImageRow(
                                    image = image,
                                    isSelected = selectedImage == image.id,
                                    isChecked = image.id in checkedImageIds,
                                    onCheckedChange = { viewModel.toggleChecked(image.id, it) },
                                    onClick = { viewModel.setSelectedImage(image.id) },
                                    onRemove = { viewModel.removeImage(image.id) },
                                    isCompactMode = isCompactMode,
                                )
                            }
                        }
                    }

                    // Unnamed images section
                    if (unnamedImages.isNotEmpty()) {
                        item(key = "unnamed-header") {
                            Spacer(modifier = Modifier.height(16.dp))
                            ImageSectionHeader(
                                title = "Dangling Images",
                                count = unnamedImages.size,
                                expanded = unnamedImagesVisible,
                                onToggle = { unnamedImagesVisible = !unnamedImagesVisible },
                            )
                        }

                        item(key = "unnamed-table-header") {
                            AnimatedVisibility(
                                visible = unnamedImagesVisible,
                                enter = expandVertically(),
                                exit = shrinkVertically(),
                            ) {
                                ImageTableHeader(
                                    sortColumn = sortColumn,
                                    sortDirection = sortDirection,
                                    onSort = { viewModel.toggleSort(it) },
                                    allSelected = unnamedImages.isNotEmpty() && unnamedImages.all { it.id in checkedImageIds },
                                    onSelectAllChange = { selected ->
                                        unnamedImages.forEach { viewModel.toggleChecked(it.id, selected) }
                                    },
                                    hasItems = unnamedImages.isNotEmpty(),
                                    isCompactMode = isCompactMode,
                                )
                            }
                        }

                        items(unnamedImages, key = { "unnamed-${it.id}" }) { image ->
                            AnimatedVisibility(
                                visible = unnamedImagesVisible,
                                enter = expandVertically(),
                                exit = shrinkVertically(),
                            ) {
                                ImageRow(
                                    image = image,
                                    isSelected = selectedImage == image.id,
                                    isChecked = image.id in checkedImageIds,
                                    onCheckedChange = { viewModel.toggleChecked(image.id, it) },
                                    onClick = { viewModel.setSelectedImage(image.id) },
                                    onRemove = { viewModel.removeImage(image.id) },
                                    isCompactMode = isCompactMode,
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
private fun ImageSectionHeader(
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
private fun ImageTableHeader(
    sortColumn: ImageSortColumn,
    sortDirection: SortDirection,
    onSort: (ImageSortColumn) -> Unit,
    allSelected: Boolean,
    onSelectAllChange: (Boolean) -> Unit,
    hasItems: Boolean,
    isCompactMode: Boolean,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CompactCheckbox(
            checked = allSelected && hasItems,
            onCheckedChange = onSelectAllChange,
            enabled = hasItems,
        )
        if (isCompactMode) {
            SortableHeaderCell("REPOSITORY", ImageSortColumn.REPOSITORY, sortColumn, sortDirection, onSort, Modifier.weight(1f))
        } else {
            SortableHeaderCell("REPOSITORY", ImageSortColumn.REPOSITORY, sortColumn, sortDirection, onSort, Modifier.weight(1.5f))
            SortableHeaderCell("TAG", ImageSortColumn.TAG, sortColumn, sortDirection, onSort, Modifier.weight(1f))
            SortableHeaderCell("IMAGE ID", ImageSortColumn.IMAGE_ID, sortColumn, sortDirection, onSort, Modifier.weight(1f))
            SortableHeaderCell("SIZE", ImageSortColumn.SIZE, sortColumn, sortDirection, onSort, Modifier.weight(0.7f))
        }
        Spacer(modifier = Modifier.width(24.dp))
    }
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
        thickness = 1.dp,
    )
}

@Composable
private fun SortableHeaderCell(
    label: String,
    column: ImageSortColumn,
    currentSort: ImageSortColumn,
    sortDirection: SortDirection,
    onSort: (ImageSortColumn) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isActive = currentSort == column
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable { onSort(column) }
                .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
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
private fun ImageRow(
    image: DockerImage,
    isSelected: Boolean,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    isCompactMode: Boolean,
) {
    var ctxMenuExpanded by remember { mutableStateOf(false) }
    var pressOffset by remember { mutableStateOf(DpOffset.Zero) }
    var inspectOpen by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    Column(
        modifier =
            Modifier.pointerInput(image.id) {
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
                    ).clickable(onClick = onClick)
                    .padding(horizontal = 8.dp, vertical = if (isCompactMode) 6.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CompactCheckbox(
                checked = isChecked,
                onCheckedChange = onCheckedChange,
            )
            if (isCompactMode) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = image.repository,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color =
                            if (image.repository == "<none>") {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = image.tag,
                            style = MaterialTheme.typography.labelSmall,
                            color =
                                if (image.tag == "<none>") {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Text(
                            text = image.shortId,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                        )
                    }
                    Text(
                        text = image.formattedSize,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            } else {
                Text(
                    text = image.repository,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color =
                        if (image.repository == "<none>") {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1.5f),
                )
                Text(
                    text = image.tag,
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (image.tag == "<none>") {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = image.shortId,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = image.formattedSize,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(0.7f),
                )
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
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
            thickness = 0.5.dp,
        )

        ImageContextMenu(
            expanded = ctxMenuExpanded,
            onDismiss = { ctxMenuExpanded = false },
            offset = pressOffset,
            onInspect = { inspectOpen = true },
            onCopyId = { copyToClipboard(image.id) },
            onRemove = onRemove,
        )

        if (inspectOpen) {
            InspectDialog(
                title = "Image: ${image.displayName}",
                jsonText = runCatching { imageInspectJson.encodeToString(image) }.getOrElse { it.message ?: "" },
                onDismiss = { inspectOpen = false },
            )
        }
    }
}

private val imageInspectJson = kotlinx.serialization.json.Json { prettyPrint = true }

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    val gb = mb / 1024.0
    return "%.2f GB".format(gb)
}
