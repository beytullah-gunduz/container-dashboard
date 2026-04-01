package com.containerdashboard.ui.state

data class ConsolePaneState(
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val output: String = "",
    val error: String? = null,
)
