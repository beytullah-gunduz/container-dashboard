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
