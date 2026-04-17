package com.containerdashboard.ui.screens.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.containerdashboard.data.engine.engineTypeFromHost
import com.containerdashboard.data.models.Container
import com.containerdashboard.data.repository.DockerRepository
import com.containerdashboard.data.repository.PreferenceRepository
import com.containerdashboard.di.AppModule
import com.containerdashboard.ui.components.LogsPaneLayout
import com.containerdashboard.ui.navigation.Screen
import com.containerdashboard.ui.state.LogsPaneState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AppViewModel : ViewModel() {
    private val repo: DockerRepository get() = AppModule.dockerRepository

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val isConnected: StateFlow<Boolean> =
        AppModule
            .dockerRepositoryFlow
            .flatMapLatest { it.isDockerAvailable() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _currentRoute = MutableStateFlow(Screen.Dashboard.route)
    val currentRoute: StateFlow<String> = _currentRoute.asStateFlow()

    val engineName: StateFlow<String> =
        PreferenceRepository
            .engineHost()
            .map { engineTypeFromHost(it).displayName }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Container Engine")

    private val _logsPaneState = MutableStateFlow(LogsPaneState())
    val logsPaneState: StateFlow<LogsPaneState> = _logsPaneState.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val containers: StateFlow<List<Container>> =
        AppModule.dockerRepositoryFlow
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

    val darkTheme: StateFlow<Boolean> =
        PreferenceRepository
            .darkTheme()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

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
    }

    private var logFollowJob: Job? = null

    fun showContainerLogs(container: Container) {
        logFollowJob?.cancel()
        _logsPaneState.value = LogsPaneState(containers = listOf(container), isLoading = true)
        if (container.isRunning) {
            startFollowing(listOf(container))
        } else {
            viewModelScope.launch { fetchLogs(container.id) }
        }
    }

    fun showGroupLogs(containers: List<Container>) {
        logFollowJob?.cancel()
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
            for (c in containers) {
                val label = c.composeService ?: c.displayName
                val result = repo.getContainerLogs(c.id)
                result.onSuccess { logs ->
                    logs.lineSequence().filter { it.isNotEmpty() }.forEach { line ->
                        merged.add("[$label] $line")
                    }
                }
            }
            _logsPaneState.update { it.copy(logs = merged.toList(), isLoading = false) }
        } catch (e: Exception) {
            _logsPaneState.update { it.copy(error = e.message, isLoading = false) }
        }
    }

    fun clearLogs() {
        logFollowJob?.cancel()
        _logsPaneState.value = LogsPaneState()
    }

    fun pauseLogsContainer() {
        val id =
            _logsPaneState.value.containers
                .firstOrNull()
                ?.id ?: return
        viewModelScope.launch {
            repo.pauseContainer(id)
            repo.refreshContainers()
        }
    }

    fun unpauseLogsContainer() {
        val id =
            _logsPaneState.value.containers
                .firstOrNull()
                ?.id ?: return
        viewModelScope.launch {
            repo.unpauseContainer(id)
            repo.refreshContainers()
        }
    }

    fun restartLogsContainer() {
        val id =
            _logsPaneState.value.containers
                .firstOrNull()
                ?.id ?: return
        viewModelScope.launch {
            repo.restartContainer(id)
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
            } catch (_: Exception) {
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
            ids.forEach { repo.removeContainer(it, force = true) }
            repo.refreshContainers()
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
