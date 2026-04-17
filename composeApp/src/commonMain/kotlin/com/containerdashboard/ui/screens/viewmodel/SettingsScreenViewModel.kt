package com.containerdashboard.ui.screens.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.containerdashboard.data.engine.ColimaConfig
import com.containerdashboard.data.engine.EngineActionStatus
import com.containerdashboard.data.engine.EngineOperations
import com.containerdashboard.data.engine.EngineType
import com.containerdashboard.data.engine.colimaProfileFromHost
import com.containerdashboard.data.engine.engineTypeFromHost
import com.containerdashboard.data.repository.PreferenceRepository
import com.containerdashboard.di.AppModule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsScreenViewModel : ViewModel() {
    private val repo get() = AppModule.dockerRepository

    fun engineHost(): Flow<String> = PreferenceRepository.engineHost()

    val engineType: StateFlow<EngineType> =
        PreferenceRepository
            .engineHost()
            .map { engineTypeFromHost(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EngineType.UNKNOWN)

    val colimaProfile: StateFlow<String?> =
        PreferenceRepository
            .engineHost()
            .map { colimaProfileFromHost(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun darkTheme(): Flow<Boolean> = PreferenceRepository.darkTheme()

    fun showSystemContainers(): Flow<Boolean> = PreferenceRepository.showSystemContainers()

    fun confirmBeforeDelete(): Flow<Boolean> = PreferenceRepository.confirmBeforeDelete()

    fun trayRefreshRateSeconds(): Flow<Int> = PreferenceRepository.trayRefreshRateSeconds()

    fun logsMaxLines(): Flow<Int> = PreferenceRepository.logsMaxLines()

    fun setLogsMaxLines(value: Int) {
        viewModelScope.launch { PreferenceRepository.setLogsMaxLines(value) }
    }

    fun setEngineHost(value: String) {
        viewModelScope.launch { PreferenceRepository.setEngineHost(value) }
    }

    fun setDarkTheme(value: Boolean) {
        viewModelScope.launch { PreferenceRepository.setDarkTheme(value) }
    }

    fun setShowSystemContainers(value: Boolean) {
        viewModelScope.launch { PreferenceRepository.setShowSystemContainers(value) }
    }

    fun setConfirmBeforeDelete(value: Boolean) {
        viewModelScope.launch { PreferenceRepository.setConfirmBeforeDelete(value) }
    }

    fun setTrayRefreshRateSeconds(value: Int) {
        viewModelScope.launch { PreferenceRepository.setTrayRefreshRateSeconds(value) }
    }

    // -- Engine management --

    val engineActionStatus: StateFlow<EngineActionStatus> = EngineOperations.actionStatus
    val engineCommandOutput: StateFlow<String> = EngineOperations.commandOutput

    private val _colimaConfig = MutableStateFlow<ColimaConfig?>(null)
    val colimaConfig: StateFlow<ColimaConfig?> = _colimaConfig.asStateFlow()

    fun loadColimaConfig() {
        viewModelScope.launch {
            val profile = colimaProfile.value ?: "default"
            _colimaConfig.value = EngineOperations.getColimaConfig(profile)
        }
    }

    fun startEngine(
        cpu: Int? = null,
        memory: Int? = null,
        disk: Int? = null,
    ) {
        viewModelScope.launch {
            val type = engineType.value
            val profile = colimaProfile.value
            EngineOperations.startEngine(type, profile, cpu, memory, disk)
            val host = PreferenceRepository.engineHost().first()
            AppModule.reconnect(host)
        }
    }

    fun stopEngine() {
        viewModelScope.launch {
            val type = engineType.value
            val profile = colimaProfile.value
            EngineOperations.stopEngine(type, profile)
        }
    }

    fun clearEngineState() {
        EngineOperations.clearState()
    }

    // -- Connection test --

    private val _connectionTestResult = MutableStateFlow<ConnectionTestState>(ConnectionTestState.Idle)
    val connectionTestResult: StateFlow<ConnectionTestState> = _connectionTestResult.asStateFlow()

    fun testConnection(host: String) {
        viewModelScope.launch {
            _connectionTestResult.value = ConnectionTestState.Testing
            try {
                val testRepo =
                    com.containerdashboard.data.repository
                        .DockerRepository(host)
                val result = testRepo.getVersion()
                testRepo.close()
                result.fold(
                    onSuccess = { version ->
                        _connectionTestResult.value =
                            ConnectionTestState.Success("Connected — Engine v${version.version}")
                    },
                    onFailure = { e ->
                        _connectionTestResult.value =
                            ConnectionTestState.Error(e.message ?: "Connection failed")
                    },
                )
            } catch (e: Exception) {
                _connectionTestResult.value = ConnectionTestState.Error(e.message ?: "Connection failed")
            }
        }
    }

    fun dismissTestResult() {
        _connectionTestResult.value = ConnectionTestState.Idle
    }

    // -- Save & reconnect --

    fun saveAndReconnect(host: String) {
        viewModelScope.launch {
            PreferenceRepository.setEngineHost(host)
            AppModule.reconnect(host)
        }
    }

    // -- Danger zone actions --

    private val _actionState = MutableStateFlow<ActionState>(ActionState.Idle)
    val actionState: StateFlow<ActionState> = _actionState.asStateFlow()

    fun pruneAll() {
        viewModelScope.launch {
            _actionState.value = ActionState.InProgress("Pruning unused resources...")
            val errors = mutableListOf<String>()
            repo.pruneContainers().onFailure { errors.add("containers: ${it.message}") }
            repo.pruneImages().onFailure { errors.add("images: ${it.message}") }
            repo.pruneVolumes().onFailure { errors.add("volumes: ${it.message}") }
            repo.pruneNetworks().onFailure { errors.add("networks: ${it.message}") }
            _actionState.value =
                if (errors.isEmpty()) {
                    ActionState.Success("All unused resources pruned")
                } else {
                    ActionState.Error("Prune failed: ${errors.joinToString("; ")}")
                }
        }
    }

    fun stopAllContainers() {
        viewModelScope.launch {
            _actionState.value = ActionState.InProgress("Stopping all containers...")
            try {
                val containers = repo.getContainers(all = false)
                val errors = mutableListOf<String>()
                // Collect current running containers once
                containers.first().forEach { container ->
                    repo.stopContainer(container.id).onFailure {
                        errors.add("${container.displayName}: ${it.message}")
                    }
                }
                _actionState.value =
                    if (errors.isEmpty()) {
                        ActionState.Success("All containers stopped")
                    } else {
                        ActionState.Error("Failed to stop: ${errors.joinToString("; ")}")
                    }
            } catch (e: Exception) {
                _actionState.value = ActionState.Error(e.message ?: "Failed to stop containers")
            }
        }
    }

    fun dismissActionState() {
        _actionState.value = ActionState.Idle
    }
}

sealed interface ConnectionTestState {
    data object Idle : ConnectionTestState

    data object Testing : ConnectionTestState

    data class Success(
        val message: String,
    ) : ConnectionTestState

    data class Error(
        val message: String,
    ) : ConnectionTestState
}

sealed interface ActionState {
    data object Idle : ActionState

    data class InProgress(
        val message: String,
    ) : ActionState

    data class Success(
        val message: String,
    ) : ActionState

    data class Error(
        val message: String,
    ) : ActionState
}
