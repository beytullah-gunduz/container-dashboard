package com.containerdashboard.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ContainerNetworkSettings(
    @SerialName("Networks")
    val networks: Map<String, ContainerNetwork> = emptyMap()
)
