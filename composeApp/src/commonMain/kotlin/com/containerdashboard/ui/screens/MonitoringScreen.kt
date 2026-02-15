package com.containerdashboard.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.containerdashboard.data.models.ContainerStats
import com.containerdashboard.ui.components.CircularSlider


import com.containerdashboard.ui.screens.viewmodel.MonitoringScreenViewModel
import com.containerdashboard.ui.screens.viewmodel.UsageHistory
import com.containerdashboard.ui.theme.DockerColors

@Composable
fun MonitoringScreen(
    modifier: Modifier = Modifier,
    viewModel: MonitoringScreenViewModel = viewModel { MonitoringScreenViewModel() }
) {
    val stats by viewModel.containerStats.collectAsState(listOf())
    val error by viewModel.error.collectAsState()
    val history by viewModel.usageHistory.collectAsState(UsageHistory())
val refreshRate by viewModel.refreshRate.collectAsState()

    val scrollState = rememberScrollState()

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
                    text = "Monitoring",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${stats.size} running containers",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    modifier = Modifier.size(8.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = if (stats.isNotEmpty()) DockerColors.Running else MaterialTheme.colorScheme.onSurfaceVariant
                ) {}
                Text(
                    text = "Live",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
CircularSlider(
        value = refreshRate,
        onValueChange = { viewModel.setRefreshRate(it) },
        valueRange = 1f..5f,
        activeColor = DockerColors.DockerBlue
)

            }
        }

        // Usage History Graphs
        if (stats.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                UsageHistoryGraph(
                    title = "CPU Usage",
                    icon = Icons.Outlined.Memory,
                    iconTint = DockerColors.DockerBlue,
                    history = history.cpuHistory,
                    maxHistorySize = 60,
                    barColor = { getCpuColor(it) },
                    currentValue = "%.1f%%".format(
                        stats.sumOf { it.cpuPercent } / stats.size
                    ),
                    modifier = Modifier.weight(1f)
                )
                UsageHistoryGraph(
                    title = "Memory Usage",
                    icon = Icons.Outlined.Storage,
                    iconTint = DockerColors.DockerBlueDark,
                    history = history.memoryHistory,
                    maxHistorySize = 60,
                    barColor = { getMemoryColor(it) },
                    currentValue = "%.1f%%".format(
                        stats.sumOf { it.memoryPercent } / stats.size
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Error message
        error?.let { errorMessage ->
            Card(
                modifier = Modifier.fillMaxWidth(),
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

        if (stats.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Outlined.MonitorHeart,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "No running containers",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Start some containers to see CPU and memory usage",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            // CPU Usage Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Memory,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = DockerColors.DockerBlue
                        )
                        Text(
                            text = "CPU Usage",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    stats.forEach { stat ->
                        ContainerBarRow(
                            containerName = stat.containerName,
                            value = stat.cpuPercent,
                            maxValue = 100.0,
                            label = "%.1f%%".format(stat.cpuPercent),
                            barColor = getCpuColor(stat.cpuPercent)
                        )
                    }
                }
            }

            // Memory Usage Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Storage,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = DockerColors.DockerBlueDark
                        )
                        Text(
                            text = "Memory Usage",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    stats.forEach { stat ->
                        ContainerBarRow(
                            containerName = stat.containerName,
                            value = stat.memoryPercent,
                            maxValue = 100.0,
                            label = "${stat.formattedMemoryUsage} / ${stat.formattedMemoryLimit}",
                            barColor = getMemoryColor(stat.memoryPercent)
                        )
                    }
                }
            }

            // Detailed Stats Table
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Detailed Stats",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    // Table Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CONTAINER",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1.5f)
                        )
                        Text(
                            text = "CPU %",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(0.7f)
                        )
                        Text(
                            text = "MEMORY USAGE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1.2f)
                        )
                        Text(
                            text = "MEMORY %",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(0.7f)
                        )
                    }

                    stats.forEach { stat ->
                        StatsTableRow(stat = stat)
                    }
                }
            }
        }
    }
}

