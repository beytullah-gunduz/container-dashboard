package com.containerdashboard.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class IPAM(
    @SerialName("Driver")
    val driver: String = "default",
    @SerialName("Config")
    val config: List<IPAMConfig>? = null,
    @SerialName("Options")
    val options: Map<String, String>? = null
)
