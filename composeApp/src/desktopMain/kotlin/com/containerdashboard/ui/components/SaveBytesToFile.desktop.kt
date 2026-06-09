package com.containerdashboard.ui.components

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.SwingUtilities

actual fun saveBytesToFile(
    suggestedFileName: String,
    bytes: ByteArray,
): Boolean {
    val safe = sanitizeSuggestedFileName(suggestedFileName)
    var directory: String? = null
    var file: String? = null
    // FileDialog.isVisible is a blocking modal call that must run on the AWT EDT.
    SwingUtilities.invokeAndWait {
        val dialog = FileDialog(null as Frame?, "Save File", FileDialog.SAVE)
        dialog.file = safe
        dialog.isVisible = true
        directory = dialog.directory
        file = dialog.file
    }
    val dir = directory ?: return false
    val name = file ?: return false
    return runCatching {
        val dest = File(dir, name)
        // Guard against path traversal: the canonical path of the chosen file must stay
        // inside the directory the dialog returned.
        val canonicalDest = dest.canonicalPath
        val canonicalDir = File(dir).canonicalPath
        if (!canonicalDest.startsWith(canonicalDir + File.separator) &&
            canonicalDest != canonicalDir
        ) {
            return@runCatching // silently refuse to write outside the chosen directory
        }
        dest.writeBytes(bytes)
    }.isSuccess
}
