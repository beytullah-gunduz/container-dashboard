package com.containerdashboard.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DockerVersion(
    @SerialName("Version")
    val version: String = "",
    @SerialName("ApiVersion")
    val apiVersion: String = "",
    @SerialName("MinAPIVersion")
    val minAPIVersion: String = "",
    @SerialName("GitCommit")
    val gitCommit: String = "",
    @SerialName("GoVersion")
    val goVersion: String = "",
    @SerialName("Os")
    val os: String = "",
    @SerialName("Arch")
    val arch: String = "",
    @SerialName("BuildTime")
    val buildTime: String = ""
)
