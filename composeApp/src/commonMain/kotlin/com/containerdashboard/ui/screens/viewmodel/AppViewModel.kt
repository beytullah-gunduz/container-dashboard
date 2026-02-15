package com.containerdashboard.ui.screens.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.containerdashboard.data.repository.DockerRepository
import com.containerdashboard.di.AppModule
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class AppViewModel : ViewModel() {

    private val repo: DockerRepository = AppModule.dockerRepository

    val isConnected: StateFlow<Boolean> = repo.isDockerAvailable()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
}
