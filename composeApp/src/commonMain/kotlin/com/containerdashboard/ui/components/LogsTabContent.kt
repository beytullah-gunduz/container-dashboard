package com.containerdashboard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.containerdashboard.data.repository.PreferenceRepository
import com.containerdashboard.ui.state.LogsFilterState
import com.containerdashboard.ui.state.LogsPaneState
import com.containerdashboard.ui.theme.AppColors
import com.containerdashboard.ui.theme.Spacing
import com.containerdashboard.ui.theme.monospaceMedium
import com.dockerdashboard.composeapp.generated.resources.Res
import com.dockerdashboard.composeapp.generated.resources.arrow_drop_down_filled
import com.dockerdashboard.composeapp.generated.resources.close
import com.dockerdashboard.composeapp.generated.resources.search
import com.dockerdashboard.composeapp.generated.resources.vertical_align_bottom
import com.dockerdashboard.composeapp.generated.resources.wrap_text
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun LogsTabContent(
    state: LogsPaneState,
    onRefresh: () -> Unit,
) {
    val listState = rememberLazyListState()
    val horizontalScrollState = rememberScrollState()
    val wordWrap by PreferenceRepository.logsWordWrap().collectAsState(initial = true)
    val maxLines by PreferenceRepository.logsMaxLines().collectAsState(initial = 1000)
    val scope = rememberCoroutineScope()

    // Auto-follow: pinned to the bottom until the user scrolls away from it;
    // scrolling back to the bottom (or pressing "Scroll to bottom") resumes
    // following. Tracked from scroll-gesture endings rather than raw layout
    // info so freshly appended lines don't flip the flag by themselves.
    var autoFollow by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { inProgress ->
                if (!inProgress) autoFollow = listState.isAtBottom()
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = AppColors.AccentBlue,
                        strokeWidth = 3.dp,
                    )
                }
            }
            state.error != null -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(Spacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = state.error,
                        color = AppColors.Stopped,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(Spacing.md))
                    Button(onClick = onRefresh) { Text("Retry") }
                }
            }
            state.logs.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No logs available",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            else -> {
                val filterText by LogsFilterState.filterText.collectAsState()
                val selectedService by LogsFilterState.selectedService.collectAsState()
                var serviceDropdownExpanded by remember { mutableStateOf(false) }

                val serviceNames =
                    remember(state.containers) {
                        state.containers.map { it.composeService ?: it.displayName }.distinct()
                    }

                val lineKeyer = remember { LogLineKeyer() }
                val displayedLines =
                    remember(state.logs, filterText, selectedService) {
                        val keys = lineKeyer.keysFor(state.logs)
                        buildList {
                            state.logs.forEachIndexed { index, line ->
                                val matches =
                                    (selectedService == null || line.startsWith("[$selectedService]")) &&
                                        (filterText.isBlank() || line.contains(filterText, ignoreCase = true))
                                if (matches) add(LogLine(keys[index], line))
                            }
                        }
                    }

                LaunchedEffect(displayedLines) {
                    if (autoFollow && displayedLines.isNotEmpty()) {
                        listState.scrollToItem(displayedLines.lastIndex)
                    }
                }

                LaunchedEffect(selectedService, filterText) {
                    autoFollow = true
                    if (displayedLines.isNotEmpty()) {
                        listState.scrollToItem(displayedLines.lastIndex)
                    }
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    // Filter bar
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                                .height(28.dp)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(6.dp),
                                ).padding(horizontal = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Service dropdown (group mode only)
                        if (state.isGroupMode) {
                            Box {
                                Row(
                                    modifier =
                                        Modifier
                                            .clickable { serviceDropdownExpanded = true }
                                            .padding(end = Spacing.xs),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = selectedService ?: "All",
                                        style = MaterialTheme.typography.labelSmall,
                                        color =
                                            if (selectedService != null) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Icon(
                                        painterResource(Res.drawable.arrow_drop_down_filled),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                DropdownMenu(
                                    expanded = serviceDropdownExpanded,
                                    onDismissRequest = { serviceDropdownExpanded = false },
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "All",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight =
                                                    if (selectedService == null) {
                                                        FontWeight.Bold
                                                    } else {
                                                        FontWeight.Normal
                                                    },
                                            )
                                        },
                                        onClick = {
                                            LogsFilterState.selectedService.value = null
                                            serviceDropdownExpanded = false
                                        },
                                    )
                                    serviceNames.forEach { name ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    name,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight =
                                                        if (selectedService == name) {
                                                            FontWeight.Bold
                                                        } else {
                                                            FontWeight.Normal
                                                        },
                                                )
                                            },
                                            onClick = {
                                                LogsFilterState.selectedService.value = name
                                                serviceDropdownExpanded = false
                                            },
                                        )
                                    }
                                }
                            }
                            Box(
                                modifier =
                                    Modifier
                                        .width(1.dp)
                                        .height(16.dp)
                                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }

                        Icon(
                            painterResource(Res.drawable.search),
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        BasicTextField(
                            value = filterText,
                            onValueChange = { LogsFilterState.filterText.value = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle =
                                MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                            decorationBox = { innerTextField ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (filterText.isEmpty()) {
                                        Text(
                                            "Filter logs...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        )
                                    }
                                    innerTextField()
                                }
                            },
                        )
                        if (filterText.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            AppTooltip(label = "Clear filter") {
                                IconButton(
                                    onClick = { LogsFilterState.filterText.value = "" },
                                    modifier = Modifier.size(18.dp),
                                ) {
                                    Icon(
                                        painterResource(Res.drawable.close),
                                        contentDescription = "Clear filter",
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    SelectionContainer(modifier = Modifier.weight(1f)) {
                        if (wordWrap) {
                            LogLinesList(
                                listState = listState,
                                lines = displayedLines,
                                wordWrap = true,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            // With wrap off, the whole list pans horizontally as one
                            // unit. Lazy layouts can't size to their widest item, so
                            // measure the longest line (monospace, so longest by char
                            // count is widest) and give the list an explicit width.
                            val textMeasurer = rememberTextMeasurer()
                            val logStyle = MaterialTheme.typography.monospaceMedium
                            val density = LocalDensity.current
                            val contentWidth =
                                remember(displayedLines, logStyle, density) {
                                    val longest = displayedLines.maxByOrNull { it.text.length }?.text
                                    if (longest.isNullOrEmpty()) {
                                        0.dp
                                    } else {
                                        with(density) {
                                            textMeasurer
                                                .measure(longest, logStyle, softWrap = false)
                                                .size.width
                                                .toDp()
                                        } + Spacing.md * 2
                                    }
                                }
                            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                val listWidth = maxOf(contentWidth, maxWidth)
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .horizontalScroll(horizontalScrollState),
                                ) {
                                    LogLinesList(
                                        listState = listState,
                                        lines = displayedLines,
                                        wordWrap = false,
                                        modifier = Modifier.fillMaxHeight().width(listWidth),
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            Text(
                                text = "${state.logs.size} lines",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            AppTooltip(label = if (wordWrap) "Disable word wrap" else "Enable word wrap") {
                                IconButton(
                                    onClick = {
                                        scope.launch { PreferenceRepository.setLogsWordWrap(!wordWrap) }
                                    },
                                    modifier = Modifier.size(20.dp),
                                ) {
                                    Icon(
                                        painterResource(Res.drawable.wrap_text),
                                        contentDescription = if (wordWrap) "Disable word wrap" else "Enable word wrap",
                                        modifier = Modifier.size(14.dp),
                                        tint =
                                            if (wordWrap) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                    )
                                }
                            }
                            AppTooltip(label = "Scroll to bottom") {
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            val lastIndex = listState.layoutInfo.totalItemsCount - 1
                                            if (lastIndex >= 0) {
                                                listState.animateScrollToItem(lastIndex)
                                            }
                                            autoFollow = true
                                        }
                                    },
                                    modifier = Modifier.size(20.dp),
                                ) {
                                    Icon(
                                        painterResource(Res.drawable.vertical_align_bottom),
                                        contentDescription = "Scroll to bottom",
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        if (state.isFollowing) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Box(
                                    modifier =
                                        Modifier
                                            .size(6.dp)
                                            .background(AppColors.Running, CircleShape),
                                )
                                Text(
                                    text = "Live",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AppColors.Running,
                                )
                            }
                        } else {
                            Text(
                                text = "Last $maxLines lines",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogLinesList(
    listState: LazyListState,
    lines: List<LogLine>,
    wordWrap: Boolean,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(Spacing.md),
    ) {
        items(items = lines, key = { it.key }) { line ->
            Text(
                text = line.text,
                style = MaterialTheme.typography.monospaceMedium,
                color = MaterialTheme.colorScheme.onSurface,
                softWrap = wordWrap,
            )
        }
    }
}

private data class LogLine(
    val key: Long,
    val text: String,
)

/**
 * Assigns stable, monotonically increasing ids to log lines across buffer
 * updates that append at the end and trim from the front (the repository
 * caps the buffer at the configured max), so `LazyColumn` items keep their
 * identity — and the viewport keeps its anchor — as the capped buffer slides.
 */
private class LogLineKeyer {
    private var lines: List<String> = emptyList()
    private var ids: LongArray = LongArray(0)
    private var nextId = 0L

    fun keysFor(newLines: List<String>): LongArray {
        if (newLines === lines) return ids
        val old = lines
        val oldIds = ids
        // Find the smallest front-trim that makes the remaining old lines a
        // prefix of the new list. Mismatches fail on the first comparison in
        // the common case, so this is effectively O(n).
        var dropped = 0
        while (dropped <= old.size) {
            val overlap = old.size - dropped
            if (overlap <= newLines.size && isPrefix(old, dropped, newLines, overlap)) break
            dropped++
        }
        val overlap = old.size - dropped
        val newIds = LongArray(newLines.size)
        for (i in 0 until overlap) newIds[i] = oldIds[dropped + i]
        for (i in overlap until newLines.size) newIds[i] = nextId++
        lines = newLines
        ids = newIds
        return newIds
    }

    private fun isPrefix(
        old: List<String>,
        from: Int,
        new: List<String>,
        count: Int,
    ): Boolean {
        for (i in 0 until count) {
            if (old[from + i] != new[i]) return false
        }
        return true
    }
}

private fun LazyListState.isAtBottom(): Boolean {
    val info = layoutInfo
    val lastVisible = info.visibleItemsInfo.lastOrNull() ?: return true
    return lastVisible.index == info.totalItemsCount - 1 &&
        lastVisible.offset + lastVisible.size <= info.viewportEndOffset
}
