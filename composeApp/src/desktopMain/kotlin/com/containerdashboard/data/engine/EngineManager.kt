package com.containerdashboard.data.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

@Serializable
data class ColimaStatus(
    val status: String = "",
    val cpu: Int = 0,
    val memory: Long = 0,
    val disk: Long = 0,
    val arch: String = "",
    val runtime: String = "",
)

sealed interface EngineActionState {
    data object Idle : EngineActionState

    data class Running(
        val message: String,
    ) : EngineActionState

    data class Done(
        val success: Boolean,
        val message: String,
    ) : EngineActionState
}

object EngineManager {
    private val logger = LoggerFactory.getLogger(EngineManager::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    private val _output = MutableStateFlow("")
    val output: StateFlow<String> = _output.asStateFlow()

    private val _actionState = MutableStateFlow<EngineActionState>(EngineActionState.Idle)
    val actionState: StateFlow<EngineActionState> = _actionState.asStateFlow()

    private fun appendOutput(line: String) {
        _output.value = (_output.value + line + "\n").takeLast(4000)
    }

    fun clearState() {
        _actionState.value = EngineActionState.Idle
        _output.value = ""
    }

    suspend fun getColimaStatus(profile: String = "default"): ColimaStatus? =
        withContext(Dispatchers.IO) {
            try {
                val cmd = mutableListOf("colima", "status", "--json")
                if (profile != "default") {
                    cmd.addAll(listOf("--profile", profile))
                }
                val proc =
                    ProcessBuilder(cmd)
                        .redirectErrorStream(true)
                        .start()
                val text = proc.inputStream.bufferedReader().readText()
                val exitCode = proc.waitFor()
                if (exitCode != 0) return@withContext null
                json.decodeFromString<ColimaStatus>(text)
            } catch (e: Exception) {
                logger.debug("Failed to get Colima status: {}", e.message)
                null
            }
        }

    suspend fun startEngine(
        type: EngineType,
        profile: String? = null,
        cpu: Int? = null,
        memory: Int? = null,
        disk: Int? = null,
    ): Boolean {
        _output.value = ""
        _actionState.value = EngineActionState.Running("Starting ${type.displayName}...")
        return withContext(Dispatchers.IO) {
            try {
                val cmd = buildCommand(type, "start", profile, cpu, memory, disk)
                appendOutput("$ ${cmd.joinToString(" ")}")
                val success = runProcess(cmd)
                _actionState.value =
                    if (success) {
                        EngineActionState.Done(true, "${type.displayName} started")
                    } else {
                        EngineActionState.Done(false, "Failed to start ${type.displayName}")
                    }
                success
            } catch (e: Exception) {
                logger.error("Failed to start engine", e)
                appendOutput("Error: ${e.message}")
                _actionState.value = EngineActionState.Done(false, e.message ?: "Unknown error")
                false
            }
        }
    }

    suspend fun stopEngine(
        type: EngineType,
        profile: String? = null,
    ): Boolean {
        _output.value = ""
        _actionState.value = EngineActionState.Running("Stopping ${type.displayName}...")
        return withContext(Dispatchers.IO) {
            try {
                val cmd = buildCommand(type, "stop", profile)
                appendOutput("$ ${cmd.joinToString(" ")}")
                val success = runProcess(cmd)
                _actionState.value =
                    if (success) {
                        EngineActionState.Done(true, "${type.displayName} stopped")
                    } else {
                        EngineActionState.Done(false, "Failed to stop ${type.displayName}")
                    }
                success
            } catch (e: Exception) {
                logger.error("Failed to stop engine", e)
                appendOutput("Error: ${e.message}")
                _actionState.value = EngineActionState.Done(false, e.message ?: "Unknown error")
                false
            }
        }
    }

    private fun buildCommand(
        type: EngineType,
        action: String,
        profile: String?,
        cpu: Int? = null,
        memory: Int? = null,
        disk: Int? = null,
    ): List<String> =
        when (type) {
            EngineType.COLIMA -> {
                val cmd = mutableListOf("colima", action)
                if (!profile.isNullOrEmpty() && profile != "default") {
                    cmd.addAll(listOf("--profile", profile))
                }
                if (action == "start") {
                    cpu?.let { cmd.addAll(listOf("--cpu", it.toString())) }
                    memory?.let { cmd.addAll(listOf("--memory", it.toString())) }
                    disk?.let { cmd.addAll(listOf("--disk", it.toString())) }
                }
                cmd
            }
            EngineType.DOCKER_DESKTOP ->
                when (action) {
                    "start" -> listOf("open", "-a", "Docker")
                    "stop" -> listOf("osascript", "-e", "quit app \"Docker\"")
                    else -> listOf("echo", "unsupported")
                }
            EngineType.ORBSTACK ->
                when (action) {
                    "start" -> listOf("open", "-a", "OrbStack")
                    "stop" -> listOf("osascript", "-e", "quit app \"OrbStack\"")
                    else -> listOf("echo", "unsupported")
                }
            EngineType.LIMA ->
                when (action) {
                    "start" -> listOf("limactl", "start")
                    "stop" -> listOf("limactl", "stop")
                    else -> listOf("echo", "unsupported")
                }
            EngineType.RANCHER_DESKTOP ->
                when (action) {
                    "start" -> listOf("open", "-a", "Rancher Desktop")
                    "stop" -> listOf("osascript", "-e", "quit app \"Rancher Desktop\"")
                    else -> listOf("echo", "unsupported")
                }
            EngineType.UNKNOWN -> listOf("echo", "Unknown engine")
        }

    private fun runProcess(cmd: List<String>): Boolean {
        val proc =
            ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start()

        proc.inputStream.bufferedReader().forEachLine { line ->
            appendOutput(line)
        }

        return proc.waitFor() == 0
    }
}
