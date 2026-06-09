package com.containerdashboard

import com.containerdashboard.data.repository.calculateCpuPercent
import com.containerdashboard.data.repository.extractDiskIo
import com.containerdashboard.data.repository.extractNetworkIo
import com.github.dockerjava.api.model.Statistics
import com.github.dockerjava.core.DockerClientConfig
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers the pure stats arithmetic extracted from DesktopDockerRepository. Statistics objects
 * are built through docker-java's own deserializer so the JSON shapes match what real daemons
 * send (cgroup v1 vs v2 differ in which CPU fields are present).
 */
class DockerStatsMathTest {
    private val mapper = DockerClientConfig.getDefaultObjectMapper()

    private fun stats(json: String): Statistics = mapper.readValue(json, Statistics::class.java)

    // --- calculateCpuPercent ---

    @Test
    fun `cgroup v1 shape uses percpuUsage size as the cpu count`() {
        val s =
            stats(
                """
                {"cpu_stats":{"cpu_usage":{"total_usage":400,"percpu_usage":[100,100,100,100]},
                              "system_cpu_usage":2000},
                 "precpu_stats":{"cpu_usage":{"total_usage":200},"system_cpu_usage":1000}}
                """.trimIndent(),
            )
        // cpuDelta 200 / systemDelta 1000 * 4 cpus * 100 = 80%
        assertEquals(80.0, calculateCpuPercent(s, hostProcessors = 1))
    }

    @Test
    fun `cgroup v2 shape without percpuUsage falls back to onlineCpus`() {
        val s =
            stats(
                """
                {"cpu_stats":{"cpu_usage":{"total_usage":300},"system_cpu_usage":1000,"online_cpus":8},
                 "precpu_stats":{"cpu_usage":{"total_usage":100},"system_cpu_usage":600}}
                """.trimIndent(),
            )
        // cpuDelta 200 / systemDelta 400 * 8 cpus * 100 = 400%
        assertEquals(400.0, calculateCpuPercent(s, hostProcessors = 1))
    }

    @Test
    fun `cgroup v2 shape without percpuUsage or onlineCpus falls back to host processors`() {
        val s =
            stats(
                """
                {"cpu_stats":{"cpu_usage":{"total_usage":200},"system_cpu_usage":2000},
                 "precpu_stats":{"cpu_usage":{"total_usage":100},"system_cpu_usage":1000}}
                """.trimIndent(),
            )
        // cpuDelta 100 / systemDelta 1000 * 6 host cpus * 100 = 60% — NOT capped at 100/1-cpu.
        assertEquals(60.0, calculateCpuPercent(s, hostProcessors = 6), absoluteTolerance = 1e-9)
    }

    @Test
    fun `default host-processor fallback uses the runtime cpu count, not 1`() {
        val s =
            stats(
                """
                {"cpu_stats":{"cpu_usage":{"total_usage":200},"system_cpu_usage":2000},
                 "precpu_stats":{"cpu_usage":{"total_usage":100},"system_cpu_usage":1000}}
                """.trimIndent(),
            )
        val expected = 0.1 * Runtime.getRuntime().availableProcessors() * 100.0
        assertEquals(expected, calculateCpuPercent(s))
    }

    @Test
    fun `zero system delta returns zero`() {
        val s =
            stats(
                """
                {"cpu_stats":{"cpu_usage":{"total_usage":400},"system_cpu_usage":1000},
                 "precpu_stats":{"cpu_usage":{"total_usage":200},"system_cpu_usage":1000}}
                """.trimIndent(),
            )
        assertEquals(0.0, calculateCpuPercent(s, hostProcessors = 4))
    }

