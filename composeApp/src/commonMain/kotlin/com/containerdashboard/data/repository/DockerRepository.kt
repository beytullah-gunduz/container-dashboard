package com.containerdashboard.data.repository

import com.containerdashboard.data.models.*
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for Docker operations.
 * This can be implemented with actual Docker API calls.
 */
interface DockerRepository {
    // System
    suspend fun getSystemInfo(): Result<SystemInfo>
    suspend fun getVersion(): Result<DockerVersion>
    
    // Containers
    fun getContainers(all: Boolean = true): Flow<List<Container>>
    suspend fun getContainer(id: String): Result<Container>
    suspend fun getContainerLogs(id: String, tail: Int = 500, timestamps: Boolean = true): Result<String>
    suspend fun startContainer(id: String): Result<Unit>
    suspend fun stopContainer(id: String): Result<Unit>
    suspend fun restartContainer(id: String): Result<Unit>
    suspend fun pauseContainer(id: String): Result<Unit>
    suspend fun unpauseContainer(id: String): Result<Unit>
    suspend fun removeContainer(id: String, force: Boolean = false): Result<Unit>
    
    // Images
    fun getImages(): Flow<List<DockerImage>>
    suspend fun getImage(id: String): Result<DockerImage>
    suspend fun pullImage(name: String, tag: String = "latest"): Flow<String>
    suspend fun removeImage(id: String, force: Boolean = false): Result<Unit>
    
    // Volumes
    fun getVolumes(): Flow<List<Volume>>
    suspend fun getVolume(name: String): Result<Volume>
    suspend fun createVolume(name: String, driver: String = "local"): Result<Volume>
    suspend fun removeVolume(name: String): Result<Unit>
    
    // Networks
    fun getNetworks(): Flow<List<DockerNetwork>>
    suspend fun getNetwork(id: String): Result<DockerNetwork>
    suspend fun createNetwork(name: String, driver: String = "bridge"): Result<DockerNetwork>
    suspend fun removeNetwork(id: String): Result<Unit>
    
    // Stats

fun getContainerStats(refreshRateMillis: Long = 3000L): Flow<List<ContainerStats>>



    // Prune operations
    suspend fun pruneContainers(): Result<PruneResult>
    suspend fun pruneImages(): Result<PruneResult>
    suspend fun pruneVolumes(): Result<PruneResult>
    suspend fun pruneNetworks(): Result<PruneResult>
}
