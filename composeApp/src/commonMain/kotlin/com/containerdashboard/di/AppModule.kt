package com.containerdashboard.di

import com.containerdashboard.data.repository.DockerRepository
import com.containerdashboard.data.repository.PreferenceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppModule {
    private val _dockerRepository: MutableStateFlow<DockerRepository> =
        MutableStateFlow(DockerRepository(PreferenceRepository.initialEngineHost))

    val dockerRepositoryFlow: StateFlow<DockerRepository> = _dockerRepository.asStateFlow()

    val dockerRepository: DockerRepository get() = _dockerRepository.value

    fun reconnect(newHost: String) {
        val old = _dockerRepository.value
        _dockerRepository.value = DockerRepository(newHost)
        old.close()
    }
}
