package com.containerdashboard.ui.screens.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.containerdashboard.data.models.Container
import com.containerdashboard.data.repository.DockerRepository
import com.containerdashboard.data.repository.PreferenceRepository
import com.containerdashboard.di.AppModule
import com.containerdashboard.ui.navigation.Screen
import com.containerdashboard.ui.state.LogsPaneState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AppViewModel : ViewModel() {
    private val repo: DockerRepository = AppModule.dockerRepository

    val isConnected: StateFlow<Boolean> =
        repo
            .isDockerAvailable()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _currentRoute = MutableStateFlow(Screen.Dashboard.route)
    val currentRoute: StateFlow<String> = _currentRoute.asStateFlow()

    private val _logsPaneState = MutableStateFlow(LogsPaneState())
    val logsPaneState: StateFlow<LogsPaneState> = _logsPaneState.asStateFlow()

    val darkTheme: StateFlow<Boolean> =
        PreferenceRepository
            .darkTheme()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun navigate(route: String) {
        _currentRoute.value = route
    }

    fun showContainerLogs(container: Container) {
        viewModelScope.launch {
            _logsPaneState.value = LogsPaneState(container = container, isLoading = true)
            fetchLogs(container.id)
        }
    }

    fun refreshLogs() {
        val container = _logsPaneState.value.container ?: return
        viewModelScope.launch {
            _logsPaneState.update { it.copy(isLoading = true, error = null) }
            fetchLogs(container.id)
        }
    }

    fun clearLogs() {
        _logsPaneState.value = LogsPaneState()
    }

    private suspend fun fetchLogs(containerId: String) {
        try {
            val result = repo.getContainerLogs(containerId)
            result.fold(
                onSuccess = { logs ->
                    _logsPaneState.update { it.copy(logs = logs, isLoading = false) }
                },
                onFailure = { e ->
                    _logsPaneState.update { it.copy(error = e.message, isLoading = false) }
                },
            )
        } catch (e: Exception) {
            _logsPaneState.update { it.copy(error = e.message, isLoading = false) }
        }
    }
}
