package com.containerdashboard.ui.state

import com.containerdashboard.data.models.Container

data class ContainersState(
    val containers: List<Container> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val actionInProgress: String? = null // container id being acted on
)
