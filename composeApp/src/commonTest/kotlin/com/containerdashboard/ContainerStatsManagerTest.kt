package com.containerdashboard

import com.containerdashboard.data.models.ContainerStats
import com.containerdashboard.data.repository.ContainerStatsManager
import com.containerdashboard.data.repository.aggregateContainerStats
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ContainerStatsManagerTest {
    private fun stat(
        id: String,
        cpu: Double = 1.0,
    ) = ContainerStats(containerId = id, containerName = id, cpuPercent = cpu, memoryUsage = 0L, memoryLimit = 0L)

    // ---------------------------------------------------------------------
    // aggregateContainerStats — incremental fan-in
    // ---------------------------------------------------------------------

    @Test
    fun `aggregate emits a fast container before a slow one (incremental, not combine)`() =
        runTest {
            val statsFor: (String, String) -> Flow<ContainerStats> = { id, _ ->
                if (id == "slow") {
                    flow {
                        delay(1_000)
                        emit(stat("slow"))
                    }
                } else {
                    flowOf(stat(id))
                }
            }
            val idSets =
                aggregateContainerStats(
                    running = flowOf(listOf("fast" to "fast", "slow" to "slow")),
                    statsFor = statsFor,
                ).toList().map { snapshot -> snapshot.map { it.containerId }.toSet() }

            // With `combine`, "fast" could never appear without "slow"; merge+scan lets it.
            assertTrue(idSets.contains(setOf("fast")), "fast should appear alone before slow: $idSets")
            assertTrue(
                idSets.indexOf(setOf("fast")) < idSets.indexOf(setOf("fast", "slow")),
                "fast-alone must precede fast+slow: $idSets",
            )
        }

    @Test
    fun `aggregate drops a container that leaves the running set`() =
        runTest {
            val statsFor: (String, String) -> Flow<ContainerStats> = { id, _ -> flowOf(stat(id)) }
            val running =
                flow {
                    emit(listOf("a" to "a", "b" to "b"))
                    delay(10)
                    emit(listOf("a" to "a"))
                }
            val emissions = aggregateContainerStats(running, statsFor).toList()
            assertEquals(setOf("a"), emissions.last().map { it.containerId }.toSet())
        }

    // ---------------------------------------------------------------------
    // ContainerStatsManager — caching & eviction
    // ---------------------------------------------------------------------

    @Test
    fun `statsFor returns the same cached stream instance per id`() =
        runTest {
            val manager = ContainerStatsManager(backgroundScope, { _, _ -> emptyFlow() })
            val first = manager.statsFor("c1", "c1")
            assertSame(first, manager.statsFor("c1", "c1"))
            assertSame(first, manager.statsFor("c1", "renamed")) // same instance regardless of name
        }

    @Test
    fun `retain evicts ids that are no longer live`() =
        runTest {
            val manager = ContainerStatsManager(backgroundScope, { _, _ -> emptyFlow() })
            manager.statsFor("a", "a")
            manager.statsFor("b", "b")
            assertEquals(setOf("a", "b"), manager.cachedIds())
            manager.retain(setOf("a"))
            assertEquals(setOf("a"), manager.cachedIds())
        }
}
