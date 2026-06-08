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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
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

    // Shared source for the list; `hasLoaded` must derive from this raw flow rather than the
    // seeded `containers` StateFlow, which would replay its emptyList seed and flip `hasLoaded`
    // true before the first real fetch (see ContainersScreenViewModel for the full rationale).
    private val containersFlow: Flow<List<Container>> = repo.getContainers(all = true)

    val containers: StateFlow<List<Container>> =
        containersFlow
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
        containersFlow
            .map { true }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val connectionState: StateFlow<EngineConnectionState> =
        repoFlow
            .flatMapLatest { it.isDockerAvailable() }
            .map { available ->
                if (available) EngineConnectionState.CONNECTED else EngineConnectionState.UNAVAILABLE
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EngineConnectionState.CHECKING)

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
