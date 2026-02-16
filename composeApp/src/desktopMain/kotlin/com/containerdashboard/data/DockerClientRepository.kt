package com.containerdashboard.data

import com.containerdashboard.data.models.Container
import com.containerdashboard.data.models.ContainerPort
import com.containerdashboard.data.models.ContainerStats
import com.containerdashboard.data.models.DockerImage
import com.containerdashboard.data.models.DockerNetwork
import com.containerdashboard.data.models.DockerVersion
import com.containerdashboard.data.models.IPAM
import com.containerdashboard.data.models.IPAMConfig
import com.containerdashboard.data.models.NetworkContainer
import com.containerdashboard.data.models.SystemInfo
import com.containerdashboard.data.models.Volume
import com.containerdashboard.data.repository.DockerRepository
import com.containerdashboard.data.repository.PruneResult
import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.Event
import com.github.dockerjava.api.model.EventType
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Duration
import com.github.dockerjava.api.model.Container as DockerContainer
import com.github.dockerjava.api.model.Image as DockerJavaImage
import com.github.dockerjava.api.model.Network as DockerNetworkModel

class DockerClientRepository(
    private val dockerHost: String = "unix:///var/run/docker.sock",
) : DockerRepository {
    private val logger = LoggerFactory.getLogger(DockerClientRepository::class.java)

    private val config =
        DefaultDockerClientConfig
            .createDefaultConfigBuilder()
            .withDockerHost(dockerHost)
            .build()

    private val httpClient =
        ApacheDockerHttpClient
            .Builder()
            .dockerHost(config.dockerHost)
            .maxConnections(100)
            .connectionTimeout(Duration.ofSeconds(30))
            .responseTimeout(Duration.ofSeconds(45))
            .build()

    private val dockerClient: DockerClient = DockerClientImpl.getInstance(config, httpClient)

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Periodically checks whether the Docker daemon is reachable by pinging it.
     * Emits `true` when Docker is available, `false` otherwise.
     * Uses [distinctUntilChanged] so collectors only receive updates on actual status changes.
     */
    override fun isDockerAvailable(checkIntervalMillis: Long): Flow<Boolean> =
        flow {
            while (true) {
                val available =
                    try {
                        val socketPath = dockerHost.removePrefix("unix://")
                        if (!File(socketPath).exists()) {
                            false
                        } else {
                            // Verify the daemon actually responds
                            dockerClient.pingCmd().exec()
                            true
                        }
                    } catch (e: Exception) {
                        logger.debug("Docker daemon not reachable: {}", e.message)
                        false
                    }
                emit(available)
                delay(checkIntervalMillis)
            }
        }.distinctUntilChanged().flowOn(Dispatchers.IO)

    private val dockerEvents: SharedFlow<Event> =
        callbackFlow {
            val callback =
                object : com.github.dockerjava.api.async.ResultCallback.Adapter<Event>() {
                    override fun onNext(event: Event?) {
                        event?.let { trySend(it) }
                    }

                    override fun onError(throwable: Throwable?) {
                        logger.warn("Docker event stream error", throwable)
                        close()
                    }
                }
            try {
                dockerClient.eventsCmd().exec(callback)
            } catch (e: Exception) {
                logger.error("Failed to start Docker event stream", e)
                close()
            }
            awaitClose {
                try {
                    callback.close()
                } catch (e: Exception) {
                    logger.debug("Error closing event callback: {}", e.message)
                }
            }
        }.shareIn(scope, SharingStarted.Lazily)

    // System
    override suspend fun getSystemInfo(): Result<SystemInfo> =
        withContext(Dispatchers.IO) {
            try {
                val info = dockerClient.infoCmd().exec()
                Result.success(
                    SystemInfo(
                        id = info.id ?: "",
                        containers = info.containers ?: 0,
                        containersRunning = info.containersRunning ?: 0,
                        containersPaused = info.containersPaused ?: 0,
                        containersStopped = info.containersStopped ?: 0,
                        images = info.images ?: 0,
                        driver = info.driver ?: "",
                        memTotal = info.memTotal ?: 0,
                        ncpu = info.ncpu ?: 0,
                        kernelVersion = info.kernelVersion ?: "",
                        operatingSystem = info.operatingSystem ?: "",
                        osType = info.osType ?: "",
                        architecture = info.architecture ?: "",
                        name = info.name ?: "",
                        serverVersion = info.serverVersion ?: "",
                        dockerRootDir = info.dockerRootDir ?: "",
                    ),
                )
            } catch (e: Exception) {
                logger.error("Failed to get system info", e)
                Result.failure(e)
            }
        }

    override suspend fun getVersion(): Result<DockerVersion> =
        withContext(Dispatchers.IO) {
            try {
                val version = dockerClient.versionCmd().exec()
                Result.success(
                    DockerVersion(
                        version = version.version ?: "",
                        apiVersion = version.apiVersion ?: "",
                        minAPIVersion = version.minAPIVersion ?: "",
                        gitCommit = version.gitCommit ?: "",
                        goVersion = version.goVersion ?: "",
                        os = version.operatingSystem ?: "",
                        arch = version.arch ?: "",
                        buildTime = version.buildTime ?: "",
                    ),
                )
            } catch (e: Exception) {
                logger.error("Failed to get Docker version", e)
                Result.failure(e)
            }
        }

    // Containers
    override fun getContainers(all: Boolean): Flow<List<Container>> =
        dockerEvents
            .filter { it.type == EventType.CONTAINER }
            .map {
                try {
                    dockerClient
                        .listContainersCmd()
                        .withShowAll(all)
                        .exec()
                        .map { it.toContainer() }
                } catch (e: Exception) {
                    logger.warn("Failed to list containers: {}", e.message)
                    emptyList()
                }
            }.onStart {
                emit(
                    try {
                        dockerClient
                            .listContainersCmd()
                            .withShowAll(all)
                            .exec()
                            .map { it.toContainer() }
                    } catch (e: Exception) {
                        logger.warn("Failed to list containers on start: {}", e.message)
                        emptyList()
                    },
                )
            }.catch { e ->
                logger.error("Container flow error", e)
                emit(emptyList())
            }.flowOn(Dispatchers.IO)

    override suspend fun getContainer(id: String): Result<Container> =
        withContext(Dispatchers.IO) {
            try {
                val container =
                    dockerClient
                        .listContainersCmd()
                        .withShowAll(true)
                        .withIdFilter(listOf(id))
                        .exec()
                        .firstOrNull()
                        ?.toContainer()
                        ?: throw Exception("Container not found")
                Result.success(container)
            } catch (e: Exception) {
                logger.error("Failed to get container {}", id, e)
                Result.failure(e)
            }
        }

    override suspend fun getContainerLogs(
        id: String,
        tail: Int,
        timestamps: Boolean,
    ): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val logCallback =
                    object : com.github.dockerjava.api.async.ResultCallback.Adapter<com.github.dockerjava.api.model.Frame>() {
                        val logs = StringBuilder()

                        override fun onNext(frame: com.github.dockerjava.api.model.Frame?) {
                            frame?.let {
                                logs.append(String(it.payload ?: ByteArray(0)))
                            }
                        }
                    }

                dockerClient
                    .logContainerCmd(id)
                    .withStdOut(true)
                    .withStdErr(true)
                    .withTail(tail)
                    .withTimestamps(timestamps)
                    .exec(logCallback)
                    .awaitCompletion()

                Result.success(logCallback.logs.toString())
            } catch (e: Exception) {
                logger.error("Failed to get logs for container {}", id, e)
                Result.failure(e)
            }
        }

    override suspend fun startContainer(id: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                dockerClient.startContainerCmd(id).exec()
                logger.info("Started container {}", id)
                Result.success(Unit)
            } catch (e: Exception) {
                logger.error("Failed to start container {}", id, e)
                Result.failure(e)
            }
        }

    override suspend fun stopContainer(id: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                dockerClient.stopContainerCmd(id).exec()
                logger.info("Stopped container {}", id)
                Result.success(Unit)
            } catch (e: Exception) {
                logger.error("Failed to stop container {}", id, e)
                Result.failure(e)
            }
        }

    override suspend fun restartContainer(id: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                dockerClient.restartContainerCmd(id).exec()
                logger.info("Restarted container {}", id)
                Result.success(Unit)
            } catch (e: Exception) {
                logger.error("Failed to restart container {}", id, e)
                Result.failure(e)
            }
        }

    override suspend fun pauseContainer(id: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                dockerClient.pauseContainerCmd(id).exec()
                logger.info("Paused container {}", id)
                Result.success(Unit)
            } catch (e: Exception) {
                logger.error("Failed to pause container {}", id, e)
                Result.failure(e)
            }
        }

    override suspend fun unpauseContainer(id: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                dockerClient.unpauseContainerCmd(id).exec()
                logger.info("Unpaused container {}", id)
                Result.success(Unit)
            } catch (e: Exception) {
                logger.error("Failed to unpause container {}", id, e)
                Result.failure(e)
            }
        }

    override suspend fun removeContainer(
        id: String,
        force: Boolean,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                dockerClient.removeContainerCmd(id).withForce(force).exec()
                logger.info("Removed container {}", id)
                Result.success(Unit)
            } catch (e: Exception) {
                logger.error("Failed to remove container {}", id, e)
                Result.failure(e)
            }
        }

    // Images
    override fun getImages(): Flow<List<DockerImage>> =
        dockerEvents
            .filter { it.type == EventType.IMAGE }
            .map {
                try {
                    dockerClient
                        .listImagesCmd()
                        .withShowAll(true)
                        .exec()
                        .map { it.toDockerImage() }
                } catch (e: Exception) {
                    logger.warn("Failed to list images: {}", e.message)
                    emptyList()
                }
            }.onStart {
                emit(
                    try {
                        dockerClient
                            .listImagesCmd()
                            .withShowAll(true)
                            .exec()
                            .map { it.toDockerImage() }
                    } catch (e: Exception) {
                        logger.warn("Failed to list images on start: {}", e.message)
                        emptyList()
                    },
                )
            }.catch { e ->
                logger.error("Image flow error", e)
                emit(emptyList())
            }.flowOn(Dispatchers.IO)

    override suspend fun getImage(id: String): Result<DockerImage> =
        withContext(Dispatchers.IO) {
            try {
                val image =
                    dockerClient
                        .listImagesCmd()
                        .withImageNameFilter(id)
                        .exec()
                        .firstOrNull()
                        ?.toDockerImage()
                        ?: throw Exception("Image not found")
                Result.success(image)
            } catch (e: Exception) {
                logger.error("Failed to get image {}", id, e)
                Result.failure(e)
            }
        }

    override suspend fun pullImage(
        name: String,
        tag: String,
    ): Flow<String> =
        flow {
            emit("Pulling $name:$tag...")
            try {
                logger.info("Pulling image {}:{}", name, tag)
                dockerClient
                    .pullImageCmd(name)
                    .withTag(tag)
                    .start()
                    .awaitCompletion()
                logger.info("Successfully pulled image {}:{}", name, tag)
                emit("Successfully pulled $name:$tag")
            } catch (e: Exception) {
                logger.error("Failed to pull image {}:{}", name, tag, e)
                emit("Error: ${e.message}")
            }
        }.flowOn(Dispatchers.IO)

    override suspend fun removeImage(
        id: String,
        force: Boolean,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                dockerClient.removeImageCmd(id).withForce(force).exec()
                logger.info("Removed image {}", id)
                Result.success(Unit)
            } catch (e: Exception) {
                logger.error("Failed to remove image {}", id, e)
                Result.failure(e)
            }
        }

    // Volumes
    override fun getVolumes(): Flow<List<Volume>> =
        dockerEvents
            .filter { it.type == EventType.VOLUME }
            .map {
                try {
                    dockerClient
                        .listVolumesCmd()
                        .exec()
                        .volumes
                        ?.map { volume ->
                            Volume(
                                name = volume.name ?: "",
                                driver = volume.driver ?: "local",
                                mountpoint = volume.mountpoint ?: "",
                                scope = "local",
                                labels = volume.labels,
                            )
                        }
                        ?: emptyList()
                } catch (e: Exception) {
                    logger.warn("Failed to list volumes: {}", e.message)
                    emptyList()
                }
            }.onStart {
                emit(
                    try {
                        dockerClient
                            .listVolumesCmd()
                            .exec()
                            .volumes
                            ?.map { volume ->
                                Volume(
                                    name = volume.name ?: "",
                                    driver = volume.driver ?: "local",
                                    mountpoint = volume.mountpoint ?: "",
                                    scope = "local",
                                    labels = volume.labels,
                                )
                            }
                            ?: emptyList()
                    } catch (e: Exception) {
                        logger.warn("Failed to list volumes on start: {}", e.message)
                        emptyList()
                    },
                )
            }.catch { e ->
                logger.error("Volume flow error", e)
                emit(emptyList())
            }.flowOn(Dispatchers.IO)

    override suspend fun getVolume(name: String): Result<Volume> =
        withContext(Dispatchers.IO) {
            try {
                val volume = dockerClient.inspectVolumeCmd(name).exec()
                Result.success(
                    Volume(
                        name = volume.name ?: "",
                        driver = volume.driver ?: "local",
                        mountpoint = volume.mountpoint ?: "",
                        scope = "local",
                    ),
                )
            } catch (e: Exception) {
                logger.error("Failed to get volume {}", name, e)
                Result.failure(e)
            }
        }

    override suspend fun createVolume(
        name: String,
        driver: String,
    ): Result<Volume> =
        withContext(Dispatchers.IO) {
            try {
                val response =
                    dockerClient
                        .createVolumeCmd()
                        .withName(name)
                        .withDriver(driver)
                        .exec()
                logger.info("Created volume {}", name)
                Result.success(
                    Volume(
                        name = response.name ?: name,
                        driver = response.driver ?: driver,
                        mountpoint = response.mountpoint ?: "",
                    ),
                )
            } catch (e: Exception) {
                logger.error("Failed to create volume {}", name, e)
                Result.failure(e)
            }
        }

    override suspend fun removeVolume(name: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                dockerClient.removeVolumeCmd(name).exec()
                logger.info("Removed volume {}", name)
                Result.success(Unit)
            } catch (e: Exception) {
                logger.error("Failed to remove volume {}", name, e)
                Result.failure(e)
            }
        }

    // Networks
    override fun getNetworks(): Flow<List<DockerNetwork>> =
        dockerEvents
            .filter { it.type == EventType.NETWORK }
            .map {
                try {
                    dockerClient
                        .listNetworksCmd()
                        .exec()
                        .map { it.toDockerNetwork() }
                } catch (e: Exception) {
                    logger.warn("Failed to list networks: {}", e.message)
                    emptyList()
                }
            }.onStart {
                emit(
                    try {
                        dockerClient
                            .listNetworksCmd()
                            .exec()
                            .map { it.toDockerNetwork() }
                    } catch (e: Exception) {
                        logger.warn("Failed to list networks on start: {}", e.message)
                        emptyList()
                    },
                )
            }.catch { e ->
                logger.error("Network flow error", e)
                emit(emptyList())
            }.flowOn(Dispatchers.IO)

    override suspend fun getNetwork(id: String): Result<DockerNetwork> =
        withContext(Dispatchers.IO) {
            try {
                val network = dockerClient.inspectNetworkCmd().withNetworkId(id).exec()
                Result.success(
                    DockerNetwork(
                        id = network.id ?: "",
                        name = network.name ?: "",
                        driver = network.driver ?: "bridge",
                        scope = network.scope ?: "local",
                    ),
                )
            } catch (e: Exception) {
                logger.error("Failed to get network {}", id, e)
                Result.failure(e)
            }
        }

    override suspend fun createNetwork(
        name: String,
        driver: String,
    ): Result<DockerNetwork> =
        withContext(Dispatchers.IO) {
            try {
                val response =
                    dockerClient
                        .createNetworkCmd()
                        .withName(name)
                        .withDriver(driver)
                        .exec()
                logger.info("Created network {}", name)
                Result.success(
                    DockerNetwork(
                        id = response.id ?: "",
                        name = name,
                        driver = driver,
                    ),
                )
            } catch (e: Exception) {
                logger.error("Failed to create network {}", name, e)
                Result.failure(e)
            }
        }

    override suspend fun removeNetwork(id: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                dockerClient.removeNetworkCmd(id).exec()
                logger.info("Removed network {}", id)
                Result.success(Unit)
            } catch (e: Exception) {
                logger.error("Failed to remove network {}", id, e)
                Result.failure(e)
            }
        }

    // Stats — reacts to container changes, streams each container's stats via callbackFlow and combines them
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun getContainerStats(refreshRateMillis: Long): Flow<List<ContainerStats>> =
        getContainers(true)
            .distinctUntilChanged()
            .flatMapLatest { containers ->
                val running = containers.filter { it.isRunning }
                if (running.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    combine(
                        running.map { container ->
                            singleContainerStats(container.id, container.displayName, refreshRateMillis)
                        },
                    ) { stats -> stats.toList() }
                }
            }

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun singleContainerStats(
        containerId: String,
        containerName: String,
        refreshRateMillis: Long,
    ): Flow<ContainerStats> =
        callbackFlow<ContainerStats> {
            val callback =
                object : com.github.dockerjava.api.async.ResultCallback.Adapter<com.github.dockerjava.api.model.Statistics>() {
                    override fun onNext(s: com.github.dockerjava.api.model.Statistics?) {
                        s?.let {
                            val cpuPercent = calculateCpuPercent(it)
                            val memUsage = it.memoryStats?.usage ?: 0L
                            val memLimit = it.memoryStats?.limit ?: 0L
                            trySend(
                                ContainerStats(
                                    containerId = containerId,
                                    containerName = containerName,
                                    cpuPercent = cpuPercent,
                                    memoryUsage = memUsage,
                                    memoryLimit = memLimit,
                                ),
                            )
                        }
                    }

                    override fun onError(throwable: Throwable?) {
                        logger.warn("Stats stream error for container {}: {}", containerId, throwable?.message)
                        close()
                    }

                    override fun onComplete() {
                        close()
                    }
                }
            try {
                dockerClient.statsCmd(containerId).withNoStream(false).exec(callback)
            } catch (e: Exception) {
                logger.error("Failed to start stats stream for container {}", containerId, e)
                close()
            }
            awaitClose {
                try {
                    callback.close()
                } catch (e: Exception) {
                    logger.debug("Error closing stats callback: {}", e.message)
                }
            }
        }.catch { e ->
            logger.debug("Stats flow error for container {}: {}", containerId, e.message)
        }.sample(refreshRateMillis)

    private fun calculateCpuPercent(stats: com.github.dockerjava.api.model.Statistics): Double {
        val cpuStats = stats.cpuStats ?: return 0.0
        val preCpuStats = stats.preCpuStats ?: return 0.0

        val cpuDelta = (cpuStats.cpuUsage?.totalUsage ?: 0L) - (preCpuStats.cpuUsage?.totalUsage ?: 0L)
        val systemDelta = (cpuStats.systemCpuUsage ?: 0L) - (preCpuStats.systemCpuUsage ?: 0L)

        if (systemDelta <= 0L || cpuDelta < 0L) return 0.0

        val numCpus = cpuStats.cpuUsage?.percpuUsage?.size ?: cpuStats.onlineCpus?.toInt() ?: 1
        return (cpuDelta.toDouble() / systemDelta.toDouble()) * numCpus * 100.0
    }

    // Prune operations
    override suspend fun pruneContainers(): Result<PruneResult> =
        withContext(Dispatchers.IO) {
            try {
                val response = dockerClient.pruneCmd(com.github.dockerjava.api.model.PruneType.CONTAINERS).exec()
                logger.info("Pruned containers, reclaimed {} bytes", response.spaceReclaimed)
                Result.success(
                    PruneResult(
                        deletedCount = response.spaceReclaimed?.toInt() ?: 0,
                        reclaimedSpace = response.spaceReclaimed ?: 0,
                    ),
                )
            } catch (e: Exception) {
                logger.error("Failed to prune containers", e)
                Result.failure(e)
            }
        }

    override suspend fun pruneImages(): Result<PruneResult> =
        withContext(Dispatchers.IO) {
            try {
                val response = dockerClient.pruneCmd(com.github.dockerjava.api.model.PruneType.IMAGES).exec()
                logger.info("Pruned images, reclaimed {} bytes", response.spaceReclaimed)
                Result.success(
                    PruneResult(
                        deletedCount = response.spaceReclaimed?.toInt() ?: 0,
                        reclaimedSpace = response.spaceReclaimed ?: 0,
                    ),
                )
            } catch (e: Exception) {
                logger.error("Failed to prune images", e)
                Result.failure(e)
            }
        }

    override suspend fun pruneVolumes(): Result<PruneResult> =
        withContext(Dispatchers.IO) {
            try {
                val response = dockerClient.pruneCmd(com.github.dockerjava.api.model.PruneType.VOLUMES).exec()
                logger.info("Pruned volumes, reclaimed {} bytes", response.spaceReclaimed)
                Result.success(
                    PruneResult(
                        deletedCount = response.spaceReclaimed?.toInt() ?: 0,
                        reclaimedSpace = response.spaceReclaimed ?: 0,
                    ),
                )
            } catch (e: Exception) {
                logger.error("Failed to prune volumes", e)
                Result.failure(e)
            }
        }

    override suspend fun pruneNetworks(): Result<PruneResult> =
        withContext(Dispatchers.IO) {
            try {
                dockerClient.pruneCmd(com.github.dockerjava.api.model.PruneType.NETWORKS).exec()
                logger.info("Pruned networks")
                Result.success(
                    PruneResult(
                        deletedCount = 0,
                        reclaimedSpace = 0,
                    ),
                )
            } catch (e: Exception) {
                logger.error("Failed to prune networks", e)
                Result.failure(e)
            }
        }

    fun close() {
        try {
            scope.cancel()
            dockerClient.close()
            httpClient.close()
        } catch (e: Exception) {
            logger.warn("Error during DockerClientRepository shutdown", e)
        }
    }

    // Extension functions to convert Docker Java models to our models
    private fun DockerContainer.toContainer(): Container =
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

    private fun DockerJavaImage.toDockerImage(): DockerImage =
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

    private fun DockerNetworkModel.toDockerNetwork(): DockerNetwork =
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
}
