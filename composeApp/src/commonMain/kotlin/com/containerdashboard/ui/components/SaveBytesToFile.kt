package com.containerdashboard.ui.components

/** Prompt the user for a host location and write [bytes] there. Returns true on success. */
expect fun saveBytesToFile(
    suggestedFileName: String,
    bytes: ByteArray,
): Boolean
