package com.containerdashboard.ui.shortcuts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import com.containerdashboard.ui.theme.Spacing
import com.containerdashboard.ui.util.isMacHost

private val mod = if (isMacHost) "\u2318" else "Ctrl"

private data class ShortcutEntry(
    val keys: String,
    val label: String,
)

private val entries =
    listOf(
        ShortcutEntry("$mod K", "Open command palette"),
        ShortcutEntry("$mod F", "Focus search"),
        ShortcutEntry("$mod ,", "Open Settings"),
        ShortcutEntry("$mod 1 – $mod 7", "Jump to sidebar screen"),
        ShortcutEntry("Esc", "Close overlays / logs pane"),
        ShortcutEntry("? or $mod /", "Show this cheatsheet"),
    )

@Composable
fun KeyboardShortcutsOverlay(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier =
            Modifier.onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (ev.key == Key.Escape) {
                    onDismiss()
                    true
                } else {
                    false
                }
            },
        title = { Text("Keyboard shortcuts") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                entries.forEach { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                    ) {
                        Text(
                            text = entry.keys,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.width(140.dp).padding(end = Spacing.md),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = entry.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
