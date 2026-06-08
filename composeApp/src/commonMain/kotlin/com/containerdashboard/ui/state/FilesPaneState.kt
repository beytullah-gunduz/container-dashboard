package com.containerdashboard.ui.state

import com.containerdashboard.data.models.ContainerFileContent
import com.containerdashboard.data.models.ContainerFileEntry

/**
 * One visible row in the flattened file tree. The tree is lazy: a directory's children only exist
 * here once it has been expanded.
 */
data class FileTreeNode(
    val entry: ContainerFileEntry,
    val depth: Int,
    val isExpandable: Boolean,
    val isExpanded: Boolean,
    val isLoading: Boolean,
    /** Per-node load error (e.g. permission denied when expanding), shown inline. */
    val error: String? = null,
)

/**
 * State for the Files tab of the container extra pane. Loaded lazily the first time the tab is
 * opened for a container (see [com.containerdashboard.ui.screens.viewmodel.AppViewModel.openFilesTab]).
 */
data class FilesPaneState(
    val containerId: String? = null,
    val isRunning: Boolean = false,
    /** True once the initial listing has been kicked off (idempotency guard for tab re-selection). */
    val loaded: Boolean = false,
    val isLoading: Boolean = false, // root-level load in progress
    val error: String? = null, // root-level load error
    val nodes: List<FileTreeNode> = emptyList(), // flattened, in display order
    // Inline viewer (null selectedFile => showing the tree).
    val selectedFile: ContainerFileEntry? = null,
    val fileContent: ContainerFileContent? = null,
    val isFileLoading: Boolean = false,
    val fileError: String? = null,
    val isDownloading: Boolean = false,
)
