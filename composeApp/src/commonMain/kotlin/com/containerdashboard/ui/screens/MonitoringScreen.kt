package com.containerdashboard.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.containerdashboard.data.models.ContainerStats
import com.containerdashboard.ui.components.CircularSlider
import com.containerdashboard.ui.components.EmptyState
import com.containerdashboard.ui.components.SearchBar
import com.containerdashboard.ui.components.SemiDonutGauge
import com.containerdashboard.ui.screens.viewmodel.DerivedContainerStats
import com.containerdashboard.ui.screens.viewmodel.MonitoringScreenViewModel
import com.containerdashboard.ui.screens.viewmodel.MonitoringSort
import com.containerdashboard.ui.screens.viewmodel.MonitoringSortDirection
import com.containerdashboard.ui.screens.viewmodel.UsageHistory
import com.containerdashboard.ui.theme.AppColors
import com.containerdashboard.ui.theme.Radius
import com.containerdashboard.ui.theme.Spacing

// Shared left-padding for history-graph canvases so the bar areas of
// UsageHistoryGraph and IoHistoryGraph line up vertically when the
// graphs are stacked. Sized for the widest expected label ("999 MB/s").
private val GRAPH_Y_AXIS_PADDING = 72.dp

@Composable
fun MonitoringScreen(
    modifier: Modifier = Modifier,
    viewModel: MonitoringScreenViewModel = viewModel { MonitoringScreenViewModel() },
) {
    val statsOrNull by viewModel.derivedStats.collectAsState(null)
    val stats = statsOrNull.orEmpty()
    // null before the first emission — covers first-open and re-entry while
    // hasLoaded's 5s cache is still stale-true.
    val isLoading = statsOrNull == null
    val error by viewModel.error.collectAsState()
    val history by viewModel.usageHistory.collectAsState(UsageHistory())
    val refreshRate by viewModel.refreshRate.collectAsState()

    val scrollState = rememberScrollState()

    var searchQuery by remember { mutableStateOf("") }
    val sortColumn by viewModel.sortColumn.collectAsState()
    val sortDirection by viewModel.sortDirection.collectAsState()
    val onSortChange: (MonitoringSort) -> Unit = { column -> viewModel.toggleSort(column) }

    val filteredStats =
        remember(stats, searchQuery, sortColumn, sortDirection) {
            val query = searchQuery.trim()
            val filtered =
                if (query.isEmpty()) {
                    stats
                } else {
                    stats.filter {
                        it.containerName.contains(query, ignoreCase = true) ||
                            it.containerId.contains(query, ignoreCase = true)
                    }
                }
            val comparator: Comparator<DerivedContainerStats> =
                when (sortColumn) {
                    MonitoringSort.NAME -> compareBy { it.containerName.lowercase() }
                    MonitoringSort.CPU -> compareBy { it.cpuPercent }
                    MonitoringSort.MEM -> compareBy { it.memoryPercent }
                    MonitoringSort.DISK_R -> compareBy { it.diskReadBytesPerSec }
                    MonitoringSort.DISK_W -> compareBy { it.diskWriteBytesPerSec }
                    MonitoringSort.NET_RX -> compareBy { it.networkRxBytesPerSec }
                    MonitoringSort.NET_TX -> compareBy { it.networkTxBytesPerSec }
                }
            val sorted = filtered.sortedWith(comparator)
            if (sortDirection == MonitoringSortDirection.DESC) sorted.reversed() else sorted
        }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isCompactMode = maxWidth < COMPACT_THRESHOLD
        val isNarrowTable = maxWidth < NARROW_TABLE_THRESHOLD
        val outerPadding = if (isCompactMode) 16.dp else 24.dp
        val sectionSpacing = if (isCompactMode) 16.dp else 24.dp

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
                        text = "Monitoring",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${stats.size} running containers",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Surface(
                        modifier = Modifier.size(8.dp),
                        shape = RoundedCornerShape(Radius.sm),
                        color = if (stats.isNotEmpty()) AppColors.Running else MaterialTheme.colorScheme.onSurfaceVariant,
                    ) {}
                    Text(
                        text = "Live",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    CircularSlider(
                        value = refreshRate,
                        onValueChange = { viewModel.setRefreshRate(it) },
                        valueRange = 1f..5f,
                        activeColor = AppColors.AccentBlue,
                    )
                }
            }

            // Usage History Graphs
            if (stats.isNotEmpty()) {
                val cpuGraph: @Composable (Modifier) -> Unit = { m ->
                    UsageHistoryGraph(
                        title = "CPU Usage",
                        icon = Icons.Outlined.Memory,
                        iconTint = AppColors.AccentBlue,
                        history = history.cpuHistory,
                        maxHistorySize = 60,
                        barColor = { getCpuColor(it) },
                        modifier = m,
                    )
                }
                val memGraph: @Composable (Modifier) -> Unit = { m ->
                    UsageHistoryGraph(
                        title = "Memory Usage",
                        icon = Icons.Outlined.Storage,
                        iconTint = AppColors.AccentBlueDark,
                        history = history.memoryHistory,
                        maxHistorySize = 60,
                        barColor = { getMemoryColor(it) },
                        modifier = m,
                    )
                }
                val diskGraph: @Composable (Modifier) -> Unit = { m ->
                    IoHistoryGraph(
                        title = "Disk IO",
                        icon = Icons.Outlined.Storage,
                        iconTint = AppColors.AccentBlue,
                        seriesA = history.diskReadHistory,
                        seriesB = history.diskWriteHistory,
                        seriesALabel = "Read",
                        seriesBLabel = "Write",
                        seriesAColor = AppColors.AccentBlue,
                        seriesBColor = AppColors.Running,
                        maxHistorySize = 60,
                        currentA = stats.sumOf { it.diskReadBytesPerSec },
                        currentB = stats.sumOf { it.diskWriteBytesPerSec },
                        modifier = m,
                    )
                }
                val netGraph: @Composable (Modifier) -> Unit = { m ->
                    IoHistoryGraph(
                        title = "Network IO",
                        icon = Icons.Outlined.NetworkCheck,
                        iconTint = AppColors.AccentBlueLight,
                        seriesA = history.networkRxHistory,
                        seriesB = history.networkTxHistory,
                        seriesALabel = "Rx",
                        seriesBLabel = "Tx",
                        seriesAColor = AppColors.AccentBlueLight,
                        seriesBColor = AppColors.Warning,
                        maxHistorySize = 60,
                        currentA = stats.sumOf { it.networkRxBytesPerSec },
                        currentB = stats.sumOf { it.networkTxBytesPerSec },
                        modifier = m,
                    )
                }
                if (isCompactMode) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        cpuGraph(Modifier.fillMaxWidth())
                        memGraph(Modifier.fillMaxWidth())
                        diskGraph(Modifier.fillMaxWidth())
                        netGraph(Modifier.fillMaxWidth())
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                        ) {
                            cpuGraph(Modifier.weight(1f))
                            memGraph(Modifier.weight(1f))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                        ) {
                            diskGraph(Modifier.weight(1f))
                            netGraph(Modifier.weight(1f))
                        }
                    }
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

            if (isLoading) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.lg),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        ),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        EmptyState(
                            icon = Icons.Outlined.MonitorHeart,
                            title = "Collecting data…",
                            body = "Waiting for the first stats snapshot from the container engine.",
                        )
                        CircularProgressIndicator(
                            modifier =
                                Modifier
                                    .padding(bottom = Spacing.xl)
                                    .size(24.dp),
                            strokeWidth = 2.5.dp,
                        )
                    }
                }
            } else if (stats.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.lg),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        ),
                ) {
                    EmptyState(
                        icon = Icons.Outlined.MonitorHeart,
                        title = "No running containers",
                        body = "Start some containers to see CPU, memory, disk, and network usage.",
                    )
                }
            } else {
                // CPU Usage Card
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
                        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            Icon(
                                Icons.Outlined.Memory,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = AppColors.AccentBlue,
                            )
                            Text(
                                text = "CPU Usage",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }

                        stats.forEach { stat ->
                            ContainerBarRow(
                                containerName = stat.containerName,
                                value = stat.cpuPercent,
                                maxValue = 100.0,
                                label = "%.1f%%".format(stat.cpuPercent),
                                barColor = getCpuColor(stat.cpuPercent),
                            )
                        }
                    }
                }

                // Memory Usage Card
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
                        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            Icon(
                                Icons.Outlined.Memory,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = AppColors.AccentBlueDark,
                            )
                            Text(
                                text = "Memory Usage",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }

                        stats.forEach { stat ->
                            ContainerBarRow(
                                containerName = stat.containerName,
                                value = stat.memoryPercent,
                                maxValue = 100.0,
                                label =
                                    "${ContainerStats.formatBytes(stat.memoryUsage)} / " +
                                        ContainerStats.formatBytes(stat.memoryLimit),
                                barColor = getMemoryColor(stat.memoryPercent),
                            )
                        }
                    }
                }

                // Detailed Stats Table
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.lg),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        ),
                ) {
                    Column(
                        modifier = Modifier.padding(if (isCompactMode) 16.dp else 20.dp),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Per-Container Resources",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            SearchBar(
                                query = searchQuery,
                                onQueryChange = { searchQuery = it },
                                placeholder = "Filter containers",
                                compact = true,
                                modifier = Modifier.fillMaxWidth(fraction = if (isCompactMode) 0.6f else 0.4f),
                            )
                        }

                        if (isNarrowTable) {
                            NarrowTableHeader(
                                sortColumn = sortColumn,
                                sortDirection = sortDirection,
                                onSortChange = onSortChange,
                            )
                        } else {
                            WideTableHeader(
                                sortColumn = sortColumn,
                                sortDirection = sortDirection,
                                onSortChange = onSortChange,
                            )
                        }
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                            thickness = 1.dp,
                        )

                        if (filteredStats.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xl),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "No containers match the filter",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            filteredStats.forEach { stat ->
                                StatsTableRow(stat = stat, isNarrow = isNarrowTable)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WideTableHeader(
    sortColumn: MonitoringSort,
    sortDirection: MonitoringSortDirection,
    onSortChange: (MonitoringSort) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(horizontal = Spacing.md, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        SortableHeader("Container", MonitoringSort.NAME, sortColumn, sortDirection, onSortChange, Modifier.weight(1.4f))
        SortableHeader("CPU %", MonitoringSort.CPU, sortColumn, sortDirection, onSortChange, Modifier.weight(0.7f))
        SortableHeader("Memory %", MonitoringSort.MEM, sortColumn, sortDirection, onSortChange, Modifier.weight(0.7f))
        HeaderLabel("Memory", Modifier.weight(1.0f))
        SortableHeader("Disk R", MonitoringSort.DISK_R, sortColumn, sortDirection, onSortChange, Modifier.weight(0.9f))
        SortableHeader("Disk W", MonitoringSort.DISK_W, sortColumn, sortDirection, onSortChange, Modifier.weight(0.9f))
        SortableHeader("Net \u2193", MonitoringSort.NET_RX, sortColumn, sortDirection, onSortChange, Modifier.weight(0.9f))
        SortableHeader("Net \u2191", MonitoringSort.NET_TX, sortColumn, sortDirection, onSortChange, Modifier.weight(0.9f))
    }
}

@Composable
private fun NarrowTableHeader(
    sortColumn: MonitoringSort,
    sortDirection: MonitoringSortDirection,
    onSortChange: (MonitoringSort) -> Unit,
) {
    // Condensed two-column sort header: left = name/CPU/Mem choices,
    // right = disk/network choices. Users click the chip matching
    // the dimension they want to sort by; the arrow marks the active one.
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(horizontal = Spacing.md, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SortChip("Name", MonitoringSort.NAME, sortColumn, sortDirection, onSortChange)
            SortChip("CPU", MonitoringSort.CPU, sortColumn, sortDirection, onSortChange)
            SortChip("Mem", MonitoringSort.MEM, sortColumn, sortDirection, onSortChange)
        }
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SortChip("Disk R", MonitoringSort.DISK_R, sortColumn, sortDirection, onSortChange)
            SortChip("Disk W", MonitoringSort.DISK_W, sortColumn, sortDirection, onSortChange)
            SortChip("Net \u2193", MonitoringSort.NET_RX, sortColumn, sortDirection, onSortChange)
            SortChip("Net \u2191", MonitoringSort.NET_TX, sortColumn, sortDirection, onSortChange)
        }
    }
}

