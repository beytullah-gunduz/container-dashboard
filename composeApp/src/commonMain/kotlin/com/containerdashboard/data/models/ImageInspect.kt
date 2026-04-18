package com.containerdashboard.data.models

import kotlinx.serialization.Serializable

/**
 * Rich image detail model used by the details drawer.
 *
 * Populated from `docker inspect` on an image. `rawJson` is the full,
 * pretty-printed docker-java `InspectImageResponse` for the "Raw JSON" tab.
 */
@Serializable
data class ImageInspect(
    val id: String,
    val shortId: String,
    val repoTags: List<String>,
    val repoDigests: List<String>,
    val architecture: String,
    val os: String,
    val size: Long,
    val virtualSize: Long,
    val createdAt: String,
    val dockerVersion: String,
    val author: String,
    val entrypoint: List<String>,
    val command: List<String>,
    val workingDir: String,
    val user: String,
    val exposedPorts: List<String>,
    val environment: List<EnvVar>,
    val labels: Map<String, String>,
    val layers: List<String>,
    val rawJson: String,
)
