package com.containerdashboard.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.dockerdashboard.composeapp.generated.resources.Res
import com.dockerdashboard.composeapp.generated.resources.add
import org.jetbrains.compose.resources.painterResource

/**
 * Shared wrapper for resource-creation dialogs (Create Volume, Create Network, …).
 *
 * Keeps the visual presentation consistent across the app: outlined Add icon,
 * primary confirm button, Enter/Escape keyboard shortcuts. Form content goes
 * in the [content] slot.
 */
@Composable
fun CreateResourceDialog(
    title: String,
    confirmLabel: String = "Create",
    confirmEnabled: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onBlockedConfirm: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier =
            Modifier.onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (ev.key) {
                    Key.Enter -> {
                        if (confirmEnabled) {
                            onConfirm()
                            true
                        } else {
                            // Surface inline validation feedback rather than
                            // silently swallowing the keystroke.
                            onBlockedConfirm()
                            false
                        }
                    }
                    Key.Escape -> {
                        onDismiss()
                        true
                    }
                    else -> false
                }
            },
        icon = {
            Icon(
                painter = painterResource(Res.drawable.add),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = { Text(title) },
        text = content,
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = confirmEnabled,
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
