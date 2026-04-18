package com.containerdashboard.data.models

import kotlinx.serialization.Serializable

/**
 * Rich volume detail model used by the details drawer.
 *
 * Populated from `docker inspect` on a volume. `rawJson` is the full,
 * pretty-printed docker-java `InspectVolumeResponse` for the "Raw JSON" tab.
 */
@Serializable
data class VolumeInspect(
    val name: String,
    val driver: String,
    val mountpoint: String,
    val scope: String,
    val createdAt: String,
    val options: Map<String, String>,
    val labels: Map<String, String>,
    val rawJson: String,
)
