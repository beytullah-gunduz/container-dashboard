package com.containerdashboard.data.repository

import com.github.dockerjava.api.model.Statistics

// Pure stats arithmetic extracted from DesktopDockerRepository so it is unit-testable
// with fixed docker-java Statistics objects.

internal fun calculateCpuPercent(
    stats: Statistics,
    hostProcessors: Int = Runtime.getRuntime().availableProcessors(),
): Double {
    val cpuStats = stats.cpuStats ?: return 0.0
    val preCpuStats = stats.preCpuStats ?: return 0.0

    val cpuDelta = (cpuStats.cpuUsage?.totalUsage ?: 0L) - (preCpuStats.cpuUsage?.totalUsage ?: 0L)
    val systemDelta = (cpuStats.systemCpuUsage ?: 0L) - (preCpuStats.systemCpuUsage ?: 0L)

    if (systemDelta <= 0L || cpuDelta < 0L) return 0.0

    // cgroup v2 reports neither percpuUsage nor (sometimes) onlineCpus; falling back to 1
    // would cap CPU% at 100 on multi-core hosts, so use the host's processor count instead.
    val numCpus =
        cpuStats.cpuUsage?.percpuUsage?.size
            ?: cpuStats.onlineCpus?.toInt()
            ?: hostProcessors
    return (cpuDelta.toDouble() / systemDelta.toDouble()) * numCpus * 100.0
}

// Sum cumulative disk IO from blkioStats.ioServiceBytesRecursive entries by op.
// Many backends (notably cgroup v2 / rootless Docker) report an empty list —
// we return (0, 0) in that case rather than failing.
internal fun extractDiskIo(stats: Statistics): Pair<Long, Long> {
    val entries = stats.blkioStats?.ioServiceBytesRecursive ?: return 0L to 0L
    if (entries.isEmpty()) return 0L to 0L
    var read = 0L
    var write = 0L
    for (entry in entries) {
        val op = entry?.op?.lowercase() ?: continue
        val value = entry.value ?: continue
        when (op) {
            "read" -> read += value
            "write" -> write += value
        }
    }
    return read to write
}

// Sum cumulative rx/tx bytes across every network interface reported.
// Returns (0, 0) when the networks map is missing or empty.
internal fun extractNetworkIo(stats: Statistics): Pair<Long, Long> {
    val networks = stats.networks ?: return 0L to 0L
    if (networks.isEmpty()) return 0L to 0L
    var rx = 0L
    var tx = 0L
    for (net in networks.values) {
        if (net == null) continue
        rx += net.rxBytes ?: 0L
        tx += net.txBytes ?: 0L
    }
    return rx to tx
}
