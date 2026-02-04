package com.containerdashboard.ui.screens.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.containerdashboard.di.AppModule
import com.containerdashboard.ui.state.DashboardState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DashboardScreenViewModel : ViewModel() {
    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val repo = AppModule.dockerRepository

                // Load system info
                val sysInfoResult = repo.getSystemInfo()
                val versionResult = repo.getVersion()

                // Load containers
                repo.getContainers(all = true)
                    .catch { e -> _state.update { it.copy(error = e.message) } }
                    .collect { containers ->
                        _state.update { it.copy(containers = containers) }
                    }

                // Load images
                repo.getImages()
                    .catch { e -> _state.update { it.copy(error = e.message) } }
                    .collect { images ->
                        _state.update { it.copy(images = images) }
                    }

                // Load volumes
                repo.getVolumes()
                    .catch { e -> _state.update { it.copy(error = e.message) } }
                    .collect { volumes ->
                        _state.update { it.copy(volumes = volumes) }
                    }

                // Load networks
                repo.getNetworks()
                    .catch { e -> _state.update { it.copy(error = e.message) } }
                    .collect { networks ->
                        _state.update { it.copy(networks = networks) }
                    }

                _state.update {
                    it.copy(
                        systemInfo = sysInfoResult.getOrNull(),
                        version = versionResult.getOrNull(),
                        isLoading = false,
                        isConnected = sysInfoResult.isSuccess
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to connect to container engine",
                        isConnected = false
                    )
                }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
