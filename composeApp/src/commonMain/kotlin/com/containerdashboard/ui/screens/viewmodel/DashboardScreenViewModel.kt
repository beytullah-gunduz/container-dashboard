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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DashboardScreenViewModel(
    private val repoProvider: () -> DockerRepository = { AppModule.dockerRepository },
    private val repoFlow: StateFlow<DockerRepository> = AppModule.dockerRepositoryFlow,
) : ViewModel() {
    private val repo: DockerRepository get() = repoProvider()

    private val _systemInfo = MutableStateFlow<SystemInfo?>(null)
    val systemInfo: StateFlow<SystemInfo?> = _systemInfo.asStateFlow()

    private val _version = MutableStateFlow<DockerVersion?>(null)
    val version: StateFlow<DockerVersion?> = _version.asStateFlow()

    val containers: StateFlow<List<Container>> =
        repo
            .getContainers(all = true)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val images: StateFlow<List<DockerImage>> =
        repo
            .getImages()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val volumes: StateFlow<List<Volume>> =
        repo
            .getVolumes()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val networks: StateFlow<List<DockerNetwork>> =
        repo
            .getNetworks()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Emits `false` until the first containers list has been delivered. */
    val hasLoaded: StateFlow<Boolean> =
        containers
            .map { true }
            .onStart { emit(false) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val isConnected: StateFlow<Boolean> =
        repoFlow
            .flatMapLatest { it.isDockerAvailable() }
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
