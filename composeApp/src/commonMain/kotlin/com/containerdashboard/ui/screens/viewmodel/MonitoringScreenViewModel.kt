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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
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

enum class MonitoringSort { NAME, CPU, MEM, DISK_R, DISK_W, NET_RX, NET_TX }

enum class MonitoringSortDirection { ASC, DESC }

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

class MonitoringScreenViewModel(
    private val repoProvider: () -> DockerRepository = { AppModule.dockerRepository },
) : ViewModel() {
    private val repo: DockerRepository get() = repoProvider()

    private val maxHistorySize = 60

    private val _refreshRate = MutableStateFlow(1f) // seconds

    val refreshRate: StateFlow<Float> = _refreshRate.asStateFlow()

    private val systemInfo: StateFlow<SystemInfo?> =
        flow { emit(repo.getSystemInfo().getOrThrow()) }
            .retryWhen { _, _ ->
                delay(2_000)
                true
            }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val aggregation: StateFlow<MonitoringAggregation> =
        PreferenceRepository
            .monitoringAggregation()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonitoringAggregation.ENGINE)

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    private val rawStats =
        _refreshRate
            .flatMapLatest { seconds ->
                repo.getContainerStats().sample((seconds * 1000).toLong())
            }

    /**
     * Per-container derived stats. Uses a rolling fold keyed by container id
     * to compute bytes/sec rates from cumulative docker-java counters.
     *
     * Hot [StateFlow] with [SharingStarted.WhileSubscribed] (5s stop timeout):
     * the grace window keeps the fold's previous-sample map alive across brief
     * re-subscription (recomposition restart, AnimatedContent swap), while the
     * expensive per-container stats streams are torn down once the Monitoring
     * screen has been off-composition for >5s. This matters because the VM is
     * retained for the window's lifetime — [SharingStarted.Eagerly] would keep
     * streaming stats from the daemon forever, even while on other screens.
     *
     * Seeded `null` (not `emptyList()`): `null` means "no snapshot yet" so the UI can show a
     * loading spinner until the first stats arrive, distinct from an empty-but-delivered list
     * (no running containers). Seeding `emptyList()` would make the two indistinguishable and
     * flash "No running containers" during the initial load.
     */
    val derivedStats: StateFlow<List<DerivedContainerStats>?> =
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
            }.drop(1)
            .map { it.derived }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Aggregate history across all containers. CPU and memory follow the
     * selected [MonitoringAggregation] mode; disk/network are summed bytes/sec.
     *
     * Hot [StateFlow] with the same [SharingStarted.WhileSubscribed] policy as
     * [derivedStats]; [combine] makes the fold reactive so a change to
     * [aggregation] or arrival of [systemInfo] is applied on the next tick.
     * Tradeoff: leaving Monitoring for >5s tears down the stream and the
     * ~60-sample ring buffer rebuilds on return — acceptable for a real-time
     * graph (persisting longer history would be a separate feature).
     */
    val usageHistory: StateFlow<UsageHistory> =
        combine(derivedStats, systemInfo, aggregation) { stats, sysInfo, mode ->
            Triple(stats, sysInfo, mode)
        }.runningFold(UsageHistory()) { acc, (stats, sysInfo, mode) ->
            if (stats.isNullOrEmpty()) {
                acc
            } else {
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
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UsageHistory())

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _sortColumn = MutableStateFlow(MonitoringSort.NAME)
    val sortColumn: StateFlow<MonitoringSort> = _sortColumn.asStateFlow()

    private val _sortDirection = MutableStateFlow(MonitoringSortDirection.ASC)
    val sortDirection: StateFlow<MonitoringSortDirection> = _sortDirection.asStateFlow()

    fun toggleSort(column: MonitoringSort) {
        if (_sortColumn.value == column) {
            _sortDirection.value =
                if (_sortDirection.value == MonitoringSortDirection.ASC) {
                    MonitoringSortDirection.DESC
                } else {
                    MonitoringSortDirection.ASC
                }
        } else {
            _sortColumn.value = column
            // Sort by name ascending by default; metrics descending so
            // the busiest container is at the top on first click.
            _sortDirection.value =
                if (column == MonitoringSort.NAME) {
                    MonitoringSortDirection.ASC
                } else {
                    MonitoringSortDirection.DESC
                }
        }
    }

    fun setRefreshRate(seconds: Float) {
        _refreshRate.value = seconds
    }

    fun clearError() {
        _error.value = null
    }

    internal fun rate(
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

internal fun aggregateCpu(
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

internal fun aggregateMemory(
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
