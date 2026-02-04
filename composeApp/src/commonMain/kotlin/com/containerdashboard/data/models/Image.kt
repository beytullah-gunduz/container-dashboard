package com.containerdashboard.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DockerImage(
    @SerialName("Id")
    val id: String,
    @SerialName("ParentId")
    val parentId: String = "",
    @SerialName("RepoTags")
    val repoTags: List<String>? = null,
    @SerialName("RepoDigests")
    val repoDigests: List<String>? = null,
    @SerialName("Created")
    val created: Long = 0,
    @SerialName("Size")
    val size: Long = 0,
    @SerialName("VirtualSize")
    val virtualSize: Long = 0,
    @SerialName("SharedSize")
    val sharedSize: Long = -1,
    @SerialName("Labels")
    val labels: Map<String, String>? = null,
    @SerialName("Containers")
    val containers: Int = -1
) {
    val displayName: String
        get() = repoTags?.firstOrNull()?.takeIf { it != "<none>:<none>" } 
            ?: id.removePrefix("sha256:").take(12)
    
    val shortId: String
        get() = id.removePrefix("sha256:").take(12)
    
    val tag: String
        get() = repoTags?.firstOrNull()?.substringAfter(":", "latest") ?: "latest"
    
    val repository: String
        get() = repoTags?.firstOrNull()?.substringBefore(":") ?: "<none>"
    
    val formattedSize: String
        get() = formatBytes(size)
    
    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes < 1024) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024) return "%.1f KB".format(kb)
            val mb = kb / 1024.0
            if (mb < 1024) return "%.1f MB".format(mb)
            val gb = mb / 1024.0
            return "%.2f GB".format(gb)
        }
    }
}
