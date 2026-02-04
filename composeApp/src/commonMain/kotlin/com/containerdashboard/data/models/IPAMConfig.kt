package com.containerdashboard.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class IPAMConfig(
    @SerialName("Subnet")
    val subnet: String? = null,
    @SerialName("Gateway")
    val gateway: String? = null,
    @SerialName("IPRange")
    val ipRange: String? = null
)
