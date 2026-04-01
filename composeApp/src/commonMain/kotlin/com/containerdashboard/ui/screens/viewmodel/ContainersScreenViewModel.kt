package com.containerdashboard.ui.screens.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.containerdashboard.data.models.Container
import com.containerdashboard.data.models.ContainerStats
import com.containerdashboard.data.repository.DockerRepository
import com.containerdashboard.di.AppModule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ContainersScreenViewModel : ViewModel() {
    private val repo: DockerRepository get() = AppModule.dockerRepository

    val containers: Flow<List<Container>> = repo.getContainers(all = true)

    val statsById: Flow<Map<String, ContainerStats>> =
        repo.getContainerStats(refreshRateMillis = 3000L).map { statsList ->
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

    private fun containerAction(
        id: String,
        action: suspend DockerRepository.(String) -> Result<Unit>,
    ) {
        viewModelScope.launch {
            _actionInProgress.value = id
            repo.action(id).onFailure { _error.value = it.message }
            _actionInProgress.value = null
        }
    }

    fun startContainer(id: String) = containerAction(id) { startContainer(it) }

    fun stopContainer(id: String) = containerAction(id) { stopContainer(it) }

    fun pauseContainer(id: String) = containerAction(id) { pauseContainer(it) }

    fun unpauseContainer(id: String) = containerAction(id) { unpauseContainer(it) }

    fun removeContainer(id: String) = containerAction(id) { removeContainer(it, force = true) }

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
            val errors = mutableListOf<String>()

            for (id in idsToDelete) {
                repo.removeContainer(id, force = true).onFailure {
                    errors.add(it.message ?: "Failed to delete container")
                }
            }

            _selectedContainerIds.value = emptySet()
            _isDeletingSelected.value = false

            if (errors.isNotEmpty()) {
                _error.value = "Failed to delete ${errors.size} container(s)"
            }
        }
    }

    fun deleteAllContainers(containers: List<Container>) {
        viewModelScope.launch {
            _isDeletingAll.value = true
            val errors = mutableListOf<String>()

            for (container in containers) {
                repo.removeContainer(container.id, force = true).onFailure {
                    errors.add(it.message ?: "Failed to delete container ${container.displayName}")
                }
            }

            _selectedContainerIds.value = emptySet()
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
}

enum class ContainerFilter {
    ALL,
    RUNNING,
    STOPPED,
}
