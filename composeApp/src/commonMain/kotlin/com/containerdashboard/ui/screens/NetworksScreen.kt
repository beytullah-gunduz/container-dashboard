package com.containerdashboard.ui.screens

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.containerdashboard.data.models.DockerNetwork
import com.containerdashboard.ui.components.CompactCheckbox
import com.containerdashboard.ui.components.SearchBar
import com.containerdashboard.ui.screens.viewmodel.NetworksScreenViewModel

// Threshold for switching between compact and expanded layouts.
// Kept in sync with ContainersScreen.COMPACT_THRESHOLD.
private val COMPACT_THRESHOLD = 700.dp

@Composable
fun NetworksScreen(
    modifier: Modifier = Modifier,
    viewModel: NetworksScreenViewModel = viewModel { NetworksScreenViewModel() },
) {
    val networks by viewModel.networks.collectAsState(listOf())
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedNetwork by viewModel.selectedNetworkId.collectAsState()
    val showCreateDialog by viewModel.showCreateDialog.collectAsState()
    val error by viewModel.error.collectAsState()
    val checkedNetworkIds by viewModel.checkedNetworkIds.collectAsState()
    val isDeletingSelected by viewModel.isDeletingSelected.collectAsState()

    val filteredNetworks =
        networks.filter { network ->
            searchQuery.isEmpty() || network.name.contains(searchQuery, ignoreCase = true)
        }

    val systemNetworks = listOf("bridge", "host", "none")
    val customNetworks = filteredNetworks.filter { it.name !in systemNetworks }

    // Create Network Dialog
    if (showCreateDialog) {
        var networkName by remember { mutableStateOf("") }
        var selectedDriver by remember { mutableStateOf("bridge") }
        AlertDialog(
            onDismissRequest = { viewModel.setShowCreateDialog(false) },
            title = { Text("Create Network") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = networkName,
                        onValueChange = { networkName = it },
                        label = { Text("Network Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Text("Driver", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("bridge", "overlay", "macvlan").forEach { driver ->
                            FilterChip(
                                selected = selectedDriver == driver,
                                onClick = { selectedDriver = driver },
                                label = { Text(driver) },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (networkName.isNotBlank()) {
                            viewModel.createNetwork(networkName, selectedDriver)
                            viewModel.setShowCreateDialog(false)
                        }
                    },
                    enabled = networkName.isNotBlank(),
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setShowCreateDialog(false) }) {
                    Text("Cancel")
                }
            },
        )
    }

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
                        text = "Networks",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${networks.size} networks, ${networks.count { it.name !in systemNetworks }} custom",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (checkedNetworkIds.isNotEmpty()) {
                        Button(
                            onClick = { viewModel.deleteSelectedNetworks() },
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
                            Text("Delete ${checkedNetworkIds.size} selected")
                        }
                        OutlinedButton(onClick = { viewModel.clearChecked() }) {
                            Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Clear")
                        }
                    }
                    Button(onClick = { viewModel.setShowCreateDialog(true) }) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create network")
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
                placeholder = "Search networks...",
                modifier = if (isCompactMode) Modifier.fillMaxWidth() else Modifier.fillMaxWidth(0.4f),
                compact = isCompactMode,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Table Header
            NetworkTableHeader(
                allSelected = customNetworks.isNotEmpty() && customNetworks.all { it.id in checkedNetworkIds },
                onSelectAllChange = { selectAll ->
                    if (selectAll) {
                        viewModel.checkAll(customNetworks.map { it.id })
                    } else {
                        viewModel.clearChecked()
                    }
                },
                hasItems = customNetworks.isNotEmpty(),
                isCompactMode = isCompactMode,
            )

            if (filteredNetworks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No networks found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                // Network List
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(filteredNetworks, key = { it.id }) { network ->
                        NetworkRow(
                            network = network,
                            isSelected = selectedNetwork == network.id,
                            isSystem = network.name in systemNetworks,
                            isChecked = network.id in checkedNetworkIds,
                            onCheckedChange = { viewModel.toggleChecked(network.id, it) },
                            onClick = { viewModel.setSelectedNetwork(network.id) },
                            onRemove = { viewModel.removeNetwork(network.id) },
                            isCompactMode = isCompactMode,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NetworkTableHeader(
    allSelected: Boolean,
    onSelectAllChange: (Boolean) -> Unit,
    hasItems: Boolean,
    isCompactMode: Boolean,
) {
    Column {
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
                Text(
                    text = "NETWORK",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Text(
                    text = "NAME",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1.2f),
                )
                Text(
                    text = "NETWORK ID",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(0.8f),
                )
                Text(
                    text = "DRIVER",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(0.7f),
                )
                Text(
                    text = "SCOPE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(0.5f),
                )
                Text(
                    text = "SUBNET",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "CONTAINERS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(0.6f),
                )
            }
            Spacer(modifier = Modifier.width(32.dp))
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
            thickness = 1.dp,
        )
    }
}

@Composable
private fun NetworkRow(
    network: DockerNetwork,
    isSelected: Boolean,
    isSystem: Boolean,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    isCompactMode: Boolean,
) {
    Column {
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
                enabled = !isSystem,
            )
            if (isCompactMode) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Hub,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint =
                                if (isSystem) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                        )
                        Text(
                            text = network.name,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (isSystem) {
                            Surface(
                                shape = RoundedCornerShape(3.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                            ) {
                                Text(
                                    text = "System",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                )
                            }
                        }
                        if (network.containerCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(3.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Text(
                                    text = "${network.containerCount}",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                )
                            }
                        }
                    }
                    Text(
                        text = "${network.driver} · ${network.scope}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                    )
                    if (network.subnet.isNotBlank()) {
                        Text(
                            text = network.subnet,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            } else {
                // Name
                Row(
                    modifier = Modifier.weight(1.2f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Outlined.Hub,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint =
                            if (isSystem) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                    )
                    Text(
                        text = network.name,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isSystem) {
                        Surface(
                            shape = RoundedCornerShape(3.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Text(
                                text = "System",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            )
                        }
                    }
                }

                // Network ID
                Text(
                    text = network.shortId,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(0.8f),
                )

                // Driver
                Text(
                    text = network.driver,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    modifier = Modifier.weight(0.7f),
                )

                // Scope
                Text(
                    text = network.scope,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(0.5f),
                )

                // Subnet
                Text(
                    text = network.subnet,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                // Containers
                Box(modifier = Modifier.weight(0.6f)) {
                    if (network.containerCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(3.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                text = "${network.containerCount}",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            )
                        }
                    } else {
                        Text(
                            text = "0",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Actions
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(24.dp),
                enabled = !isSystem,
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Delete",
                    modifier = Modifier.size(14.dp),
                    tint =
                        if (!isSystem) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        },
                )
            }
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
            thickness = 0.5.dp,
        )
    }
}
