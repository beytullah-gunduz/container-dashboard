package com.containerdashboard.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.containerdashboard.data.models.Container
import com.containerdashboard.ui.components.ContainerStatus
import com.containerdashboard.ui.components.MiniStatsCard
import com.containerdashboard.ui.components.SkeletonBar
import com.containerdashboard.ui.components.StatsCard
import com.containerdashboard.ui.components.rememberSkeletonAlpha
import com.containerdashboard.ui.components.toContainerStatus
import com.containerdashboard.ui.screens.viewmodel.DashboardScreenViewModel
import com.containerdashboard.ui.theme.AppColors
import com.containerdashboard.ui.theme.Radius
import com.containerdashboard.ui.theme.Spacing

// Threshold for switching between compact and expanded layouts.
// Kept in sync with ContainersScreen.COMPACT_THRESHOLD.
private val COMPACT_THRESHOLD = 700.dp

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
    val hasLoaded by viewModel.hasLoaded.collectAsState()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isCompactMode = maxWidth < COMPACT_THRESHOLD
        val outerPadding = if (isCompactMode) 16.dp else 24.dp
        val sectionSpacing = if (isCompactMode) 16.dp else 24.dp
        val cardSpacing = if (isCompactMode) 12.dp else 16.dp

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(outerPadding),
            verticalArrangement = Arrangement.spacedBy(sectionSpacing),
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
                        color = if (isConnected) AppColors.Running else MaterialTheme.colorScheme.onSurfaceVariant,
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

            val containersCard: @Composable (Modifier) -> Unit = { m ->
                MaybeSkeletonStatsCard(
                    title = "Containers",
                    value = containers.size.toString(),
                    subtitle = "$runningContainers running",
                    icon = Icons.Outlined.ViewInAr,
                    iconTint = AppColors.AccentBlue,
                    isLoading = !hasLoaded,
                    modifier = m,
                )
            }
            val imagesCard: @Composable (Modifier) -> Unit = { m ->
                MaybeSkeletonStatsCard(
                    title = "Images",
                    value = images.size.toString(),
                    subtitle = formatBytes(totalImageSize),
                    icon = Icons.Outlined.Layers,
                    iconTint = AppColors.Running,
                    isLoading = !hasLoaded,
                    modifier = m,
                )
            }
            val volumesCard: @Composable (Modifier) -> Unit = { m ->
                MaybeSkeletonStatsCard(
                    title = "Volumes",
                    value = volumes.size.toString(),
                    subtitle = "${volumes.size} total",
                    icon = Icons.Outlined.Storage,
                    iconTint = AppColors.Warning,
                    isLoading = !hasLoaded,
                    modifier = m,
                )
            }
            val networksCard: @Composable (Modifier) -> Unit = { m ->
                MaybeSkeletonStatsCard(
                    title = "Networks",
                    value = networks.size.toString(),
                    subtitle = "${networks.count { it.driver == "bridge" }} bridge",
                    icon = Icons.Outlined.Hub,
                    iconTint = AppColors.AccentBlueDark,
                    isLoading = !hasLoaded,
                    modifier = m,
                )
            }

            if (isCompactMode) {
                Column(verticalArrangement = Arrangement.spacedBy(cardSpacing)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(cardSpacing),
                    ) {
                        containersCard(Modifier.weight(1f))
                        imagesCard(Modifier.weight(1f))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(cardSpacing),
                    ) {
                        volumesCard(Modifier.weight(1f))
                        networksCard(Modifier.weight(1f))
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(cardSpacing),
                ) {
                    containersCard(Modifier.weight(1f))
                    imagesCard(Modifier.weight(1f))
                    volumesCard(Modifier.weight(1f))
                    networksCard(Modifier.weight(1f))
                }
            }

            // Container Status Section
            val containerStatusCard: @Composable (Modifier) -> Unit = { m ->
                Card(
                    modifier = m,
                    shape = RoundedCornerShape(Radius.lg),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        ),
                ) {
                    Column(
                        modifier = Modifier.padding(if (isCompactMode) 16.dp else 20.dp),
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
                            if (!hasLoaded) {
                                val alpha = rememberSkeletonAlpha()
                                repeat(3) {
                                    Column(
                                        modifier = Modifier.padding(8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        SkeletonBar(width = 40.dp, height = 22.dp, alpha = alpha)
                                        SkeletonBar(width = 56.dp, height = 10.dp, alpha = alpha)
                                    }
                                }
                            } else {
                                MiniStatsCard(
                                    label = "Running",
                                    value = running.toString(),
                                    valueColor = AppColors.Running,
                                )
                                MiniStatsCard(
                                    label = "Paused",
                                    value = paused.toString(),
                                    valueColor = AppColors.Paused,
                                )
                                MiniStatsCard(
                                    label = "Stopped",
                                    value = stopped.toString(),
                                    valueColor = AppColors.Stopped,
                                )
                            }
                        }
                    }
                }
            }
            val systemInfoCard: @Composable (Modifier) -> Unit = { m ->
                Card(
                    modifier = m,
                    shape = RoundedCornerShape(Radius.lg),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        ),
                ) {
                    Column(
                        modifier = Modifier.padding(if (isCompactMode) 16.dp else 20.dp),
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

            if (isCompactMode) {
                Column(verticalArrangement = Arrangement.spacedBy(cardSpacing)) {
                    containerStatusCard(Modifier.fillMaxWidth())
                    systemInfoCard(Modifier.fillMaxWidth())
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(cardSpacing),
                ) {
                    containerStatusCard(Modifier.weight(1f))
                    systemInfoCard(Modifier.weight(1f))
                }
            }

            // Recent Activity
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.lg),
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

                    if (!hasLoaded) {
                        val alpha = rememberSkeletonAlpha()
                        repeat(3) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SkeletonBar(width = 8.dp, height = 8.dp, alpha = alpha)
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    SkeletonBar(width = 160.dp, height = 12.dp, alpha = alpha)
                                    SkeletonBar(width = 100.dp, height = 10.dp, alpha = alpha)
                                }
                            }
                        }
                    } else if (containers.isEmpty()) {
                        Text(
                            text = "No containers found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        containers.take(5).forEach { container ->
                            RecentContainerItem(container = container, isCompactMode = isCompactMode)
                        }
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
private fun RecentContainerItem(
    container: Container,
    isCompactMode: Boolean,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(8.dp),
            shape = RoundedCornerShape(Radius.sm),
            color =
                when (container.state.toContainerStatus()) {
                    ContainerStatus.RUNNING -> AppColors.Running
                    ContainerStatus.PAUSED -> AppColors.Paused
                    ContainerStatus.CREATED -> AppColors.AccentBlue
                    ContainerStatus.RESTARTING -> AppColors.Warning
                    ContainerStatus.STOPPED,
                    ContainerStatus.EXITED,
                    ContainerStatus.REMOVING,
                    ContainerStatus.DEAD,
                    -> AppColors.Stopped
                },
        ) {}

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = container.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                text = container.status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            if (isCompactMode) {
                Text(
                    text = container.image,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }

        if (!isCompactMode) {
            Text(
                text = container.image,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

/**
 * Renders [StatsCard] normally when data is available and a skeleton variant
 * with shimmer placeholders for the value and subtitle rows while the first
 * fetch is still in flight. The icon stays visible so the card's identity is
 * preserved during the swap.
 */
@Composable
private fun MaybeSkeletonStatsCard(
    title: String,
    value: String,
    subtitle: String?,
    icon: ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!isLoading) {
        StatsCard(
            title = title,
            value = value,
            subtitle = subtitle,
            icon = icon,
            iconTint = iconTint,
            modifier = modifier,
        )
        return
    }

    val alpha = rememberSkeletonAlpha()
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(Radius.lg),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SkeletonBar(width = 56.dp, height = 22.dp, alpha = alpha)
                SkeletonBar(width = 72.dp, height = 10.dp, alpha = alpha)
            }

            Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(Radius.lg))
                        .background(iconTint.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp),
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
