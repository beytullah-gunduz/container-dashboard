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
import kotlinx.coroutines.flow.runningFold

data class UsageHistory(
    val cpuHistory: List<Double> = emptyList(),
    val memoryHistory: List<Double> = emptyList(),
)

class MonitoringScreenViewModel : ViewModel() {
    private val repo: DockerRepository = AppModule.dockerRepository

    private val maxHistorySize = 60

    private val _refreshRate = MutableStateFlow(1f) // seconds

    val refreshRate: StateFlow<Float> = _refreshRate.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val containerStats: Flow<List<ContainerStats>> =
        _refreshRate
            .flatMapLatest { seconds ->
                repo.getContainerStats(refreshRateMillis = (seconds * 1000).toLong())
            }

    val usageHistory: Flow<UsageHistory> =
        containerStats.runningFold(UsageHistory()) { acc, stats ->
            if (stats.isEmpty()) {
                acc
            } else {
                val avgCpu = stats.sumOf { it.cpuPercent } / stats.size
                val avgMem = stats.sumOf { it.memoryPercent } / stats.size
                UsageHistory(
                    cpuHistory = (acc.cpuHistory + avgCpu).takeLast(maxHistorySize),
                    memoryHistory = (acc.memoryHistory + avgMem).takeLast(maxHistorySize),
                )
            }
        }

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun setRefreshRate(seconds: Float) {
        _refreshRate.value = seconds
    }

    fun clearError() {
        _error.value = null
    }
}
