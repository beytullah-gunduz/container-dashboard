package com.containerdashboard.ui.screens.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.containerdashboard.data.models.Volume
import com.containerdashboard.data.repository.DockerRepository
import com.containerdashboard.di.AppModule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class VolumeSortColumn {
    NAME,
    DRIVER,
    MOUNTPOINT,
}

class VolumesScreenViewModel : ViewModel() {
    private val repo: DockerRepository get() = AppModule.dockerRepository

    val volumes: Flow<List<Volume>> = repo.getVolumes()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedVolumeName = MutableStateFlow<String?>(null)
    val selectedVolumeName: StateFlow<String?> = _selectedVolumeName.asStateFlow()

    private val _showCreateDialog = MutableStateFlow(false)
    val showCreateDialog: StateFlow<Boolean> = _showCreateDialog.asStateFlow()

    private val _sortColumn = MutableStateFlow(VolumeSortColumn.NAME)
    val sortColumn: StateFlow<VolumeSortColumn> = _sortColumn.asStateFlow()

    private val _sortDirection = MutableStateFlow(SortDirection.ASC)
    val sortDirection: StateFlow<SortDirection> = _sortDirection.asStateFlow()

    private val _checkedVolumeNames = MutableStateFlow(setOf<String>())
    val checkedVolumeNames: StateFlow<Set<String>> = _checkedVolumeNames.asStateFlow()

    private val _isDeletingSelected = MutableStateFlow(false)
    val isDeletingSelected: StateFlow<Boolean> = _isDeletingSelected.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun toggleChecked(
        name: String,
        checked: Boolean,
    ) {
        _checkedVolumeNames.update { if (checked) it + name else it - name }
    }

    fun checkAll(names: List<String>) {
        _checkedVolumeNames.value = names.toSet()
    }

    fun uncheckAll(names: List<String>) {
        _checkedVolumeNames.update { it - names.toSet() }
    }

    fun clearChecked() {
        _checkedVolumeNames.value = emptySet()
    }

    fun deleteSelectedVolumes() {
        viewModelScope.launch {
            _isDeletingSelected.value = true
            val names = _checkedVolumeNames.value.toList()
            val errors = mutableListOf<String>()
            for (name in names) {
                repo.removeVolume(name).onFailure {
                    errors.add(it.message ?: "Failed to delete volume")
                }
            }
            _checkedVolumeNames.value = emptySet()
            _isDeletingSelected.value = false
            if (errors.isNotEmpty()) {
                _error.value = "Failed to delete ${errors.size} volume(s)"
            }
        }
    }

    fun toggleSort(column: VolumeSortColumn) {
        if (_sortColumn.value == column) {
            _sortDirection.value =
                if (_sortDirection.value == SortDirection.ASC) {
                    SortDirection.DESC
                } else {
                    SortDirection.ASC
                }
        } else {
            _sortColumn.value = column
            _sortDirection.value = SortDirection.ASC
        }
    }

    fun createVolume(name: String) {
        viewModelScope.launch {
            try {
                repo.createVolume(name, "local")
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun removeVolume(name: String) {
        viewModelScope.launch {
            try {
                repo.removeVolume(name)
            } catch (e: Exception) {
                _error.value = e.message
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
        _error.value = null
    }
}
