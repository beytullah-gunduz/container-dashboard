package com.containerdashboard.data.models

import com.containerdashboard.ui.util.formatBytes as sharedFormatBytes
import com.containerdashboard.ui.util.formatBytesPerSecond as sharedFormatBytesPerSecond

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
        fun formatBytes(bytes: Long): String = sharedFormatBytes(bytes)

        fun formatBytesPerSecond(bytesPerSec: Long): String = sharedFormatBytesPerSecond(bytesPerSec)
    }
}
