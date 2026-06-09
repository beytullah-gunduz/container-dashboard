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

    // Each card is fed by an independent repo flow. Both the list StateFlow and its "loaded" flag
    // derive from the SAME raw flow (captured here once) — the flag must come from the raw flow,
    // not the seeded list StateFlow, which would replay its emptyList seed and flip the flag true
    // before the first real fetch (see ContainersScreenViewModel for the full rationale).
    private val containersFlow: Flow<List<Container>> = repo.getContainers(all = true)
    private val imagesFlow: Flow<List<DockerImage>> = repo.getImages()
    private val volumesFlow: Flow<List<Volume>> = repo.getVolumes()
    private val networksFlow: Flow<List<DockerNetwork>> = repo.getNetworks()

    val containers: StateFlow<List<Container>> =
        containersFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val images: StateFlow<List<DockerImage>> =
        imagesFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val volumes: StateFlow<List<Volume>> =
        volumesFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val networks: StateFlow<List<DockerNetwork>> =
        networksFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Per-card loaded flags: each stays false until ITS list has first arrived. Gating the
    // Images/Volumes/Networks cards behind the containers flag alone flashed a premature "0" the
    // instant containers loaded, before those (independently-fetched) lists actually arrived.
    val hasLoaded: StateFlow<Boolean> = containersFlow.firstEmissionFlag()
    val imagesLoaded: StateFlow<Boolean> = imagesFlow.firstEmissionFlag()
    val volumesLoaded: StateFlow<Boolean> = volumesFlow.firstEmissionFlag()
    val networksLoaded: StateFlow<Boolean> = networksFlow.firstEmissionFlag()

    /** `false` until this flow delivers its first value, then `true`. */
    private fun Flow<*>.firstEmissionFlag(): StateFlow<Boolean> =
        map { true }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

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
