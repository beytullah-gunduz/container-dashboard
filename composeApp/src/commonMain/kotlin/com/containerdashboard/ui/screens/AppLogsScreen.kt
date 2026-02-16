package com.containerdashboard.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VerticalAlignBottom
import androidx.compose.material.icons.outlined.VerticalAlignCenter
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.containerdashboard.logging.AppLogEntry
import com.containerdashboard.ui.screens.viewmodel.AppLogsScreenViewModel
import com.containerdashboard.ui.theme.DockerColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun AppLogsScreen(
    modifier: Modifier = Modifier,
    viewModel: AppLogsScreenViewModel = viewModel { AppLogsScreenViewModel() },
) {
    val entries by viewModel.entries.collectAsState()
    val totalCount by viewModel.totalCount.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val levelFilter by viewModel.levelFilter.collectAsState()
    val autoScroll by viewModel.autoScroll.collectAsState()

    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new entries arrive
    LaunchedEffect(entries.size, autoScroll) {
        if (autoScroll && entries.isNotEmpty()) {
            listState.animateScrollToItem(entries.lastIndex)
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ── Header ──────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Application Logs",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${entries.size} of $totalCount entries",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Auto-scroll toggle
                IconButton(onClick = { viewModel.toggleAutoScroll() }) {
                    Icon(
                        imageVector =
                            if (autoScroll) {
                                Icons.Outlined.VerticalAlignBottom
                            } else {
                                Icons.Outlined.VerticalAlignCenter
                            },
                        contentDescription = "Toggle auto-scroll",
                        tint =
                            if (autoScroll) {
                                DockerColors.DockerBlue
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }

                // Clear logs
                OutlinedButton(onClick = { viewModel.clearLogs() }) {
                    Icon(
                        Icons.Outlined.DeleteSweep,
                        null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Clear")
                }
            }
        }

        // ── Search + Filters ────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search logs…") },
                leadingIcon = {
                    Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Outlined.Clear, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
            )
        }

        // Level filter chips
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LevelFilterChip(label = "All", selected = levelFilter == null) {
                viewModel.setLevelFilter(null)
            }
            listOf("DEBUG", "INFO", "WARN", "ERROR").forEach { level ->
                LevelFilterChip(
                    label = level,
                    selected = levelFilter == level,
                    color = levelColor(level),
                ) {
                    viewModel.setLevelFilter(level)
                }
            }
        }

        // ── Log List ────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ),
        ) {
            if (entries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Article,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text =
                                if (searchQuery.isNotEmpty() || levelFilter != null) {
                                    "No matching log entries"
                                } else {
                                    "No log entries yet"
                                },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text =
                                if (searchQuery.isNotEmpty() || levelFilter != null) {
                                    "Try adjusting your search or filters"
                                } else {
                                    "Logs will appear here as the application runs"
                                },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(entries, key = { "${it.timestamp}-${it.message.hashCode()}-${it.threadName}" }) { entry ->
                        LogEntryRow(entry = entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogEntryRow(entry: AppLogEntry) {
    val bgColor by animateColorAsState(
        targetValue =
            when (entry.level) {
                "ERROR" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                "WARN" -> Color(0xFFFFF3E0).copy(alpha = 0.10f)
                else -> Color.Transparent
            },
    )

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(bgColor)
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Timestamp
        Text(
            text = formatTimestamp(entry.timestamp),
            style =
                MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )

        // Level badge
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = levelColor(entry.level).copy(alpha = 0.15f),
            modifier = Modifier.width(48.dp),
        ) {
            Text(
                text = entry.level,
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                    ),
                color = levelColor(entry.level),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                maxLines = 1,
            )
        }

        // Thread
        Text(
            text = "[${entry.threadName}]",
            style =
                MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                ),
            color = DockerColors.DockerBlue.copy(alpha = 0.7f),
            maxLines = 1,
        )

        // Logger (short name)
        Text(
            text = entry.shortLoggerName,
            style =
                MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                ),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            maxLines = 1,
        )

        // Message
        Text(
            text =
                buildString {
                    append(entry.message)
                    entry.throwable?.let { append(" | $it") }
                },
            style =
                MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Visible,
        )
    }
}

@Composable
private fun LevelFilterChip(
    label: String,
    selected: Boolean,
    color: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            )
        },
        colors =
            FilterChipDefaults.filterChipColors(
                selectedContainerColor = color.copy(alpha = 0.15f),
                selectedLabelColor = color,
            ),
        border =
            FilterChipDefaults.filterChipBorder(
                borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                selectedBorderColor = color.copy(alpha = 0.5f),
                enabled = true,
                selected = selected,
            ),
    )
}

private fun levelColor(level: String): Color =
    when (level) {
        "ERROR" -> DockerColors.Stopped
        "WARN" -> DockerColors.Warning
        "INFO" -> DockerColors.Running
        "DEBUG" -> DockerColors.DockerBlue
        "TRACE" -> DockerColors.TextMuted
        else -> DockerColors.TextSecondary
    }

private val timestampFormatter =
    DateTimeFormatter
        .ofPattern("HH:mm:ss.SSS")
        .withZone(ZoneId.systemDefault())

private fun formatTimestamp(millis: Long): String = timestampFormatter.format(Instant.ofEpochMilli(millis))
