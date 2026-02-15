package com.containerdashboard.ui.screens.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.containerdashboard.data.models.Container
import com.containerdashboard.data.repository.DockerRepository
import com.containerdashboard.di.AppModule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ContainersScreenViewModel : ViewModel() {

    val repo: DockerRepository = AppModule.dockerRepository

    val containers: Flow<List<Container>> = repo.getContainers(all = true)

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

    fun startContainer(id: String) {
        viewModelScope.launch {
            _actionInProgress.value = id
            try {
                repo.startContainer(id)
            } catch (e: Exception) {
                _error.value = e.message
            }
            _actionInProgress.value = null
        }
    }

    fun stopContainer(id: String) {
        viewModelScope.launch {
            _actionInProgress.value = id
            try {
                repo.stopContainer(id)
            } catch (e: Exception) {
                _error.value = e.message
            }
            _actionInProgress.value = null
        }
    }

    fun pauseContainer(id: String) {
        viewModelScope.launch {
            _actionInProgress.value = id
            try {
                repo.pauseContainer(id)
            } catch (e: Exception) {
                _error.value = e.message
            }
            _actionInProgress.value = null
        }
    }

    fun unpauseContainer(id: String) {
        viewModelScope.launch {
            _actionInProgress.value = id
            try {
                repo.unpauseContainer(id)
            } catch (e: Exception) {
                _error.value = e.message
            }
            _actionInProgress.value = null
        }
    }

    fun removeContainer(id: String) {
        viewModelScope.launch {
            _actionInProgress.value = id
            try {
                repo.removeContainer(id, force = false)
            } catch (e: Exception) {
                _error.value = e.message
            }
            _actionInProgress.value = null
        }
    }

    fun deleteSelectedContainers() {
        viewModelScope.launch {
            _isDeletingSelected.value = true
            val idsToDelete = _selectedContainerIds.value.toList()
            val errors = mutableListOf<String>()

            for (id in idsToDelete) {
                try {
                    repo.removeContainer(id, force = false)
                } catch (e: Exception) {
                    errors.add(e.message ?: "Failed to delete container")
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
                try {
                    repo.removeContainer(container.id, force = true)
                } catch (e: Exception) {
                    errors.add(e.message ?: "Failed to delete container ${container.displayName}")
                }
            }

            _selectedContainerIds.value = emptySet()
            _isDeletingAll.value = false

            if (errors.isNotEmpty()) {
                _error.value = "Failed to delete ${errors.size} container(s)"
            }
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
        _error.value = null
    }
}

enum class ContainerFilter {
    ALL, RUNNING, STOPPED
}
