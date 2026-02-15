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
import com.containerdashboard.data.models.Volume
import com.containerdashboard.ui.screens.viewmodel.VolumeSortColumn
import com.containerdashboard.ui.screens.viewmodel.VolumesScreenViewModel
import com.containerdashboard.ui.screens.viewmodel.SortDirection
import com.containerdashboard.ui.components.SearchBar

@Composable
fun VolumesScreen(
    modifier: Modifier = Modifier,
    viewModel: VolumesScreenViewModel = viewModel { VolumesScreenViewModel() }
) {
    val volumes by viewModel.volumes.collectAsState(listOf())
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedVolume by viewModel.selectedVolumeName.collectAsState()
    val showCreateDialog by viewModel.showCreateDialog.collectAsState()
    val sortColumn by viewModel.sortColumn.collectAsState()
    val sortDirection by viewModel.sortDirection.collectAsState()
    val error by viewModel.error.collectAsState()

    val filteredVolumes = volumes
        .filter { volume ->
            searchQuery.isEmpty() || volume.name.contains(searchQuery, ignoreCase = true)
        }
        .let { list ->
            val ascending = sortDirection == SortDirection.ASC
            when (sortColumn) {
                VolumeSortColumn.NAME -> if (ascending) list.sortedBy { it.name.lowercase() } else list.sortedByDescending { it.name.lowercase() }
                VolumeSortColumn.DRIVER -> if (ascending) list.sortedBy { it.driver.lowercase() } else list.sortedByDescending { it.driver.lowercase() }
                VolumeSortColumn.MOUNTPOINT -> if (ascending) list.sortedBy { it.mountpoint.lowercase() } else list.sortedByDescending { it.mountpoint.lowercase() }
            }
        }

    // Create Volume Dialog
    if (showCreateDialog) {
        var volumeName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { viewModel.setShowCreateDialog(false) },
            title = { Text("Create Volume") },
            text = {
                OutlinedTextField(
                    value = volumeName,
                    onValueChange = { volumeName = it },
                    label = { Text("Volume Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (volumeName.isNotBlank()) {
                            viewModel.createVolume(volumeName)
                            viewModel.setShowCreateDialog(false)
                        }
                    },
                    enabled = volumeName.isNotBlank()
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setShowCreateDialog(false) }) {
                    Text("Cancel")
                }
            }
        )
    }

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
                    text = "Volumes",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${volumes.size} volumes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { viewModel.setShowCreateDialog(true) }) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Create volume")
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

        // Search
        SearchBar(
            query = searchQuery,
            onQueryChange = { viewModel.setSearchQuery(it) },
            placeholder = "Search volumes...",
            modifier = Modifier.fillMaxWidth(0.4f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Table Header
        VolumeTableHeader(
            sortColumn = sortColumn,
            sortDirection = sortDirection,
            onSort = { viewModel.toggleSort(it) }
        )

        if (filteredVolumes.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No volumes found",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // Volume List
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(filteredVolumes, key = { it.name }) { volume ->
                    VolumeRow(
                        volume = volume,
                        isSelected = selectedVolume == volume.name,
                        onClick = { viewModel.setSelectedVolume(volume.name) },
                        onRemove = { viewModel.removeVolume(volume.name) }
                    )
                }
            }
        }
    }
}

@Composable
private fun VolumeTableHeader(
    sortColumn: VolumeSortColumn,
    sortDirection: SortDirection,
    onSort: (VolumeSortColumn) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        VolumeSortableHeaderCell("NAME", VolumeSortColumn.NAME, sortColumn, sortDirection, onSort, Modifier.weight(1.5f))
        VolumeSortableHeaderCell("DRIVER", VolumeSortColumn.DRIVER, sortColumn, sortDirection, onSort, Modifier.weight(0.7f))
        VolumeSortableHeaderCell("MOUNTPOINT", VolumeSortColumn.MOUNTPOINT, sortColumn, sortDirection, onSort, Modifier.weight(2f))
        Spacer(modifier = Modifier.width(48.dp))
    }
}

@Composable
private fun VolumeSortableHeaderCell(
    label: String,
    column: VolumeSortColumn,
    currentSort: VolumeSortColumn,
    sortDirection: SortDirection,
    onSort: (VolumeSortColumn) -> Unit,
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
private fun VolumeRow(
    volume: Volume,
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
        // Name
        Row(
            modifier = Modifier.weight(1.5f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Outlined.Storage,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = volume.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }

        // Driver
        Text(
            text = volume.driver,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.7f)
        )

        // Mountpoint
        Text(
            text = volume.mountpoint,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(2f),
            maxLines = 1
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
