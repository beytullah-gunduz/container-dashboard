package com.containerdashboard.data.models

import kotlinx.serialization.Serializable

/**
 * Rich network detail model used by the details drawer.
 *
 * Populated from `docker inspect` on a network. `rawJson` is the full,
 * pretty-printed docker-java `Network` response for the "Raw JSON" tab.
 */
@Serializable
data class NetworkInspect(
    val id: String,
    val shortId: String,
    val name: String,
    val driver: String,
    val scope: String,
    val attachable: Boolean,
    val ingress: Boolean,
    val internal: Boolean,
    val ipv6Enabled: Boolean,
    val createdAt: String,
    val ipamDriver: String,
    val ipamConfig: List<IpamConfigEntry>,
    val options: Map<String, String>,
    val labels: Map<String, String>,
    val attachedContainers: List<AttachedContainer>,
    val rawJson: String,
)

@Serializable
data class IpamConfigEntry(
    val subnet: String,
    val gateway: String,
    val ipRange: String,
)

@Serializable
data class AttachedContainer(
    val id: String,
    val name: String,
    val ipv4Address: String,
    val ipv6Address: String,
    val macAddress: String,
)
