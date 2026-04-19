package com.containerdashboard.ui.util

/**
 * Format a byte count as a human-readable string (e.g. `"1.23 MB"`).
 *
 * Uses binary multiples (KiB = 1024 B) but short suffixes for brevity
 * (KB/MB/GB), matching the convention used across the UI.
 */
fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    val gb = mb / 1024.0
    return "%.2f GB".format(gb)
}

/**
 * Format a bytes-per-second rate as a human-readable string (e.g.
 * `"1.23 MB/s"`). Uses the same binary-multiple convention as
 * [formatBytes].
 */
fun formatBytesPerSecond(bytesPerSec: Long): String {
    if (bytesPerSec < 1024) return "$bytesPerSec B/s"
    val kb = bytesPerSec / 1024.0
    if (kb < 1024) return "%.1f KB/s".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB/s".format(mb)
    val gb = mb / 1024.0
    return "%.2f GB/s".format(gb)
}
