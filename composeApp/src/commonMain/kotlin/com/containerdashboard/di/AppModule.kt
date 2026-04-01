package com.containerdashboard.di

import com.containerdashboard.data.repository.DockerRepository
import com.containerdashboard.data.repository.PreferenceRepository

object AppModule {
    var dockerRepository: DockerRepository = DockerRepository(PreferenceRepository.initialEngineHost)
        private set

    fun reconnect(newHost: String) {
        dockerRepository.close()
        dockerRepository = DockerRepository(newHost)
    }
}
