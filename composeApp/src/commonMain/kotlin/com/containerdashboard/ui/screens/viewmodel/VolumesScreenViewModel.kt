package com.containerdashboard.ui.screens.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.containerdashboard.di.AppModule
import com.containerdashboard.ui.state.VolumesState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class VolumesScreenViewModel : ViewModel() {
    private val _state = MutableStateFlow(VolumesState())
    val state: StateFlow<VolumesState> = _state.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedVolumeName = MutableStateFlow<String?>(null)
    val selectedVolumeName: StateFlow<String?> = _selectedVolumeName.asStateFlow()

    private val _showCreateDialog = MutableStateFlow(false)
    val showCreateDialog: StateFlow<Boolean> = _showCreateDialog.asStateFlow()

    init {
        loadVolumes()
    }

    fun loadVolumes() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                AppModule.dockerRepository.getVolumes()
                    .catch { e -> _state.update { it.copy(error = e.message, isLoading = false) } }
                    .collect { volumes ->
                        _state.update { it.copy(volumes = volumes, isLoading = false) }
                    }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun createVolume(name: String) {
        viewModelScope.launch {
            try {
                AppModule.dockerRepository.createVolume(name, "local")
                loadVolumes()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun removeVolume(name: String) {
        viewModelScope.launch {
            try {
                AppModule.dockerRepository.removeVolume(name)
                loadVolumes()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedVolume(volumeName: String?) {
        _selectedVolumeName.value = volumeName
    }

    fun setShowCreateDialog(show: Boolean) {
        _showCreateDialog.value = show
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
