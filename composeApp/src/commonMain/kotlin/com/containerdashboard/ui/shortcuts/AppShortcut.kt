package com.containerdashboard.ui.shortcuts

import com.containerdashboard.ui.util.isMacHost

/**
 * Single source of truth for every user-facing keyboard shortcut.
 *
 * The `AppShortcutScope` handler and the `KeyboardShortcutsOverlay` cheatsheet
 * both render from this enum, so a new shortcut only has to be added once.
 *
 * The `keys` string uses the `<mod>` placeholder for the platform modifier key
 * (Command on macOS, Ctrl elsewhere); [displayKeys] substitutes the real glyph
 * at render time.
 *
 * Note: the actual key-event matching in [AppShortcutScope] is still
 * hand-rolled — if you add an entry here, wire the matcher there as well.
 */
enum class AppShortcut(
    val keys: String,
    val label: String,
) {
    OPEN_PALETTE("<mod> K", "Open command palette"),
    FOCUS_SEARCH("<mod> F", "Focus search"),
    OPEN_SETTINGS("<mod> ,", "Open Settings"),
    JUMP_SCREEN("<mod> 1 – <mod> 7", "Jump to sidebar screen"),
    CLOSE_OVERLAYS("Esc", "Close overlays / logs pane"),
    SHOW_CHEATSHEET("? or <mod> /", "Show this cheatsheet"),
    ;

    /** Render [keys] with the platform modifier glyph substituted in. */
    fun displayKeys(): String {
        val mod = if (isMacHost) "\u2318" else "Ctrl"
        return keys.replace("<mod>", mod)
    }
}
