package com.containerdashboard.ui.state

import com.containerdashboard.data.models.Volume

data class VolumesState(
    val volumes: List<Volume> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)
