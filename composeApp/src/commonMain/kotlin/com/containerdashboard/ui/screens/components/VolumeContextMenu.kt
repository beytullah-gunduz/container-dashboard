package com.containerdashboard.ui.screens.components

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpOffset
import com.dockerdashboard.composeapp.generated.resources.Res
import com.dockerdashboard.composeapp.generated.resources.content_copy
import com.dockerdashboard.composeapp.generated.resources.delete
import com.dockerdashboard.composeapp.generated.resources.info
import org.jetbrains.compose.resources.painterResource

@Composable
fun VolumeContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    offset: DpOffset = DpOffset.Zero,
    onInspect: () -> Unit,
    onCopyName: () -> Unit,
    onRemove: () -> Unit,
) {
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
            leadingIcon = { Icon(painterResource(Res.drawable.info), null) },
        )
        DropdownMenuItem(
            text = { Text("Copy name") },
            onClick = {
                onDismiss()
                onCopyName()
            },
            leadingIcon = { Icon(painterResource(Res.drawable.content_copy), null) },
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = {
                Text("Delete", color = MaterialTheme.colorScheme.error)
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
