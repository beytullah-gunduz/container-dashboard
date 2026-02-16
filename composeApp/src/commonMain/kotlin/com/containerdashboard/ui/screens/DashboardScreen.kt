package com.containerdashboard.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.containerdashboard.data.models.Container
import com.containerdashboard.ui.components.MiniStatsCard
import com.containerdashboard.ui.components.StatsCard
import com.containerdashboard.ui.screens.viewmodel.DashboardScreenViewModel
import com.containerdashboard.ui.theme.DockerColors

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    viewModel: DashboardScreenViewModel = viewModel { DashboardScreenViewModel() },
) {
    val scrollState = rememberScrollState()

    val systemInfo by viewModel.systemInfo.collectAsState()
    val version by viewModel.version.collectAsState()
    val containers by viewModel.containers.collectAsState(listOf())
    val images by viewModel.images.collectAsState(listOf())
    val volumes by viewModel.volumes.collectAsState(listOf())
    val networks by viewModel.networks.collectAsState(listOf())
    val error by viewModel.error.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Dashboard",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (isConnected) "Connected to container engine" else "Overview of your container environment",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isConnected) DockerColors.Running else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Error message
        error?.let { errorMessage ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { viewModel.clearError() }) {
                        Icon(Icons.Default.Close, null)
                    }
                }
            }
        }

        // Stats Cards Row
        val runningContainers = containers.count { it.isRunning }
        val totalImageSize = images.sumOf { it.size }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatsCard(
                title = "Containers",
                value = containers.size.toString(),
                subtitle = "$runningContainers running",
                icon = Icons.Outlined.ViewInAr,
                iconTint = DockerColors.DockerBlue,
                modifier = Modifier.weight(1f),
            )
            StatsCard(
                title = "Images",
                value = images.size.toString(),
                subtitle = formatBytes(totalImageSize),
                icon = Icons.Outlined.Layers,
                iconTint = DockerColors.Running,
                modifier = Modifier.weight(1f),
            )
            StatsCard(
                title = "Volumes",
                value = volumes.size.toString(),
                subtitle = "${volumes.size} total",
                icon = Icons.Outlined.Storage,
                iconTint = DockerColors.Warning,
                modifier = Modifier.weight(1f),
            )
            StatsCard(
                title = "Networks",
                value = networks.size.toString(),
                subtitle = "${networks.count { it.driver == "bridge" }} bridge",
                icon = Icons.Outlined.Hub,
                iconTint = DockerColors.DockerBlueDark,
                modifier = Modifier.weight(1f),
            )
        }

        // Container Status Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Container Status Card
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                ) {
                    Text(
                        text = "Container Status",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    val running = containers.count { it.isRunning }
                    val paused = containers.count { it.isPaused }
                    val stopped = containers.count { it.isStopped }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        MiniStatsCard(
                            label = "Running",
                            value = running.toString(),
                            valueColor = DockerColors.Running,
                        )
                        MiniStatsCard(
                            label = "Paused",
                            value = paused.toString(),
                            valueColor = DockerColors.Paused,
                        )
                        MiniStatsCard(
                            label = "Stopped",
                            value = stopped.toString(),
                            valueColor = DockerColors.Stopped,
                        )
                    }
                }
            }

            // System Info Card
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                ) {
                    Text(
                        text = "System Information",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    SystemInfoRow("Engine Version", version?.version ?: "-")
                    SystemInfoRow("API Version", version?.apiVersion ?: "-")
                    SystemInfoRow("OS/Arch", "${systemInfo?.osType ?: "-"}/${systemInfo?.architecture ?: "-"}")
                    SystemInfoRow("CPUs", systemInfo?.ncpu?.toString() ?: "-")
                    SystemInfoRow("Memory", systemInfo?.formattedMemory ?: "-")
                }
            }
        }

        // Recent Activity
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Recent Containers",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                if (containers.isEmpty()) {
                    Text(
                        text = "No containers found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    containers.take(5).forEach { container ->
                        RecentContainerItem(container = container)
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemInfoRow(
    label: String,
    value: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun RecentContainerItem(container: Container) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(8.dp),
                shape = RoundedCornerShape(4.dp),
                color =
                    when {
                        container.isRunning -> DockerColors.Running
                        container.isPaused -> DockerColors.Paused
                        else -> DockerColors.Stopped
                    },
            ) {}

            Column {
                Text(
                    text = container.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = container.status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text(
            text = container.image,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
