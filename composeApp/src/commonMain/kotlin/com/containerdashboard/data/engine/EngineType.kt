package com.containerdashboard.data.engine

enum class EngineType(
    val displayName: String,
) {
    COLIMA("Colima"),
    DOCKER_DESKTOP("Docker Desktop"),
    ORBSTACK("OrbStack"),
    LIMA("Lima"),
    RANCHER_DESKTOP("Rancher Desktop"),
    UNKNOWN("Container Engine"),
}

fun engineTypeFromHost(host: String): EngineType =
    when {
        host.contains("/.colima/") -> EngineType.COLIMA
        host.contains("/.orbstack/") -> EngineType.ORBSTACK
        host.contains("/.lima/") -> EngineType.LIMA
        host.contains("/.rd/") -> EngineType.RANCHER_DESKTOP
        host.contains("/docker.sock") -> EngineType.DOCKER_DESKTOP
        else -> EngineType.UNKNOWN
    }

fun colimaProfileFromHost(host: String): String? {
    if (!host.contains("/.colima/")) return null
    val after = host.substringAfter("/.colima/")
    val profile = after.substringBefore("/")
    return profile.ifEmpty { null }
}
