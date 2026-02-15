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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.containerdashboard.data.models.DockerImage
import com.containerdashboard.ui.screens.viewmodel.ImageSortColumn
import com.containerdashboard.ui.screens.viewmodel.ImagesScreenViewModel
import com.containerdashboard.ui.screens.viewmodel.SortDirection
import com.containerdashboard.ui.components.SearchBar
import kotlinx.coroutines.delay

@Composable
fun ImagesScreen(
    modifier: Modifier = Modifier,
    viewModel: ImagesScreenViewModel = viewModel { ImagesScreenViewModel() }
) {
    val state by viewModel.state.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedImage by viewModel.selectedImageId.collectAsState()
    val autoRefresh by viewModel.autoRefresh().collectAsState(initial = false)
    val refreshInterval by viewModel.refreshInterval().collectAsState(initial = 5f)
    val sortColumn by viewModel.sortColumn.collectAsState()
    val sortDirection by viewModel.sortDirection.collectAsState()

    LaunchedEffect(autoRefresh, refreshInterval) {
        while (autoRefresh) {
            delay((refreshInterval * 1000).toLong())
            viewModel.loadImages()
        }
    }

    val filteredImages = state.images
        .filter { image ->
            searchQuery.isEmpty() ||
                image.repository.contains(searchQuery, ignoreCase = true) ||
                image.tag.contains(searchQuery, ignoreCase = true)
        }
        .let { list: List<DockerImage> ->
            val ascending = sortDirection == SortDirection.ASC
            when (sortColumn) {
                ImageSortColumn.REPOSITORY -> if (ascending) list.sortedBy { it.repository.lowercase() } else list.sortedByDescending { it.repository.lowercase() }
                ImageSortColumn.TAG -> if (ascending) list.sortedBy { it.tag.lowercase() } else list.sortedByDescending { it.tag.lowercase() }
                ImageSortColumn.IMAGE_ID -> if (ascending) list.sortedBy { it.shortId.lowercase() } else list.sortedByDescending { it.shortId.lowercase() }
                ImageSortColumn.SIZE -> if (ascending) list.sortedBy { it.size } else list.sortedByDescending { it.size }
            }
        }

    val totalSize = state.images.sumOf { it.size }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Images",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${state.images.size} images, ${formatBytes(totalSize)} total",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!autoRefresh) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.loadImages() },
                        enabled = !state.isLoading
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Refresh")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Error message
        state.error?.let { error ->
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
                    Text(error, color = MaterialTheme.colorScheme.onErrorContainer)
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
            modifier = Modifier.fillMaxWidth(0.4f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Table Header
        ImageTableHeader(
            sortColumn = sortColumn,
            sortDirection = sortDirection,
            onSort = { viewModel.toggleSort(it) }
        )

        // Loading indicator
        if (state.isLoading && state.images.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (filteredImages.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No images found",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // Image List
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(filteredImages, key = { it.id }) { image ->
                    ImageRow(
                        image = image,
                        isSelected = selectedImage == image.id,
                        onClick = { viewModel.setSelectedImage(image.id) },
                        onRemove = { viewModel.removeImage(image.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageTableHeader(
    sortColumn: ImageSortColumn,
    sortDirection: SortDirection,
    onSort: (ImageSortColumn) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SortableHeaderCell("REPOSITORY", ImageSortColumn.REPOSITORY, sortColumn, sortDirection, onSort, Modifier.weight(1.5f))
        SortableHeaderCell("TAG", ImageSortColumn.TAG, sortColumn, sortDirection, onSort, Modifier.weight(1f))
        SortableHeaderCell("IMAGE ID", ImageSortColumn.IMAGE_ID, sortColumn, sortDirection, onSort, Modifier.weight(1f))
        SortableHeaderCell("SIZE", ImageSortColumn.SIZE, sortColumn, sortDirection, onSort, Modifier.weight(0.7f))
        Spacer(modifier = Modifier.width(48.dp))
    }
}

@Composable
private fun SortableHeaderCell(
    label: String,
    column: ImageSortColumn,
    currentSort: ImageSortColumn,
    sortDirection: SortDirection,
    onSort: (ImageSortColumn) -> Unit,
    modifier: Modifier = Modifier
) {
    val isActive = currentSort == column
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable { onSort(column) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
        if (isActive) {
            Icon(
                imageVector = if (sortDirection == SortDirection.ASC) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                contentDescription = if (sortDirection == SortDirection.ASC) "Ascending" else "Descending",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ImageRow(
    image: DockerImage,
    isSelected: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                else MaterialTheme.colorScheme.surface
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Repository
        Text(
            text = image.repository,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (image.repository == "<none>")
                MaterialTheme.colorScheme.onSurfaceVariant
            else
                MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1.5f)
        )

        // Tag
        Text(
            text = image.tag,
            style = MaterialTheme.typography.bodyMedium,
            color = if (image.tag == "<none>")
                MaterialTheme.colorScheme.onSurfaceVariant
            else
                MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )

        // Image ID
        Text(
            text = image.shortId,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )

        // Size
        Text(
            text = image.formattedSize,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(0.7f)
        )

        // Actions
        Row(
            modifier = Modifier.width(48.dp),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Delete",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    val gb = mb / 1024.0
    return "%.2f GB".format(gb)
}
