package com.containerdashboard.data

import java.io.File

data class DockerHostPreset(
    val socketPath: String,
    val label: String,
) {
    val uri: String get() = "unix://$socketPath"
}

object DockerHostConfig {
    private val home: String = System.getProperty("user.home")

    private val staticPresets: List<DockerHostPreset> =
        listOf(
            DockerHostPreset("/var/run/docker.sock", "Docker Desktop / standard"),
            DockerHostPreset("$home/.colima/default/docker.sock", "Colima"),
            DockerHostPreset("$home/.docker/run/docker.sock", "Docker Desktop (newer)"),
            DockerHostPreset("$home/.orbstack/run/docker.sock", "OrbStack"),
            DockerHostPreset("$home/.lima/default/sock/docker.sock", "Lima"),
            DockerHostPreset("$home/.rd/docker.sock", "Rancher Desktop"),
        )

    private fun colimaProfilePresets(): List<DockerHostPreset> {
        val colimaDir = File("$home/.colima")
        val profiles = colimaDir.takeIf { it.isDirectory }?.listFiles { f -> f.isDirectory } ?: return emptyList()
        return profiles
            .filter { it.name != "default" && File(it, "docker.sock").exists() }
            .map { DockerHostPreset("${it.absolutePath}/docker.sock", "Colima (${it.name})") }
    }

    val presets: List<DockerHostPreset>
        get() {
            val dynamic = colimaProfilePresets()
            val colimaDefaultIdx = staticPresets.indexOfFirst { it.socketPath.contains("/.colima/default/") }
            return if (dynamic.isEmpty() || colimaDefaultIdx < 0) {
                staticPresets + dynamic
            } else {
                staticPresets.subList(0, colimaDefaultIdx + 1) + dynamic + staticPresets.subList(colimaDefaultIdx + 1, staticPresets.size)
            }
        }

    val fallbackUri: String = "unix:///var/run/docker.sock"

    fun detectDockerHost(): String {
        val socket = presets.firstOrNull { File(it.socketPath).exists() }
        return socket?.uri ?: fallbackUri
    }
}
