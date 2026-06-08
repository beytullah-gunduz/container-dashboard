package com.containerdashboard.data.repository

import com.containerdashboard.data.models.ContainerStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.shareIn
import java.util.concurrent.ConcurrentHashMap

/**
 * Aggregate a changing set of running containers' per-container stats flows into a map keyed by
 * container id, INCREMENTALLY: each container appears as soon as ITS stream emits.
 *
 * Pure (no I/O, no sharing/sampling) so it is unit-testable with a fake [statsFor]. Uses
 * `merge` + `scan` rather than `combine` — `combine` would withhold all output until every
 * stream has emitted once, blanking every cell on the slowest/stalled stream.
 *
 * Shrinking the running set is free: `flatMapLatest` re-seeds the `scan` map to empty on every
 * change, and only currently-running ids feed the new `merge`, so dropped ids disappear.
 *
 * The non-empty branch drops `scan`'s initial empty map. With a non-empty running set, an empty
 * emission is indistinguishable downstream from "no running containers" and would flash that state
 * during the first sample's warm-up (and on every set change). An empty map is therefore emitted
 * ONLY by the genuinely-empty branch — so consumers can treat empty as "nothing running" and
 * "no emission yet" as "still collecting."
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun aggregateContainerStatsMap(
    running: Flow<List<Pair<String, String>>>,
    statsFor: (id: String, name: String) -> Flow<ContainerStats>,
): Flow<Map<String, ContainerStats>> =
    running.flatMapLatest { current ->
        if (current.isEmpty()) {
            flowOf(emptyMap())
        } else {
            current
                .map { (id, name) -> statsFor(id, name).map { stat -> id to stat } }
                .merge()
                .scan(emptyMap<String, ContainerStats>()) { acc, (id, stat) -> acc + (id to stat) }
                .drop(1) // suppress scan's seed empty; wait for the first real sample
        }
    }

/** [aggregateContainerStatsMap] as an ordered list — for engine-wide consumers (Monitoring, tray). */
internal fun aggregateContainerStats(
    running: Flow<List<Pair<String, String>>>,
    statsFor: (id: String, name: String) -> Flow<ContainerStats>,
): Flow<List<ContainerStats>> = aggregateContainerStatsMap(running, statsFor).map { it.values.toList() }

/**
 * Per-container, reference-counted hot stats streams. One Docker stats stream per container id,
 * shared across all consumers (Containers list, Monitoring, tray): subscribing to the same id
 * attaches to the same underlying stream, and re-subscribing to an unchanged id replays its last
 * value instantly (no re-warmup).
 *
 * @param scope repo-lifetime scope (Dispatchers.IO + SupervisorJob).
 * @param rawStatsStream opens ONE persistent docker stats stream for an id, mapped to
 *   [ContainerStats]. Must throw (not complete) on transient failure so [retryWhen] can recover it.
 *   Not pre-sampled — this manager owns sampling so every consumer shares one source cadence.
 */
class ContainerStatsManager(
    private val scope: CoroutineScope,
    private val rawStatsStream: (id: String, name: String) -> Flow<ContainerStats>,
    private val sampleMs: Long = 1_000L,
    private val stopTimeoutMs: Long = 5_000L,
    private val replayExpirationMs: Long = 10_000L,
) {
    private val cache = ConcurrentHashMap<String, SharedFlow<ContainerStats>>()

    /** Hot, cached, ref-counted stats stream for [id]. Shared across all consumers. */
    @OptIn(FlowPreview::class)
    fun statsFor(
        id: String,
        name: String,
    ): SharedFlow<ContainerStats> =
        // computeIfAbsent (NOT getOrPut): atomic, so racing callers can't create two streams
        // for the same id (which would break dedup and open duplicate Docker connections).
        cache.computeIfAbsent(id) {
            val source = if (sampleMs > 0) rawStatsStream(id, name).sample(sampleMs) else rawStatsStream(id, name)
            source
                // Self-heal a dropped/errored stream without waiting for the running set to change.
                // Retries only while subscribed; WhileSubscribed cancels the loop when nobody listens.
                .retryWhen { _, attempt ->
                    delay(minOf(BASE_BACKOFF_MS shl attempt.toInt().coerceIn(0, 5), MAX_BACKOFF_MS))
                    true
                }.shareIn(
                    scope = scope,
                    started =
                        SharingStarted.WhileSubscribed(
                            stopTimeoutMillis = stopTimeoutMs,
                            // Must be >= stopTimeoutMs: keeps replay for a quick re-subscribe but
                            // drops a stopped container's stale value once truly abandoned.
                            replayExpirationMillis = replayExpirationMs,
                        ),
                    replay = 1,
                )
        }

    /** Drop cached streams whose id is not in [live]. Idle streams have already stopped via
     *  WhileSubscribed; this just releases the (small) cache entries so the map can't grow without
     *  bound as containers come and go. */
    fun retain(live: Set<String>) {
        cache.keys.retainAll(live)
    }

    /** Test/diagnostic view of which ids are currently cached. */
    internal fun cachedIds(): Set<String> = cache.keys.toSet()

    private companion object {
        const val BASE_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 30_000L
    }
}
