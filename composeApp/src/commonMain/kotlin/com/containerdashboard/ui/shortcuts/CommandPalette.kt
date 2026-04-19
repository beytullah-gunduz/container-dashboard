package com.containerdashboard.ui.shortcuts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.containerdashboard.ui.theme.IconSize
import com.containerdashboard.ui.theme.Radius
import com.containerdashboard.ui.theme.Spacing
import kotlinx.coroutines.delay

data class PaletteAction(
    val id: String,
    val label: String,
    val subtitle: String? = null,
    val section: String,
    val keyboardHint: String? = null,
    val icon: ImageVector? = null,
    val onRun: () -> Unit,
)

@Composable
fun CommandPalette(
    actions: List<PaletteAction>,
    onClose: () -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnClickOutside = true,
            ),
    ) {
        var rawQuery by remember { mutableStateOf("") }
        var query by remember { mutableStateOf("") }
        var selectedIndex by remember { mutableStateOf(0) }
        val focusRequester = remember { FocusRequester() }

        LaunchedEffect(rawQuery) {
            delay(50)
            query = rawQuery
            selectedIndex = 0
        }

        LaunchedEffect(Unit) {
            runCatching { focusRequester.requestFocus() }
        }

        val filtered by remember {
            derivedStateOf {
                if (query.isBlank()) {
                    actions
                } else {
                    actions.filter {
                        it.label.contains(query, ignoreCase = true) ||
                            (it.subtitle?.contains(query, ignoreCase = true) == true)
                    }
                }
            }
        }

        val sectioned by remember {
            derivedStateOf {
                filtered.groupBy { it.section }.toList()
            }
        }

        val flatActions by remember {
            derivedStateOf {
                sectioned.flatMap { it.second }
            }
        }

        val listState = rememberLazyListState()

        LaunchedEffect(selectedIndex, flatActions.size) {
            if (flatActions.isEmpty()) return@LaunchedEffect
            val safe = selectedIndex.coerceIn(0, flatActions.lastIndex)
            if (safe != selectedIndex) selectedIndex = safe
        }

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Surface(
                modifier =
                    Modifier
                        .padding(top = 80.dp)
                        .width(560.dp)
                        .heightIn(max = 520.dp)
                        .onPreviewKeyEvent { ev ->
                            if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (ev.key) {
                                Key.Escape -> {
                                    onClose()
                                    true
                                }
                                Key.Enter -> {
                                    flatActions.getOrNull(selectedIndex)?.let {
                                        it.onRun()
                                        onClose()
                                    }
                                    true
                                }
                                Key.DirectionDown -> {
                                    if (flatActions.isNotEmpty()) {
                                        selectedIndex = (selectedIndex + 1) % flatActions.size
                                    }
                                    true
                                }
                                Key.DirectionUp -> {
                                    if (flatActions.isNotEmpty()) {
                                        selectedIndex =
                                            if (selectedIndex <= 0) flatActions.lastIndex else selectedIndex - 1
                                    }
                                    true
                                }
                                else -> false
                            }
                        },
                shape = RoundedCornerShape(Radius.lg),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 12.dp,
            ) {
                Column {
                    // Search input row
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(IconSize.lg),
                        )
                        BasicTextField(
                            value = rawQuery,
                            onValueChange = { rawQuery = it },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester),
                            textStyle =
                                TextStyle(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                                ),
                            singleLine = true,
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            decorationBox = { inner ->
                                Box {
                                    if (rawQuery.isEmpty()) {
                                        Text(
                                            "Type a command or search…",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        )
                                    }
                                    inner()
                                }
                            },
                        )
                    }

                    androidx.compose.material3.HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )

                    if (flatActions.isEmpty()) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.xl),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "No results",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyColumn(state = listState) {
                            var runningIndex = 0
                            sectioned.forEach { (sectionName, sectionActions) ->
                                item(key = "section-$sectionName") {
                                    Text(
                                        text = sectionName.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier =
                                            Modifier.padding(
                                                horizontal = Spacing.md,
                                                vertical = Spacing.xs,
                                            ),
                                    )
                                }
                                items(
                                    items = sectionActions,
                                    key = { action -> "action-${action.id}" },
                                ) { action ->
                                    val localIndex = runningIndex
                                    runningIndex += 1
                                    val isSelected = localIndex == selectedIndex
                                    PaletteRow(
                                        action = action,
                                        isSelected = isSelected,
                                        onClick = {
                                            action.onRun()
                                            onClose()
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaletteRow(
    action: PaletteAction,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val bg =
        if (isSelected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            androidx.compose.ui.graphics.Color.Transparent
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(bg)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ).padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (action.icon != null) {
            Icon(
                imageVector = action.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(IconSize.md),
            )
        } else {
            Spacer(modifier = Modifier.size(IconSize.md))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = action.label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (action.subtitle != null) {
                Text(
                    text = action.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (action.keyboardHint != null) {
            Text(
                text = action.keyboardHint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}
