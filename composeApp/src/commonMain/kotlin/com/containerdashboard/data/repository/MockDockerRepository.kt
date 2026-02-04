package com.containerdashboard.data.repository

import com.containerdashboard.data.models.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Mock implementation for UI development and testing.
 */
class MockDockerRepository : DockerRepository {
    
    override suspend fun getSystemInfo(): Result<SystemInfo> = Result.success(
        SystemInfo(
            id = "ABCD:1234:5678",
            containers = 12,
            containersRunning = 5,
            containersPaused = 1,
            containersStopped = 6,
            images = 24,
            driver = "overlay2",
            memTotal = 17179869184,
            ncpu = 8,
            kernelVersion = "5.15.0-generic",
            operatingSystem = "Docker Desktop",
            osType = "linux",
            architecture = "x86_64",
            name = "docker-desktop",
            serverVersion = "24.0.7",
            dockerRootDir = "/var/lib/docker"
        )
    )
    
    override suspend fun getVersion(): Result<DockerVersion> = Result.success(
        DockerVersion(
            version = "24.0.7",
            apiVersion = "1.43",
            minAPIVersion = "1.12",
            gitCommit = "311b9ff",
            goVersion = "go1.20.10",
            os = "linux",
            arch = "amd64",
            buildTime = "2023-10-26T09:08:17.000000000+00:00"
        )
    )
    
    override fun getContainers(all: Boolean): Flow<List<Container>> = flow {
        emit(
            listOf(
                Container(
                    id = "abc123def456",
                    names = listOf("/nginx-proxy"),
                    image = "nginx:latest",
                    state = "running",
                    status = "Up 2 hours"
                ),
                Container(
                    id = "def456ghi789",
                    names = listOf("/postgres-db"),
                    image = "postgres:15",
                    state = "running",
                    status = "Up 5 hours"
                )
            )
        )
    }
    
    override suspend fun getContainer(id: String): Result<Container> = Result.success(
        Container(id = id, names = listOf("/test"), image = "test:latest", state = "running")
    )
    
    override suspend fun getContainerLogs(id: String, tail: Int, timestamps: Boolean): Result<String> = Result.success(
        """
        2024-01-15T10:30:00.000Z Starting application...
        2024-01-15T10:30:01.000Z Loading configuration...
        2024-01-15T10:30:02.000Z Connected to database
        2024-01-15T10:30:03.000Z Server listening on port 8080
        2024-01-15T10:30:15.000Z GET /api/health 200 OK
        2024-01-15T10:31:00.000Z GET /api/users 200 OK
        """.trimIndent()
    )
    
    override suspend fun startContainer(id: String): Result<Unit> = Result.success(Unit)
    override suspend fun stopContainer(id: String): Result<Unit> = Result.success(Unit)
    override suspend fun restartContainer(id: String): Result<Unit> = Result.success(Unit)
    override suspend fun pauseContainer(id: String): Result<Unit> = Result.success(Unit)
    override suspend fun unpauseContainer(id: String): Result<Unit> = Result.success(Unit)
    override suspend fun removeContainer(id: String, force: Boolean): Result<Unit> = Result.success(Unit)
    
    override fun getImages(): Flow<List<DockerImage>> = flow {
        emit(
            listOf(
                DockerImage(
                    id = "sha256:abc123",
                    repoTags = listOf("nginx:latest"),
                    size = 142000000
                )
            )
        )
    }
    
    override suspend fun getImage(id: String): Result<DockerImage> = Result.success(
        DockerImage(id = id, repoTags = listOf("test:latest"), size = 100000000)
    )
    
    override suspend fun pullImage(name: String, tag: String): Flow<String> = flow {
        emit("Pulling $name:$tag...")
        emit("Download complete")
    }
    
    override suspend fun removeImage(id: String, force: Boolean): Result<Unit> = Result.success(Unit)
    
    override fun getVolumes(): Flow<List<Volume>> = flow {
        emit(
            listOf(
                Volume(name = "postgres-data", driver = "local"),
                Volume(name = "redis-data", driver = "local")
            )
        )
    }
    
    override suspend fun getVolume(name: String): Result<Volume> = Result.success(
        Volume(name = name, driver = "local")
    )
    
    override suspend fun createVolume(name: String, driver: String): Result<Volume> = Result.success(
        Volume(name = name, driver = driver)
    )
    
    override suspend fun removeVolume(name: String): Result<Unit> = Result.success(Unit)
    
    override fun getNetworks(): Flow<List<DockerNetwork>> = flow {
        emit(
            listOf(
                DockerNetwork(id = "abc123", name = "bridge", driver = "bridge"),
                DockerNetwork(id = "def456", name = "app-network", driver = "bridge")
            )
        )
    }
    
    override suspend fun getNetwork(id: String): Result<DockerNetwork> = Result.success(
        DockerNetwork(id = id, name = "test-network", driver = "bridge")
    )
    
    override suspend fun createNetwork(name: String, driver: String): Result<DockerNetwork> = Result.success(
        DockerNetwork(id = "new123", name = name, driver = driver)
    )
    
    override suspend fun removeNetwork(id: String): Result<Unit> = Result.success(Unit)
    
    override suspend fun pruneContainers(): Result<PruneResult> = Result.success(PruneResult(3, 500000000))
    override suspend fun pruneImages(): Result<PruneResult> = Result.success(PruneResult(5, 1200000000))
    override suspend fun pruneVolumes(): Result<PruneResult> = Result.success(PruneResult(2, 300000000))
    override suspend fun pruneNetworks(): Result<PruneResult> = Result.success(PruneResult(1, 0))
}
