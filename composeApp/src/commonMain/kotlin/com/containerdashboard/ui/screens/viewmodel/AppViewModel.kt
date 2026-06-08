package com.containerdashboard.ui.screens.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.containerdashboard.data.engine.engineTypeFromHost
import com.containerdashboard.data.models.Container
import com.containerdashboard.data.models.ContainerFileEntry
import com.containerdashboard.data.models.FileType
import com.containerdashboard.data.repository.DockerRepository
import com.containerdashboard.data.repository.PreferenceRepository
import com.containerdashboard.data.util.joinPath
import com.containerdashboard.data.util.normalizePath
import com.containerdashboard.di.AppModule
import com.containerdashboard.ui.components.LogsPaneLayout
import com.containerdashboard.ui.navigation.Screen
import com.containerdashboard.ui.state.FileTreeNode
import com.containerdashboard.ui.state.FilesPaneState
import com.containerdashboard.ui.state.LogsPaneState
import com.containerdashboard.ui.theme.ThemeMode
import com.containerdashboard.ui.util.formatBytes
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val MAX_VIEW_BYTES = 1024 * 1024 // 1 MiB inline-preview cap
private const val MAX_DOWNLOAD_BYTES = 100L * 1024 * 1024 // 100 MB download cap
private const val ROOT_PATH = "/"

