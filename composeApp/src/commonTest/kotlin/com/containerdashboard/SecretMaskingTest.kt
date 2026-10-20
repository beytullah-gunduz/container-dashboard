package com.containerdashboard

import com.containerdashboard.ui.components.maskSecretEnvValues
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The Raw JSON tab's masking of secret env values (review-2026-10, S5.3). */
class SecretMaskingTest {
    private val inspect =
        """
        {
          "Config" : {
            "Env" : [ "PATH=/usr/local/bin:/usr/bin", "POSTGRES_PASSWORD=hunter2", "api_token=abc\"def", "DEMO=1" ],
            "Labels" : { "com.example.secret" : "not-an-env-pair" }
          }
        }
        """.trimIndent()

    @Test
    fun `secret env values are masked and nothing else changes`() {
        val masked = maskSecretEnvValues(inspect)

        assertFalse("hunter2" in masked)
        assertFalse("abc" in masked, "a value with an escaped quote is masked whole")
        assertTrue("\"POSTGRES_PASSWORD=••••••••\"" in masked)
        assertTrue("\"api_token=••••••••\"" in masked)
        assertEquals(
            inspect
                .replace("\"POSTGRES_PASSWORD=hunter2\"", "\"POSTGRES_PASSWORD=••••••••\"")
                .replace("\"api_token=abc\\\"def\"", "\"api_token=••••••••\""),
            masked,
            "only the two secret values change; layout, other env vars and labels are kept",
        )
    }

    @Test
    fun `json without secrets comes back unchanged`() {
        val plain = """{ "Env" : [ "PATH=/bin", "DEMO=1" ], "Name" : "/demo" }"""
        assertEquals(plain, maskSecretEnvValues(plain))
    }
}
