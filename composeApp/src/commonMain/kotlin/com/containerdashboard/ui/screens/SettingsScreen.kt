package com.containerdashboard.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.containerdashboard.BuildConfig
import com.containerdashboard.data.DockerHostConfig
import com.containerdashboard.data.engine.ColimaConfig
import com.containerdashboard.data.engine.EngineActionStatus
import com.containerdashboard.data.engine.EngineType
import com.containerdashboard.ui.components.AppBrandMark
import com.containerdashboard.ui.components.ConfirmActionDialog
import com.containerdashboard.ui.icons.outlined.BrightnessAuto
import com.containerdashboard.ui.icons.outlined.CleaningServices
import com.containerdashboard.ui.icons.outlined.DarkMode
import com.containerdashboard.ui.icons.outlined.LightMode
import com.containerdashboard.ui.icons.outlined.MonitorHeart
import com.containerdashboard.ui.icons.outlined.NetworkCheck
import com.containerdashboard.ui.icons.outlined.RestartAlt
import com.containerdashboard.ui.icons.outlined.Save
import com.containerdashboard.ui.icons.outlined.Stop
import com.containerdashboard.ui.icons.outlined.ViewInAr
import com.containerdashboard.ui.screens.viewmodel.ActionState
import com.containerdashboard.ui.screens.viewmodel.ConnectionTestState
import com.containerdashboard.ui.screens.viewmodel.MonitoringAggregation
import com.containerdashboard.ui.screens.viewmodel.SettingsScreenViewModel
import com.containerdashboard.ui.theme.AppColors
import com.containerdashboard.ui.theme.Radius
import com.containerdashboard.ui.theme.Spacing
import com.containerdashboard.ui.theme.ThemeMode

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsScreenViewModel = viewModel { SettingsScreenViewModel() },
) {
    val scrollState = rememberScrollState()

    val dockerHost by viewModel.engineHost().collectAsState(initial = DockerHostConfig.detectDockerHost())
    val themeMode by viewModel.themeMode().collectAsState(initial = ThemeMode.AUTO)
    val monitoringAggregation by viewModel
        .monitoringAggregation()
        .collectAsState(initial = MonitoringAggregation.ENGINE)
    val showSystemContainers by viewModel.showSystemContainers().collectAsState(initial = false)
    val confirmBeforeDelete by viewModel.confirmBeforeDelete().collectAsState(initial = true)
    val trayRefreshRate by viewModel.trayRefreshRateSeconds().collectAsState(initial = 5)
    val logsMaxLines by viewModel.logsMaxLines().collectAsState(initial = 1000)
    val connectionTestResult by viewModel.connectionTestResult.collectAsState()
    val actionState by viewModel.actionState.collectAsState()
    val engineType by viewModel.engineType.collectAsState()
    val colimaProfile by viewModel.colimaProfile.collectAsState()
    val colimaConfig by viewModel.colimaConfig.collectAsState()
    val engineActionStatus by viewModel.engineActionStatus.collectAsState()
    val engineCommandOutput by viewModel.engineCommandOutput.collectAsState()

    var confirmPrune by remember { mutableStateOf(false) }
    var confirmStopAll by remember { mutableStateOf(false) }

    // Load Colima config when engine type is Colima
    androidx.compose.runtime.LaunchedEffect(engineType) {
        if (engineType == EngineType.COLIMA) {
            viewModel.loadColimaConfig()
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        // Header
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )

        // Container Engine Section
        SettingsSection(title = "Container Engine") {
            EngineHostField(
                value = dockerHost,
                onValueChange = { viewModel.setEngineHost(it) },
            )

            Spacer(modifier = Modifier.height(Spacing.md))

            // Connection test feedback
            when (val state = connectionTestResult) {
                is ConnectionTestState.Testing -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        modifier = Modifier.padding(bottom = Spacing.sm),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("Testing connection...", style = MaterialTheme.typography.bodySmall)
                    }
                }
                is ConnectionTestState.Success -> {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = Spacing.sm),
                    )
                }
                is ConnectionTestState.Error -> {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = Spacing.sm),
                    )
                }
                ConnectionTestState.Idle -> {}
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                val isTesting = connectionTestResult is ConnectionTestState.Testing
                OutlinedButton(
                    onClick = {
                        viewModel.dismissTestResult()
                        viewModel.testConnection(dockerHost)
                    },
                    enabled = !isTesting,
                    modifier = Modifier.weight(1f),
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(Icons.Outlined.NetworkCheck, null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text(if (isTesting) "Testing…" else "Test Connection")
                }
                Button(
                    onClick = { viewModel.saveAndReconnect(dockerHost) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.Save, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text("Save & Reconnect")
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.sm))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Log Buffer Size",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = "Maximum lines kept in the log viewer ($logsMaxLines lines)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(Spacing.lg))
                val lineOptions = listOf(500, 1000, 2000, 5000)
                SingleChoiceSegmentedButtonRow {
                    lineOptions.forEachIndexed { index, count ->
                        SegmentedButton(
                            shape =
                                SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = lineOptions.size,
                                ),
                            onClick = { viewModel.setLogsMaxLines(count) },
                            selected = logsMaxLines == count,
                            label = { Text("$count") },
                        )
                    }
                }
            }
        }

        // Engine Management Section
        EngineManagementSection(
            engineType = engineType,
            colimaProfile = colimaProfile,
            colimaConfig = colimaConfig,
            actionStatus = engineActionStatus,
            commandOutput = engineCommandOutput,
            onStart = { cpu, mem, disk -> viewModel.startEngine(cpu, mem, disk) },
            onStop = { viewModel.stopEngine() },
            onClearState = { viewModel.clearEngineState() },
        )

        // Appearance Section
        SettingsSection(title = "Appearance") {
            ThemeModeSelector(
                selected = themeMode,
                onSelect = { viewModel.setThemeMode(it) },
            )
        }

        // Monitoring Section
        SettingsSection(title = "Monitoring") {
            MonitoringAggregationSelector(
                selected = monitoringAggregation,
                onSelect = { viewModel.setMonitoringAggregation(it) },
            )
        }

        // Behavior Section
        SettingsSection(title = "Behavior") {
            SettingsSwitch(
                title = "Show System Containers",
                subtitle = "Display system containers in the list",
                checked = showSystemContainers,
                onCheckedChange = { viewModel.setShowSystemContainers(it) },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.sm))

            SettingsSwitch(
                title = "Confirm Before Delete",
                subtitle = "Show confirmation dialog before deleting resources",
                checked = confirmBeforeDelete,
                onCheckedChange = { viewModel.setConfirmBeforeDelete(it) },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.sm))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Tray Stats Refresh Rate",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = "How often to update resource stats in the system tray (${trayRefreshRate}s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(Spacing.lg))
                val refreshOptions = listOf(3, 5, 10, 30)
                SingleChoiceSegmentedButtonRow {
                    refreshOptions.forEachIndexed { index, seconds ->
                        SegmentedButton(
                            shape =
                                SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = refreshOptions.size,
                                ),
                            onClick = { viewModel.setTrayRefreshRateSeconds(seconds) },
                            selected = trayRefreshRate == seconds,
                            label = { Text("${seconds}s") },
                        )
                    }
                }
            }
        }

        // Danger Zone Section
        SettingsSection(
            title = "Danger Zone",
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f),
        ) {
            Text(
                text = "Destructive actions. Each asks for confirmation before running.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )

            Spacer(modifier = Modifier.height(Spacing.sm))

            // Action feedback — reserves a stable height so the action buttons
            // below don't shift when the state transitions between Idle and
            // InProgress/Success/Error.
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 24.dp)
                        .padding(bottom = Spacing.sm),
                contentAlignment = Alignment.CenterStart,
            ) {
                when (val state = actionState) {
                    is ActionState.InProgress -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text(state.message, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    is ActionState.Success -> {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    is ActionState.Error -> {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    ActionState.Idle -> Unit
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                OutlinedButton(
                    onClick = { confirmPrune = true },
                    enabled = actionState !is ActionState.InProgress,
                    colors =
                        ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.CleaningServices, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text("Prune All Unused")
                }

                OutlinedButton(
                    onClick = { confirmStopAll = true },
                    enabled = actionState !is ActionState.InProgress,
                    colors =
                        ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.Stop, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text("Stop All Containers")
                }
            }
        }

        // About Section
        SettingsSection(title = "About") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppBrandMark(modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    text = "Container Dashboard",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(modifier = Modifier.height(Spacing.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Version", style = MaterialTheme.typography.bodyMedium)
                Text(
                    BuildConfig.VERSION,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(Spacing.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Built with", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Compose Multiplatform",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (confirmPrune) {
        ConfirmActionDialog(
            title = "Prune all unused resources?",
            body = "This removes unused containers, images, volumes, and networks. This action cannot be undone.",
            confirmLabel = "Prune",
            onConfirm = {
                confirmPrune = false
                viewModel.dismissActionState()
                viewModel.pruneAll()
            },
            onDismiss = { confirmPrune = false },
        )
    }

    if (confirmStopAll) {
        ConfirmActionDialog(
            title = "Stop all containers?",
            body = "This will stop every running container on the host.",
            confirmLabel = "Stop all",
            onConfirm = {
                confirmStopAll = false
                viewModel.dismissActionState()
                viewModel.stopAllContainers()
            },
            onDismiss = { confirmStopAll = false },
        )
    }
}

@Composable
private fun EngineManagementSection(
    engineType: EngineType,
    colimaProfile: String?,
    colimaConfig: ColimaConfig?,
    actionStatus: EngineActionStatus,
    commandOutput: String,
    onStart: (cpu: Int?, memory: Int?, disk: Int?) -> Unit,
    onStop: () -> Unit,
    onClearState: () -> Unit,
) {
    val isColima = engineType == EngineType.COLIMA
    val isRunning = actionStatus !is EngineActionStatus.Running

    var cpu by remember(colimaConfig) { mutableStateOf(colimaConfig?.cpu?.toString() ?: "4") }
    var memory by remember(colimaConfig) { mutableStateOf(colimaConfig?.memoryGB?.toString() ?: "8") }
    var disk by remember(colimaConfig) { mutableStateOf(colimaConfig?.diskGB?.toString() ?: "60") }

    // Range validation for Colima quotas. Blank = "use default", not an error.
    val cpuError = cpu.isNotBlank() && (cpu.toIntOrNull()?.let { it !in 1..256 } ?: true)
    val memoryError = memory.isNotBlank() && (memory.toIntOrNull()?.let { it !in 1..512 } ?: true)
    val diskError = disk.isNotBlank() && (disk.toIntOrNull()?.let { it !in 1..2048 } ?: true)
    val quotasValid = !cpuError && !memoryError && !diskError

    val profileLabel =
        if (colimaProfile != null && colimaProfile != "default") {
            "${engineType.displayName} ($colimaProfile)"
        } else {
            engineType.displayName
        }

    SettingsSection(title = "Engine Management") {
        // Engine name + status
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = profileLabel,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val statusColor =
                    when (actionStatus) {
                        is EngineActionStatus.Running -> MaterialTheme.colorScheme.tertiary
                        is EngineActionStatus.Done ->
                            if (actionStatus.success) AppColors.Running else AppColors.Stopped
                        else ->
                            if (colimaConfig != null) AppColors.Running else AppColors.Stopped
                    }
                val statusText =
                    when (actionStatus) {
                        is EngineActionStatus.Running -> actionStatus.message
                        is EngineActionStatus.Done -> actionStatus.message
                        else ->
                            if (colimaConfig != null || !isColima) "Running" else "Stopped"
                    }
                androidx.compose.material3.Surface(
                    modifier = Modifier.size(8.dp),
                    shape = RoundedCornerShape(Radius.sm),
                    color = statusColor,
                ) {}
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.md))

        // Colima-specific resource config
        if (isColima) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("CPU (cores)", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    OutlinedTextField(
                        value = cpu,
                        onValueChange = { cpu = it.filter { c -> c.isDigit() } },
                        singleLine = true,
                        isError = cpuError,
                        supportingText =
                            if (cpuError) {
                                { Text("1-256 cores") }
                            } else {
                                null
                            },
                        shape = RoundedCornerShape(Radius.md),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Memory (GB)", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    OutlinedTextField(
                        value = memory,
                        onValueChange = { memory = it.filter { c -> c.isDigit() } },
                        singleLine = true,
                        isError = memoryError,
                        supportingText =
                            if (memoryError) {
                                { Text("1-512 GB") }
                            } else {
                                null
                            },
                        shape = RoundedCornerShape(Radius.md),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Disk (GB)", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    OutlinedTextField(
                        value = disk,
                        onValueChange = { disk = it.filter { c -> c.isDigit() } },
                        singleLine = true,
                        isError = diskError,
                        supportingText =
                            if (diskError) {
                                { Text("1-2048 GB") }
                            } else {
                                null
                            },
                        shape = RoundedCornerShape(Radius.md),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (colimaConfig != null) {
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = "Current: ${colimaConfig.cpu} CPUs · ${colimaConfig.memoryGB} GB RAM · ${colimaConfig.diskGB} GB disk",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(Spacing.md))
        } else {
            Text(
                text = "To configure CPU and memory, use ${engineType.displayName}'s own settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(Spacing.md))
        }

        // Command output log
        if (commandOutput.isNotBlank()) {
            androidx.compose.material3.Surface(
                shape = RoundedCornerShape(Radius.md),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                val outputScrollState = rememberScrollState()
                androidx.compose.runtime.LaunchedEffect(commandOutput) {
                    outputScrollState.animateScrollTo(outputScrollState.maxValue)
                }
                Text(
                    text = commandOutput.trimEnd(),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .verticalScroll(outputScrollState)
                            .padding(Spacing.sm),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(Spacing.md))
        }

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            val isBusy = actionStatus is EngineActionStatus.Running

            if (colimaConfig != null || !isColima) {
                // Engine is running — show Stop
                OutlinedButton(
                    onClick = {
                        onClearState()
                        onStop()
                    },
                    enabled = !isBusy,
                    colors =
                        ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.Stop, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text("Stop")
                }

                if (isColima) {
                    Button(
                        onClick = {
                            onClearState()
                            onStop()
                            onStart(
                                cpu.toIntOrNull(),
                                memory.toIntOrNull(),
                                disk.toIntOrNull(),
                            )
                        },
                        enabled = !isBusy && quotasValid,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.RestartAlt, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Text("Restart")
                    }
                }
            } else {
                // Engine is stopped — show Start
                Button(
                    onClick = {
                        onClearState()
                        onStart(
                            if (isColima) cpu.toIntOrNull() else null,
                            if (isColima) memory.toIntOrNull() else null,
                            if (isColima) disk.toIntOrNull() else null,
                        )
                    },
                    enabled = !isBusy && (!isColima || quotasValid),
                    modifier = Modifier.weight(1f),
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(Icons.Outlined.PlayArrow, null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text("Start")
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        shape = RoundedCornerShape(Radius.lg),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(Spacing.lg))
            content()
        }
    }
}

@Composable
private fun ThemeModeSelector(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    val options: List<Triple<ThemeMode, String, ImageVector>> =
        listOf(
            Triple(ThemeMode.AUTO, "Auto", Icons.Outlined.BrightnessAuto),
            Triple(ThemeMode.LIGHT, "Light", Icons.Outlined.LightMode),
            Triple(ThemeMode.DARK, "Dark", Icons.Outlined.DarkMode),
        )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Theme",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "Auto follows your system appearance",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(Spacing.lg))
        SingleChoiceSegmentedButtonRow {
            options.forEachIndexed { index, (mode, label, icon) ->
                SegmentedButton(
                    shape =
                        SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = options.size,
                        ),
                    onClick = { onSelect(mode) },
                    selected = selected == mode,
                    icon = {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                        )
                    },
                    label = { Text(label) },
                )
            }
        }
    }
}

@Composable
private fun MonitoringAggregationSelector(
    selected: MonitoringAggregation,
    onSelect: (MonitoringAggregation) -> Unit,
) {
    val options: List<Triple<MonitoringAggregation, String, ImageVector>> =
        listOf(
            Triple(MonitoringAggregation.ENGINE, "Engine", Icons.Outlined.MonitorHeart),
            Triple(MonitoringAggregation.CONTAINER_AVG, "Per container", Icons.Outlined.ViewInAr),
        )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Usage aggregation",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "How CPU and memory are totaled in the Monitoring gauges",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(Spacing.lg))
        SingleChoiceSegmentedButtonRow {
            options.forEachIndexed { index, (mode, label, icon) ->
                SegmentedButton(
                    shape =
                        SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = options.size,
                        ),
                    onClick = { onSelect(mode) },
                    selected = selected == mode,
                    icon = {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                        )
                    },
                    label = {
                        Text(
                            text = label,
                            maxLines = 1,
                            softWrap = false,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EngineHostField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
            label = { Text("Engine host") },
            placeholder = { Text(DockerHostConfig.fallbackUri) },
            singleLine = true,
            shape = RoundedCornerShape(Radius.md),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DockerHostConfig.presets.forEach { preset ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = preset.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = preset.uri,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        onValueChange(preset.uri)
                        expanded = false
                    },
                )
            }
        }
    }
}
