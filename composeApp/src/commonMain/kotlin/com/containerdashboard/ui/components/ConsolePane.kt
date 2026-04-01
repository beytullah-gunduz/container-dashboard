package com.containerdashboard.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.containerdashboard.ui.state.LogsPaneState
import com.containerdashboard.ui.theme.AppColors

@Composable
fun ContainerExtraPane(
    logsState: LogsPaneState,
    onRefreshLogs: () -> Unit,
    onClose: () -> Unit,
    consoleContent: @Composable () -> Unit,
    onConsoleTabSelected: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Logs", "Console")

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color(0xFF1A1A1A),
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header with tabs
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF252526))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
                SecondaryTabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier.weight(1f),
                    containerColor = Color.Transparent,
                    contentColor = AppColors.AccentBlue,
                    divider = {},
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = {
                                selectedTab = index
                                if (index == 1) {
                                    onConsoleTabSelected()
                                }
                            },
                            text = {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.labelMedium,
                                    color =
                                        if (selectedTab == index) {
                                            AppColors.AccentBlue
                                        } else {
                                            Color.White.copy(alpha = 0.6f)
                                        },
                                )
                            },
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                logsState.container?.let {
                    Text(
                        text = it.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.5f),
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        modifier = Modifier.size(18.dp),
                        tint = Color.White.copy(alpha = 0.7f),
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF404040))

            when (selectedTab) {
                0 ->
                    LogsTabContent(
                        state = logsState,
                        onRefresh = onRefreshLogs,
                    )
                1 -> consoleContent()
            }
        }
    }
}

@Composable
private fun LogsTabContent(
    state: LogsPaneState,
    onRefresh: () -> Unit,
) {
    val scrollState = rememberScrollState()

    LaunchedEffect(state.logs) {
        scrollState.animateScrollTo(scrollState.maxValue)
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
                        color = Color.White.copy(alpha = 0.4f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    SelectionContainer(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.logs,
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .verticalScroll(scrollState)
                                    .padding(12.dp),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = Color(0xFFD4D4D4),
                        )
                    }

                    HorizontalDivider(color = Color(0xFF404040))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "${state.logs.lines().size} lines",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.4f),
                        )
                        Text(
                            text = "Last 500 lines",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.4f),
                        )
                    }
                }
            }
        }
    }
}