@Composable
private fun ContainerBarRow(
    containerName: String,
    value: Double,
    maxValue: Double,
    label: String,
    barColor: Color
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = containerName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = barColor
            )
        }

        // Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            val fraction = (value / maxValue).coerceIn(0.0, 1.0).toFloat()
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .clip(RoundedCornerShape(4.dp))
                    .background(barColor)
            )
        }
    }
}

@Composable
private fun StatsTableRow(stat: ContainerStats) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Container name
        Row(
            modifier = Modifier.weight(1.5f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Outlined.ViewInAr,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = DockerColors.Running
            )
            Column {
                Text(
                    text = stat.containerName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stat.containerId.take(12),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // CPU %
        Text(
            text = "%.1f%%".format(stat.cpuPercent),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = getCpuColor(stat.cpuPercent),
            modifier = Modifier.weight(0.7f)
        )

        // Memory usage
        Text(
            text = "${stat.formattedMemoryUsage} / ${stat.formattedMemoryLimit}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1.2f)
        )

        // Memory %
        Text(
            text = "%.1f%%".format(stat.memoryPercent),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = getMemoryColor(stat.memoryPercent),
            modifier = Modifier.weight(0.7f)
        )
    }
}

@Composable
private fun UsageHistoryGraph(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    history: List<Double>,
    maxHistorySize: Int,
    barColor: (Double) -> Color,
    currentValue: String,
    maxValue: Double = 100.0,
    modifier: Modifier = Modifier
) {
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Title row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = iconTint
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = currentValue,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = iconTint
                )
            }

            // Graph
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            ) {
                // Y-axis labels
                Box(modifier = Modifier.fillMaxSize().padding(start = 4.dp)) {
                    Text(
                        text = "100%",
                        style = MaterialTheme.typography.labelSmall,
                        color = labelColor.copy(alpha = 0.5f),
                        modifier = Modifier.align(Alignment.TopStart)
                    )
                    Text(
                        text = "50%",
                        style = MaterialTheme.typography.labelSmall,
                        color = labelColor.copy(alpha = 0.5f),
                        modifier = Modifier.align(Alignment.CenterStart)
                    )
                    Text(
                        text = "0%",
                        style = MaterialTheme.typography.labelSmall,
                        color = labelColor.copy(alpha = 0.5f),
                        modifier = Modifier.align(Alignment.BottomStart)
                    )
                }

                Canvas(modifier = Modifier.fillMaxSize().padding(start = 32.dp)) {
                    val chartWidth = size.width
                    val chartHeight = size.height
                    val barWidth = chartWidth / maxHistorySize
                    val gap = 1f

                    // Grid lines at 25%, 50%, 75%
                    for (i in 1..3) {
                        val y = chartHeight * (1 - i / 4f)
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(chartWidth, y),
                            strokeWidth = 1f
                        )
                    }

                    // Bars — oldest on left, newest on right
                    val startOffset = (maxHistorySize - history.size) * barWidth
                    history.forEachIndexed { index, value ->
                        val x = startOffset + index * barWidth
                        val fraction = (value / maxValue).coerceIn(0.0, 1.0).toFloat()
                        val barHeight = chartHeight * fraction

                        drawRoundRect(
                            color = barColor(value),
                            topLeft = Offset(x + gap, chartHeight - barHeight),
                            size = Size((barWidth - gap * 2).coerceAtLeast(1f), barHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f)
                        )
                    }
                }
            }
        }
    }
}

private fun getCpuColor(percent: Double): Color {
    return when {
        percent < 25 -> DockerColors.Running
        percent < 60 -> DockerColors.Warning
        else -> DockerColors.Stopped
    }
}

private fun getMemoryColor(percent: Double): Color {
    return when {
        percent < 50 -> DockerColors.Running
        percent < 80 -> DockerColors.Warning
        else -> DockerColors.Stopped
    }
}
