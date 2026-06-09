package com.containerdashboard

import com.containerdashboard.data.repository.ExecResult
import com.containerdashboard.data.repository.mapShellError
import java.io.FileNotFoundException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Covers the exec-stderr → exception mapping extracted from DesktopDockerRepository. The
 * stderr strings mirror what real daemons / busybox emit; the mapping drives the error text
 * users see in the file browser.
 */
class DockerShellExecTest {
    private fun result(
        stderr: String = "",
        exitCode: Int? = 1,
    ) = ExecResult(
        stdout = ByteArray(0),
        stderr = stderr.toByteArray(Charsets.UTF_8),
        exitCode = exitCode,
        timedOut = false,
    )

    @Test
    fun `executable not found message maps to no-shell explanation`() {
        val stderr =
            "OCI runtime exec failed: exec failed: unable to start container process: " +
                "exec: \"ls\": executable file not found in \$PATH: unknown"
        val e = mapShellError(result(stderr, exitCode = 1), "/etc")
        assertIs<IllegalStateException>(e)
        assertEquals("This container has no shell/coreutils, so its filesystem can't be browsed.", e.message)
    }

    @Test
    fun `exit codes 126 and 127 map to no-shell explanation even with empty stderr`() {
        for (code in listOf(126, 127)) {
            val e = mapShellError(result("", exitCode = code), "/etc")
            assertIs<IllegalStateException>(e)
            assertEquals("This container has no shell/coreutils, so its filesystem can't be browsed.", e.message)
        }
    }

    @Test
    fun `exit code 126 wins over permission denied stderr`() {
        // Pins precedence: the no-shell branch checks exit codes before the message patterns,
        // so a permission-denied message paired with exit 126 reports "no shell/coreutils".
        val e = mapShellError(result("ls: /etc/shadow: Permission denied", exitCode = 126), "/etc/shadow")
        assertIs<IllegalStateException>(e)
        assertEquals("This container has no shell/coreutils, so its filesystem can't be browsed.", e.message)
    }

    @Test
    fun `permission denied maps to SecurityException carrying the path`() {
        val e = mapShellError(result("ls: /etc/shadow: Permission denied", exitCode = 1), "/etc/shadow")
        assertIs<SecurityException>(e)
        assertEquals("Permission denied: /etc/shadow", e.message)
    }

    @Test
    fun `no such file maps to FileNotFoundException with the path as message`() {
        val e = mapShellError(result("ls: /missing: No such file or directory", exitCode = 1), "/missing")
        assertIs<FileNotFoundException>(e)
        assertEquals("/missing", e.message)
    }

    @Test
    fun `container not running maps to a friendly stopped message`() {
        val e = mapShellError(result("Error response from daemon: container abc is not running", exitCode = 1), "/etc")
        assertIs<IllegalStateException>(e)
        assertEquals("Container is no longer running.", e.message)
    }

    @Test
    fun `unrecognized stderr surfaces its first non-blank line verbatim`() {
        val e = mapShellError(result("\n\nls: something odd happened\nsecond line", exitCode = 2), "/etc")
        assertIs<IllegalStateException>(e)
        assertEquals("ls: something odd happened", e.message)
    }

    @Test
    fun `empty stderr falls back to generic exit-code message`() {
        val e = mapShellError(result("", exitCode = 2), "/etc")
        assertIs<IllegalStateException>(e)
        assertEquals("Command failed (exit 2) for /etc", e.message)
    }

    @Test
    fun `blank-only stderr with null exit code reports exit null`() {
        val e = mapShellError(result("   \n\t\n", exitCode = null), "/var")
        assertIs<IllegalStateException>(e)
        assertEquals("Command failed (exit null) for /var", e.message)
    }
}
