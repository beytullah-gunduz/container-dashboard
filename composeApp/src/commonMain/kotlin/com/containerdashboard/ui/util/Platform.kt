package com.containerdashboard.ui.util

/**
 * True when the JVM reports macOS as the host OS.
 *
 * Used across the UI to pick platform-appropriate modifier glyphs
 * (e.g. "⌘" vs "Ctrl") in keyboard shortcut hints. Evaluated once at
 * class-load time — the host OS does not change over the lifetime of
 * the process.
 */
val isMacHost: Boolean = System.getProperty("os.name", "").contains("mac", ignoreCase = true)

/** True when the JVM reports Windows as the host OS. */
val isWindowsHost: Boolean = System.getProperty("os.name", "").contains("windows", ignoreCase = true)

/** True when the JVM reports Linux as the host OS. */
val isLinuxHost: Boolean =
    !isMacHost && !isWindowsHost &&
        System.getProperty("os.name", "").contains("linux", ignoreCase = true)
