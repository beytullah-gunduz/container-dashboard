package com.containerdashboard.data.repository

import com.containerdashboard.data.models.AttachedContainer
import com.containerdashboard.data.models.Container
import com.containerdashboard.data.models.ContainerInspect
import com.containerdashboard.data.models.ContainerPort
import com.containerdashboard.data.models.ContainerStats
import com.containerdashboard.data.models.DockerImage
import com.containerdashboard.data.models.DockerNetwork
import com.containerdashboard.data.models.DockerVersion
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
import com.containerdashboard.data.models.SystemInfo
import com.containerdashboard.data.models.Volume
import com.containerdashboard.data.models.VolumeInspect
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Duration
import com.github.dockerjava.api.model.Container as DockerContainer
import com.github.dockerjava.api.model.Image as DockerJavaImage
import com.github.dockerjava.api.model.Network as DockerNetworkModel

private var cachedMaxLogLines: Int = 1000
private var maxLogLinesLastRead: Long = 0

private val maxLogLines: Int
    get() {
        val now = System.currentTimeMillis()
        if (now - maxLogLinesLastRead > 5000) {
            maxLogLinesLastRead = now
            cachedMaxLogLines = PreferenceRepository.logsMaxLinesSync
        }
        return cachedMaxLogLines
    }

actual class DockerRepository actual constructor(
    private val dockerHost: String,
) {
    private val logger = LoggerFactory.getLogger(DockerRepository::class.java)

    private val config =
        DefaultDockerClientConfig
            .createDefaultConfigBuilder()
            .withDockerHost(dockerHost)
            .build()

    private fun createHttpClient(): ApacheDockerHttpClient =
        ApacheDockerHttpClient
            .Builder()
            .dockerHost(config.dockerHost)
            .maxConnections(100)
            .connectionTimeout(Duration.ofSeconds(30))
            .responseTimeout(Duration.ofSeconds(45))
            .build()

    @Volatile private var httpClient = createHttpClient()

    @Volatile private var dockerClient: DockerClient = DockerClientImpl.getInstance(config, httpClient)

    val client: DockerClient get() = dockerClient

    private fun rebuildClient() {
        val oldHttp = httpClient
        httpClient = createHttpClient()
        dockerClient = DockerClientImpl.getInstance(config, httpClient)
        oldHttp.close()
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile private var lastAvailable = false

    private inline fun <T> withRetryOnPoolShutdown(block: () -> T): T =
        try {
            block()
        } catch (e: Exception) {
            if (e.message?.contains("shut down") == true) {
                rebuildClient()
                block()
            } else {
                throw e
            }
        }

    actual fun isDockerAvailable(checkIntervalMillis: Long): Flow<Boolean> =
        flow {
            while (true) {
                val available =
                    try {
                        val socketPath = dockerHost.removePrefix("unix://")
                        if (!File(socketPath).exists()) {
                            false
                        } else {
                            if (!lastAvailable) {
                                rebuildClient()
                            }
                            dockerClient.pingCmd().exec()
                            true
                        }
                    } catch (e: Exception) {
                        logger.debug("Docker daemon not reachable: {}", e.message)
                        false
                    }
                lastAvailable = available
                emit(available)
                delay(checkIntervalMillis)
            }
        }.distinctUntilChanged().flowOn(Dispatchers.IO)

    private val dockerEvents: SharedFlow<Event> =
        flow {
            while (true) {
                try {
                    val events =
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
                        }
                    events.collect { emit(it) }
                } catch (e: Exception) {
                    logger.debug("Docker event stream ended, retrying in 5s: {}", e.message)
                }
                delay(5000)
            }
        }.shareIn(scope, SharingStarted.Lazily)

    // System
    actual suspend fun getSystemInfo(): Result<SystemInfo> =
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

    actual suspend fun getVersion(): Result<DockerVersion> =
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

    private val containerRefreshTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    private val imagesRefreshTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    private val volumesRefreshTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    private val networksRefreshTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 16)

    actual suspend fun refreshContainers() {
        containerRefreshTrigger.emit(Unit)
    }

    actual suspend fun refreshImages() {
        imagesRefreshTrigger.emit(Unit)
    }

    actual suspend fun refreshVolumes() {
        volumesRefreshTrigger.emit(Unit)
    }

    actual suspend fun refreshNetworks() {
        networksRefreshTrigger.emit(Unit)
    }

    // Containers — hot shared flow. One poll timer + one list call per 15 s,
    // supplemented by dockerEvents-driven refreshes. Replay-1 so new subscribers
    // see the latest list immediately.
    private val containersSharedAll: SharedFlow<List<Container>> by lazy {
        merge(
            dockerEvents.filter { it.type == EventType.CONTAINER }.map { },
            containerRefreshTrigger,
            flow {
                while (true) {
                    delay(15_000)
                    emit(Unit)
                }
            },
        ).map {
            try {
                withRetryOnPoolShutdown {
                    dockerClient
                        .listContainersCmd()
                        .withShowAll(true)
                        .exec()
                        .map { it.toContainer() }
                }
            } catch (e: Exception) {
                logger.warn("Failed to list containers: {}", e.message)
                emptyList()
            }
        }.onStart {
            emit(
                try {
                    withRetryOnPoolShutdown {
                        dockerClient
                            .listContainersCmd()
                            .withShowAll(true)
                            .exec()
                            .map { it.toContainer() }
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to list containers on start: {}", e.message)
                    emptyList()
                },
            )
        }.catch { e ->
            logger.error("Container flow error", e)
            emit(emptyList())
        }.flowOn(Dispatchers.IO)
            .shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)
    }

    actual fun getContainers(all: Boolean): Flow<List<Container>> =
        if (all) {
            containersSharedAll
        } else {
            // One-shot cold fallback for the single all=false caller
            // (SettingsScreenViewModel.stopAllContainers uses .first()).
            flow {
                emit(
                    try {
                        withRetryOnPoolShutdown {
                            dockerClient
                                .listContainersCmd()
                                .withShowAll(false)
                                .exec()
                                .map { it.toContainer() }
                        }
                    } catch (e: Exception) {
                        logger.warn("Failed to list containers (all=false): {}", e.message)
                        emptyList()
                    },
                )
            }.flowOn(Dispatchers.IO)
        }

    actual suspend fun getContainer(id: String): Result<Container> =
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

    actual suspend fun inspectContainer(id: String): Result<ContainerInspect> =
        withContext(Dispatchers.IO) {
            try {
                val response =
                    withRetryOnPoolShutdown {
                        dockerClient.inspectContainerCmd(id).exec()
                    }
                Result.success(response.toContainerInspect())
            } catch (e: Exception) {
                logger.error("Failed to inspect container {}", id, e)
                Result.failure(e)
            }
        }

    actual suspend fun inspectImage(id: String): Result<ImageInspect> =
        withContext(Dispatchers.IO) {
            try {
                val response =
                    withRetryOnPoolShutdown {
                        dockerClient.inspectImageCmd(id).exec()
                    }
                Result.success(response.toImageInspect())
            } catch (e: Exception) {
                logger.error("Failed to inspect image {}", id, e)
                Result.failure(e)
            }
        }

    actual suspend fun inspectVolume(name: String): Result<VolumeInspect> =
        withContext(Dispatchers.IO) {
            try {
                val response =
                    withRetryOnPoolShutdown {
                        dockerClient.inspectVolumeCmd(name).exec()
                    }
                Result.success(response.toVolumeInspect())
            } catch (e: Exception) {
                logger.error("Failed to inspect volume {}", name, e)
                Result.failure(e)
            }
        }

    actual suspend fun inspectNetwork(id: String): Result<NetworkInspect> =
        withContext(Dispatchers.IO) {
            try {
                val response =
                    withRetryOnPoolShutdown {
                        dockerClient.inspectNetworkCmd().withNetworkId(id).exec()
                    }
                Result.success(response.toNetworkInspect())
            } catch (e: Exception) {
                logger.error("Failed to inspect network {}", id, e)
                Result.failure(e)
            }
        }

    actual suspend fun getContainerLogs(
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

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    actual fun followContainerLogs(
        id: String,
        tail: Int,
    ): Flow<List<String>> =
        callbackFlow {
            val lines = mutableListOf<String>()
            val callback =
                object : com.github.dockerjava.api.async.ResultCallback.Adapter<com.github.dockerjava.api.model.Frame>() {
                    override fun onNext(frame: com.github.dockerjava.api.model.Frame?) {
                        frame?.let {
                            val text = String(it.payload ?: ByteArray(0))
                            val snapshot =
                                synchronized(lines) {
                                    text.lineSequence().filter { l -> l.isNotEmpty() }.forEach { l ->
                                        lines.add(l)
                                    }
                                    val cap = maxLogLines
                                    if (lines.size > cap) {
                                        val excess = lines.size - cap
                                        repeat(excess) { lines.removeFirst() }
                                    }
                                    lines.toList()
                                }
                            trySend(snapshot)
                        }
                    }

                    override fun onError(throwable: Throwable?) {
                        logger.warn("Log follow stream error for {}: {}", id, throwable?.message)
                        close()
                    }

                    override fun onComplete() {
                        close()
                    }
                }

            try {
                dockerClient
                    .logContainerCmd(id)
                    .withStdOut(true)
                    .withStdErr(true)
                    .withTail(tail)
                    .withFollowStream(true)
                    .exec(callback)
            } catch (e: Exception) {
                logger.error("Failed to follow logs for container {}", id, e)
                close(e)
            }

            awaitClose {
                try {
                    callback.close()
                } catch (_: Exception) {
                }
            }
        }.conflate().sample(100).flowOn(Dispatchers.IO)

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    actual fun followMultipleContainerLogs(
        containers: List<Pair<String, String>>,
        tail: Int,
    ): Flow<List<String>> =
        channelFlow {
            val lines = mutableListOf<String>()
            val lock = Any()

            val jobs =
                containers.map { (containerId, label) ->
                    launch {
                        callbackFlow {
                            val callback =
                                object : com.github.dockerjava.api.async.ResultCallback.Adapter<com.github.dockerjava.api.model.Frame>() {
                                    override fun onNext(frame: com.github.dockerjava.api.model.Frame?) {
                                        frame?.let {
                                            val text = String(it.payload ?: ByteArray(0))
                                            trySend(text)
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
                                dockerClient
                                    .logContainerCmd(containerId)
                                    .withStdOut(true)
                                    .withStdErr(true)
                                    .withTail(tail)
                                    .withFollowStream(true)
                                    .exec(callback)
                            } catch (e: Exception) {
                                close(e)
                            }
                            awaitClose { runCatching { callback.close() } }
                        }.collect { text ->
                            val newLines =
                                text
                                    .lineSequence()
                                    .filter { it.isNotEmpty() }
                                    .map { "[$label] $it" }
                                    .toList()
                            if (newLines.isNotEmpty()) {
                                val snapshot =
                                    synchronized(lock) {
                                        lines.addAll(newLines)
                                        val cap = maxLogLines
                                        if (lines.size > cap) {
                                            val excess = lines.size - cap
                                            repeat(excess) { lines.removeFirst() }
                                        }
                                        lines.toList()
                                    }
                                send(snapshot)
                            }
                        }
                    }
                }
            jobs.forEach { it.join() }
        }.conflate().sample(100).flowOn(Dispatchers.IO)

    actual suspend fun startContainer(id: String): Result<Unit> =
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

    actual suspend fun stopContainer(id: String): Result<Unit> =
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

    actual suspend fun restartContainer(id: String): Result<Unit> =
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

    actual suspend fun pauseContainer(id: String): Result<Unit> =
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

    actual suspend fun unpauseContainer(id: String): Result<Unit> =
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

    actual suspend fun removeContainer(
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

    // Images — hot shared flow (shared across DashboardScreenViewModel + ImagesScreenViewModel).
    private val imagesShared: SharedFlow<List<DockerImage>> by lazy {
        merge(
            dockerEvents.filter { it.type == EventType.IMAGE }.map { },
            imagesRefreshTrigger,
            flow {
                while (true) {
                    delay(15_000)
                    emit(Unit)
                }
            },
        ).map {
            try {
                withRetryOnPoolShutdown {
                    dockerClient
                        .listImagesCmd()
                        .withShowAll(true)
                        .exec()
                        .map { it.toDockerImage() }
                }
            } catch (e: Exception) {
                logger.warn("Failed to list images: {}", e.message)
                emptyList()
            }
        }.onStart {
            emit(
                try {
                    withRetryOnPoolShutdown {
                        dockerClient
                            .listImagesCmd()
                            .withShowAll(true)
                            .exec()
                            .map { it.toDockerImage() }
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to list images on start: {}", e.message)
                    emptyList()
                },
            )
        }.catch { e ->
            logger.error("Image flow error", e)
            emit(emptyList())
        }.flowOn(Dispatchers.IO)
            .shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)
    }

    actual fun getImages(): Flow<List<DockerImage>> = imagesShared

    actual suspend fun getImage(id: String): Result<DockerImage> =
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

    actual suspend fun pullImage(
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

    actual suspend fun removeImage(
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

    // Volumes — hot shared flow.
    private val volumesShared: SharedFlow<List<Volume>> by lazy {
        merge(
            dockerEvents.filter { it.type == EventType.VOLUME }.map { },
            volumesRefreshTrigger,
            flow {
                while (true) {
                    delay(15_000)
                    emit(Unit)
                }
            },
        ).map {
            try {
                withRetryOnPoolShutdown {
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
                }
            } catch (e: Exception) {
                logger.warn("Failed to list volumes: {}", e.message)
                emptyList()
            }
        }.onStart {
            emit(
                try {
                    withRetryOnPoolShutdown {
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
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to list volumes on start: {}", e.message)
                    emptyList()
                },
            )
        }.catch { e ->
            logger.error("Volume flow error", e)
            emit(emptyList())
        }.flowOn(Dispatchers.IO)
            .shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)
    }

    actual fun getVolumes(): Flow<List<Volume>> = volumesShared

    actual suspend fun getVolume(name: String): Result<Volume> =
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

    actual suspend fun createVolume(
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

    actual suspend fun removeVolume(name: String): Result<Unit> =
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

    // Networks — hot shared flow.
    private val networksShared: SharedFlow<List<DockerNetwork>> by lazy {
        merge(
            dockerEvents.filter { it.type == EventType.NETWORK }.map { },
            networksRefreshTrigger,
            flow {
                while (true) {
                    delay(15_000)
                    emit(Unit)
                }
            },
        ).map {
            try {
                withRetryOnPoolShutdown {
                    dockerClient
                        .listNetworksCmd()
                        .exec()
                        .map { it.toDockerNetwork() }
                }
            } catch (e: Exception) {
                logger.warn("Failed to list networks: {}", e.message)
                emptyList()
            }
        }.onStart {
            emit(
                try {
                    withRetryOnPoolShutdown {
                        dockerClient
                            .listNetworksCmd()
                            .exec()
                            .map { it.toDockerNetwork() }
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to list networks on start: {}", e.message)
                    emptyList()
                },
            )
        }.catch { e ->
            logger.error("Network flow error", e)
            emit(emptyList())
        }.flowOn(Dispatchers.IO)
            .shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)
    }

    actual fun getNetworks(): Flow<List<DockerNetwork>> = networksShared

    actual suspend fun getNetwork(id: String): Result<DockerNetwork> =
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

    actual suspend fun createNetwork(
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

    actual suspend fun removeNetwork(id: String): Result<Unit> =
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

    // One-shot container list (for tray stats)
    suspend fun listContainersOnce(all: Boolean = true): List<Container> =
        withContext(Dispatchers.IO) {
            try {
                dockerClient
                    .listContainersCmd()
                    .withShowAll(all)
                    .exec()
                    .map { it.toContainer() }
            } catch (e: Exception) {
                logger.warn("Failed to list containers once: {}", e.message)
                emptyList()
            }
        }

    // One-shot stats for a list of containers (for tray stats)
    suspend fun getContainerStatsOnce(containers: List<Pair<String, String>>): List<ContainerStats> =
        withContext(Dispatchers.IO) {
            containers.mapNotNull { (id, name) ->
                try {
                    var result: com.github.dockerjava.api.model.Statistics? = null
                    val callback =
                        object : com.github.dockerjava.api.async.ResultCallback.Adapter<com.github.dockerjava.api.model.Statistics>() {
                            override fun onNext(s: com.github.dockerjava.api.model.Statistics?) {
                                if (s != null) result = s
                            }
                        }
                    dockerClient.statsCmd(id).withNoStream(true).exec(callback)
                    callback.awaitCompletion()
                    result?.let {
                        val (diskR, diskW) = extractDiskIo(it)
                        val (netRx, netTx) = extractNetworkIo(it)
                        ContainerStats(
                            containerId = id,
                            containerName = name,
                            cpuPercent = calculateCpuPercent(it),
                            memoryUsage = it.memoryStats?.usage ?: 0L,
                            memoryLimit = it.memoryStats?.limit ?: 0L,
                            diskReadBytes = diskR,
                            diskWriteBytes = diskW,
                            networkRxBytes = netRx,
                            networkTxBytes = netTx,
                        )
                    }
                } catch (e: Exception) {
                    logger.debug("Failed to get one-shot stats for {}: {}", id, e.message)
                    null
                }
            }
        }

    // Stats — hot shared flow keyed on the stable running set.
    //
    // Key change vs prior implementation: distinctUntilChanged runs on
    // List<Pair<id, displayName>> of *running* containers only, so the
    // frequent status-text updates ("Up 3 minutes" → "Up 4 minutes")
    // no longer trip flatMapLatest and tear down every per-container
    // stats stream. The streams only restart when the running set
    // genuinely changes.
    //
    // Shared so Containers + Monitoring screens observe one set of
    // per-container HTTP stats streams; slower consumers downsample
    // via .sample(...) on their side.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val containerStatsShared: SharedFlow<List<ContainerStats>> by lazy {
        getContainers(true)
            .map { list -> list.filter { it.isRunning }.map { it.id to it.displayName } }
            .distinctUntilChanged()
            .flatMapLatest { running ->
                if (running.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    combine(
                        running.map { (id, name) ->
                            singleContainerStats(id, name, 1_000L)
                        },
                    ) { stats -> stats.toList() }
                }
            }.shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)
    }

    actual fun getContainerStats(): Flow<List<ContainerStats>> = containerStatsShared

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
                            val (diskR, diskW) = extractDiskIo(it)
                            val (netRx, netTx) = extractNetworkIo(it)
                            trySend(
                                ContainerStats(
                                    containerId = containerId,
                                    containerName = containerName,
                                    cpuPercent = cpuPercent,
                                    memoryUsage = memUsage,
                                    memoryLimit = memLimit,
                                    diskReadBytes = diskR,
                                    diskWriteBytes = diskW,
                                    networkRxBytes = netRx,
                                    networkTxBytes = netTx,
                                ),
                            )
                        }
                    }

                    override fun onError(throwable: Throwable?) {
                        logger.debug("Stats stream closed for container {}: {}", containerId, throwable?.message)
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

    // Sum cumulative disk IO from blkioStats.ioServiceBytesRecursive entries by op.
    // Many backends (notably cgroup v2 / rootless Docker) report an empty list —
    // we return (0, 0) in that case rather than failing.
    private fun extractDiskIo(stats: com.github.dockerjava.api.model.Statistics): Pair<Long, Long> {
        val entries = stats.blkioStats?.ioServiceBytesRecursive ?: return 0L to 0L
        if (entries.isEmpty()) return 0L to 0L
        var read = 0L
        var write = 0L
        for (entry in entries) {
            val op = entry?.op?.lowercase() ?: continue
            val value = entry.value ?: continue
            when (op) {
                "read" -> read += value
                "write" -> write += value
            }
        }
        return read to write
    }

    // Sum cumulative rx/tx bytes across every network interface reported.
    // Returns (0, 0) when the networks map is missing or empty.
    private fun extractNetworkIo(stats: com.github.dockerjava.api.model.Statistics): Pair<Long, Long> {
        val networks = stats.networks ?: return 0L to 0L
        if (networks.isEmpty()) return 0L to 0L
        var rx = 0L
        var tx = 0L
        for (net in networks.values) {
            if (net == null) continue
            rx += net.rxBytes ?: 0L
            tx += net.txBytes ?: 0L
        }
        return rx to tx
    }

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
    actual suspend fun pruneContainers(): Result<PruneResult> =
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

    actual suspend fun pruneImages(): Result<PruneResult> =
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

    actual suspend fun pruneVolumes(): Result<PruneResult> =
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

    actual suspend fun pruneNetworks(): Result<PruneResult> =
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

    // Exec sessions
    private val execSessions = mutableMapOf<String, java.io.OutputStream>()

    actual suspend fun createExecSession(
        containerId: String,
        cmd: List<String>,
    ): Result<ExecSession> =
        withContext(Dispatchers.IO) {
            try {
                val execCreate =
                    dockerClient
                        .execCreateCmd(containerId)
                        .withCmd(*cmd.toTypedArray())
                        .withAttachStdin(true)
                        .withAttachStdout(true)
                        .withAttachStderr(true)
                        .withTty(true)
                        .exec()

                val execId = execCreate.id
                val pipedOutput = java.io.PipedOutputStream()
                val pipedInput = java.io.PipedInputStream(pipedOutput)

                val outputFlow =
                    callbackFlow {
                        val callback =
                            object : com.github.dockerjava.api.async.ResultCallback.Adapter<com.github.dockerjava.api.model.Frame>() {
                                override fun onNext(frame: com.github.dockerjava.api.model.Frame?) {
                                    frame?.let {
                                        val text = String(it.payload ?: ByteArray(0))
                                        trySend(text)
                                    }
                                }

                                override fun onError(throwable: Throwable?) {
                                    logger.warn("Exec stream error for {}: {}", execId, throwable?.message)
                                    close()
                                }

                                override fun onComplete() {
                                    close()
                                }
                            }

                        val execStart =
                            dockerClient
                                .execStartCmd(execId)
                                .withDetach(false)
                                .withTty(true)
                                .withStdIn(pipedInput)

                        execStart.exec(callback)

                        awaitClose {
                            try {
                                callback.close()
                            } catch (e: Exception) {
                                logger.debug("Error closing exec callback: {}", e.message)
                            }
                        }
                    }.flowOn(Dispatchers.IO)

                execSessions[execId] = pipedOutput
                logger.info("Created exec session {} for container {}", execId, containerId)
                Result.success(ExecSession(execId = execId, containerId = containerId, output = outputFlow))
            } catch (e: Exception) {
                logger.error("Failed to create exec session for container {}", containerId, e)
                Result.failure(e)
            }
        }

    actual suspend fun sendExecInput(
        session: ExecSession,
        input: String,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val outputStream =
                    execSessions[session.execId]
                        ?: return@withContext Result.failure(Exception("Exec session not found"))
                outputStream.write(input.toByteArray())
                outputStream.flush()
                Result.success(Unit)
            } catch (e: Exception) {
                logger.error("Failed to send input to exec session {}", session.execId, e)
                Result.failure(e)
            }
        }

    actual suspend fun closeExecSession(session: ExecSession): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                execSessions.remove(session.execId)?.close()
                logger.info("Closed exec session {}", session.execId)
                Result.success(Unit)
            } catch (e: Exception) {
                logger.error("Failed to close exec session {}", session.execId, e)
                Result.failure(e)
            }
        }

    actual fun close() {
        try {
            scope.cancel()
            dockerClient.close()
            httpClient.close()
        } catch (e: Exception) {
            logger.warn("Error during DockerRepository shutdown", e)
        }
    }

    private val inspectJsonMapper: ObjectMapper =
        ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)

    private fun toPrettyJson(obj: Any): String =
        try {
            inspectJsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj)
        } catch (e: Exception) {
            logger.warn("Failed to serialize docker-java response to JSON: {}", e.message)
            obj.toString()
        }

    private fun com.github.dockerjava.api.command.InspectContainerResponse.toContainerInspect(): ContainerInspect {
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

    private fun com.github.dockerjava.api.command.InspectImageResponse.toImageInspect(): ImageInspect {
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

    private fun com.github.dockerjava.api.model.Network.toNetworkInspect(): NetworkInspect {
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

    private fun com.github.dockerjava.api.command.InspectVolumeResponse.toVolumeInspect(): VolumeInspect =
        VolumeInspect(
            name = this.name ?: "",
            driver = this.driver ?: "",
            mountpoint = this.mountpoint ?: "",
            scope = "",
            createdAt = "",
            options = this.options ?: emptyMap(),
            labels = this.labels ?: emptyMap(),
            rawJson = toPrettyJson(this),
        )

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
