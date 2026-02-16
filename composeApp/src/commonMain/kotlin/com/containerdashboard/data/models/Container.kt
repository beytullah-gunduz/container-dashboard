package com.containerdashboard.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Container(
    @SerialName("Id")
    val id: String,
    @SerialName("Names")
    val names: List<String>,
    @SerialName("Image")
    val image: String,
    @SerialName("ImageID")
    val imageId: String = "",
    @SerialName("Command")
    val command: String = "",
    @SerialName("Created")
    val created: Long = 0,
    @SerialName("State")
    val state: String = "unknown",
    @SerialName("Status")
    val status: String = "",
    @SerialName("Ports")
    val ports: List<ContainerPort> = emptyList(),
    @SerialName("Labels")
    val labels: Map<String, String> = emptyMap(),
    @SerialName("SizeRw")
    val sizeRw: Long? = null,
    @SerialName("SizeRootFs")
    val sizeRootFs: Long? = null,
    @SerialName("NetworkSettings")
    val networkSettings: ContainerNetworkSettings? = null,
) {
    val displayName: String
        get() = names.firstOrNull()?.removePrefix("/") ?: id.take(12)

    val shortId: String
        get() = id.take(12)

    val isRunning: Boolean
        get() = state.lowercase() == "running"

    val isPaused: Boolean
        get() = state.lowercase() == "paused"

    val isStopped: Boolean
        get() = state.lowercase() == "exited" || state.lowercase() == "stopped"
}
