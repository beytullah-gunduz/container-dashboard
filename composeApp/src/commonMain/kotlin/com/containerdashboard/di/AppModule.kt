package com.containerdashboard.di

import com.containerdashboard.data.repository.DockerRepository

/**
 * Simple dependency injection container.
 * In a real app, you might use Koin or another DI framework.
 */
object AppModule {
    private var _dockerRepository: DockerRepository? = null
    
    val dockerRepository: DockerRepository
        get() = _dockerRepository ?: throw IllegalStateException("DockerRepository not initialized")
    
    fun initialize(repository: DockerRepository) {
        _dockerRepository = repository
    }
    
    val isInitialized: Boolean
        get() = _dockerRepository != null
}
