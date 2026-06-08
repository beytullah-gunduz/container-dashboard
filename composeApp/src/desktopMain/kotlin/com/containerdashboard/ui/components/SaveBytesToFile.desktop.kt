package com.containerdashboard.ui.components

import java.awt.FileDialog
import java.awt.Frame
import java.io.File

actual fun saveBytesToFile(
    suggestedFileName: String,
    bytes: ByteArray,
): Boolean {
    val dialog = FileDialog(null as Frame?, "Save File", FileDialog.SAVE)
    dialog.file = suggestedFileName
    dialog.isVisible = true
    val directory = dialog.directory ?: return false
    val file = dialog.file ?: return false
    return runCatching {
        File(directory, file).writeBytes(bytes)
    }.isSuccess
}
