package com.containerdashboard.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NetworkContainer(
    @SerialName("Name")
    val name: String? = null,
    @SerialName("EndpointID")
    val endpointId: String = "",
    @SerialName("MacAddress")
    val macAddress: String = "",
    @SerialName("IPv4Address")
    val ipv4Address: String = "",
    @SerialName("IPv6Address")
    val ipv6Address: String = ""
)
