package com.containerdashboard.data.engine

import kotlinx.coroutines.flow.StateFlow

data class ColimaConfig(
    val cpu: Int,
    val memoryGB: Int,
    val diskGB: Int,
)

sealed interface EngineActionStatus {
    data object Idle : EngineActionStatus
    data class Running(val message: String) : EngineActionStatus
    data class Done(val success: Boolean, val message: String) : EngineActionStatus
}

expect object EngineOperations {
    val actionStatus: StateFlow<EngineActionStatus>
    val commandOutput: StateFlow<String>

    fun clearState()

    suspend fun getColimaConfig(profile: String = "default"): ColimaConfig?

    suspend fun startEngine(
        type: EngineType,
        profile: String? = null,
        cpu: Int? = null,
        memory: Int? = null,
        disk: Int? = null,
    ): Boolean

    suspend fun stopEngine(
        type: EngineType,
        profile: String? = null,
    ): Boolean
}
