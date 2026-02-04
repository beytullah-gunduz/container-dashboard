package com.containerdashboard.ui.state

import com.containerdashboard.data.models.*

data class DashboardState(
    val systemInfo: SystemInfo? = null,
    val version: DockerVersion? = null,
    val containers: List<Container> = emptyList(),
    val images: List<DockerImage> = emptyList(),
    val volumes: List<Volume> = emptyList(),
    val networks: List<DockerNetwork> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val isConnected: Boolean = false
)
