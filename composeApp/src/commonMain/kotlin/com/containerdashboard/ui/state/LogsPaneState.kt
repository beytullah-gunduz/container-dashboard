package com.containerdashboard.ui.state

import com.containerdashboard.data.models.Container

data class LogsPaneState(
    val container: Container? = null,
    val logs: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)
