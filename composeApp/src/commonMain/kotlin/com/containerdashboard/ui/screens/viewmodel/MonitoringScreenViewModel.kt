package com.containerdashboard.ui.screens.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.containerdashboard.data.models.ContainerStats
import com.containerdashboard.data.models.SystemInfo
import com.containerdashboard.data.repository.DockerRepository
import com.containerdashboard.data.repository.PreferenceRepository
import com.containerdashboard.di.AppModule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * How the Monitoring screen aggregates per-container CPU and memory into
 * the single number shown above the history graph.
 */
enum class MonitoringAggregation {
    /** sum(container usage) / engine capacity — answers "how full is the engine?" */
    ENGINE,

    /** mean of per-container percentages — legacy behavior. */
    CONTAINER_AVG,
}

/**
 * Snapshot in the per-metric history ring buffer. Disk and network values
 * are aggregate (sum across running containers) byte-per-second rates.
 */
data class UsageHistory(
    val cpuHistory: List<Double> = emptyList(),
    val memoryHistory: List<Double> = emptyList(),
    val diskReadHistory: List<Long> = emptyList(),
    val diskWriteHistory: List<Long> = emptyList(),
    val networkRxHistory: List<Long> = emptyList(),
    val networkTxHistory: List<Long> = emptyList(),
)

/**
 * Per-container stats with derived rates (bytes/sec) computed against
 * the prior cumulative sample. The first sample for a given container
 * yields a rate of zero.
 */
data class DerivedContainerStats(
    val containerId: String,
    val containerName: String,
    val cpuPercent: Double,
    val memoryPercent: Double,
    val memoryUsage: Long,
    val memoryLimit: Long,
    val diskReadBytesPerSec: Long,
    val diskWriteBytesPerSec: Long,
    val networkRxBytesPerSec: Long,
    val networkTxBytesPerSec: Long,
)

private data class PreviousSample(
    val stats: ContainerStats,
    val timestampMillis: Long,
)

private data class DerivedState(
    val derived: List<DerivedContainerStats>,
    val previous: Map<String, PreviousSample>,
)

class MonitoringScreenViewModel : ViewModel() {
    private val repo: DockerRepository get() = AppModule.dockerRepository

    private val maxHistorySize = 60

    private val _refreshRate = MutableStateFlow(1f) // seconds

    val refreshRate: StateFlow<Float> = _refreshRate.asStateFlow()

    private val systemInfo = MutableStateFlow<SystemInfo?>(null)

