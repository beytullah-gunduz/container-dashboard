package com.containerdashboard.ui.components

expect fun saveLogsToFile(
    suggestedFileName: String,
    content: String,
): Boolean
