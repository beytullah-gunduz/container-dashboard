package com.containerdashboard.data.repository

import com.containerdashboard.data.models.AttachedContainer
import com.containerdashboard.data.models.Container
import com.containerdashboard.data.models.ContainerInspect
import com.containerdashboard.data.models.ContainerPort
import com.containerdashboard.data.models.DockerImage
import com.containerdashboard.data.models.DockerNetwork
import com.containerdashboard.data.models.EnvVar
import com.containerdashboard.data.models.IPAM
import com.containerdashboard.data.models.IPAMConfig
import com.containerdashboard.data.models.ImageInspect
import com.containerdashboard.data.models.IpamConfigEntry
import com.containerdashboard.data.models.MountInfo
import com.containerdashboard.data.models.NetworkAttachment
import com.containerdashboard.data.models.NetworkContainer
import com.containerdashboard.data.models.NetworkInspect
import com.containerdashboard.data.models.PortMapping
import com.containerdashboard.data.models.VolumeInspect
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import org.slf4j.LoggerFactory
import com.github.dockerjava.api.model.Container as DockerContainer
import com.github.dockerjava.api.model.Image as DockerJavaImage
import com.github.dockerjava.api.model.Network as DockerNetworkModel

// Pure docker-java → app-model mapping functions extracted from DesktopDockerRepository so
// they are unit-testable with fixed docker-java response objects.

private val mapperLogger = LoggerFactory.getLogger("com.containerdashboard.data.repository.DockerJavaMappers")

private val inspectJsonMapper: ObjectMapper =
    ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)

internal fun toPrettyJson(obj: Any): String =
    try {
        inspectJsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj)
    } catch (e: Exception) {
        mapperLogger.warn("Failed to serialize docker-java response to JSON: {}", e.message)
        obj.toString()
    }

internal fun com.github.dockerjava.api.command.InspectContainerResponse.toContainerInspect(): ContainerInspect {
    val config = this.config
    val hostConfig = this.hostConfig
    val netSettings = this.networkSettings
    val state = this.state

    val statusText = state?.status ?: ""
    val stateText =
        when {
            state?.running == true -> "running"
            state?.paused == true -> "paused"
            state?.restarting == true -> "restarting"
            state?.dead == true -> "dead"
            !statusText.isNullOrBlank() -> statusText
            else -> "unknown"
        }

    val envPairs =
        config?.env?.map { entry ->
            val idx = entry.indexOf('=')
            if (idx >= 0) {
                EnvVar(entry.substring(0, idx), entry.substring(idx + 1))
            } else {
                EnvVar(entry, "")
            }
        } ?: emptyList()

    val mounts =
        this.mounts?.map { mount ->
            MountInfo(
                type = if (!mount.name.isNullOrBlank()) "volume" else "bind",
                source = mount.source ?: "",
                destination = mount.destination?.path ?: "",
                mode = mount.mode ?: "",
                rw = mount.rw ?: true,
            )
        } ?: emptyList()

    val hostBindings = hostConfig?.portBindings?.bindings ?: emptyMap()
    val exposedBindings = netSettings?.ports?.bindings ?: emptyMap()
    val allBindingKeys = (hostBindings.keys + exposedBindings.keys).distinct()

    val portMappings =
        allBindingKeys.flatMap { exposed ->
            val bindings = exposedBindings[exposed] ?: hostBindings[exposed]
            if (bindings == null || bindings.isEmpty()) {
                listOf(
                    PortMapping(
                        containerPort = exposed.port,
                        hostPort = null,
                        protocol = exposed.protocol?.name?.lowercase() ?: "tcp",
                        hostIp = null,
                    ),
                )
            } else {
                bindings.map { binding ->
                    PortMapping(
                        containerPort = exposed.port,
                        hostPort = binding.hostPortSpec?.toIntOrNull(),
                        protocol = exposed.protocol?.name?.lowercase() ?: "tcp",
                        hostIp = binding.hostIp,
                    )
                }
            }
        }

    val networkAttachments =
        netSettings?.networks?.entries?.map { (netName, net) ->
            NetworkAttachment(
                name = netName,
                ipAddress = net.ipAddress ?: "",
                gateway = net.gateway ?: "",
                macAddress = net.macAddress ?: "",
                aliases = net.aliases ?: emptyList(),
            )
        } ?: emptyList()

    val restartPolicyText =
        hostConfig?.restartPolicy?.let { rp ->
            val base = rp.name.orEmpty().ifBlank { "no" }
            val retries = rp.maximumRetryCount ?: 0
            if (base == "on-failure" && retries > 0) "$base:$retries" else base
        } ?: "no"

    val entrypointList = config?.entrypoint?.toList().orEmpty()
    val cmdText =
        config
            ?.cmd
            ?.joinToString(" ")
            .orEmpty()
            .ifBlank {
                this.path.orEmpty() +
                    (
                        this.args
                            ?.takeIf { it.isNotEmpty() }
                            ?.joinToString(" ", prefix = " ")
                            .orEmpty()
                    )
            }.trim()

    val displayName = (this.name ?: "").removePrefix("/")

    return ContainerInspect(
        id = this.id ?: "",
        name = displayName,
        image = config?.image ?: "",
        imageId = this.imageId ?: "",
        status = statusText,
        state = stateText,
        createdAt = this.created ?: "",
        startedAt = state?.startedAt ?: "",
        command = cmdText,
        entrypoint = entrypointList,
        workingDir = config?.workingDir ?: "",
        user = config?.user ?: "",
        restartPolicy = restartPolicyText,
        hostname = config?.hostName ?: "",
        platform = this.platform ?: "",
        environment = envPairs,
        mounts = mounts,
        ports = portMappings,
        networks = networkAttachments,
        labels = config?.labels ?: emptyMap(),
        rawJson = toPrettyJson(this),
    )
}

