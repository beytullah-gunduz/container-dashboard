package com.containerdashboard.ui.screens.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.containerdashboard.data.models.*
import com.containerdashboard.data.repository.DockerRepository
import com.containerdashboard.di.AppModule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

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
                _isConnected.value = sysInfoResult.isSuccess
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to connect to container engine"
                _isConnected.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
