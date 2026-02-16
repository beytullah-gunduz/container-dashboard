package com.containerdashboard.ui.screens.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.containerdashboard.data.models.Container
import com.containerdashboard.data.models.DockerImage
import com.containerdashboard.data.models.DockerNetwork
import com.containerdashboard.data.models.DockerVersion
import com.containerdashboard.data.models.SystemInfo
import com.containerdashboard.data.models.Volume
import com.containerdashboard.data.repository.DockerRepository
import com.containerdashboard.di.AppModule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DashboardScreenViewModel : ViewModel() {
    val repo: DockerRepository = AppModule.dockerRepository

    private val _systemInfo = MutableStateFlow<SystemInfo?>(null)
    val systemInfo: StateFlow<SystemInfo?> = _systemInfo.asStateFlow()

    private val _version = MutableStateFlow<DockerVersion?>(null)
    val version: StateFlow<DockerVersion?> = _version.asStateFlow()

    val containers: Flow<List<Container>> = repo.getContainers(all = true)

    val images: Flow<List<DockerImage>> = repo.getImages()

    val volumes: Flow<List<Volume>> = repo.getVolumes()

    val networks: Flow<List<DockerNetwork>> = repo.getNetworks()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val isConnected: StateFlow<Boolean> =
        repo
            .isDockerAvailable()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        loadSystemInfo()
    }

    private fun loadSystemInfo() {
        viewModelScope.launch {
            try {
                val sysInfoResult = repo.getSystemInfo()
                val versionResult = repo.getVersion()

                _systemInfo.value = sysInfoResult.getOrNull()
                _version.value = versionResult.getOrNull()
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to connect to container engine"
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
