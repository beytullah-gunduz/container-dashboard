package com.containerdashboard.ui.screens.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.containerdashboard.data.models.Container
import com.containerdashboard.data.models.ContainerStats
import com.containerdashboard.data.repository.ContainerColumnWidths
import com.containerdashboard.data.repository.DockerRepository
import com.containerdashboard.data.repository.PreferenceRepository
import com.containerdashboard.di.AppModule
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class ContainersScreenViewModel : ViewModel() {
    private val repo: DockerRepository get() = AppModule.dockerRepository

    val containers: Flow<List<Container>> = repo.getContainers(all = true)

    /** Emits `false` until the first list of containers has been delivered. */
    val hasLoaded: StateFlow<Boolean> =
        containers
            .map { true }
            .onStart { emit(false) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    @OptIn(FlowPreview::class)
    val statsById: Flow<Map<String, ContainerStats>> =
        repo.getContainerStats().sample(3_000L).map { statsList ->
            statsList.associateBy { it.containerId }
        }

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _actionInProgress = MutableStateFlow<String?>(null)
    val actionInProgress: StateFlow<String?> = _actionInProgress.asStateFlow()

    private val _selectedContainerIds = MutableStateFlow(setOf<String>())
    val selectedContainerIds: StateFlow<Set<String>> = _selectedContainerIds.asStateFlow()

    private val _isDeletingSelected = MutableStateFlow(false)
    val isDeletingSelected: StateFlow<Boolean> = _isDeletingSelected.asStateFlow()

    private val _isDeletingAll = MutableStateFlow(false)
    val isDeletingAll: StateFlow<Boolean> = _isDeletingAll.asStateFlow()

    private val _pendingDeleteIds = MutableStateFlow(setOf<String>())
    val pendingDeleteIds: StateFlow<Set<String>> = _pendingDeleteIds.asStateFlow()

    // Compose-project expand state. Kept in the VM (not `remember` inside
    // the screen) so groups stay expanded when the user navigates away
    // and comes back.
    private val _expandedRunningProjects = MutableStateFlow(setOf<String>())
    val expandedRunningProjects: StateFlow<Set<String>> = _expandedRunningProjects.asStateFlow()

    private val _expandedOtherProjects = MutableStateFlow(setOf<String>())
    val expandedOtherProjects: StateFlow<Set<String>> = _expandedOtherProjects.asStateFlow()

    fun toggleRunningProject(project: String) {
        _expandedRunningProjects.update { if (project in it) it - project else it + project }
    }

    fun toggleOtherProject(project: String) {
        _expandedOtherProjects.update { if (project in it) it - project else it + project }
    }

    val columnWidths: Flow<ContainerColumnWidths> = PreferenceRepository.containerColumnWidths()

    fun setColumnWidths(widths: ContainerColumnWidths) {
        viewModelScope.launch { PreferenceRepository.setContainerColumnWidths(widths) }
    }

    private fun containerAction(
        id: String,
        awaitAfter: ((Container) -> Boolean)? = null,
        action: suspend DockerRepository.(String) -> Result<Unit>,
    ) {
        viewModelScope.launch {
            _actionInProgress.value = id
            val result = repo.action(id).onFailure { _error.value = it.message }
            if (result.isSuccess && awaitAfter != null) {
                repo.refreshContainers()
                withTimeoutOrNull(5000) {
                    containers.first { list ->
                        val c = list.find { it.id == id }
                        c == null || awaitAfter(c)
                    }
                }
                delay(400)
            }
            _actionInProgress.value = null
        }
    }

    fun startContainer(id: String) = containerAction(id, awaitAfter = { it.isRunning }) { startContainer(it) }

    fun stopContainer(id: String) = containerAction(id, awaitAfter = { !it.isRunning && !it.isPaused }) { stopContainer(it) }

    fun restartContainer(id: String) = containerAction(id, awaitAfter = { it.isRunning }) { restartContainer(it) }

    fun pauseContainer(id: String) = containerAction(id, awaitAfter = { it.isPaused }) { pauseContainer(it) }

    fun unpauseContainer(id: String) = containerAction(id, awaitAfter = { it.isRunning }) { unpauseContainer(it) }

    fun removeContainer(id: String) {
        viewModelScope.launch {
            _pendingDeleteIds.update { it + id }
            repo.removeContainer(id, force = true).onFailure { _error.value = it.message }
            repo.refreshContainers()
            _pendingDeleteIds.update { it - id }
        }
    }

    private val _isStoppingSelected = MutableStateFlow(false)
    val isStoppingSelected: StateFlow<Boolean> = _isStoppingSelected.asStateFlow()

    fun stopSelectedContainers(runningIds: List<String>) {
        viewModelScope.launch {
            _isStoppingSelected.value = true
            val errors = mutableListOf<String>()
            for (id in runningIds) {
                repo.stopContainer(id).onFailure {
                    errors.add(it.message ?: "Failed to stop container")
                }
            }
            repo.refreshContainers()
            _isStoppingSelected.value = false
            if (errors.isNotEmpty()) {
                _error.value = "Failed to stop ${errors.size} container(s)"
            }
        }
    }

    fun deleteSelectedContainers() {
        viewModelScope.launch {
            _isDeletingSelected.value = true
            val idsToDelete = _selectedContainerIds.value.toList()
            _pendingDeleteIds.update { it + idsToDelete }
            val failedIds = mutableSetOf<String>()
            val errors = mutableListOf<String>()

            for (id in idsToDelete) {
                repo.removeContainer(id, force = true).onFailure {
                    failedIds.add(id)
                    errors.add(it.message ?: "Failed to delete container")
                }
            }

            repo.refreshContainers()
            _pendingDeleteIds.update { it - idsToDelete.toSet() }
            _selectedContainerIds.value = failedIds
            _isDeletingSelected.value = false

            if (errors.isNotEmpty()) {
                _error.value = "Failed to delete ${errors.size} container(s)"
            }
        }
    }

    fun deleteAllContainers(containers: List<Container>) {
        viewModelScope.launch {
            _isDeletingAll.value = true
            val allIds = containers.map { it.id }
            _pendingDeleteIds.update { it + allIds }
            val failedIds = mutableSetOf<String>()
            val errors = mutableListOf<String>()

            for (container in containers) {
                repo.removeContainer(container.id, force = true).onFailure {
                    failedIds.add(container.id)
                    errors.add(it.message ?: "Failed to delete container ${container.displayName}")
                }
            }

            repo.refreshContainers()
            _pendingDeleteIds.update { it - allIds.toSet() }
            _selectedContainerIds.value = failedIds
            _isDeletingAll.value = false

            if (errors.isNotEmpty()) {
                _error.value = "Failed to delete ${errors.size} container(s)"
            }
        }
    }

    fun toggleContainerSelection(
        containerId: String,
        selected: Boolean,
    ) {
        _selectedContainerIds.update { ids ->
            if (selected) ids + containerId else ids - containerId
        }
    }

    fun selectAllContainers(containerIds: List<String>) {
        _selectedContainerIds.value = containerIds.toSet()
    }

    fun clearSelection() {
        _selectedContainerIds.value = emptySet()
    }

    fun clearError() {
        _error.value = null
    }

    /** Ask the repository to re-fetch containers now. */
    fun refresh() {
        viewModelScope.launch { repo.refreshContainers() }
    }
}

enum class ContainerFilter {
    ALL,
    RUNNING,
    STOPPED,
}
