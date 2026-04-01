package com.containerdashboard.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Stop
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.containerdashboard.data.DockerHostConfig
import com.containerdashboard.ui.screens.viewmodel.ActionState
import com.containerdashboard.ui.screens.viewmodel.ConnectionTestState
import com.containerdashboard.ui.screens.viewmodel.SettingsScreenViewModel

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsScreenViewModel = viewModel { SettingsScreenViewModel() },
) {
    val scrollState = rememberScrollState()

    val dockerHost by viewModel.engineHost().collectAsState(initial = DockerHostConfig.detectDockerHost())
    val darkTheme by viewModel.darkTheme().collectAsState(initial = true)
    val showSystemContainers by viewModel.showSystemContainers().collectAsState(initial = false)
    val confirmBeforeDelete by viewModel.confirmBeforeDelete().collectAsState(initial = true)
    val trayRefreshRate by viewModel.trayRefreshRateSeconds().collectAsState(initial = 5)
    val connectionTestResult by viewModel.connectionTestResult.collectAsState()
    val actionState by viewModel.actionState.collectAsState()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
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

            Spacer(modifier = Modifier.height(12.dp))

            // Connection test feedback
            when (val state = connectionTestResult) {
                is ConnectionTestState.Testing -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 8.dp),
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
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                is ConnectionTestState.Error -> {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                ConnectionTestState.Idle -> {}
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        viewModel.dismissTestResult()
                        viewModel.testConnection(dockerHost)
                    },
                    enabled = connectionTestResult !is ConnectionTestState.Testing,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.NetworkCheck, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Test Connection")
                }
                Button(
                    onClick = { viewModel.saveAndReconnect(dockerHost) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.Save, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save & Reconnect")
                }
            }
        }

        // Appearance Section
        SettingsSection(title = "Appearance") {
            SettingsSwitch(
                title = "Dark Theme",
                subtitle = "Use dark color scheme",
                checked = darkTheme,
                onCheckedChange = { viewModel.setDarkTheme(it) },
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

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SettingsSwitch(
                title = "Confirm Before Delete",
                subtitle = "Show confirmation dialog before deleting resources",
                checked = confirmBeforeDelete,
                onCheckedChange = { viewModel.setConfirmBeforeDelete(it) },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

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
                Spacer(modifier = Modifier.width(16.dp))
                val refreshOptions = listOf(3, 5, 10, 30)
                SingleChoiceSegmentedButtonRow {
                    refreshOptions.forEachIndexed { index, seconds ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(
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
                text = "These actions cannot be undone. Please proceed with caution.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Action feedback
            when (val state = actionState) {
                is ActionState.InProgress -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 8.dp),
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
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                is ActionState.Error -> {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                ActionState.Idle -> {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        viewModel.dismissActionState()
                        viewModel.pruneAll()
                    },
                    enabled = actionState !is ActionState.InProgress,
                    colors =
                        ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.CleaningServices, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Prune All Unused")
                }

                OutlinedButton(
                    onClick = {
                        viewModel.dismissActionState()
                        viewModel.stopAllContainers()
                    },
                    enabled = actionState !is ActionState.InProgress,
                    colors =
                        ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.Stop, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Stop All Containers")
                }
            }
        }

        // About Section
        SettingsSection(title = "About") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Version", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "1.0.0",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
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
}

@Composable
private fun SettingsSection(
    title: String,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
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
            Spacer(modifier = Modifier.height(16.dp))
            content()
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

    Column {
        Text(
            text = "Engine Host",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                placeholder = { Text(DockerHostConfig.fallbackUri) },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
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
}
