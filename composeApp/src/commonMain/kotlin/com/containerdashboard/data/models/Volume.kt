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
    val usageData: VolumeUsageData? = null,
) {
    val displayName: String
        get() = name

    val formattedSize: String
        get() = usageData?.let { DockerImage.formatBytes(it.size) } ?: "N/A"

    val isAnonymous: Boolean
        get() = labels?.containsKey(ANON_LABEL) == true

    companion object {
        /** Docker writes this label (empty value) on every volume it creates implicitly. */
        const val ANON_LABEL: String = "com.docker.volume.anonymous"
    }
}
