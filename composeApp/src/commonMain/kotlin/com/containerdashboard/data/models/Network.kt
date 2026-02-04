package com.containerdashboard.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DockerNetwork(
    @SerialName("Id")
    val id: String,
    @SerialName("Name")
    val name: String,
    @SerialName("Created")
    val created: String = "",
    @SerialName("Scope")
    val scope: String = "local",
    @SerialName("Driver")
    val driver: String = "bridge",
    @SerialName("EnableIPv6")
    val enableIPv6: Boolean = false,
    @SerialName("Internal")
    val internal: Boolean = false,
    @SerialName("Attachable")
    val attachable: Boolean = false,
    @SerialName("Ingress")
    val ingress: Boolean = false,
    @SerialName("IPAM")
    val ipam: IPAM? = null,
    @SerialName("Options")
    val options: Map<String, String>? = null,
    @SerialName("Labels")
    val labels: Map<String, String>? = null,
    @SerialName("Containers")
    val containers: Map<String, NetworkContainer>? = null
) {
    val shortId: String
        get() = id.take(12)
    
    val containerCount: Int
        get() = containers?.size ?: 0
    
    val subnet: String
        get() = ipam?.config?.firstOrNull()?.subnet ?: "N/A"
    
    val gateway: String
        get() = ipam?.config?.firstOrNull()?.gateway ?: "N/A"
}
