package com.containerdashboard

import com.containerdashboard.data.models.SystemInfo
import com.containerdashboard.ui.screens.viewmodel.DerivedContainerStats
import com.containerdashboard.ui.screens.viewmodel.MonitoringAggregation
import com.containerdashboard.ui.screens.viewmodel.MonitoringScreenViewModel
import com.containerdashboard.ui.screens.viewmodel.aggregateCpu
import com.containerdashboard.ui.screens.viewmodel.aggregateMemory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class MonitoringHelpersTest {
    private lateinit var vm: MonitoringScreenViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        val fake = FakeDockerRepository()
        vm = MonitoringScreenViewModel(repoProvider = { fake })
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -------------------------------------------------------------------------
    // rate() — bytes-per-second from cumulative counter
    // -------------------------------------------------------------------------

    @Test
    fun `rate returns 0 when previous is null (first sample)`() {
        assertEquals(0L, vm.rate(null, 1_000L, 1.0))
    }

    @Test
    fun `rate returns 0 when elapsed is zero`() {
        assertEquals(0L, vm.rate(500L, 1_000L, 0.0))
    }

    @Test
    fun `rate returns 0 on counter reset (delta negative)`() {
        // Counter wrapped or container restarted — delta < 0 → treat as no data
        assertEquals(0L, vm.rate(1_000L, 500L, 1.0))
    }

    @Test
    fun `rate computes bytes per second correctly over 2 seconds`() {
        // 2 000 bytes in 2 s → 1 000 B/s
        assertEquals(1_000L, vm.rate(0L, 2_000L, 2.0))
    }

    @Test
    fun `rate computes bytes per second with fractional elapsed`() {
        // 500 bytes in 0.5 s → 1 000 B/s
        assertEquals(1_000L, vm.rate(0L, 500L, 0.5))
    }

    // -------------------------------------------------------------------------
    // aggregateCpu()
    // -------------------------------------------------------------------------

    private fun makeStats(
        cpuPercent: Double,
        memoryUsage: Long = 0L,
        memoryLimit: Long = 1L,
    ) = DerivedContainerStats(
        containerId = "id",
        containerName = "name",
        cpuPercent = cpuPercent,
        memoryPercent = if (memoryLimit > 0) (memoryUsage.toDouble() / memoryLimit) * 100.0 else 0.0,
        memoryUsage = memoryUsage,
        memoryLimit = memoryLimit,
        diskReadBytesPerSec = 0L,
        diskWriteBytesPerSec = 0L,
        networkRxBytesPerSec = 0L,
        networkTxBytesPerSec = 0L,
    )

    @Test
    fun `aggregateCpu ENGINE mode divides total cpu by ncpu`() {
        // 2 containers × 50 % each, engine has 4 CPUs → 100 / 4 = 25.0 %
        val stats = listOf(makeStats(50.0), makeStats(50.0))
        val sysInfo = SystemInfo(ncpu = 4)
        val result = aggregateCpu(stats, MonitoringAggregation.ENGINE, sysInfo)
        assertEquals(25.0, result, 0.001)
    }

    @Test
    fun `aggregateCpu CONTAINER_AVG mode averages per-container percentages`() {
        val stats = listOf(makeStats(30.0), makeStats(70.0))
        val result = aggregateCpu(stats, MonitoringAggregation.CONTAINER_AVG, sysInfo = null)
        assertEquals(50.0, result, 0.001)
    }

    @Test
    fun `aggregateCpu ENGINE mode falls back to container average when sysInfo is null`() {
        val stats = listOf(makeStats(40.0), makeStats(60.0))
        val result = aggregateCpu(stats, MonitoringAggregation.ENGINE, sysInfo = null)
        assertEquals(50.0, result, 0.001)
    }

    @Test
    fun `aggregateCpu ENGINE mode falls back to container average when ncpu is 0`() {
        val stats = listOf(makeStats(40.0), makeStats(60.0))
        val sysInfo = SystemInfo(ncpu = 0)
        val result = aggregateCpu(stats, MonitoringAggregation.ENGINE, sysInfo)
        assertEquals(50.0, result, 0.001)
    }

    // -------------------------------------------------------------------------
    // aggregateMemory()
    // -------------------------------------------------------------------------

    @Test
    fun `aggregateMemory ENGINE mode divides total usage by memTotal`() {
        // 2 containers use 256 MB each, total engine RAM = 2 GB → 512/2048 * 100 = 25 %
        val mb = 1024L * 1024L
        val stats = listOf(makeStats(0.0, memoryUsage = 256 * mb), makeStats(0.0, memoryUsage = 256 * mb))
        val sysInfo = SystemInfo(memTotal = 2048 * mb)
        val result = aggregateMemory(stats, MonitoringAggregation.ENGINE, sysInfo)
        assertEquals(25.0, result, 0.001)
    }

    @Test
    fun `aggregateMemory CONTAINER_AVG mode averages per-container percentages`() {
        // Container A: 25 %, Container B: 75 % → avg = 50 %
        val mb = 1024L * 1024L
        val stats =
            listOf(
                makeStats(0.0, memoryUsage = 256 * mb, memoryLimit = 1024 * mb),
                makeStats(0.0, memoryUsage = 768 * mb, memoryLimit = 1024 * mb),
            )
        val result = aggregateMemory(stats, MonitoringAggregation.CONTAINER_AVG, sysInfo = null)
        assertEquals(50.0, result, 0.001)
    }

    @Test
    fun `aggregateMemory ENGINE mode falls back to container average when memTotal is 0`() {
        val mb = 1024L * 1024L
        val stats =
            listOf(
                makeStats(0.0, memoryUsage = 256 * mb, memoryLimit = 1024 * mb),
                makeStats(0.0, memoryUsage = 768 * mb, memoryLimit = 1024 * mb),
            )
        val sysInfo = SystemInfo(memTotal = 0)
        val result = aggregateMemory(stats, MonitoringAggregation.ENGINE, sysInfo)
        assertEquals(50.0, result, 0.001)
    }
}
