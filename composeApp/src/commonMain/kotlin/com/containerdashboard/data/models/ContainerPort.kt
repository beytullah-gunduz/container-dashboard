package com.containerdashboard.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ContainerPort(
    @SerialName("IP")
    val ip: String? = null,
    @SerialName("PrivatePort")
    val privatePort: Int,
    @SerialName("PublicPort")
    val publicPort: Int? = null,
    @SerialName("Type")
    val type: String = "tcp"
) {
    val displayString: String
        get() = if (publicPort != null) {
            "${ip ?: "0.0.0.0"}:$publicPort->$privatePort/$type"
        } else {
            "$privatePort/$type"
        }
}
