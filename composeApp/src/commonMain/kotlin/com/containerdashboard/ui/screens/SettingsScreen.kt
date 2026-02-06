package com.containerdashboard.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.containerdashboard.ui.screens.viewmodel.SettingsScreenViewModel

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsScreenViewModel = viewModel { SettingsScreenViewModel() }
) {
    val scrollState = rememberScrollState()
    
    val dockerHost by viewModel.engineHost().collectAsState(initial = "unix:///var/run/docker.sock")
    val darkTheme by viewModel.darkTheme().collectAsState(initial = true)
    val autoRefresh by viewModel.autoRefresh().collectAsState(initial = false)

    val refreshInterval by viewModel.refreshInterval().collectAsState(initial = 5f)
    val showSystemContainers by viewModel.showSystemContainers().collectAsState(initial = false)
    val confirmBeforeDelete by viewModel.confirmBeforeDelete().collectAsState(initial = true)
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Header
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        
        // Container Engine Section
        SettingsSection(title = "Container Engine") {
            SettingsTextField(
                label = "Engine Host",
                value = dockerHost,
                onValueChange = { viewModel.engineHost = it },
                placeholder = "unix:///var/run/docker.sock"
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { /* Test connection */ },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.NetworkCheck, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Test Connection")
                }
                Button(
                    onClick = { /* Save */ },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.Save, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save")
                }
            }
        }
        
        // Appearance Section
        SettingsSection(title = "Appearance") {
            SettingsSwitch(
                title = "Dark Theme",
                subtitle = "Use dark color scheme",
                checked = darkTheme,
                onCheckedChange = { viewModel.darkTheme = it }
            )
        }
        
        // Behavior Section
        SettingsSection(title = "Behavior") {
            SettingsSwitch(
                title = "Auto Refresh",
                subtitle = "Automatically refresh container status",
                checked = autoRefresh,
                onCheckedChange = {
                    viewModel.autoRefresh = it
                }

            )
            
            if (autoRefresh) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Refresh Interval",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "${refreshInterval.toInt()} seconds",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = refreshInterval,
                        onValueChange = { viewModel.refreshInterval = it },
                        valueRange = 1f..30f,
                        steps = 28
                    )
                }
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            SettingsSwitch(
                title = "Show System Containers",
                subtitle = "Display system containers in the list",
                checked = showSystemContainers,
                onCheckedChange = { viewModel.showSystemContainers = it }
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            SettingsSwitch(
                title = "Confirm Before Delete",
                subtitle = "Show confirmation dialog before deleting resources",
                checked = confirmBeforeDelete,
                onCheckedChange = { viewModel.confirmBeforeDelete = it }
            )
        }
        
        // Danger Zone Section
        SettingsSection(
            title = "Danger Zone",
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
        ) {
            Text(
                text = "These actions cannot be undone. Please proceed with caution.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { /* Prune all */ },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.CleaningServices, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Prune All Unused")
                }
                
                OutlinedButton(
                    onClick = { /* Stop all */ },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.weight(1f)
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
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Version", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "1.0.0",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Built with", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Compose Multiplatform",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
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
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SettingsTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder) },
            singleLine = true,
            shape = RoundedCornerShape(8.dp)
        )
    }
}