    private val aggregation: StateFlow<MonitoringAggregation> =
        PreferenceRepository
            .monitoringAggregation()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonitoringAggregation.ENGINE)

    init {
        viewModelScope.launch {
            systemInfo.value = repo.getSystemInfo().getOrNull()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    private val rawStats: Flow<List<ContainerStats>> =
        _refreshRate
            .flatMapLatest { seconds ->
                repo.getContainerStats().sample((seconds * 1000).toLong())
            }

    /**
     * Per-container derived stats. Uses a rolling fold keyed by container id
     * to compute bytes/sec rates from cumulative docker-java counters.
     */
    val derivedStats: Flow<List<DerivedContainerStats>> =
        rawStats
            .runningFold(DerivedState(emptyList(), emptyMap())) { acc, stats ->
                val now = currentTimeMillis()
                val newPrevious = mutableMapOf<String, PreviousSample>()
                val derived =
                    stats.map { current ->
                        val prev = acc.previous[current.containerId]
                        val elapsedSec =
                            if (prev != null) {
                                max((now - prev.timestampMillis) / 1000.0, 0.001)
                            } else {
                                0.0
                            }
                        val diskR = rate(prev?.stats?.diskReadBytes, current.diskReadBytes, elapsedSec)
                        val diskW = rate(prev?.stats?.diskWriteBytes, current.diskWriteBytes, elapsedSec)
                        val netRx = rate(prev?.stats?.networkRxBytes, current.networkRxBytes, elapsedSec)
                        val netTx = rate(prev?.stats?.networkTxBytes, current.networkTxBytes, elapsedSec)
                        newPrevious[current.containerId] = PreviousSample(current, now)
                        DerivedContainerStats(
                            containerId = current.containerId,
                            containerName = current.containerName,
                            cpuPercent = current.cpuPercent,
                            memoryPercent = current.memoryPercent,
                            memoryUsage = current.memoryUsage,
                            memoryLimit = current.memoryLimit,
                            diskReadBytesPerSec = diskR,
                            diskWriteBytesPerSec = diskW,
                            networkRxBytesPerSec = netRx,
                            networkTxBytesPerSec = netTx,
                        )
                    }
                DerivedState(derived, newPrevious)
            }.map { it.derived }

    /**
     * Aggregate history across all containers. CPU and memory follow the
     * selected [MonitoringAggregation] mode; disk/network are summed bytes/sec.
     */
    val usageHistory: Flow<UsageHistory> =
        derivedStats.runningFold(UsageHistory()) { acc, stats ->
            if (stats.isEmpty()) {
                acc
            } else {
                val mode = aggregation.value
                val sysInfo = systemInfo.value
                val aggCpu = aggregateCpu(stats, mode, sysInfo)
                val aggMem = aggregateMemory(stats, mode, sysInfo)
                val totalDiskRead = stats.sumOf { it.diskReadBytesPerSec }
                val totalDiskWrite = stats.sumOf { it.diskWriteBytesPerSec }
                val totalNetRx = stats.sumOf { it.networkRxBytesPerSec }
                val totalNetTx = stats.sumOf { it.networkTxBytesPerSec }
                UsageHistory(
                    cpuHistory = (acc.cpuHistory + aggCpu).takeLast(maxHistorySize),
                    memoryHistory = (acc.memoryHistory + aggMem).takeLast(maxHistorySize),
                    diskReadHistory = (acc.diskReadHistory + totalDiskRead).takeLast(maxHistorySize),
                    diskWriteHistory = (acc.diskWriteHistory + totalDiskWrite).takeLast(maxHistorySize),
                    networkRxHistory = (acc.networkRxHistory + totalNetRx).takeLast(maxHistorySize),
                    networkTxHistory = (acc.networkTxHistory + totalNetTx).takeLast(maxHistorySize),
                )
            }
        }

    /** Emits `false` until the first stats snapshot has been delivered. */
    val hasLoaded: StateFlow<Boolean> =
        rawStats
            .map { true }
            .onStart { emit(false) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun setRefreshRate(seconds: Float) {
        _refreshRate.value = seconds
    }

    fun clearError() {
        _error.value = null
    }

    private fun rate(
        previous: Long?,
        current: Long,
        elapsedSec: Double,
    ): Long {
        if (previous == null || elapsedSec <= 0.0) return 0L
        val delta = current - previous
        if (delta <= 0L) return 0L
        return (delta / elapsedSec).toLong()
    }

    private fun currentTimeMillis(): Long = System.currentTimeMillis()
}

private fun aggregateCpu(
    stats: List<DerivedContainerStats>,
    mode: MonitoringAggregation,
    sysInfo: SystemInfo?,
): Double {
    val nCpu = sysInfo?.ncpu?.takeIf { it > 0 }
    return if (mode == MonitoringAggregation.ENGINE && nCpu != null) {
        // cpuPercent is already "per CPU × 100" per Docker convention, so the
        // sum maxes at nCpu × 100; dividing by nCpu normalizes to 0..100 engine %.
        stats.sumOf { it.cpuPercent } / nCpu
    } else {
        stats.sumOf { it.cpuPercent } / stats.size
    }
}

private fun aggregateMemory(
    stats: List<DerivedContainerStats>,
    mode: MonitoringAggregation,
    sysInfo: SystemInfo?,
): Double {
    val memTotal = sysInfo?.memTotal?.takeIf { it > 0 }
    return if (mode == MonitoringAggregation.ENGINE && memTotal != null) {
        (stats.sumOf { it.memoryUsage }.toDouble() / memTotal) * 100.0
    } else {
        stats.sumOf { it.memoryPercent } / stats.size
    }
}
