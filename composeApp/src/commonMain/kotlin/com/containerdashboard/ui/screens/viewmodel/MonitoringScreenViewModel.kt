package com.containerdashboard.ui.screens.viewmodel

import androidx.lifecycle.ViewModel
import com.containerdashboard.data.models.ContainerStats
import com.containerdashboard.data.repository.DockerRepository
import com.containerdashboard.di.AppModule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest

class MonitoringScreenViewModel : ViewModel() {

    val repo: DockerRepository = AppModule.dockerRepository

    private val _refreshInterval = MutableStateFlow(3f) // seconds
    val refreshInterval: StateFlow<Float> = _refreshInterval.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val containerStats: Flow<List<ContainerStats>> = _refreshInterval
        .flatMapLatest { seconds ->
            repo.getContainerStats(intervalMillis = (seconds * 1000).toLong())
        }

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun setRefreshInterval(seconds: Float) {
        _refreshInterval.value = seconds
    }

    fun clearError() {
        _error.value = null
    }
}
