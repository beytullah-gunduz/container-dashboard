package com.containerdashboard

import com.containerdashboard.data.models.Container
import com.containerdashboard.data.models.ContainerInspect
import com.containerdashboard.data.models.ContainerStats
import com.containerdashboard.data.models.DockerImage
import com.containerdashboard.data.models.DockerNetwork
import com.containerdashboard.data.models.DockerVersion
import com.containerdashboard.data.models.ImageInspect
import com.containerdashboard.data.models.NetworkInspect
import com.containerdashboard.data.models.SystemInfo
import com.containerdashboard.data.models.Volume
import com.containerdashboard.data.models.VolumeInspect
import com.containerdashboard.data.repository.DockerRepository
import com.containerdashboard.data.repository.PruneResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Test double for [DockerRepository].
 *
 * Safe defaults: every [Flow]-returning method returns an empty or single-value
 * flow; every suspend [Result] method returns success. Override individual vars
 * in a test to drive specific failure or data scenarios.
 */
class FakeDockerRepository(
    var systemInfo: SystemInfo = SystemInfo(),
    var containers: List<Container> = emptyList(),
    var containerStats: List<ContainerStats> = emptyList(),
    var images: List<DockerImage> = emptyList(),
    var volumes: List<Volume> = emptyList(),
    var networks: List<DockerNetwork> = emptyList(),
    var dockerVersion: DockerVersion = DockerVersion(),
    // Per-operation result overrides — set to failure to drive error paths.
    var pauseResult: Result<Unit> = Result.success(Unit),
    var unpauseResult: Result<Unit> = Result.success(Unit),
    var restartResult: Result<Unit> = Result.success(Unit),
    var removeContainerResult: Result<Unit> = Result.success(Unit),
    var startResult: Result<Unit> = Result.success(Unit),
    var stopResult: Result<Unit> = Result.success(Unit),
    var removeImageResult: Result<Unit> = Result.success(Unit),
    var removeVolumeResult: Result<Unit> = Result.success(Unit),
    var removeNetworkResult: Result<Unit> = Result.success(Unit),
    var createVolumeResult: Result<Volume>? = null,
    var createNetworkResult: Result<DockerNetwork>? = null,
) : DockerRepository {
    // --- Availability ---

    override fun isDockerAvailable(checkIntervalMillis: Long): Flow<Boolean> = flowOf(true)

    // --- System ---

    override suspend fun getSystemInfo(): Result<SystemInfo> = Result.success(systemInfo)

    override suspend fun getVersion(): Result<DockerVersion> = Result.success(dockerVersion)

    // --- Containers ---

    override fun getContainers(all: Boolean): Flow<List<Container>> = flowOf(containers)

    override suspend fun refreshContainers() = Unit

    override suspend fun refreshImages() = Unit

    override suspend fun refreshVolumes() = Unit

    override suspend fun refreshNetworks() = Unit

    override suspend fun getContainer(id: String): Result<Container> =
        containers
            .find { it.id == id }
            ?.let { Result.success(it) }
            ?: Result.failure(NoSuchElementException("Container $id not found"))

    override suspend fun inspectContainer(id: String): Result<ContainerInspect> =
        Result.success(
            ContainerInspect(
                id = id,
                name = "fake-container",
                image = "fake-image",
                imageId = "",
                status = "running",
                state = "running",
                createdAt = "",
                startedAt = "",
                command = "",
                entrypoint = emptyList(),
                workingDir = "",
                user = "",
                restartPolicy = "",
                hostname = "",
                platform = "",
                environment = emptyList(),
                mounts = emptyList(),
                ports = emptyList(),
                networks = emptyList(),
                labels = emptyMap(),
                rawJson = "{}",
            ),
        )

    override suspend fun getContainerLogs(
        id: String,
        tail: Int,
        timestamps: Boolean,
    ): Result<String> = Result.success("")

    override fun followContainerLogs(
        id: String,
        tail: Int,
    ): Flow<List<String>> = flowOf(emptyList())

    override fun followMultipleContainerLogs(
        containers: List<Pair<String, String>>,
        tail: Int,
    ): Flow<List<String>> = flowOf(emptyList())

    override suspend fun startContainer(id: String): Result<Unit> = startResult

    override suspend fun stopContainer(id: String): Result<Unit> = stopResult

    override suspend fun restartContainer(id: String): Result<Unit> = restartResult

    override suspend fun pauseContainer(id: String): Result<Unit> = pauseResult

    override suspend fun unpauseContainer(id: String): Result<Unit> = unpauseResult

    override suspend fun removeContainer(
        id: String,
        force: Boolean,
    ): Result<Unit> = removeContainerResult

    // --- Images ---

    override fun getImages(): Flow<List<DockerImage>> = flowOf(images)

    override suspend fun getImage(id: String): Result<DockerImage> =
        images
            .find { it.id == id }
            ?.let { Result.success(it) }
            ?: Result.failure(NoSuchElementException("Image $id not found"))

    override suspend fun inspectImage(id: String): Result<ImageInspect> =
        Result.success(
            ImageInspect(
                id = id,
                shortId = id.take(12),
                repoTags = emptyList(),
                repoDigests = emptyList(),
                architecture = "",
                os = "",
                size = 0,
                virtualSize = 0,
                createdAt = "",
                dockerVersion = "",
                author = "",
                entrypoint = emptyList(),
                command = emptyList(),
                workingDir = "",
                user = "",
                exposedPorts = emptyList(),
                environment = emptyList(),
                labels = emptyMap(),
                layers = emptyList(),
                rawJson = "{}",
            ),
        )

    override suspend fun pullImage(
        name: String,
        tag: String,
    ): Flow<String> = flowOf("Pulled $name:$tag")

    override suspend fun removeImage(
        id: String,
        force: Boolean,
    ): Result<Unit> = removeImageResult

    // --- Volumes ---

    override fun getVolumes(): Flow<List<Volume>> = flowOf(volumes)

    override suspend fun getVolume(name: String): Result<Volume> =
        volumes
            .find { it.name == name }
            ?.let { Result.success(it) }
            ?: Result.failure(NoSuchElementException("Volume $name not found"))

    override suspend fun inspectVolume(name: String): Result<VolumeInspect> =
        Result.success(
            VolumeInspect(
                name = name,
                driver = "local",
                mountpoint = "",
                scope = "local",
                createdAt = "",
                options = emptyMap(),
                labels = emptyMap(),
                rawJson = "{}",
            ),
        )

    override suspend fun createVolume(
        name: String,
        driver: String,
    ): Result<Volume> = createVolumeResult ?: Result.success(Volume(name = name, driver = driver))

    override suspend fun removeVolume(name: String): Result<Unit> = removeVolumeResult

    // --- Networks ---

    override fun getNetworks(): Flow<List<DockerNetwork>> = flowOf(networks)

    override suspend fun getNetwork(id: String): Result<DockerNetwork> =
        networks
            .find { it.id == id }
            ?.let { Result.success(it) }
            ?: Result.failure(NoSuchElementException("Network $id not found"))

    override suspend fun inspectNetwork(id: String): Result<NetworkInspect> =
        Result.success(
            NetworkInspect(
                id = id,
                shortId = id.take(12),
                name = "fake-network",
                driver = "bridge",
                scope = "local",
                attachable = false,
                ingress = false,
                internal = false,
                ipv6Enabled = false,
                createdAt = "",
                ipamDriver = "default",
                ipamConfig = emptyList(),
                options = emptyMap(),
                labels = emptyMap(),
                attachedContainers = emptyList(),
                rawJson = "{}",
            ),
        )

    override suspend fun createNetwork(
        name: String,
        driver: String,
    ): Result<DockerNetwork> = createNetworkResult ?: Result.success(DockerNetwork(id = "fake-id", name = name, driver = driver))

    override suspend fun removeNetwork(id: String): Result<Unit> = removeNetworkResult

    // --- Stats ---

    override fun getContainerStats(): Flow<List<ContainerStats>> = flowOf(containerStats)

    // --- Prune ---

    override suspend fun pruneContainers(): Result<PruneResult> = Result.success(PruneResult(0))

    override suspend fun pruneImages(): Result<PruneResult> = Result.success(PruneResult(0))

    override suspend fun pruneVolumes(): Result<PruneResult> = Result.success(PruneResult(0))

    override suspend fun pruneNetworks(): Result<PruneResult> = Result.success(PruneResult(0))

    // --- Lifecycle ---

    override fun close() = Unit
}
