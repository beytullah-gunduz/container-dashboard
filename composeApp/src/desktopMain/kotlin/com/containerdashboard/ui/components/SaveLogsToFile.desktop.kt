package com.containerdashboard.ui.components

import java.awt.FileDialog
import java.awt.Frame
import java.io.File

actual fun saveLogsToFile(
    suggestedFileName: String,
    content: String,
): Boolean {
    val dialog = FileDialog(null as Frame?, "Save Logs", FileDialog.SAVE)
    dialog.file = suggestedFileName
    dialog.isVisible = true
    val directory = dialog.directory ?: return false
    val file = dialog.file ?: return false
    return runCatching {
        File(directory, file).writeText(content)
    }.isSuccess
}
