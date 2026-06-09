package com.containerdashboard.ui.components

/**
 * Sanitize a container-supplied suggested filename before passing it to a save dialog.
 *
 * Strips path separators and ASCII control characters so a crafted container image cannot
 * pre-fill the dialog with a path like `../../../Library/LaunchAgents/evil.plist`.
 * Only the last path component is kept; an empty result falls back to "download".
 */
fun sanitizeSuggestedFileName(name: String): String {
    // Keep only the last component after any '/' or '\'.
    val base = name.substringAfterLast('/').substringAfterLast('\\')
    // Drop ASCII control characters (0x00–0x1F and DEL 0x7F).
    val clean = base.filter { it.code !in 0..31 && it.code != 127 }
    return clean.ifBlank { "download" }
}
