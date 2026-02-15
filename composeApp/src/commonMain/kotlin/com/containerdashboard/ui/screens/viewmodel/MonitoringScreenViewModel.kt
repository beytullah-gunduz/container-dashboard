package com.containerdashboard.ui.screens.viewmodel

import androidx.lifecycle.ViewModel
import com.containerdashboard.data.models.ContainerStats
import com.containerdashboard.data.repository.DockerRepository
import com.containerdashboard.di.AppModule


import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.runningFold

data class UsageHistory(
        val cpuHistory: List<Double> = emptyList(),
        val memoryHistory: List<Double> = emptyList()
)


class MonitoringScreenViewModel : ViewModel() {

private val repo: DockerRepository = AppModule.dockerRepository


private val maxHistorySize = 60


val containerStats: Flow<List<ContainerStats>> = repo.getContainerStats()

val usageHistory: Flow<UsageHistory> =
        containerStats.runningFold(UsageHistory()) { acc, stats ->
            if (stats.isEmpty()) acc
            else {
                val avgCpu = stats.sumOf { it.cpuPercent } / stats.size
                val avgMem = stats.sumOf { it.memoryPercent } / stats.size
                UsageHistory(
                        cpuHistory = (acc.cpuHistory + avgCpu).takeLast(maxHistorySize),
                        memoryHistory = (acc.memoryHistory + avgMem).takeLast(maxHistorySize)
                )
            }
        }


    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()


    fun clearError() {
        _error.value = null
    }
}
