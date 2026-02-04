package com.containerdashboard.ui.state

import com.containerdashboard.data.models.DockerImage

data class ImagesState(
    val images: List<DockerImage> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val pullProgress: String? = null
)