internal fun com.github.dockerjava.api.command.InspectImageResponse.toImageInspect(): ImageInspect {
    val config = this.config
    val envPairs =
        config?.env?.map { entry ->
            val idx = entry.indexOf('=')
            if (idx >= 0) {
                EnvVar(entry.substring(0, idx), entry.substring(idx + 1))
            } else {
                EnvVar(entry, "")
            }
        } ?: emptyList()

    val exposedPortStrings =
        config
            ?.exposedPorts
            ?.map { port ->
                val proto = port?.protocol?.name?.lowercase() ?: "tcp"
                "${port?.port ?: 0}/$proto"
            }.orEmpty()

    val rawId = this.id ?: ""
    val shortId = rawId.removePrefix("sha256:").take(12)

    val layerDigests = this.rootFS?.layers.orEmpty()

    return ImageInspect(
        id = rawId,
        shortId = shortId,
        repoTags = this.repoTags.orEmpty(),
        repoDigests = this.repoDigests.orEmpty(),
        architecture = this.arch ?: "",
        os = this.os ?: "",
        size = this.size ?: 0L,
        virtualSize = this.virtualSize ?: 0L,
        createdAt = this.created ?: "",
        dockerVersion = this.dockerVersion ?: "",
        author = this.author ?: "",
        entrypoint = config?.entrypoint?.toList().orEmpty(),
        command = config?.cmd?.toList().orEmpty(),
        workingDir = config?.workingDir ?: "",
        user = config?.user ?: "",
        exposedPorts = exposedPortStrings,
        environment = envPairs,
        labels = config?.labels ?: emptyMap(),
        layers = layerDigests,
        rawJson = toPrettyJson(this),
    )
}

