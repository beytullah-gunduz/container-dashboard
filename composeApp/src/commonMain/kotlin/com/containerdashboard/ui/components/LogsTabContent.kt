package com.containerdashboard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.WrapText
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VerticalAlignBottom
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.containerdashboard.data.repository.PreferenceRepository
import com.containerdashboard.ui.state.LogsPaneState
import com.containerdashboard.ui.theme.AppColors
import com.containerdashboard.ui.theme.monospaceMedium
import kotlinx.coroutines.launch

@Composable
internal fun LogsTabContent(
    state: LogsPaneState,
    onRefresh: () -> Unit,
) {
    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()
    val wordWrap by PreferenceRepository.logsWordWrap().collectAsState(initial = true)
    val maxLines by PreferenceRepository.logsMaxLines().collectAsState(initial = 1000)
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.logs) {
        verticalScrollState.scrollTo(verticalScrollState.maxValue)
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
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = state.error,
                        color = AppColors.Stopped,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
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
                var filterText by remember { mutableStateOf("") }
                var selectedService by remember { mutableStateOf<String?>(null) }
                var serviceDropdownExpanded by remember { mutableStateOf(false) }

                val serviceNames =
                    remember(state.containers) {
                        state.containers.map { it.composeService ?: it.displayName }.distinct()
                    }

                val displayedLogs =
                    remember(state.logs, filterText, selectedService) {
                        state.logs
                            .asSequence()
                            .filter { line ->
                                (selectedService == null || line.startsWith("[$selectedService]")) &&
                                    (filterText.isBlank() || line.contains(filterText, ignoreCase = true))
                            }.joinToString("\n")
                    }

                LaunchedEffect(selectedService, filterText) {
                    verticalScrollState.scrollTo(verticalScrollState.maxValue)
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    // Filter bar
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .height(28.dp)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(6.dp),
                                ).padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Service dropdown (group mode only)
                        if (state.isGroupMode) {
                            Box {
                                Row(
                                    modifier =
                                        Modifier
                                            .clickable { serviceDropdownExpanded = true }
                                            .padding(end = 4.dp),
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
                                        Icons.Filled.ArrowDropDown,
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
                                            selectedService = null
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
                                                selectedService = name
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
                            Icons.Outlined.Search,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        BasicTextField(
                            value = filterText,
                            onValueChange = { filterText = it },
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
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = { filterText = "" },
                                modifier = Modifier.size(18.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Clear,
                                    contentDescription = "Clear filter",
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    SelectionContainer(modifier = Modifier.weight(1f)) {
                        Text(
                            text = displayedLogs,
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .verticalScroll(verticalScrollState)
                                    .then(
                                        if (!wordWrap) {
                                            Modifier.horizontalScroll(horizontalScrollState)
                                        } else {
                                            Modifier
                                        },
                                    ).padding(12.dp),
                            style = MaterialTheme.typography.monospaceMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            softWrap = wordWrap,
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                                        Icons.AutoMirrored.Outlined.WrapText,
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
                                            verticalScrollState.animateScrollTo(verticalScrollState.maxValue)
                                        }
                                    },
                                    modifier = Modifier.size(20.dp),
                                ) {
                                    Icon(
                                        Icons.Outlined.VerticalAlignBottom,
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
