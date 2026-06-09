package com.containerdashboard

import com.containerdashboard.data.engine.EngineManager
import com.containerdashboard.data.engine.EngineType
import com.containerdashboard.ui.util.isMacHost
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins the exact argv lists [EngineManager.buildCommand] produces per engine x action (S8.3).
 *
 * `buildCommand` branches on the *host* OS (`isMacHost` / `isWindowsHost`), so every test
 * guards with an early return when not on macOS — on other OSes the expectations below
 * would legitimately differ rather than indicate a regression. Profile *extraction* and
 * the profile-validation regex are covered separately in commonTest's `EngineTypeTest`.
 */
class EngineManagerBuildCommandTest {
    // ------------------------------------------------------------------
    // Colima
    // ------------------------------------------------------------------

    @Test
    fun `colima start with null profile omits the profile flag`() {
        if (!isMacHost) return
        assertEquals(
            listOf("colima", "start"),
            EngineManager.buildCommand(EngineType.COLIMA, "start", profile = null),
        )
    }

    @Test
    fun `colima start with default profile omits the profile flag`() {
        if (!isMacHost) return
        assertEquals(
            listOf("colima", "start"),
            EngineManager.buildCommand(EngineType.COLIMA, "start", profile = "default"),
        )
    }

    @Test
    fun `colima start with empty profile omits the profile flag`() {
        if (!isMacHost) return
        assertEquals(
            listOf("colima", "start"),
            EngineManager.buildCommand(EngineType.COLIMA, "start", profile = ""),
        )
    }

    @Test
    fun `colima start with named profile and all resources emits every flag in order`() {
        if (!isMacHost) return
        assertEquals(
            listOf("colima", "start", "--profile", "work", "--cpu", "4", "--memory", "8", "--disk", "100"),
            EngineManager.buildCommand(
                EngineType.COLIMA,
                "start",
                profile = "work",
                cpu = 4,
                memory = 8,
                disk = 100,
            ),
        )
    }

    @Test
    fun `colima start emits only the resource flags that are set`() {
        if (!isMacHost) return
        assertEquals(
            listOf("colima", "start", "--cpu", "2"),
            EngineManager.buildCommand(EngineType.COLIMA, "start", profile = null, cpu = 2),
        )
        assertEquals(
            listOf("colima", "start", "--memory", "16", "--disk", "60"),
            EngineManager.buildCommand(EngineType.COLIMA, "start", profile = null, memory = 16, disk = 60),
        )
    }

    @Test
    fun `colima stop keeps the profile flag but ignores resource flags`() {
        if (!isMacHost) return
        assertEquals(
            listOf("colima", "stop", "--profile", "work"),
            EngineManager.buildCommand(
                EngineType.COLIMA,
                "stop",
                profile = "work",
                cpu = 4,
                memory = 8,
                disk = 100,
            ),
        )
    }

    @Test
    fun `colima stop with default profile is just colima stop`() {
        if (!isMacHost) return
        assertEquals(
            listOf("colima", "stop"),
            EngineManager.buildCommand(EngineType.COLIMA, "stop", profile = "default"),
        )
    }

    // ------------------------------------------------------------------
    // Docker Desktop (macOS: open -a / osascript)
    // ------------------------------------------------------------------

    @Test
    fun `docker desktop start uses open dash a`() {
        if (!isMacHost) return
        assertEquals(
            listOf("open", "-a", "Docker"),
            EngineManager.buildCommand(EngineType.DOCKER_DESKTOP, "start", profile = null),
        )
    }

    @Test
    fun `docker desktop stop quits the app via osascript`() {
        if (!isMacHost) return
        assertEquals(
            listOf("osascript", "-e", "quit app \"Docker\""),
            EngineManager.buildCommand(EngineType.DOCKER_DESKTOP, "stop", profile = null),
        )
    }

    // ------------------------------------------------------------------
    // OrbStack (macOS-only)
    // ------------------------------------------------------------------

    @Test
    fun `orbstack start uses open dash a`() {
        if (!isMacHost) return
        assertEquals(
            listOf("open", "-a", "OrbStack"),
            EngineManager.buildCommand(EngineType.ORBSTACK, "start", profile = null),
        )
    }

    @Test
    fun `orbstack stop quits the app via osascript`() {
        if (!isMacHost) return
        assertEquals(
            listOf("osascript", "-e", "quit app \"OrbStack\""),
            EngineManager.buildCommand(EngineType.ORBSTACK, "stop", profile = null),
        )
    }

    // ------------------------------------------------------------------
    // Lima
    // ------------------------------------------------------------------

    @Test
    fun `lima start and stop use limactl without profile or resource flags`() {
        if (!isMacHost) return
        assertEquals(
            listOf("limactl", "start"),
            EngineManager.buildCommand(EngineType.LIMA, "start", profile = "work", cpu = 4),
        )
        assertEquals(
            listOf("limactl", "stop"),
            EngineManager.buildCommand(EngineType.LIMA, "stop", profile = "work"),
        )
    }

    // ------------------------------------------------------------------
    // Rancher Desktop (macOS: open -a / osascript)
    // ------------------------------------------------------------------

    @Test
    fun `rancher desktop start uses open dash a`() {
        if (!isMacHost) return
        assertEquals(
            listOf("open", "-a", "Rancher Desktop"),
            EngineManager.buildCommand(EngineType.RANCHER_DESKTOP, "start", profile = null),
        )
    }

    @Test
    fun `rancher desktop stop quits the app via osascript`() {
        if (!isMacHost) return
        assertEquals(
            listOf("osascript", "-e", "quit app \"Rancher Desktop\""),
            EngineManager.buildCommand(EngineType.RANCHER_DESKTOP, "stop", profile = null),
        )
    }

    // ------------------------------------------------------------------
    // Unsupported combinations
    // ------------------------------------------------------------------

    @Test
    fun `unknown engine yields null for both actions`() {
        if (!isMacHost) return
        assertNull(EngineManager.buildCommand(EngineType.UNKNOWN, "start", profile = null))
        assertNull(EngineManager.buildCommand(EngineType.UNKNOWN, "stop", profile = null))
    }

    @Test
    fun `unrecognized action yields null for app-bundle engines`() {
        if (!isMacHost) return
        // Colima passes the action through verbatim; the app-bundle engines only know
        // start/stop and return null (surfaced to the user as "not supported").
        assertNull(EngineManager.buildCommand(EngineType.DOCKER_DESKTOP, "restart", profile = null))
        assertNull(EngineManager.buildCommand(EngineType.ORBSTACK, "restart", profile = null))
        assertNull(EngineManager.buildCommand(EngineType.RANCHER_DESKTOP, "restart", profile = null))
        assertNull(EngineManager.buildCommand(EngineType.LIMA, "restart", profile = null))
    }
}
