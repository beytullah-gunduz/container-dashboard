package com.containerdashboard.ui.screens.components

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpOffset
import com.containerdashboard.data.models.Container
import com.containerdashboard.ui.theme.AppColors
import com.dockerdashboard.composeapp.generated.resources.Res
import com.dockerdashboard.composeapp.generated.resources.article
import com.dockerdashboard.composeapp.generated.resources.article_filled
import com.dockerdashboard.composeapp.generated.resources.content_copy
import com.dockerdashboard.composeapp.generated.resources.delete
import com.dockerdashboard.composeapp.generated.resources.info
import com.dockerdashboard.composeapp.generated.resources.pause
import com.dockerdashboard.composeapp.generated.resources.play_arrow
import com.dockerdashboard.composeapp.generated.resources.restart_alt
import com.dockerdashboard.composeapp.generated.resources.stop
import org.jetbrains.compose.resources.painterResource

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
                    painterResource(if (isViewingLogs) Res.drawable.article_filled else Res.drawable.article),
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
                    leadingIcon = { Icon(painterResource(Res.drawable.restart_alt), null) },
                )
                DropdownMenuItem(
                    text = { Text("Pause") },
                    onClick = {
                        onDismiss()
                        onPause()
                    },
                    leadingIcon = { Icon(painterResource(Res.drawable.pause), null) },
                )
                DropdownMenuItem(
                    text = { Text("Stop") },
                    onClick = {
                        onDismiss()
                        onStop()
                    },
                    leadingIcon = { Icon(painterResource(Res.drawable.stop), null, tint = AppColors.Stopped) },
                )
            }
            container.isPaused -> {
                DropdownMenuItem(
                    text = { Text("Resume") },
                    onClick = {
                        onDismiss()
                        onUnpause()
                    },
                    leadingIcon = { Icon(painterResource(Res.drawable.play_arrow), null, tint = AppColors.Running) },
                )
            }
            else -> {
                DropdownMenuItem(
                    text = { Text("Start") },
                    onClick = {
                        onDismiss()
                        onStart()
                    },
                    leadingIcon = { Icon(painterResource(Res.drawable.play_arrow), null, tint = AppColors.Running) },
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
            leadingIcon = { Icon(painterResource(Res.drawable.info), null) },
        )
        DropdownMenuItem(
            text = { Text("Copy ID") },
            onClick = {
                onDismiss()
                onCopyId()
            },
            leadingIcon = { Icon(painterResource(Res.drawable.content_copy), null) },
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
                    painterResource(Res.drawable.delete),
                    null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
        )
    }
}
