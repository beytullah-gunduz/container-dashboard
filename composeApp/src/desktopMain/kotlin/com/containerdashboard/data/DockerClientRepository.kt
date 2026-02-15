package com.containerdashboard.data

import com.containerdashboard.data.models.*
import com.containerdashboard.data.repository.DockerRepository
import com.containerdashboard.data.repository.PruneResult
import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.Container as DockerContainer
import com.github.dockerjava.api.model.Event
import com.github.dockerjava.api.model.EventType
import com.github.dockerjava.api.model.Image as DockerJavaImage
import com.github.dockerjava.api.model.Network as DockerNetworkModel
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Duration

class DockerClientRepository(
    private val dockerHost: String = "unix:///var/run/docker.sock"
) : DockerRepository {
    
    private val config = DefaultDockerClientConfig.createDefaultConfigBuilder()
        .withDockerHost(dockerHost)
        .build()
    
    private val httpClient = ApacheDockerHttpClient.Builder()
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
    override fun isDockerAvailable(checkIntervalMillis: Long): Flow<Boolean> = flow {
        while (true) {
            val available = try {
                val socketPath = dockerHost.removePrefix("unix://")
                if (!File(socketPath).exists()) {
                    false
                } else {
                    // Verify the daemon actually responds
                    dockerClient.pingCmd().exec()
                    true
                }
            } catch (_: Exception) {
                false
            }
            emit(available)
            delay(checkIntervalMillis)
        }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    private val dockerEvents: SharedFlow<Event> = callbackFlow {
        val callback = object : com.github.dockerjava.api.async.ResultCallback.Adapter<Event>() {
            override fun onNext(event: Event?) {
                event?.let { trySend(it) }
            }
            override fun onError(throwable: Throwable?) {
                // Don't propagate – the flow simply stops producing events
                close()
            }
        }
        try {
            dockerClient.eventsCmd().exec(callback)
        } catch (_: Exception) {
            close()
        }
        awaitClose {
            try { callback.close() } catch (_: Exception) {}
        }
    }.shareIn(scope, SharingStarted.Lazily)
    
    // System
    override suspend fun getSystemInfo(): Result<SystemInfo> = withContext(Dispatchers.IO) {
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
                    dockerRootDir = info.dockerRootDir ?: ""
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getVersion(): Result<DockerVersion> = withContext(Dispatchers.IO) {
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
                    buildTime = version.buildTime ?: ""
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Containers
    override fun getContainers(all: Boolean): Flow<List<Container>> = dockerEvents
        .filter { it.type == EventType.CONTAINER }
        .map {
            try {
                dockerClient.listContainersCmd()
                    .withShowAll(all)
                    .exec()
                    .map { it.toContainer() }
            } catch (_: Exception) {
                emptyList()
            }
        }
        .onStart {
            emit(
                try {
                    dockerClient.listContainersCmd()
                        .withShowAll(all)
                        .exec()
                        .map { it.toContainer() }
                } catch (_: Exception) {
                    emptyList()
                }
            )
        }
        .catch { emit(emptyList()) }
        .flowOn(Dispatchers.IO)

    
    override suspend fun getContainer(id: String): Result<Container> = withContext(Dispatchers.IO) {
        try {
            val container = dockerClient.listContainersCmd()
                .withShowAll(true)
                .withIdFilter(listOf(id))
                .exec()
                .firstOrNull()
                ?.toContainer()
                ?: throw Exception("Container not found")
            Result.success(container)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getContainerLogs(id: String, tail: Int, timestamps: Boolean): Result<String> = withContext(Dispatchers.IO) {
        try {
            val logCallback = object : com.github.dockerjava.api.async.ResultCallback.Adapter<com.github.dockerjava.api.model.Frame>() {
                val logs = StringBuilder()
                
                override fun onNext(frame: com.github.dockerjava.api.model.Frame?) {
                    frame?.let {
                        logs.append(String(it.payload ?: ByteArray(0)))
                    }
                }
            }
            
            dockerClient.logContainerCmd(id)
                .withStdOut(true)
                .withStdErr(true)
                .withTail(tail)
                .withTimestamps(timestamps)
                .exec(logCallback)
                .awaitCompletion()
            
            Result.success(logCallback.logs.toString())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun startContainer(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            dockerClient.startContainerCmd(id).exec()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun stopContainer(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            dockerClient.stopContainerCmd(id).exec()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun restartContainer(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            dockerClient.restartContainerCmd(id).exec()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun pauseContainer(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            dockerClient.pauseContainerCmd(id).exec()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun unpauseContainer(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            dockerClient.unpauseContainerCmd(id).exec()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun removeContainer(id: String, force: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            dockerClient.removeContainerCmd(id).withForce(force).exec()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Images
    override fun getImages(): Flow<List<DockerImage>> = dockerEvents
        .filter { it.type == EventType.IMAGE }
        .map {
            try {
                dockerClient.listImagesCmd()
                    .withShowAll(true)
                    .exec()
                    .map { it.toDockerImage() }
            } catch (_: Exception) {
                emptyList()
            }
        }
        .onStart {
            emit(
                try {
                    dockerClient.listImagesCmd()
                        .withShowAll(true)
                        .exec()
                        .map { it.toDockerImage() }
                } catch (_: Exception) {
                    emptyList()
                }
            )
        }
        .catch { emit(emptyList()) }
        .flowOn(Dispatchers.IO)
    
    override suspend fun getImage(id: String): Result<DockerImage> = withContext(Dispatchers.IO) {
        try {
            val image = dockerClient.listImagesCmd()
                .withImageNameFilter(id)
                .exec()
                .firstOrNull()
                ?.toDockerImage()
                ?: throw Exception("Image not found")
            Result.success(image)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun pullImage(name: String, tag: String): Flow<String> = flow {
        emit("Pulling $name:$tag...")
        try {
            dockerClient.pullImageCmd(name)
                .withTag(tag)
                .start()
                .awaitCompletion()
            emit("Successfully pulled $name:$tag")
        } catch (e: Exception) {
            emit("Error: ${e.message}")
        }
    }.flowOn(Dispatchers.IO)
    
    override suspend fun removeImage(id: String, force: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            dockerClient.removeImageCmd(id).withForce(force).exec()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Volumes
    override fun getVolumes(): Flow<List<Volume>> = dockerEvents
        .filter { it.type == EventType.VOLUME }
        .map {
            try {
                dockerClient.listVolumesCmd()
                    .exec()
                    .volumes
                    ?.map { volume ->
                        Volume(
                            name = volume.name ?: "",
                            driver = volume.driver ?: "local",
                            mountpoint = volume.mountpoint ?: "",
                            scope = "local",
                            labels = volume.labels
                        )
                    }
                    ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }
        .onStart {
            emit(
                try {
                    dockerClient.listVolumesCmd()
                        .exec()
                        .volumes
                        ?.map { volume ->
                            Volume(
                                name = volume.name ?: "",
                                driver = volume.driver ?: "local",
                                mountpoint = volume.mountpoint ?: "",
                                scope = "local",
                                labels = volume.labels
                            )
                        }
                        ?: emptyList()
                } catch (_: Exception) {
                    emptyList()
                }
            )
        }
        .catch { emit(emptyList()) }
        .flowOn(Dispatchers.IO)
    
    override suspend fun getVolume(name: String): Result<Volume> = withContext(Dispatchers.IO) {
        try {
            val volume = dockerClient.inspectVolumeCmd(name).exec()
            Result.success(
                Volume(
                    name = volume.name ?: "",
                    driver = volume.driver ?: "local",
                    mountpoint = volume.mountpoint ?: "",
                    scope = "local"
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun createVolume(name: String, driver: String): Result<Volume> = withContext(Dispatchers.IO) {
        try {
            val response = dockerClient.createVolumeCmd()
                .withName(name)
                .withDriver(driver)
                .exec()
            Result.success(
                Volume(
                    name = response.name ?: name,
                    driver = response.driver ?: driver,
                    mountpoint = response.mountpoint ?: ""
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun removeVolume(name: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            dockerClient.removeVolumeCmd(name).exec()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Networks
    override fun getNetworks(): Flow<List<DockerNetwork>> = dockerEvents
        .filter { it.type == EventType.NETWORK }
        .map {
            try {
                dockerClient.listNetworksCmd()
                    .exec()
                    .map { it.toDockerNetwork() }
            } catch (_: Exception) {
                emptyList()
            }
        }
        .onStart {
            emit(
                try {
                    dockerClient.listNetworksCmd()
                        .exec()
                        .map { it.toDockerNetwork() }
                } catch (_: Exception) {
                    emptyList()
                }
            )
        }
        .catch { emit(emptyList()) }
        .flowOn(Dispatchers.IO)
    
    override suspend fun getNetwork(id: String): Result<DockerNetwork> = withContext(Dispatchers.IO) {
        try {
            val network = dockerClient.inspectNetworkCmd().withNetworkId(id).exec()
            Result.success(
                DockerNetwork(
                    id = network.id ?: "",
                    name = network.name ?: "",
                    driver = network.driver ?: "bridge",
                    scope = network.scope ?: "local"
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun createNetwork(name: String, driver: String): Result<DockerNetwork> = withContext(Dispatchers.IO) {
        try {
            val response = dockerClient.createNetworkCmd()
                .withName(name)
                .withDriver(driver)
                .exec()
            Result.success(
                DockerNetwork(
                    id = response.id ?: "",
                    name = name,
                    driver = driver
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun removeNetwork(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            dockerClient.removeNetworkCmd(id).exec()
            Result.success(Unit)
        } catch (e: Exception) {
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
                    }
                ) { stats -> stats.toList() }
            }
        }

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun singleContainerStats(containerId: String, containerName: String, refreshRateMillis: Long): Flow<ContainerStats> = callbackFlow<ContainerStats> {
        val callback = object : com.github.dockerjava.api.async.ResultCallback.Adapter<com.github.dockerjava.api.model.Statistics>() {
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
                            memoryLimit = memLimit
                        )
                    )
                }
            }

            override fun onError(throwable: Throwable?) {
                close()
            }

            override fun onComplete() {
                close()
            }
        }
        try {
            dockerClient.statsCmd(containerId).withNoStream(false).exec(callback)
        } catch (_: Exception) {
            close()
        }
        awaitClose {
            try { callback.close() } catch (_: Exception) {}
        }
    }.catch { /* silently recover */ }.sample(refreshRateMillis)

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
    override suspend fun pruneContainers(): Result<PruneResult> = withContext(Dispatchers.IO) {
        try {
            val response = dockerClient.pruneCmd(com.github.dockerjava.api.model.PruneType.CONTAINERS).exec()
            Result.success(PruneResult(
                deletedCount = response.spaceReclaimed?.toInt() ?: 0,
                reclaimedSpace = response.spaceReclaimed ?: 0
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun pruneImages(): Result<PruneResult> = withContext(Dispatchers.IO) {
        try {
            val response = dockerClient.pruneCmd(com.github.dockerjava.api.model.PruneType.IMAGES).exec()
            Result.success(PruneResult(
                deletedCount = response.spaceReclaimed?.toInt() ?: 0,
                reclaimedSpace = response.spaceReclaimed ?: 0
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun pruneVolumes(): Result<PruneResult> = withContext(Dispatchers.IO) {
        try {
            val response = dockerClient.pruneCmd(com.github.dockerjava.api.model.PruneType.VOLUMES).exec()
            Result.success(PruneResult(
                deletedCount = response.spaceReclaimed?.toInt() ?: 0,
                reclaimedSpace = response.spaceReclaimed ?: 0
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun pruneNetworks(): Result<PruneResult> = withContext(Dispatchers.IO) {
        try {
            dockerClient.pruneCmd(com.github.dockerjava.api.model.PruneType.NETWORKS).exec()
            Result.success(PruneResult(
                deletedCount = 0,
                reclaimedSpace = 0
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun close() {
        try {
            scope.cancel()
            dockerClient.close()
            httpClient.close()
        } catch (e: Exception) {
            // Ignore close errors
        }
    }
    
    // Extension functions to convert Docker Java models to our models
    private fun DockerContainer.toContainer(): Container {
        return Container(
            id = this.id ?: "",
            names = this.names?.toList() ?: emptyList(),
            image = this.image ?: "",
            imageId = this.imageId ?: "",
            command = this.command ?: "",
            created = this.created ?: 0,
            state = this.state ?: "unknown",
            status = this.status ?: "",
            ports = this.ports?.map { port ->
                ContainerPort(
                    ip = port.ip,
                    privatePort = port.privatePort ?: 0,
                    publicPort = port.publicPort,
                    type = port.type ?: "tcp"
                )
            } ?: emptyList(),
            labels = this.labels ?: emptyMap()
        )
    }
    
    private fun DockerJavaImage.toDockerImage(): DockerImage {
        return DockerImage(
            id = this.id ?: "",
            parentId = this.parentId ?: "",
            repoTags = this.repoTags?.toList(),
            repoDigests = this.repoDigests?.toList(),
            created = this.created ?: 0,
            size = this.size ?: 0,
            virtualSize = this.virtualSize ?: 0,
            labels = this.labels
        )
    }
    
    private fun DockerNetworkModel.toDockerNetwork(): DockerNetwork {
        return DockerNetwork(
            id = this.id ?: "",
            name = this.name ?: "",
            driver = this.driver ?: "bridge",
            scope = this.scope ?: "local",
            internal = this.internal ?: false,
            attachable = this.isAttachable ?: false,
            ipam = this.ipam?.let { ipam ->
                IPAM(
                    driver = ipam.driver ?: "default",
                    config = ipam.config?.map { config ->
                        IPAMConfig(
                            subnet = config.subnet,
                            gateway = config.gateway,
                            ipRange = config.ipRange
                        )
                    }
                )
            },
            labels = this.labels,
            containers = this.containers?.mapValues { (_, container) ->
                NetworkContainer(
                    name = container.name,
                    endpointId = container.endpointId ?: "",
                    macAddress = container.macAddress ?: "",
                    ipv4Address = container.ipv4Address ?: "",
                    ipv6Address = container.ipv6Address ?: ""
                )
            }
        )
    }
}
