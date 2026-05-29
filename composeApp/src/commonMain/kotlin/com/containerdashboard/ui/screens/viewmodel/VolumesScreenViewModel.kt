package com.containerdashboard.ui.screens.viewmodel

import androidx.lifecycle.viewModelScope
import com.containerdashboard.data.models.Volume
import com.containerdashboard.data.repository.DockerRepository
import com.containerdashboard.di.AppModule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class VolumeSortColumn {
    NAME,
    DRIVER,
    MOUNTPOINT,
}

class VolumesScreenViewModel(
    repoProvider: () -> DockerRepository = { AppModule.dockerRepository },
) : SortableListScreenViewModel<Volume, VolumeSortColumn>(
        repoProvider = repoProvider,
        items = repoProvider().getVolumes(),
        initialColumn = VolumeSortColumn.NAME,
    ) {
    val volumes: Flow<List<Volume>> get() = items

    val checkedVolumeNames: StateFlow<Set<String>> get() = checkedKeys
    val selectedVolumeName: StateFlow<String?> get() = selectedKey

    private val _showCreateDialog = MutableStateFlow(false)
    val showCreateDialog: StateFlow<Boolean> = _showCreateDialog.asStateFlow()

    fun setSelectedVolume(volumeName: String?) = setSelectedKey(volumeName)

    fun setShowCreateDialog(show: Boolean) {
        _showCreateDialog.value = show
    }

    fun deleteSelectedVolumes() = deleteSelected("volume") { repo.removeVolume(it) }

    fun createVolume(name: String) {
        viewModelScope.launch {
            try {
                repo.createVolume(name, "local")
            } catch (e: Exception) {
                setError(e.message)
            }
        }
    }

    fun removeVolume(name: String) {
        viewModelScope.launch {
            try {
                repo.removeVolume(name)
            } catch (e: Exception) {
                setError(e.message)
            }
        }
    }

    override suspend fun refreshItems() = repo.refreshVolumes()
}
