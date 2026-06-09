package com.containerdashboard.data.engine

import com.containerdashboard.ui.util.isLinuxHost
import com.containerdashboard.ui.util.isMacHost
import com.containerdashboard.ui.util.isWindowsHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

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

/** Allowed characters for a Colima profile name passed to `--profile`. */
private val COLIMA_PROFILE_REGEX = Regex("^[a-zA-Z0-9_-]{1,64}$")

object EngineManager {
    private val logger = LoggerFactory.getLogger(EngineManager::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    // Named timeout budgets. Colima start can take 30-60 s; give it generous headroom.
    private const val TIMEOUT_START_SECONDS = 180L
    private const val TIMEOUT_STOP_SECONDS = 60L
    private const val TIMEOUT_STATUS_SECONDS = 15L

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
            var proc: Process? = null
            try {
                val cmd = mutableListOf("colima", "status", "--json")
                if (profile != "default") {
                    cmd.addAll(listOf("--profile", profile))
                }
                proc =
                    ProcessBuilder(cmd)
                        .redirectErrorStream(true)
                        .start()
                // Drain stdout on a separate thread so readText() can't block the timeout wait.
                val textHolder = arrayOfNulls<String>(1)
                val drainThread =
                    thread(name = "colima-status-drain") {
                        textHolder[0] = proc.inputStream.bufferedReader().readText()
                    }
                val finished = proc.waitFor(TIMEOUT_STATUS_SECONDS, TimeUnit.SECONDS)
                if (!finished) {
                    proc.destroyForcibly()
                    drainThread.interrupt()
                    drainThread.join(1_000)
                    logger.warn("colima status timed out for profile '{}'", profile)
                    return@withContext null
                }
                drainThread.join(5_000)
                if (proc.exitValue() != 0) return@withContext null
                val text = textHolder[0] ?: return@withContext null
                json.decodeFromString<ColimaStatus>(text)
            } catch (e: Exception) {
                proc?.destroyForcibly()
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
        // S3.5 — validate Colima profile before passing to argv
        if (type == EngineType.COLIMA && !profile.isNullOrEmpty() && profile != "default") {
            if (!COLIMA_PROFILE_REGEX.matches(profile)) {
                val msg = "Invalid Colima profile name: \"$profile\""
                logger.warn(msg)
                _actionState.value = EngineActionState.Done(false, msg)
                return false
            }
        }
        _actionState.value = EngineActionState.Running("Starting ${type.displayName}...")
        return withContext(Dispatchers.IO) {
            try {
                val cmd =
                    buildCommand(type, "start", profile, cpu, memory, disk)
                        ?: run {
                            val osName = currentOsName()
                            val msg = "${type.displayName} is not supported on $osName"
                            logger.warn(msg)
                            _actionState.value = EngineActionState.Done(false, msg)
                            return@withContext false
                        }
                appendOutput("$ ${cmd.joinToString(" ")}")
                val success = runProcess(cmd, TIMEOUT_START_SECONDS)
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
        // S3.5 — validate Colima profile before passing to argv
        if (type == EngineType.COLIMA && !profile.isNullOrEmpty() && profile != "default") {
            if (!COLIMA_PROFILE_REGEX.matches(profile)) {
                val msg = "Invalid Colima profile name: \"$profile\""
                logger.warn(msg)
                _actionState.value = EngineActionState.Done(false, msg)
                return false
            }
        }
        _actionState.value = EngineActionState.Running("Stopping ${type.displayName}...")
        return withContext(Dispatchers.IO) {
            try {
                val cmd =
                    buildCommand(type, "stop", profile)
                        ?: run {
                            val osName = currentOsName()
                            val msg = "${type.displayName} is not supported on $osName"
                            logger.warn(msg)
                            _actionState.value = EngineActionState.Done(false, msg)
                            return@withContext false
                        }
                appendOutput("$ ${cmd.joinToString(" ")}")
                val success = runProcess(cmd, TIMEOUT_STOP_SECONDS)
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

    /** Returns a short human-readable OS name for error messages. */
    private fun currentOsName(): String =
        when {
            isMacHost -> "macOS"
            isWindowsHost -> "Windows"
            isLinuxHost -> "Linux"
            else -> System.getProperty("os.name", "this OS")
        }

    /**
     * Returns the argv list for the requested engine action, or `null` when the
     * engine/action combination is not supported on the current OS.
     *
     * Callers treat `null` as an "unsupported" signal and surface a clear
     * [EngineActionState.Done] failure rather than launching a process that would
     * produce an opaque IOException.
     */
    private fun buildCommand(
        type: EngineType,
        action: String,
        profile: String?,
        cpu: Int? = null,
        memory: Int? = null,
        disk: Int? = null,
    ): List<String>? =
        when (type) {
            EngineType.COLIMA -> {
                // limactl/colima is available on macOS and Linux; not on Windows.
                if (isWindowsHost) return null
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
                when {
                    // macOS: launch/quit the .app bundle
                    isMacHost && action == "start" -> listOf("open", "-a", "Docker")
                    isMacHost && action == "stop" -> listOf("osascript", "-e", "quit app \"Docker\"")
                    // Windows: Docker Desktop ships a CLI since v4.x
                    isWindowsHost && action == "start" -> listOf("docker", "desktop", "start")
                    isWindowsHost && action == "stop" -> listOf("docker", "desktop", "stop")
                    // Linux: Docker Desktop for Linux is not widely supported; unsupported.
                    else -> null
                }
            EngineType.ORBSTACK ->
                // OrbStack is macOS-only.
                when {
                    isMacHost && action == "start" -> listOf("open", "-a", "OrbStack")
                    isMacHost && action == "stop" -> listOf("osascript", "-e", "quit app \"OrbStack\"")
                    else -> null
                }
            EngineType.LIMA ->
                // limactl works on macOS and Linux; not on Windows.
                when {
                    !isWindowsHost && action == "start" -> listOf("limactl", "start")
                    !isWindowsHost && action == "stop" -> listOf("limactl", "stop")
                    else -> null
                }
            EngineType.RANCHER_DESKTOP ->
                // Rancher Desktop GUI is launched via open -a on macOS; no stable
                // cross-platform CLI is available for start/stop — unsupported elsewhere.
                when {
                    isMacHost && action == "start" -> listOf("open", "-a", "Rancher Desktop")
                    isMacHost && action == "stop" -> listOf("osascript", "-e", "quit app \"Rancher Desktop\"")
                    else -> null
                }
            EngineType.UNKNOWN -> null
        }

    private fun runProcess(
        cmd: List<String>,
        timeoutSeconds: Long,
    ): Boolean {
        val proc =
            ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start()

        // Drain stdout on a separate thread so forEachLine can't block the timeout wait.
        // appendOutput is called line-by-line so the UI receives live output as it arrives.
        val drainThread =
            thread(name = "engine-drain") {
                try {
                    proc.inputStream.bufferedReader().forEachLine { line ->
                        appendOutput(line)
                    }
                } catch (_: Exception) {
                    // Stream closed on timeout/destroy — expected.
                }
            }

        val finished = proc.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!finished) {
            proc.destroyForcibly()
            drainThread.interrupt()
            drainThread.join(2_000)
            val msg = "Process timed out after ${timeoutSeconds}s: ${cmd.joinToString(" ")}"
            logger.warn(msg)
            appendOutput("[timed out after ${timeoutSeconds}s]")
            return false
        }
        drainThread.join(5_000) // let any remaining output flush before returning
        return proc.exitValue() == 0
    }
}