    @Test
    fun `negative deltas return zero`() {
        // Counter went backwards (e.g. container restart between samples).
        val negativeCpu =
            stats(
                """
                {"cpu_stats":{"cpu_usage":{"total_usage":100},"system_cpu_usage":2000},
                 "precpu_stats":{"cpu_usage":{"total_usage":200},"system_cpu_usage":1000}}
                """.trimIndent(),
            )
        assertEquals(0.0, calculateCpuPercent(negativeCpu, hostProcessors = 4))

        val negativeSystem =
            stats(
                """
                {"cpu_stats":{"cpu_usage":{"total_usage":400},"system_cpu_usage":500},
                 "precpu_stats":{"cpu_usage":{"total_usage":200},"system_cpu_usage":1000}}
                """.trimIndent(),
            )
        assertEquals(0.0, calculateCpuPercent(negativeSystem, hostProcessors = 4))
    }

    @Test
    fun `missing cpu or precpu stats return zero`() {
        assertEquals(0.0, calculateCpuPercent(stats("{}"), hostProcessors = 4))
        assertEquals(
            0.0,
            calculateCpuPercent(
                stats("""{"cpu_stats":{"cpu_usage":{"total_usage":400},"system_cpu_usage":2000}}"""),
                hostProcessors = 4,
            ),
        )
    }

    @Test
    fun `empty percpuUsage list yields zero percent`() {
        // Pins current behavior: an empty (non-null) percpu_usage list gives numCpus = 0, so the
        // result is 0% instead of falling through to onlineCpus / host processors.
        val s =
            stats(
                """
                {"cpu_stats":{"cpu_usage":{"total_usage":400,"percpu_usage":[]},
                              "system_cpu_usage":2000,"online_cpus":8},
                 "precpu_stats":{"cpu_usage":{"total_usage":200},"system_cpu_usage":1000}}
                """.trimIndent(),
            )
        assertEquals(0.0, calculateCpuPercent(s, hostProcessors = 4))
    }

    // --- extractDiskIo ---

    @Test
    fun `disk io sums read and write entries and ignores other ops`() {
        val s =
            stats(
                """
                {"blkio_stats":{"io_service_bytes_recursive":[
                    {"major":8,"minor":0,"op":"Read","value":100},
                    {"major":8,"minor":16,"op":"Read","value":50},
                    {"major":8,"minor":0,"op":"Write","value":200},
                    {"major":8,"minor":0,"op":"Sync","value":10},
                    {"major":8,"minor":0,"op":"Total","value":360}]}}
                """.trimIndent(),
            )
        assertEquals(150L to 200L, extractDiskIo(s))
    }

    @Test
    fun `disk io skips entries with missing op or value`() {
        val s =
            stats(
                """
                {"blkio_stats":{"io_service_bytes_recursive":[
                    {"major":8,"minor":0,"op":"Read"},
                    {"major":8,"minor":0,"value":5},
                    {"major":8,"minor":0,"op":"Write","value":7}]}}
                """.trimIndent(),
            )
        assertEquals(0L to 7L, extractDiskIo(s))
    }

    @Test
    fun `disk io returns zeros for missing or empty blkio stats`() {
        assertEquals(0L to 0L, extractDiskIo(stats("{}")))
        // cgroup v2 / rootless backends report an empty list.
        assertEquals(
            0L to 0L,
            extractDiskIo(stats("""{"blkio_stats":{"io_service_bytes_recursive":[]}}""")),
        )
    }

    // --- extractNetworkIo ---

    @Test
    fun `network io sums all interfaces`() {
        val s =
            stats(
                """
                {"networks":{
                    "eth0":{"rx_bytes":100,"tx_bytes":10},
                    "eth1":{"rx_bytes":50,"tx_bytes":5}}}
                """.trimIndent(),
            )
        assertEquals(150L to 15L, extractNetworkIo(s))
    }

    @Test
    fun `network io treats missing counters as zero`() {
        val s = stats("""{"networks":{"eth0":{"tx_bytes":7}}}""")
        assertEquals(0L to 7L, extractNetworkIo(s))
    }

    @Test
    fun `network io returns zeros for missing or empty networks map`() {
        assertEquals(0L to 0L, extractNetworkIo(stats("{}")))
        assertEquals(0L to 0L, extractNetworkIo(stats("""{"networks":{}}""")))
    }
}
