package com.containerdashboard.di

import com.containerdashboard.data.repository.DockerRepository
import com.containerdashboard.data.repository.PreferenceRepository

/**
 * Simple dependency injection container.
 * The DockerRepository is constructed lazily using the engine host from preferences.
 */
object AppModule {
    val dockerRepository: DockerRepository by lazy {
        DockerRepository(PreferenceRepository.engineHost)
    }

    fun closeRepository() {
        dockerRepository.close()
    }
}