class AppViewModel(
    private val repoProvider: () -> DockerRepository = { AppModule.dockerRepository },
    private val repoFlow: StateFlow<DockerRepository> = AppModule.dockerRepositoryFlow,
) : ViewModel() {
    private val repo: DockerRepository get() = repoProvider()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val isConnected: StateFlow<Boolean> =
        repoFlow
            .flatMapLatest { it.isDockerAvailable() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _currentRoute =
        MutableStateFlow(
            PreferenceRepository.lastRouteSync.takeIf { it != null && Screen.entries.any { s -> s.route == it } }
                ?: Screen.Dashboard.route,
        )
    val currentRoute: StateFlow<String> = _currentRoute.asStateFlow()

    val engineName: StateFlow<String> =
        PreferenceRepository
            .engineHost()
            .map { engineTypeFromHost(it).displayName }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Container Engine")

    private val _logsPaneState = MutableStateFlow(LogsPaneState())
    val logsPaneState: StateFlow<LogsPaneState> = _logsPaneState.asStateFlow()

    private val _filesPaneState = MutableStateFlow(FilesPaneState())
    val filesPaneState: StateFlow<FilesPaneState> = _filesPaneState.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val containers: StateFlow<List<Container>> =
        repoFlow
            .flatMapLatest { it.getContainers(all = true) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            containers.collect { liveContainers ->
                val tracked = _logsPaneState.value.containers
                if (tracked.isEmpty()) return@collect

                val updated =
                    tracked.map { old ->
                        liveContainers.find { it.id == old.id } ?: old
                    }
                val allGone = tracked.all { old -> liveContainers.none { it.id == old.id } }

                if (allGone) {
                    logFollowJob?.cancel()
                    _logsPaneState.value = LogsPaneState()
                    _filesPaneState.value = FilesPaneState()
                } else if (updated != tracked) {
                    _logsPaneState.update { it.copy(containers = updated) }
                    val noneRunning = updated.none { it.isRunning }
                    if (noneRunning && _logsPaneState.value.isFollowing) {
                        logFollowJob?.cancel()
                        _logsPaneState.update {
                            it.copy(
                                logs = it.logs + "--- Stream ended (containers stopped) ---",
                                isFollowing = false,
                            )
                        }
                    }
                }
            }
        }
    }

    val themeMode: StateFlow<ThemeMode> =
        PreferenceRepository
            .themeMode()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.AUTO)

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun clearError() {
        _error.value = null
    }

    val logsPaneLayout: StateFlow<LogsPaneLayout> =
        PreferenceRepository
            .logsPaneLayout()
            .map { name ->
                try {
                    LogsPaneLayout.valueOf(name)
                } catch (_: Exception) {
                    LogsPaneLayout.AUTO
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LogsPaneLayout.AUTO)

    fun setLogsPaneLayout(layout: LogsPaneLayout) {
        viewModelScope.launch {
            PreferenceRepository.setLogsPaneLayout(layout.name)
        }
    }

    fun navigate(route: String) {
        _currentRoute.value = route
        viewModelScope.launch { PreferenceRepository.setLastRoute(route) }
    }

    private var logFollowJob: Job? = null

    fun showContainerLogs(container: Container) {
        logFollowJob?.cancel()
        _filesPaneState.value = FilesPaneState()
        _logsPaneState.value = LogsPaneState(containers = listOf(container), isLoading = true)
        if (container.isRunning) {
            startFollowing(listOf(container))
        } else {
            viewModelScope.launch { fetchLogs(container.id) }
        }
    }

    fun showGroupLogs(containers: List<Container>) {
        logFollowJob?.cancel()
        _filesPaneState.value = FilesPaneState()
        _logsPaneState.value = LogsPaneState(containers = containers, isLoading = true)
        val running = containers.any { it.isRunning }
        if (running) {
            startFollowing(containers)
        } else {
            viewModelScope.launch { fetchGroupLogs(containers) }
        }
    }

    fun refreshLogs() {
        val containers = _logsPaneState.value.containers
        if (containers.isEmpty()) return
        logFollowJob?.cancel()
        _logsPaneState.update { it.copy(isLoading = true, error = null, isFollowing = false) }
        val running = containers.any { it.isRunning }
        if (running) {
            startFollowing(containers)
        } else if (containers.size == 1) {
            viewModelScope.launch { fetchLogs(containers.first().id) }
        } else {
            viewModelScope.launch { fetchGroupLogs(containers) }
        }
    }

    private fun startFollowing(containers: List<Container>) {
        logFollowJob =
            viewModelScope.launch {
                val flow =
                    if (containers.size == 1) {
                        repo.followContainerLogs(containers.first().id)
                    } else {
                        val pairs =
                            containers.map { c ->
                                c.id to (c.composeService ?: c.displayName)
                            }
                        repo.followMultipleContainerLogs(pairs)
                    }
                flow
                    .catch { e ->
                        _logsPaneState.update {
                            it.copy(error = e.message, isLoading = false, isFollowing = false)
                        }
                    }.collect { lines ->
                        _logsPaneState.update {
                            it.copy(logs = lines, isLoading = false, isFollowing = true)
                        }
                    }
                _logsPaneState.update {
                    it.copy(
                        logs = it.logs + "--- Stream ended ---",
                        isFollowing = false,
                    )
                }
            }
    }

    private suspend fun fetchGroupLogs(containers: List<Container>) {
        try {
            val merged = mutableListOf<String>()
            val fetchErrors = mutableListOf<String>()
            for (c in containers) {
                val label = c.composeService ?: c.displayName
                val result = repo.getContainerLogs(c.id)
                result
                    .onSuccess { logs ->
                        logs.lineSequence().filter { it.isNotEmpty() }.forEach { line ->
                            merged.add("[$label] $line")
                        }
                    }.onFailure { fetchErrors.add("[$label] ${it.message ?: "Failed to fetch logs"}") }
            }
            if (fetchErrors.isNotEmpty()) {
                _error.value = "Failed to fetch logs for ${fetchErrors.size} container(s)"
            }
            _logsPaneState.update { it.copy(logs = merged.toList(), isLoading = false) }
        } catch (e: Exception) {
            _logsPaneState.update { it.copy(error = e.message, isLoading = false) }
        }
    }

    fun clearLogs() {
        logFollowJob?.cancel()
        _logsPaneState.value = LogsPaneState()
        _filesPaneState.value = FilesPaneState()
    }

    // --- Container file browsing (read-only) ---

    // Lazily-loaded tree state, keyed by node path. A symlink's children are listed from its
    // resolved target but stored under the symlink's own path, so flattening stays uniform.
    private val childrenByPath = mutableMapOf<String, List<ContainerFileEntry>>()
    private val expandedPaths = mutableSetOf<String>()
    private val loadingPaths = mutableSetOf<String>()
    private val nodeErrors = mutableMapOf<String, String>()

    fun openFilesTab(container: Container) {
        val state = _filesPaneState.value
        if (state.containerId == container.id && state.loaded) return
        resetTree()
        _filesPaneState.value =
            FilesPaneState(
                containerId = container.id,
                isRunning = container.isRunning,
                loaded = true,
            )
        if (container.isRunning) loadRoot()
    }

    fun refreshFiles() {
        resetTree()
        _filesPaneState.update { it.copy(nodes = emptyList()) }
        loadRoot()
    }

    /** Expand or collapse a directory (or symlink) node, lazily loading its children on first expand. */
    fun toggleNode(entry: ContainerFileEntry) {
        if (entry.type != FileType.DIRECTORY && entry.type != FileType.SYMLINK) return
        if (expandedPaths.remove(entry.path)) {
            _filesPaneState.update { it.copy(nodes = flattenTree()) }
            return
        }
        expandedPaths.add(entry.path)
        if (childrenByPath[entry.path] != null) {
            _filesPaneState.update { it.copy(nodes = flattenTree()) }
        } else {
            loadChildren(entry)
        }
    }

    fun openFile(entry: ContainerFileEntry) {
        val containerId = _filesPaneState.value.containerId ?: return
        if (entry.sizeBytes > MAX_VIEW_BYTES) {
            _filesPaneState.update {
                it.copy(
                    selectedFile = entry,
                    fileContent = null,
                    isFileLoading = false,
                    fileError = "File is too large to preview (${formatBytes(entry.sizeBytes)}). Download it instead.",
                )
            }
            return
        }
        _filesPaneState.update {
            it.copy(selectedFile = entry, fileContent = null, fileError = null, isFileLoading = true)
        }
        viewModelScope.launch {
            repo.readContainerFile(containerId, entry.path, MAX_VIEW_BYTES).fold(
                onSuccess = { content ->
                    _filesPaneState.update { it.copy(fileContent = content, isFileLoading = false) }
                },
                onFailure = { e ->
                    _filesPaneState.update {
                        it.copy(isFileLoading = false, fileError = e.message ?: "Failed to read file")
                    }
                },
            )
        }
    }

    fun closeFileViewer() {
        _filesPaneState.update {
            it.copy(selectedFile = null, fileContent = null, fileError = null, isFileLoading = false)
        }
    }

    /**
     * Download [entry] to the host. The platform save dialog is supplied via [onBytes] so this
     * logic stays in commonMain (mirrors how saving logs works).
     */
    fun downloadFile(
        entry: ContainerFileEntry,
        onBytes: (name: String, bytes: ByteArray) -> Unit,
    ) {
        val containerId = _filesPaneState.value.containerId ?: return
        if (entry.sizeBytes > MAX_DOWNLOAD_BYTES) {
            _error.value =
                "\"${entry.name}\" is too large to download (${formatBytes(entry.sizeBytes)}); " +
                "the limit is ${formatBytes(MAX_DOWNLOAD_BYTES)}."
            return
        }
        viewModelScope.launch {
            _filesPaneState.update { it.copy(isDownloading = true) }
            repo.downloadContainerFile(containerId, entry.path).fold(
                onSuccess = { bytes -> onBytes(entry.name, bytes) },
                onFailure = { e -> _error.value = e.message ?: "Failed to download file" },
            )
            _filesPaneState.update { it.copy(isDownloading = false) }
        }
    }

    private fun resetTree() {
        childrenByPath.clear()
        expandedPaths.clear()
        loadingPaths.clear()
        nodeErrors.clear()
    }

    private fun loadRoot() {
        val containerId = _filesPaneState.value.containerId ?: return
        _filesPaneState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            repo.listContainerDirectory(containerId, ROOT_PATH).fold(
                onSuccess = { entries ->
                    childrenByPath[ROOT_PATH] = sortEntries(entries)
                    _filesPaneState.update { it.copy(isLoading = false, error = null, nodes = flattenTree()) }
                },
                onFailure = { e ->
                    _filesPaneState.update {
                        it.copy(isLoading = false, error = e.message ?: "Failed to list directory")
                    }
                },
            )
        }
    }

    private fun loadChildren(entry: ContainerFileEntry) {
        val containerId = _filesPaneState.value.containerId ?: return
        loadingPaths.add(entry.path)
        nodeErrors.remove(entry.path)
        _filesPaneState.update { it.copy(nodes = flattenTree()) }
        viewModelScope.launch {
            repo.listContainerDirectory(containerId, resolveListPath(entry)).fold(
                onSuccess = { entries ->
                    childrenByPath[entry.path] = sortEntries(entries)
                    loadingPaths.remove(entry.path)
                    _filesPaneState.update { it.copy(nodes = flattenTree()) }
                },
                onFailure = { e ->
                    loadingPaths.remove(entry.path)
                    expandedPaths.remove(entry.path) // collapse again on failure
                    nodeErrors[entry.path] = e.message ?: "Failed to list directory"
                    _filesPaneState.update { it.copy(nodes = flattenTree()) }
                },
            )
        }
    }

    /** A symlink lists from its (relative-or-absolute) target; everything else from its own path. */
    private fun resolveListPath(entry: ContainerFileEntry): String {
        val target = entry.symlinkTarget
        if (entry.type == FileType.SYMLINK && !target.isNullOrBlank()) {
            return if (target.startsWith("/")) {
                normalizePath(target)
            } else {
                joinPath(normalizePath(entry.path, ".."), target)
            }
        }
        return entry.path
    }

    private fun sortEntries(entries: List<ContainerFileEntry>): List<ContainerFileEntry> =
        entries.sortedWith(
            compareByDescending<ContainerFileEntry> { it.type == FileType.DIRECTORY }
                .thenBy { it.name.lowercase() },
        )

    private fun flattenTree(): List<FileTreeNode> {
        val nodes = mutableListOf<FileTreeNode>()

        fun appendChildren(
            parentPath: String,
            depth: Int,
        ) {
            val children = childrenByPath[parentPath] ?: return
            for (entry in children) {
                val expandable = entry.type == FileType.DIRECTORY || entry.type == FileType.SYMLINK
                val expanded = expandable && entry.path in expandedPaths
                nodes.add(
                    FileTreeNode(
                        entry = entry,
                        depth = depth,
                        isExpandable = expandable,
                        isExpanded = expanded,
                        isLoading = entry.path in loadingPaths,
                        error = nodeErrors[entry.path],
                    ),
                )
                if (expanded) appendChildren(entry.path, depth + 1)
            }
        }
        appendChildren(ROOT_PATH, 0)
        return nodes
    }

    fun pauseLogsContainer() {
        val id =
            _logsPaneState.value.containers
                .firstOrNull()
                ?.id ?: return
        viewModelScope.launch {
            _logsPaneState.update { it.copy(isPauseActionInProgress = true) }
            val result = repo.pauseContainer(id)
            result.onFailure { _error.value = it.message ?: "Failed to pause container" }
            repo.refreshContainers()
            if (result.isSuccess) {
                awaitContainerState(id) { it.isPaused }
            }
            _logsPaneState.update { it.copy(isPauseActionInProgress = false) }
        }
    }

    fun unpauseLogsContainer() {
        val id =
            _logsPaneState.value.containers
                .firstOrNull()
                ?.id ?: return
        viewModelScope.launch {
            _logsPaneState.update { it.copy(isPauseActionInProgress = true) }
            val result = repo.unpauseContainer(id)
            result.onFailure { _error.value = it.message ?: "Failed to unpause container" }
            repo.refreshContainers()
            if (result.isSuccess) {
                awaitContainerState(id) { it.isRunning }
            }
            _logsPaneState.update { it.copy(isPauseActionInProgress = false) }
        }
    }

    // Keep the spinner up until the container actually reaches the target state,
    // then hold briefly so the row the container just moved to (e.g. the running
    // section after an unpause, or a section/card that was collapsed before the
    // move) has a visible frame of spinner before it swaps back to the icon.
    private suspend fun awaitContainerState(
        id: String,
        predicate: (Container) -> Boolean,
    ) {
        withTimeoutOrNull(5000) {
            containers.first { list ->
                val c = list.find { it.id == id }
                c == null || predicate(c)
            }
        }
        delay(400)
    }

    fun restartLogsContainer() {
        val id =
            _logsPaneState.value.containers
                .firstOrNull()
                ?.id ?: return
        viewModelScope.launch {
            repo.restartContainer(id).onFailure { _error.value = it.message ?: "Failed to restart container" }
            repo.refreshContainers()
            refreshLogs()
        }
    }

    fun saveAllLogs(onLogsReady: (containerName: String, allLogs: String) -> Unit) {
        val containers = _logsPaneState.value.containers
        if (containers.isEmpty()) return
        viewModelScope.launch {
            _logsPaneState.update { it.copy(isSavingLogs = true) }
            try {
                if (containers.size == 1) {
                    val c = containers.first()
                    repo
                        .getContainerLogs(c.id, tail = Int.MAX_VALUE, timestamps = false)
                        .onSuccess { onLogsReady(c.displayName, it) }
                } else {
                    val merged = StringBuilder()
                    for (c in containers) {
                        val label = c.composeService ?: c.displayName
                        repo
                            .getContainerLogs(c.id, tail = Int.MAX_VALUE, timestamps = false)
                            .onSuccess { logs ->
                                logs.lineSequence().filter { it.isNotEmpty() }.forEach { line ->
                                    merged.append("[$label] $line\n")
                                }
                            }
                    }
                    val name = _logsPaneState.value.displayName
                    onLogsReady(name, merged.toString())
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to save logs"
            }
            _logsPaneState.update { it.copy(isSavingLogs = false) }
        }
    }

    fun removeLogsContainer() {
        val ids = _logsPaneState.value.containers.map { it.id }
        if (ids.isEmpty()) return
        logFollowJob?.cancel()
        _logsPaneState.value = LogsPaneState()
        viewModelScope.launch {
            val errors = mutableListOf<String>()
            ids.forEach { id ->
                repo.removeContainer(id, force = true).onFailure {
                    errors.add(it.message ?: "Failed to remove container")
                }
            }
            repo.refreshContainers()
            if (errors.isNotEmpty()) {
                _error.value = errors.first()
            }
        }
    }

    private suspend fun fetchLogs(containerId: String) {
        try {
            val result = repo.getContainerLogs(containerId)
            result.fold(
                onSuccess = { logs ->
                    val lines = logs.lineSequence().filter { it.isNotEmpty() }.toList()
                    _logsPaneState.update { it.copy(logs = lines, isLoading = false) }
                },
                onFailure = { e ->
                    _logsPaneState.update { it.copy(error = e.message, isLoading = false) }
                },
            )
        } catch (e: Exception) {
            _logsPaneState.update { it.copy(error = e.message, isLoading = false) }
        }
    }
}
