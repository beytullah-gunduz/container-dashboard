package com.containerdashboard.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SystemInfo(
    @SerialName("ID")
    val id: String = "",
    @SerialName("Containers")
    val containers: Int = 0,
    @SerialName("ContainersRunning")
    val containersRunning: Int = 0,
    @SerialName("ContainersPaused")
    val containersPaused: Int = 0,
    @SerialName("ContainersStopped")
    val containersStopped: Int = 0,
    @SerialName("Images")
    val images: Int = 0,
    @SerialName("Driver")
    val driver: String = "",
    @SerialName("MemTotal")
    val memTotal: Long = 0,
    @SerialName("NCPU")
    val ncpu: Int = 0,
    @SerialName("KernelVersion")
    val kernelVersion: String = "",
    @SerialName("OperatingSystem")
    val operatingSystem: String = "",
    @SerialName("OSType")
    val osType: String = "",
    @SerialName("Architecture")
    val architecture: String = "",
    @SerialName("Name")
    val name: String = "",
    @SerialName("ServerVersion")
    val serverVersion: String = "",
    @SerialName("DockerRootDir")
    val dockerRootDir: String = ""
) {
    val formattedMemory: String
        get() = DockerImage.formatBytes(memTotal)
}
