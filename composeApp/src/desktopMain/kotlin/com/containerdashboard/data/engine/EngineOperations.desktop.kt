package com.containerdashboard.data.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

private fun EngineActionState.toCommon(): EngineActionStatus = when (this) {
    is EngineActionState.Idle -> EngineActionStatus.Idle
    is EngineActionState.Running -> EngineActionStatus.Running(message)
    is EngineActionState.Done -> EngineActionStatus.Done(success, message)
}

actual object EngineOperations {
    actual val actionStatus: StateFlow<EngineActionStatus> =
        EngineManager.actionState
            .map { it.toCommon() }
            .stateIn(scope, SharingStarted.Eagerly, EngineActionStatus.Idle)

    actual val commandOutput: StateFlow<String>
        get() = EngineManager.output

    actual fun clearState() = EngineManager.clearState()

    actual suspend fun getColimaConfig(profile: String): ColimaConfig? {
        val status = EngineManager.getColimaStatus(profile) ?: return null
        return ColimaConfig(
            cpu = status.cpu,
            memoryGB = (status.memory / (1024L * 1024L * 1024L)).toInt().coerceAtLeast(1),
            diskGB = (status.disk / (1024L * 1024L * 1024L)).toInt().coerceAtLeast(1),
        )
    }

    actual suspend fun startEngine(
        type: EngineType,
        profile: String?,
        cpu: Int?,
        memory: Int?,
        disk: Int?,
    ): Boolean = EngineManager.startEngine(type, profile, cpu, memory, disk)

    actual suspend fun stopEngine(
        type: EngineType,
        profile: String?,
    ): Boolean = EngineManager.stopEngine(type, profile)
}
