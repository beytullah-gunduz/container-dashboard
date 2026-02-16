package com.containerdashboard.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VolumeUsageData(
    @SerialName("Size")
    val size: Long = -1,
    @SerialName("RefCount")
    val refCount: Int = -1,
)
