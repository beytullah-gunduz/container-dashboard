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

    val presets: List<DockerHostPreset> =
        listOf(
            DockerHostPreset("/var/run/docker.sock", "Docker Desktop / standard"),
            DockerHostPreset("$home/.colima/default/docker.sock", "Colima"),
            DockerHostPreset("$home/.docker/run/docker.sock", "Docker Desktop (newer)"),
            DockerHostPreset("$home/.orbstack/run/docker.sock", "OrbStack"),
            DockerHostPreset("$home/.lima/default/sock/docker.sock", "Lima"),
            DockerHostPreset("$home/.rd/docker.sock", "Rancher Desktop"),
        )

    val fallbackUri: String = "unix:///var/run/docker.sock"

    fun detectDockerHost(): String {
        val socket = presets.firstOrNull { File(it.socketPath).exists() }
        return socket?.uri ?: fallbackUri
    }
}
