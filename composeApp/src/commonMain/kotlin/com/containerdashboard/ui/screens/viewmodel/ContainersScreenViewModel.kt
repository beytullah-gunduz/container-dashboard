package com.containerdashboard.ui.screens.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.containerdashboard.data.models.Container
import com.containerdashboard.data.repository.PreferenceRepository


import com.containerdashboard.di.AppModule
import com.containerdashboard.ui.state.ContainersState
import kotlinx.coroutines.flow.Flow


import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update


import kotlinx.coroutines.launch

class ContainersScreenViewModel : ViewModel() {

    private val _state = MutableStateFlow(ContainersState())
    val state: StateFlow<ContainersState> = _state.asStateFlow()

    private val _selectedContainerIds = MutableStateFlow(setOf<String>())
    val selectedContainerIds: StateFlow<Set<String>> = _selectedContainerIds.asStateFlow()

    private val _isDeletingSelected = MutableStateFlow(false)
    val isDeletingSelected: StateFlow<Boolean> = _isDeletingSelected.asStateFlow()

    private val _isDeletingAll = MutableStateFlow(false)
    val isDeletingAll: StateFlow<Boolean> = _isDeletingAll.asStateFlow()

    init {
        loadContainers()
    }

    fun autoRefresh(): Flow<Boolean> = PreferenceRepository.autoRefresh()

    fun refreshInterval(): Flow<Float> = PreferenceRepository.refreshInterval()


    fun loadContainers() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                AppModule.dockerRepository.getContainers(all = true)
                    .catch { e -> _state.update { it.copy(error = e.message, isLoading = false) } }
                    .collect { containers ->
                        _state.update { it.copy(containers = containers, isLoading = false) }
                        // Clear selection for containers that no longer exist
                        _selectedContainerIds.update { ids ->
                            ids.filter { id -> containers.any { it.id == id } }.toSet()
                        }
                    }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun startContainer(id: String) {
        viewModelScope.launch {
            _state.update { it.copy(actionInProgress = id) }
            try {
                AppModule.dockerRepository.startContainer(id)
                loadContainers()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
            _state.update { it.copy(actionInProgress = null) }
        }
    }

    fun stopContainer(id: String) {
        viewModelScope.launch {
            _state.update { it.copy(actionInProgress = id) }
            try {
                AppModule.dockerRepository.stopContainer(id)
                loadContainers()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
            _state.update { it.copy(actionInProgress = null) }
        }
    }

    fun pauseContainer(id: String) {
        viewModelScope.launch {
            _state.update { it.copy(actionInProgress = id) }
            try {
                AppModule.dockerRepository.pauseContainer(id)
                loadContainers()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
            _state.update { it.copy(actionInProgress = null) }
        }
    }

    fun unpauseContainer(id: String) {
        viewModelScope.launch {
            _state.update { it.copy(actionInProgress = id) }
            try {
                AppModule.dockerRepository.unpauseContainer(id)
                loadContainers()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
            _state.update { it.copy(actionInProgress = null) }
        }
    }

    fun removeContainer(id: String) {
        viewModelScope.launch {
            _state.update { it.copy(actionInProgress = id) }
            try {
                AppModule.dockerRepository.removeContainer(id, force = false)
                loadContainers()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
            _state.update { it.copy(actionInProgress = null) }
        }
    }

    fun deleteSelectedContainers() {
        viewModelScope.launch {
            _isDeletingSelected.value = true
            val idsToDelete = _selectedContainerIds.value.toList()
            val errors = mutableListOf<String>()

            for (id in idsToDelete) {
                try {
                    AppModule.dockerRepository.removeContainer(id, force = false)
                } catch (e: Exception) {
                    errors.add(e.message ?: "Failed to delete container")
                }
            }

            _selectedContainerIds.value = emptySet()
            _isDeletingSelected.value = false

            if (errors.isNotEmpty()) {
                _state.update { it.copy(error = "Failed to delete ${errors.size} container(s)") }
            }

            loadContainers()
        }
    }

    fun deleteAllContainers() {
        viewModelScope.launch {
            _isDeletingAll.value = true
            val containers = _state.value.containers
            val errors = mutableListOf<String>()

            for (container in containers) {
                try {
                    // Use force = true to delete running containers
                    AppModule.dockerRepository.removeContainer(container.id, force = true)
                } catch (e: Exception) {
                    errors.add(e.message ?: "Failed to delete container ${container.displayName}")
                }
            }

            _selectedContainerIds.value = emptySet()
            _isDeletingAll.value = false

            if (errors.isNotEmpty()) {
                _state.update { it.copy(error = "Failed to delete ${errors.size} container(s)") }
            }

            loadContainers()
        }
    }

    fun toggleContainerSelection(containerId: String, selected: Boolean) {
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
        _state.update { it.copy(error = null) }
    }

    fun getFilteredContainers(searchQuery: String, filter: ContainerFilter): List<Container> {
        return _state.value.containers.filter { container ->
            val matchesSearch = searchQuery.isEmpty() ||
                    container.displayName.contains(searchQuery, ignoreCase = true) ||
                    container.image.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (filter) {
                ContainerFilter.ALL -> true
                ContainerFilter.RUNNING -> container.isRunning
                ContainerFilter.STOPPED -> !container.isRunning
            }
            matchesSearch && matchesFilter
        }
    }

    fun getDeletableSelectedCount(): Int {
        return _selectedContainerIds.value.count { id ->
            _state.value.containers.find { it.id == id }?.isRunning == false
        }
    }
}

enum class ContainerFilter {
    ALL, RUNNING, STOPPED
}
