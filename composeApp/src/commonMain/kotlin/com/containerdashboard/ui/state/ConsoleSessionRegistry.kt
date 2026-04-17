package com.containerdashboard.ui.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object ConsoleSessionRegistry {
    private val _activeSessions = MutableStateFlow<Set<String>>(emptySet())
    val activeSessions: StateFlow<Set<String>> = _activeSessions.asStateFlow()

    fun register(containerId: String) {
        _activeSessions.update { it + containerId }
    }

    fun unregister(containerId: String) {
        _activeSessions.update { it - containerId }
    }
}
