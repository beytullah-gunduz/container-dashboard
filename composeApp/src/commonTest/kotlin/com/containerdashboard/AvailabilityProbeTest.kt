package com.containerdashboard

import com.containerdashboard.data.repository.probeWithRetry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Locks in the startup retry-grace that keeps the UI in "connecting" instead of flashing
 * "engine not available" when a reachable daemon hasn't answered its first ping yet. The
 * `delay` between attempts is virtual under [runTest], so these complete instantly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AvailabilityProbeTest {
    @Test
    fun `succeeds on the first attempt without retrying`() =
        runTest {
            var calls = 0
            val result =
                probeWithRetry(retries = 4, retryDelayMs = 350) {
                    calls++
                    true
                }
            assertTrue(result)
            assertEquals(1, calls, "a first-attempt success must not retry")
        }

    @Test
    fun `rides out a reachable-but-silent daemon and succeeds within budget`() =
        runTest {
            var calls = 0
            val result =
                probeWithRetry(retries = 4, retryDelayMs = 350) {
                    calls++
                    calls >= 3 // fail twice (warm-up), then answer on the third ping
                }
            assertTrue(result, "should report available once a ping finally succeeds")
            assertEquals(3, calls)
        }

    @Test
    fun `reports unavailable only after exhausting the retry budget`() =
        runTest {
            var calls = 0
            val result =
                probeWithRetry(retries = 4, retryDelayMs = 350) {
                    calls++
                    false
                }
            assertFalse(result, "an engine that never answers must be reported unavailable")
            assertEquals(5, calls, "one initial attempt plus four retries")
        }
}
