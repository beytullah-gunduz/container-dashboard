package com.containerdashboard.data.repository

import kotlinx.coroutines.delay

/**
 * Ping up to `1 + [retries]` times (one initial attempt plus [retries] retries), waiting
 * [retryDelayMs] between attempts, returning `true` as soon as [ping] succeeds and `false` only
 * after every attempt has failed.
 *
 * Factored out of the desktop availability poll so the "ride out a daemon that is reachable but
 * not yet answering" behavior can be unit-tested without a real Docker client. A freshly-launched
 * engine often accepts its socket before its HTTP API responds; reporting it as down during that
 * window is exactly what makes the dashboard flash "engine not available" at users whose engine is
 * actually running.
 */
internal suspend fun probeWithRetry(
    retries: Int,
    retryDelayMs: Long,
    ping: suspend () -> Boolean,
): Boolean {
    repeat(1 + retries) { attempt ->
        if (attempt > 0) {
            delay(retryDelayMs)
        }
        if (ping()) {
            return true
        }
    }
    return false
}