internal fun com.github.dockerjava.api.model.Network.toNetworkInspect(): NetworkInspect {
    val rawId = this.id ?: ""
    val shortId = rawId.take(12)

    val ipam = this.ipam
    val ipamEntries =
        ipam?.config?.map { entry ->
            IpamConfigEntry(
                subnet = entry.subnet ?: "",
                gateway = entry.gateway ?: "",
                ipRange = entry.ipRange ?: "",
            )
        } ?: emptyList()

    val attached =
        this.containers?.entries?.map { (cid, info) ->
            AttachedContainer(
                id = cid,
                name = info?.name ?: "",
                ipv4Address = info?.ipv4Address ?: "",
                ipv6Address = info?.ipv6Address ?: "",
                macAddress = info?.macAddress ?: "",
            )
        } ?: emptyList()

    return NetworkInspect(
        id = rawId,
        shortId = shortId,
        name = this.name ?: "",
        driver = this.driver ?: "",
        scope = this.scope ?: "",
        attachable = this.isAttachable ?: false,
        ingress = false,
        internal = this.getInternal() ?: false,
        ipv6Enabled = this.enableIPv6 ?: false,
        createdAt = "",
        ipamDriver = ipam?.driver ?: "",
        ipamConfig = ipamEntries,
        options = this.options ?: emptyMap(),
        labels = this.labels ?: emptyMap(),
        attachedContainers = attached,
        rawJson = toPrettyJson(this),
    )
}

internal fun com.github.dockerjava.api.command.InspectVolumeResponse.toVolumeInspect(): VolumeInspect =
    VolumeInspect(
        name = this.name ?: "",
        driver = this.driver ?: "",
        mountpoint = this.mountpoint ?: "",
        // docker-java 3.3.4's typed InspectVolumeResponse exposes neither Scope nor
        // CreatedAt; both are present in the raw response map its deserializer attaches to
        // every DockerObject, so read them from there (empty when the daemon omits them).
        scope = this.rawValues["Scope"] as? String ?: "",
        createdAt = this.rawValues["CreatedAt"] as? String ?: "",
        options = this.options ?: emptyMap(),
        labels = this.labels ?: emptyMap(),
        rawJson = toPrettyJson(this),
    )

// Extension functions to convert Docker Java models to our models
internal fun DockerContainer.toContainer(): Container =
    Container(
        id = this.id ?: "",
        names = this.names?.toList() ?: emptyList(),
        image = this.image ?: "",
        imageId = this.imageId ?: "",
        command = this.command ?: "",
        created = this.created ?: 0,
        state = this.state ?: "unknown",
        status = this.status ?: "",
        ports =
            this.ports?.map { port ->
                ContainerPort(
                    ip = port.ip,
                    privatePort = port.privatePort ?: 0,
                    publicPort = port.publicPort,
                    type = port.type ?: "tcp",
                )
            } ?: emptyList(),
        labels = this.labels ?: emptyMap(),
    )

internal fun DockerJavaImage.toDockerImage(): DockerImage =
    DockerImage(
        id = this.id ?: "",
        parentId = this.parentId ?: "",
        repoTags = this.repoTags?.toList(),
        repoDigests = this.repoDigests?.toList(),
        created = this.created ?: 0,
        size = this.size ?: 0,
        virtualSize = this.virtualSize ?: 0,
        labels = this.labels,
    )

internal fun DockerNetworkModel.toDockerNetwork(): DockerNetwork =
    DockerNetwork(
        id = this.id ?: "",
        name = this.name ?: "",
        driver = this.driver ?: "bridge",
        scope = this.scope ?: "local",
        internal = this.internal ?: false,
        attachable = this.isAttachable ?: false,
        ipam =
            this.ipam?.let { ipam ->
                IPAM(
                    driver = ipam.driver ?: "default",
                    config =
                        ipam.config?.map { config ->
                            IPAMConfig(
                                subnet = config.subnet,
                                gateway = config.gateway,
                                ipRange = config.ipRange,
                            )
                        },
                )
            },
        labels = this.labels,
        containers =
            this.containers?.mapValues { (_, container) ->
                NetworkContainer(
                    name = container.name,
                    endpointId = container.endpointId ?: "",
                    macAddress = container.macAddress ?: "",
                    ipv4Address = container.ipv4Address ?: "",
                    ipv6Address = container.ipv6Address ?: "",
                )
            },
    )
