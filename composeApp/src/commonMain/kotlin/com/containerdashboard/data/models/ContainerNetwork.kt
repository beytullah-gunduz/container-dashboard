package com.containerdashboard.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ContainerNetwork(
    @SerialName("NetworkID")
    val networkId: String = "",
    @SerialName("IPAddress")
    val ipAddress: String = "",
    @SerialName("Gateway")
    val gateway: String = "",
    @SerialName("MacAddress")
    val macAddress: String = ""
)
