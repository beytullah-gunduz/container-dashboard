package com.containerdashboard.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VolumeListResponse(
    @SerialName("Volumes")
    val volumes: List<Volume>? = null,
    @SerialName("Warnings")
    val warnings: List<String>? = null
)
