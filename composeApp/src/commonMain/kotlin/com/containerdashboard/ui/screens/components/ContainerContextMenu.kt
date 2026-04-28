package com.containerdashboard.ui.screens.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpOffset
import com.containerdashboard.data.models.Container
import com.containerdashboard.ui.icons.automirrored.filled.Article
import com.containerdashboard.ui.icons.automirrored.outlined.Article
import com.containerdashboard.ui.icons.outlined.ContentCopy
import com.containerdashboard.ui.icons.outlined.Pause
import com.containerdashboard.ui.icons.outlined.RestartAlt
import com.containerdashboard.ui.icons.outlined.Stop
import com.containerdashboard.ui.theme.AppColors

@Composable
fun ContainerContextMenu(
    container: Container,
    expanded: Boolean,
    onDismiss: () -> Unit,
    isViewingLogs: Boolean,
    offset: DpOffset = DpOffset.Zero,
    onViewLogs: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit,
    onPause: () -> Unit,
    onUnpause: () -> Unit,
    onInspect: () -> Unit,
    onCopyId: () -> Unit,
    onRemove: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        offset = offset,
    ) {
        DropdownMenuItem(
            text = { Text("View logs") },
            onClick = {
                onDismiss()
                onViewLogs()
            },
            leadingIcon = {
                Icon(
                    if (isViewingLogs) Icons.AutoMirrored.Filled.Article else Icons.AutoMirrored.Outlined.Article,
                    contentDescription = null,
                    tint =
                        if (isViewingLogs) {
                            AppColors.AccentBlue
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                )
            },
        )

        HorizontalDivider()

        when {
            container.isRunning -> {
                DropdownMenuItem(
                    text = { Text("Restart") },
                    onClick = {
                        onDismiss()
                        onRestart()
                    },
                    leadingIcon = { Icon(Icons.Outlined.RestartAlt, null) },
                )
                DropdownMenuItem(
                    text = { Text("Pause") },
                    onClick = {
                        onDismiss()
                        onPause()
                    },
                    leadingIcon = { Icon(Icons.Outlined.Pause, null) },
                )
                DropdownMenuItem(
                    text = { Text("Stop") },
                    onClick = {
                        onDismiss()
                        onStop()
                    },
                    leadingIcon = { Icon(Icons.Outlined.Stop, null, tint = AppColors.Stopped) },
                )
            }
            container.isPaused -> {
                DropdownMenuItem(
                    text = { Text("Resume") },
                    onClick = {
                        onDismiss()
                        onUnpause()
                    },
                    leadingIcon = { Icon(Icons.Outlined.PlayArrow, null, tint = AppColors.Running) },
                )
            }
            else -> {
                DropdownMenuItem(
                    text = { Text("Start") },
                    onClick = {
                        onDismiss()
                        onStart()
                    },
                    leadingIcon = { Icon(Icons.Outlined.PlayArrow, null, tint = AppColors.Running) },
                )
            }
        }

        HorizontalDivider()

        DropdownMenuItem(
            text = { Text("Inspect") },
            onClick = {
                onDismiss()
                onInspect()
            },
            leadingIcon = { Icon(Icons.Outlined.Info, null) },
        )
        DropdownMenuItem(
            text = { Text("Copy ID") },
            onClick = {
                onDismiss()
                onCopyId()
            },
            leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
        )

        HorizontalDivider()

        DropdownMenuItem(
            text = {
                Text(
                    "Delete",
                    color =
                        if (!container.isRunning) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        },
                )
            },
            onClick = {
                onDismiss()
                onRemove()
            },
            leadingIcon = {
                Icon(
                    Icons.Outlined.Delete,
                    null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
        )
    }
}
