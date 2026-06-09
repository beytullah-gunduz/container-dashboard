package com.containerdashboard.data.repository

// Pure shell-exec result handling extracted from DesktopDockerRepository so the error
// mapping is unit-testable without a Docker daemon.

/** Result of a one-shot, non-TTY exec. Bytes are raw (binary-safe); decode at the call site. */
internal class ExecResult(
    val stdout: ByteArray,
    val stderr: ByteArray,
    val exitCode: Int?,
    val timedOut: Boolean,
)

/** Map exec stderr / exit code to a friendly exception for the UI. */
internal fun mapShellError(
    result: ExecResult,
    path: String,
): Exception {
    val message =
        result.stderr
            .toString(Charsets.UTF_8)
            .lineSequence()
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
    return when {
        "executable file not found" in message || result.exitCode == 126 || result.exitCode == 127 ->
            IllegalStateException("This container has no shell/coreutils, so its filesystem can't be browsed.")
        "Permission denied" in message -> SecurityException("Permission denied: $path")
        "No such file or directory" in message -> java.io.FileNotFoundException(path)
        "is not running" in message -> IllegalStateException("Container is no longer running.")
        message.isNotEmpty() -> IllegalStateException(message)
        else -> IllegalStateException("Command failed (exit ${result.exitCode}) for $path")
    }
}
