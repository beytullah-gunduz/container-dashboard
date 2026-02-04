package com.containerdashboard.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Volume(
    @SerialName("Name")
    val name: String,
    @SerialName("Driver")
    val driver: String = "local",
    @SerialName("Mountpoint")
    val mountpoint: String = "",
    @SerialName("CreatedAt")
    val createdAt: String? = null,
    @SerialName("Labels")
    val labels: Map<String, String>? = null,
    @SerialName("Scope")
    val scope: String = "local",
    @SerialName("Options")
    val options: Map<String, String>? = null,
    @SerialName("UsageData")
    val usageData: VolumeUsageData? = null
) {
    val displayName: String
        get() = if (name.length > 20) name.take(20) + "..." else name
    
    val formattedSize: String
        get() = usageData?.let { DockerImage.formatBytes(it.size) } ?: "N/A"
}
