package com.containerdashboard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.containerdashboard.data.models.ContainerFileEntry
import com.containerdashboard.data.models.FileType
import com.containerdashboard.ui.state.FileTreeNode
import com.containerdashboard.ui.state.FilesPaneState
import com.containerdashboard.ui.theme.AppColors
import com.containerdashboard.ui.theme.Spacing
import com.containerdashboard.ui.theme.monospaceMedium
import com.containerdashboard.ui.util.formatBytes
import com.dockerdashboard.composeapp.generated.resources.Res
import com.dockerdashboard.composeapp.generated.resources.arrow_back_filled
import com.dockerdashboard.composeapp.generated.resources.article
import com.dockerdashboard.composeapp.generated.resources.chevron_right_filled
import com.dockerdashboard.composeapp.generated.resources.download
import com.dockerdashboard.composeapp.generated.resources.expand_more_filled
import com.dockerdashboard.composeapp.generated.resources.folder
import com.dockerdashboard.composeapp.generated.resources.info
import com.dockerdashboard.composeapp.generated.resources.refresh
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun FilesTabContent(
    state: FilesPaneState,
    onToggleNode: (ContainerFileEntry) -> Unit,
    onFileClick: (ContainerFileEntry) -> Unit,
    onRefresh: () -> Unit,
    onCloseViewer: () -> Unit,
    onDownload: (ContainerFileEntry) -> Unit,
) {
    if (!state.isRunning) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "File browsing is only available for running containers.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(Spacing.xl),
            )
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        FilesHeader(isLoading = state.isLoading, onRefresh = onRefresh)
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Box(modifier = Modifier.fillMaxSize()) {
            val selected = state.selectedFile
            if (selected != null) {
                FileViewer(state = state, selected = selected, onClose = onCloseViewer, onDownload = onDownload)
            } else {
                FileTree(
                    state = state,
                    onToggleNode = onToggleNode,
                    onFileClick = onFileClick,
                    onDownload = onDownload,
                    onRefresh = onRefresh,
                )
            }
        }
    }
}

@Composable
private fun FilesHeader(
    isLoading: Boolean,
    onRefresh: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(Res.drawable.folder),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "/",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.weight(1f))
        if (isLoading) {
            Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        } else {
            PaneActionButton(
                icon = Res.drawable.refresh,
                contentDescription = "Refresh",
                enabled = true,
                onClick = onRefresh,
            )
        }
    }
}

@Composable
private fun FileTree(
    state: FilesPaneState,
    onToggleNode: (ContainerFileEntry) -> Unit,
    onFileClick: (ContainerFileEntry) -> Unit,
    onDownload: (ContainerFileEntry) -> Unit,
    onRefresh: () -> Unit,
) {
    when {
        state.isLoading && state.nodes.isEmpty() -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = AppColors.AccentBlue,
                    strokeWidth = 3.dp,
                )
            }
        }
        state.error != null -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = state.error,
                    color = AppColors.Stopped,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(Spacing.md))
                Button(onClick = onRefresh) { Text("Retry") }
            }
        }
        state.nodes.isEmpty() -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Empty directory",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        else -> {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.nodes, key = { it.entry.path }) { node ->
                    TreeRow(
                        node = node,
                        onClick = {
                            if (node.isExpandable) {
                                onToggleNode(node.entry)
                            } else if (node.entry.type == FileType.FILE) {
                                onFileClick(node.entry)
                            }
                        },
                        onDownload = { onDownload(node.entry) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TreeRow(
    node: FileTreeNode,
    onClick: () -> Unit,
    onDownload: () -> Unit,
) {
    val entry = node.entry
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(end = Spacing.md, top = Spacing.xs, bottom = Spacing.xs)
                .padding(start = Spacing.sm + (node.depth * 16).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Expand/collapse affordance (or a per-node spinner / alignment spacer).
        Box(modifier = Modifier.size(18.dp), contentAlignment = Alignment.Center) {
            when {
                node.isLoading ->
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                node.isExpandable ->
                    Icon(
                        painter =
                            painterResource(
                                if (node.isExpanded) Res.drawable.expand_more_filled else Res.drawable.chevron_right_filled,
                            ),
                        contentDescription = if (node.isExpanded) "Collapse" else "Expand",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
            }
        }
        Spacer(modifier = Modifier.width(2.dp))
        Icon(
            painter =
                painterResource(
                    if (entry.type == FileType.DIRECTORY || entry.type == FileType.SYMLINK) Res.drawable.folder else Res.drawable.article,
                ),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint =
                if (entry.type == FileType.DIRECTORY || entry.type == FileType.SYMLINK) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
        Spacer(modifier = Modifier.width(Spacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (node.error != null) {
                Text(
                    text = node.error,
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.Stopped,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else if (entry.type == FileType.SYMLINK && entry.symlinkTarget != null) {
                Text(
                    text = "→ ${entry.symlinkTarget}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (entry.type == FileType.FILE) {
            Text(
                text = formatBytes(entry.sizeBytes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(Spacing.xs))
            PaneActionButton(
                icon = Res.drawable.download,
                contentDescription = "Download ${entry.name}",
                enabled = true,
                onClick = onDownload,
            )
        }
    }
}

@Composable
private fun FileViewer(
    state: FilesPaneState,
    selected: ContainerFileEntry,
    onClose: () -> Unit,
    onDownload: (ContainerFileEntry) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PaneActionButton(
                icon = Res.drawable.arrow_back_filled,
                contentDescription = "Back to file tree",
                enabled = true,
                onClick = onClose,
            )
            Spacer(modifier = Modifier.width(Spacing.xs))
            Text(
                text = selected.name,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            PaneActionButton(
                icon = Res.drawable.download,
                contentDescription = "Download ${selected.name}",
                enabled = !state.isDownloading,
                onClick = { onDownload(selected) },
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        val content = state.fileContent
        when {
            state.isFileLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = AppColors.AccentBlue,
                        strokeWidth = 3.dp,
                    )
                }
            }
            state.fileError != null -> {
                FileViewerNotice(message = state.fileError)
            }
            content != null && content.isBinary -> {
                FileViewerNotice(
                    message = "Binary file — ${formatBytes(content.totalBytesShown.toLong())} shown. Use Download to view it.",
                )
            }
            content != null -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (content.truncated) {
                        Text(
                            text = "Preview truncated — showing the first ${formatBytes(
                                content.totalBytesShown.toLong(),
                            )}. Download for the full file.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(horizontal = Spacing.md, vertical = 4.dp),
                        )
                    }
                    SelectionContainer(modifier = Modifier.weight(1f)) {
                        Text(
                            text = content.text,
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(Spacing.md),
                            style = MaterialTheme.typography.monospaceMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FileViewerNotice(message: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(Res.drawable.info),
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
