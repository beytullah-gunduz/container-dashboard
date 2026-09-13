package com.containerdashboard.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.containerdashboard.ui.screens.typedCountMatches
import com.dockerdashboard.composeapp.generated.resources.Res
import com.dockerdashboard.composeapp.generated.resources.warning
import org.jetbrains.compose.resources.painterResource

/**
 * Confirmation for deleting the containers currently shown on the Containers
 * screen (UX audit U1.4). States the real scope, how many running containers
 * will be force-stopped, and — for large sets — requires the count to be typed
 * back before Delete is enabled.
 */
@Composable
fun DeleteAllContainersDialog(
    title: String,
    containerCount: Int,
    runningCount: Int,
    isFilteredSubset: Boolean,
    requireTypedCount: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var typed by remember { mutableStateOf("") }
    val confirmEnabled = !requireTypedCount || typedCountMatches(typed, containerCount)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                painterResource(Res.drawable.warning),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text =
                        if (isFilteredSubset) {
                            "Only the containers matching your current search and filter will be removed."
                        } else {
                            "Every container on this engine will be removed."
                        },
                )
                if (runningCount > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text =
                            if (runningCount == 1) {
                                "1 running container will be force-stopped first."
                            } else {
                                "$runningCount running containers will be force-stopped first."
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "This action cannot be undone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                if (requireTypedCount) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        label = { Text("Type $containerCount to confirm") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                // Guard the handler, not just the button: Compose Desktop's
                // accessibility bridge performs the press action on disabled
                // controls (observed with AXPress on 1.12.0), so `enabled` alone
                // is not a safety boundary for a destructive action.
                onClick = { if (confirmEnabled) onConfirm() },
                enabled = confirmEnabled,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
            ) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
