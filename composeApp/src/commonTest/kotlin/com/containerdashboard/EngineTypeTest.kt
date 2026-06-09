package com.containerdashboard

import com.containerdashboard.data.engine.colimaProfileFromHost
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [colimaProfileFromHost] profile extraction and the profile validation
 * regex that guards argv construction in EngineManager (S3.5).
 */
class EngineTypeTest {
    // ------------------------------------------------------------------
    // colimaProfileFromHost
    // ------------------------------------------------------------------

    @Test
    fun `returns null for non-colima host`() {
        assertNull(colimaProfileFromHost("unix:///var/run/docker.sock"))
    }

    @Test
    fun `returns null for colima path without a profile segment`() {
        // Paths that contain /.colima/ but have nothing after it
        assertNull(colimaProfileFromHost("unix:///.colima//docker.sock"))
    }

    @Test
    fun `extracts default profile from colima host`() {
        assertEquals(
            "default",
            colimaProfileFromHost("unix:///${""}/.colima/default/docker.sock"),
        )
    }

    @Test
    fun `extracts custom profile from colima host`() {
        assertEquals(
            "my-profile",
            colimaProfileFromHost("unix:///Users/alice/.colima/my-profile/docker.sock"),
        )
    }

    @Test
    fun `returns null when profile segment is empty after colima marker`() {
        assertNull(colimaProfileFromHost("unix:///home/bob/.colima/"))
    }

    // ------------------------------------------------------------------
    // Colima profile validation regex (mirrors COLIMA_PROFILE_REGEX in EngineManager)
    // ------------------------------------------------------------------

    private val profileRegex = Regex("^[a-zA-Z0-9_-]{1,64}$")

    @Test
    fun `valid profiles match the regex`() {
        assertTrue(profileRegex.matches("default"))
        assertTrue(profileRegex.matches("my-profile"))
        assertTrue(profileRegex.matches("test_123"))
        assertTrue(profileRegex.matches("A"))
        assertTrue(profileRegex.matches("a".repeat(64)))
    }

    @Test
    fun `invalid profiles do not match the regex`() {
        assertFalse(profileRegex.matches("")) // empty
        assertFalse(profileRegex.matches("a".repeat(65))) // too long
        assertFalse(profileRegex.matches("has space")) // space
        assertFalse(profileRegex.matches("has/slash")) // slash
        assertFalse(profileRegex.matches("has\nnewline")) // newline
        assertFalse(profileRegex.matches("../traversal")) // path traversal
        assertFalse(profileRegex.matches("; rm -rf /")) // shell metachar
    }
}
