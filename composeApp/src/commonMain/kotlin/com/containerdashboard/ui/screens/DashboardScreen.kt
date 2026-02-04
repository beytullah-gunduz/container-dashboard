package com.containerdashboard.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.containerdashboard.data.models.Container
import com.containerdashboard.data.models.SystemInfo
import com.containerdashboard.data.models.DockerVersion
import com.containerdashboard.data.repository.DockerRepository
import com.containerdashboard.di.AppModule
import com.containerdashboard.ui.components.MiniStatsCard
import com.containerdashboard.ui.components.StatsCard
import com.containerdashboard.ui.state.DashboardState
import com.containerdashboard.ui.theme.DockerColors
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    
    var state by remember { mutableStateOf(DashboardState()) }
    
    fun loadData() {
        scope.launch {
            state = state.copy(isLoading = true, error = null)
            try {
                val repo = AppModule.dockerRepository
                
                // Load system info
                val sysInfoResult = repo.getSystemInfo()
                val versionResult = repo.getVersion()
                
                // Load containers
                repo.getContainers(all = true)
                    .catch { e -> state = state.copy(error = e.message) }
                    .collect { containers ->
                        state = state.copy(containers = containers)
                    }
                
                // Load images
                repo.getImages()
                    .catch { e -> state = state.copy(error = e.message) }
                    .collect { images ->
                        state = state.copy(images = images)
                    }
                
                // Load volumes
                repo.getVolumes()
                    .catch { e -> state = state.copy(error = e.message) }
                    .collect { volumes ->
                        state = state.copy(volumes = volumes)
                    }
                
                // Load networks
                repo.getNetworks()
                    .catch { e -> state = state.copy(error = e.message) }
                    .collect { networks ->
                        state = state.copy(networks = networks)
                    }
                
                state = state.copy(
                    systemInfo = sysInfoResult.getOrNull(),
                    version = versionResult.getOrNull(),
                    isLoading = false,
                    isConnected = sysInfoResult.isSuccess
                )
            } catch (e: Exception) {
                state = state.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to connect to container engine",
                    isConnected = false
                )
            }
        }
    }
    
    LaunchedEffect(Unit) {
        loadData()
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Dashboard",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (state.isConnected) "Connected to container engine" else "Overview of your container environment",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.isConnected) DockerColors.Running else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Button(
                onClick = { loadData() },
                enabled = !state.isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Refresh")
            }
        }
        
        // Error message
        state.error?.let { error ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
        
        // Stats Cards Row
        val runningContainers = state.containers.count { it.isRunning }
        val totalImageSize = state.images.sumOf { it.size }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatsCard(
                title = "Containers",
                value = state.containers.size.toString(),
                subtitle = "$runningContainers running",
                icon = Icons.Outlined.ViewInAr,
                iconTint = DockerColors.DockerBlue,
                modifier = Modifier.weight(1f)
            )
            StatsCard(
                title = "Images",
                value = state.images.size.toString(),
                subtitle = formatBytes(totalImageSize),
                icon = Icons.Outlined.Layers,
                iconTint = DockerColors.Running,
                modifier = Modifier.weight(1f)
            )
            StatsCard(
                title = "Volumes",
                value = state.volumes.size.toString(),
                subtitle = "${state.volumes.size} total",
                icon = Icons.Outlined.Storage,
                iconTint = DockerColors.Warning,
                modifier = Modifier.weight(1f)
            )
            StatsCard(
                title = "Networks",
                value = state.networks.size.toString(),
                subtitle = "${state.networks.count { it.driver == "bridge" }} bridge",
                icon = Icons.Outlined.Hub,
                iconTint = DockerColors.DockerBlueDark,
                modifier = Modifier.weight(1f)
            )
        }
        
        // Container Status Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Container Status Card
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "Container Status",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    val running = state.containers.count { it.isRunning }
                    val paused = state.containers.count { it.isPaused }
                    val stopped = state.containers.count { it.isStopped }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MiniStatsCard(
                            label = "Running",
                            value = running.toString(),
                            valueColor = DockerColors.Running
                        )
                        MiniStatsCard(
                            label = "Paused",
                            value = paused.toString(),
                            valueColor = DockerColors.Paused
                        )
                        MiniStatsCard(
                            label = "Stopped",
                            value = stopped.toString(),
                            valueColor = DockerColors.Stopped
                        )
                    }
                }
            }
            
            // System Info Card
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "System Information",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    val sysInfo = state.systemInfo
                    val version = state.version
                    
                    SystemInfoRow("Engine Version", version?.version ?: "-")
                    SystemInfoRow("API Version", version?.apiVersion ?: "-")
                    SystemInfoRow("OS/Arch", "${sysInfo?.osType ?: "-"}/${sysInfo?.architecture ?: "-"}")
                    SystemInfoRow("CPUs", sysInfo?.ncpu?.toString() ?: "-")
                    SystemInfoRow("Memory", sysInfo?.formattedMemory ?: "-")
                }
            }
        }
        
        // Recent Activity
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Containers",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                
                if (state.containers.isEmpty() && !state.isLoading) {
                    Text(
                        text = "No containers found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    state.containers.take(5).forEach { container ->
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
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun RecentContainerItem(
    container: Container
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(8.dp),
                shape = RoundedCornerShape(4.dp),
                color = when {
                    container.isRunning -> DockerColors.Running
                    container.isPaused -> DockerColors.Paused
                    else -> DockerColors.Stopped
                }
            ) {}
            
            Column {
                Text(
                    text = container.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = container.status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Text(
            text = container.image,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
