package com.containerdashboard.ui.state

import com.containerdashboard.data.models.DockerNetwork

data class NetworksState(
    val networks: List<DockerNetwork> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)
