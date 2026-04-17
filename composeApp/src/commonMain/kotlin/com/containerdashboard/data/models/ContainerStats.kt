package com.containerdashboard.data.models

data class ContainerStats(
    val containerId: String,
    val containerName: String,
    val cpuPercent: Double,
    val memoryUsage: Long,
    val memoryLimit: Long,
    val diskReadBytes: Long = 0L,
    val diskWriteBytes: Long = 0L,
    val networkRxBytes: Long = 0L,
    val networkTxBytes: Long = 0L,
) {
    val memoryPercent: Double
        get() = if (memoryLimit > 0) (memoryUsage.toDouble() / memoryLimit) * 100.0 else 0.0

    val formattedMemoryUsage: String
        get() = formatBytes(memoryUsage)

    val formattedMemoryLimit: String
        get() = formatBytes(memoryLimit)

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes < 1024) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024) return "%.1f KB".format(kb)
            val mb = kb / 1024.0
            if (mb < 1024) return "%.1f MB".format(mb)
            val gb = mb / 1024.0
            return "%.2f GB".format(gb)
        }

        fun formatBytesPerSecond(bytesPerSec: Long): String {
            if (bytesPerSec < 1024) return "$bytesPerSec B/s"
            val kb = bytesPerSec / 1024.0
            if (kb < 1024) return "%.1f KB/s".format(kb)
            val mb = kb / 1024.0
            if (mb < 1024) return "%.1f MB/s".format(mb)
            val gb = mb / 1024.0
            return "%.2f GB/s".format(gb)
        }
    }
}
