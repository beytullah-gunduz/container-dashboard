package com.containerdashboard.ui.screens.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpOffset
import com.containerdashboard.ui.icons.outlined.ContentCopy

private val SystemNetworks = setOf("bridge", "host", "none")

@Composable
fun NetworkContextMenu(
    networkName: String,
    expanded: Boolean,
    onDismiss: () -> Unit,
    offset: DpOffset = DpOffset.Zero,
    onInspect: () -> Unit,
    onCopyId: () -> Unit,
    onRemove: () -> Unit,
) {
    val canDelete = networkName !in SystemNetworks
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        offset = offset,
    ) {
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
                        if (canDelete) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        },
                )
            },
            enabled = canDelete,
            onClick = {
                onDismiss()
                onRemove()
            },
            leadingIcon = {
                Icon(
                    Icons.Outlined.Delete,
                    null,
                    tint =
                        if (canDelete) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        },
                )
            },
        )
    }
}