@Composable
private fun SortableHeader(
    label: String,
    column: MonitoringSort,
    activeColumn: MonitoringSort,
    direction: MonitoringSortDirection,
    onClick: (MonitoringSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isActive = activeColumn == column
    Row(
        modifier = modifier.clickable { onClick(column) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color =
                if (isActive) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        if (isActive) {
            Icon(
                imageVector =
                    if (direction == MonitoringSortDirection.ASC) {
                        Icons.Default.ArrowUpward
                    } else {
                        Icons.Default.ArrowDownward
                    },
                contentDescription = null,
                modifier = Modifier.size(10.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun HeaderLabel(
    label: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier,
        maxLines = 1,
    )
}

@Composable
private fun SortChip(
    label: String,
    column: MonitoringSort,
    activeColumn: MonitoringSort,
    direction: MonitoringSortDirection,
    onClick: (MonitoringSort) -> Unit,
) {
    val isActive = activeColumn == column
    Surface(
        shape = RoundedCornerShape(6.dp),
        color =
            if (isActive) {
                AppColors.AccentBlue.copy(alpha = 0.2f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        modifier = Modifier.clickable { onClick(column) },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color =
                    if (isActive) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            if (isActive) {
                Icon(
                    imageVector =
                        if (direction == MonitoringSortDirection.ASC) {
                            Icons.Default.ArrowUpward
                        } else {
                            Icons.Default.ArrowDownward
                        },
                    contentDescription = null,
                    modifier = Modifier.size(10.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
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
    barColor: Color,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = containerName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = barColor,
            )
        }

        // Bar
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            val fraction = (value / maxValue).coerceIn(0.0, 1.0).toFloat()
            Box(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction)
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(barColor),
            )
        }
    }
}

@Composable
private fun StatsTableRow(
    stat: DerivedContainerStats,
    isNarrow: Boolean,
) {
    Column {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp, vertical = if (isNarrow) 8.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (isNarrow) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    // Row 1: name, id, CPU, MEM
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            Icons.Outlined.ViewInAr,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = AppColors.Running,
                        )
                        Text(
                            text = stat.containerName,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "CPU",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                        Text(
                            text = "%.1f%%".format(stat.cpuPercent),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = getCpuColor(stat.cpuPercent),
                        )
                        Text(
                            text = "Mem",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                        Text(
                            text = "%.1f%%".format(stat.memoryPercent),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = getMemoryColor(stat.memoryPercent),
                        )
                    }
                    // Row 2: disk R/W, net Rx/Tx
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth().padding(start = 20.dp),
                    ) {
                        IoLabel(
                            "Disk R",
                            ContainerStats.formatBytesPerSecond(stat.diskReadBytesPerSec),
                            AppColors.AccentBlue,
                        )
                        IoLabel(
                            "Disk W",
                            ContainerStats.formatBytesPerSecond(stat.diskWriteBytesPerSec),
                            AppColors.Running,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        IoLabel(
                            "Net \u2193",
                            ContainerStats.formatBytesPerSecond(stat.networkRxBytesPerSec),
                            AppColors.AccentBlueLight,
                        )
                        IoLabel(
                            "Net \u2191",
                            ContainerStats.formatBytesPerSecond(stat.networkTxBytesPerSec),
                            AppColors.Warning,
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.weight(1.4f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Outlined.ViewInAr,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = AppColors.Running,
                    )
                    Text(
                        text = "${stat.containerName} \u00B7 ${stat.containerId.take(12)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "%.1f%%".format(stat.cpuPercent),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = getCpuColor(stat.cpuPercent),
                    maxLines = 1,
                    modifier = Modifier.weight(0.7f),
                )
                Text(
                    text = "%.1f%%".format(stat.memoryPercent),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = getMemoryColor(stat.memoryPercent),
                    maxLines = 1,
                    modifier = Modifier.weight(0.7f),
                )
                Text(
                    text = ContainerStats.formatBytes(stat.memoryUsage),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1.0f),
                )
                Text(
                    text = ContainerStats.formatBytesPerSecond(stat.diskReadBytesPerSec),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.weight(0.9f),
                )
                Text(
                    text = ContainerStats.formatBytesPerSecond(stat.diskWriteBytesPerSec),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.weight(0.9f),
                )
                Text(
                    text = ContainerStats.formatBytesPerSecond(stat.networkRxBytesPerSec),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.weight(0.9f),
                )
                Text(
                    text = ContainerStats.formatBytesPerSecond(stat.networkTxBytesPerSec),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.weight(0.9f),
                )
            }
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
            thickness = 0.5.dp,
        )
    }
}

@Composable
private fun IoLabel(
    label: String,
    value: String,
    accent: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            color = accent,
            maxLines = 1,
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
    maxValue: Double = 100.0,
    modifier: Modifier = Modifier,
) {
    // Drive the gauge from the latest history point so the two views are
    // guaranteed to agree — both reflect the same aggregation upstream.
    val currentPercent = (history.lastOrNull() ?: 0.0).toFloat()
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(Radius.lg),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            // Title row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = iconTint,
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                SemiDonutGauge(
                    percent = currentPercent,
                    color = barColor(currentPercent.toDouble()),
                )
            }

            // Graph
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(Radius.md))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
            ) {
                // Y-axis labels
                Box(modifier = Modifier.fillMaxSize().padding(start = 4.dp)) {
                    Text(
                        text = "100%",
                        style = MaterialTheme.typography.labelSmall,
                        color = labelColor.copy(alpha = 0.5f),
                        modifier = Modifier.align(Alignment.TopStart),
                    )
                    Text(
                        text = "50%",
                        style = MaterialTheme.typography.labelSmall,
                        color = labelColor.copy(alpha = 0.5f),
                        modifier = Modifier.align(Alignment.CenterStart),
                    )
                    Text(
                        text = "0%",
                        style = MaterialTheme.typography.labelSmall,
                        color = labelColor.copy(alpha = 0.5f),
                        modifier = Modifier.align(Alignment.BottomStart),
                    )
                }

                Canvas(modifier = Modifier.fillMaxSize().padding(start = GRAPH_Y_AXIS_PADDING)) {
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
                            strokeWidth = 1f,
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
                            cornerRadius =
                                androidx.compose.ui.geometry
                                    .CornerRadius(2f, 2f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dual-series IO history graph. Both series share a dynamic max value
 * derived from the window, so the graph auto-scales as throughput changes.
 * Series A (e.g. read/rx) and series B (e.g. write/tx) are drawn as thin
 * stacked columns sharing each time slot.
 */
@Composable
private fun IoHistoryGraph(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    seriesA: List<Long>,
    seriesB: List<Long>,
    seriesALabel: String,
    seriesBLabel: String,
    seriesAColor: Color,
    seriesBColor: Color,
    maxHistorySize: Int,
    currentA: Long,
    currentB: Long,
    modifier: Modifier = Modifier,
) {
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    val maxInWindow =
        (seriesA.maxOrNull() ?: 0L)
            .coerceAtLeast(seriesB.maxOrNull() ?: 0L)
            .coerceAtLeast(1L)

    // Measure the widest Y-axis label so the Canvas can shift past it.
    // GB/s values ("1.2 GB/s") can exceed the 72 dp fallback on dense
    // labels; when they fit we stick with the constant so the IO chart
    // stays vertically aligned with the CPU chart above.
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall
    val density = LocalDensity.current
    val yAxisPadding =
        remember(maxInWindow, labelStyle, density) {
            val widestPx =
                listOf(
                    ContainerStats.formatBytesPerSecond(maxInWindow),
                    ContainerStats.formatBytesPerSecond(maxInWindow / 2),
                ).maxOf { label ->
                    textMeasurer.measure(AnnotatedString(label), labelStyle).size.width
                }
            with(density) { (widestPx.toDp() + 12.dp).coerceAtLeast(GRAPH_Y_AXIS_PADDING) }
        }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(Radius.lg),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = iconTint,
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    LegendChip(seriesALabel, ContainerStats.formatBytesPerSecond(currentA), seriesAColor)
                    LegendChip(seriesBLabel, ContainerStats.formatBytesPerSecond(currentB), seriesBColor)
                }
            }

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(Radius.md))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
            ) {
                Box(modifier = Modifier.fillMaxSize().padding(start = 4.dp)) {
                    Text(
                        text = ContainerStats.formatBytesPerSecond(maxInWindow),
                        style = MaterialTheme.typography.labelSmall,
                        color = labelColor.copy(alpha = 0.5f),
                        modifier = Modifier.align(Alignment.TopStart),
                    )
                    Text(
                        text = ContainerStats.formatBytesPerSecond(maxInWindow / 2),
                        style = MaterialTheme.typography.labelSmall,
                        color = labelColor.copy(alpha = 0.5f),
                        modifier = Modifier.align(Alignment.CenterStart),
                    )
                    Text(
                        text = "0",
                        style = MaterialTheme.typography.labelSmall,
                        color = labelColor.copy(alpha = 0.5f),
                        modifier = Modifier.align(Alignment.BottomStart),
                    )
                }

                Canvas(modifier = Modifier.fillMaxSize().padding(start = yAxisPadding)) {
                    val chartWidth = size.width
                    val chartHeight = size.height
                    val slotWidth = chartWidth / maxHistorySize
                    val seriesCount = 2
                    val seriesBarWidth = (slotWidth / seriesCount).coerceAtLeast(1f)
                    val gap = 0.5f

                    // Grid lines at 25%, 50%, 75%
                    for (i in 1..3) {
                        val y = chartHeight * (1 - i / 4f)
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(chartWidth, y),
                            strokeWidth = 1f,
                        )
                    }

                    val startOffsetA = (maxHistorySize - seriesA.size) * slotWidth
                    seriesA.forEachIndexed { index, value ->
                        val x = startOffsetA + index * slotWidth
                        val fraction = (value.toDouble() / maxInWindow).coerceIn(0.0, 1.0).toFloat()
                        val barHeight = chartHeight * fraction
                        drawRoundRect(
                            color = seriesAColor,
                            topLeft = Offset(x + gap, chartHeight - barHeight),
                            size = Size((seriesBarWidth - gap * 2).coerceAtLeast(1f), barHeight),
                            cornerRadius =
                                androidx.compose.ui.geometry
                                    .CornerRadius(2f, 2f),
                        )
                    }
                    val startOffsetB = (maxHistorySize - seriesB.size) * slotWidth
                    seriesB.forEachIndexed { index, value ->
                        val x = startOffsetB + index * slotWidth + seriesBarWidth
                        val fraction = (value.toDouble() / maxInWindow).coerceIn(0.0, 1.0).toFloat()
                        val barHeight = chartHeight * fraction
                        drawRoundRect(
                            color = seriesBColor,
                            topLeft = Offset(x + gap, chartHeight - barHeight),
                            size = Size((seriesBarWidth - gap * 2).coerceAtLeast(1f), barHeight),
                            cornerRadius =
                                androidx.compose.ui.geometry
                                    .CornerRadius(2f, 2f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendChip(
    label: String,
    value: String,
    color: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}

private fun getCpuColor(percent: Double): Color =
    when {
        percent < 25 -> AppColors.Running
        percent < 60 -> AppColors.Warning
        else -> AppColors.Stopped
    }

private fun getMemoryColor(percent: Double): Color =
    when {
        percent < 50 -> AppColors.Running
        percent < 80 -> AppColors.Warning
        else -> AppColors.Stopped
    }
