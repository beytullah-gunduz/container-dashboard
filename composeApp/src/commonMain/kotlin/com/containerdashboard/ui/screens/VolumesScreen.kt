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
import com.containerdashboard.data.models.Volume
import com.containerdashboard.di.AppModule
import com.containerdashboard.ui.components.SearchBar
import com.containerdashboard.ui.state.VolumesState
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

@Composable
fun VolumesScreen(
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedVolume by remember { mutableStateOf<String?>(null) }
    var state by remember { mutableStateOf(VolumesState()) }
    var showCreateDialog by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    
    fun loadVolumes() {
        scope.launch {
            state = state.copy(isLoading = true, error = null)
            try {
                AppModule.dockerRepository.getVolumes()
                    .catch { e -> state = state.copy(error = e.message, isLoading = false) }
                    .collect { volumes ->
                        state = state.copy(volumes = volumes, isLoading = false)
                    }
            } catch (e: Exception) {
                state = state.copy(error = e.message, isLoading = false)
            }
        }
    }
    
    fun createVolume(name: String) {
        scope.launch {
            try {
                AppModule.dockerRepository.createVolume(name, "local")
                loadVolumes()
            } catch (e: Exception) {
                state = state.copy(error = e.message)
            }
        }
    }
    
    fun removeVolume(name: String) {
        scope.launch {
            try {
                AppModule.dockerRepository.removeVolume(name)
                loadVolumes()
            } catch (e: Exception) {
                state = state.copy(error = e.message)
            }
        }
    }
    
    LaunchedEffect(Unit) {
        loadVolumes()
    }
    
    val filteredVolumes = state.volumes.filter { volume ->
        searchQuery.isEmpty() || volume.name.contains(searchQuery, ignoreCase = true)
    }
    
    // Create Volume Dialog
    if (showCreateDialog) {
        var volumeName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
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
                            createVolume(volumeName)
                            showCreateDialog = false
                        }
                    },
                    enabled = volumeName.isNotBlank()
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
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
                    text = "${state.volumes.size} volumes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { loadVolumes() },
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
                Button(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Create volume")
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
                    IconButton(onClick = { state = state.copy(error = null) }) {
                        Icon(Icons.Default.Close, null)
                    }
                }
            }
        }
        
        // Search
        SearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            placeholder = "Search volumes...",
            modifier = Modifier.fillMaxWidth(0.4f)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Table Header
        VolumeTableHeader()
        
        // Loading indicator
        if (state.isLoading && state.volumes.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (filteredVolumes.isEmpty()) {
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
                        onClick = { selectedVolume = volume.name },
                        onRemove = { removeVolume(volume.name) }
                    )
                }
            }
        }
    }
}

@Composable
private fun VolumeTableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "NAME",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1.5f)
        )
        Text(
            text = "DRIVER",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(0.7f)
        )
        Text(
            text = "MOUNTPOINT",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(2f)
        )
        Spacer(modifier = Modifier.width(48.dp))
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
