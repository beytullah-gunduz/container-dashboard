package com.containerdashboard.data.models

import kotlinx.serialization.Serializable

/**
 * Rich container detail model used by the details drawer.
 *
 * Populated from `docker inspect`. `rawJson` is the full, pretty-printed
 * docker-java `InspectContainerResponse` for the "Raw JSON" tab.
 */
@Serializable
data class ContainerInspect(
    val id: String,
    val name: String,
    val image: String,
    val imageId: String,
    val status: String,
    val state: String,
    val createdAt: String,
    val startedAt: String,
    val command: String,
    val entrypoint: List<String>,
    val workingDir: String,
    val user: String,
    val restartPolicy: String,
    val hostname: String,
    val platform: String,
    val environment: List<EnvVar>,
    val mounts: List<MountInfo>,
    val ports: List<PortMapping>,
    val networks: List<NetworkAttachment>,
    val labels: Map<String, String>,
    val rawJson: String,
)

@Serializable
data class EnvVar(
    val key: String,
    val value: String,
)

@Serializable
data class MountInfo(
    val type: String,
    val source: String,
    val destination: String,
    val mode: String,
    val rw: Boolean,
)

@Serializable
data class PortMapping(
    val containerPort: Int,
    val hostPort: Int?,
    val protocol: String,
    val hostIp: String?,
)

@Serializable
data class NetworkAttachment(
    val name: String,
    val ipAddress: String,
    val gateway: String,
    val macAddress: String,
    val aliases: List<String>,
)
