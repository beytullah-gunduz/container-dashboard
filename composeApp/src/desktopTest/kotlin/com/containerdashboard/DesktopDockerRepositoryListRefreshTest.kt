package com.containerdashboard

import com.containerdashboard.data.repository.DesktopDockerRepository
import com.containerdashboard.util.DATA_DIR_PROPERTY
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A failed list call must not read as "everything was deleted" (review-2026-10 B3): after the list
 * has loaded once, the shared list flow keeps its last list and the failure is reported once per
 * streak; before the first success there is nothing to keep, so it emits an empty list as before.
 * These tests run the real repository against a tiny in-process engine whose container list
 * starts failing after a set number of requests.
 */
class DesktopDockerRepositoryListRefreshTest {
    private lateinit var server: HttpServer
    private lateinit var repo: DesktopDockerRepository
    private val containersRequests = AtomicInteger()

    // The container list answers successfully for this many requests, then fails.
    private val failAfter = AtomicInteger()

    @BeforeTest
    fun setUp() {
        // The repository's init reads PreferenceRepository (DataStore). The desktopTest task points
        // this property at build/; refuse to run against the developer's real settings.
        assertNotNull(System.getProperty(DATA_DIR_PROPERTY), "$DATA_DIR_PROPERTY must be set for tests")

        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            try {
                handle(exchange)
            } finally {
                exchange.close()
            }
        }
        server.start()
        repo = DesktopDockerRepository("tcp://127.0.0.1:${server.address.port}")
    }

    @AfterTest
    fun tearDown() {
        repo.close()
        server.stop(0)
    }

    private fun handle(exchange: HttpExchange) {
        val path = exchange.requestURI.path.replace(Regex("^/v[0-9.]+"), "")
        when (path) {
            "/_ping" -> {
                respond(exchange, "text/plain", "OK".toByteArray())
            }

            "/events" -> {
                // A stream that the engine ends at once.
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, 0)
            }

            "/containers/json" -> {
                if (containersRequests.incrementAndGet() <= failAfter.get()) {
                    respond(exchange, "application/json", CONTAINER_LIST.toByteArray())
                } else {
                    respond(exchange, "application/json", """{"message":"fake failure"}""".toByteArray(), status = 500)
                }
            }

            else -> {
                respond(exchange, "application/json", """{"message":"not found"}""".toByteArray(), status = 404)
            }
        }
    }

    private fun respond(
        exchange: HttpExchange,
        contentType: String,
        body: ByteArray,
        status: Int = 200,
    ) {
        exchange.responseHeaders.add("Content-Type", contentType)
        exchange.sendResponseHeaders(status, body.size.toLong())
        exchange.responseBody.write(body)
    }

    @Test
    fun `a failed refresh keeps the last container list and reports it once`() =
        runBlocking {
            failAfter.set(1)
            val emissions = CopyOnWriteArrayList<List<String>>()
            val failures = CopyOnWriteArrayList<String>()
            // UNDISPATCHED: the failures collector is subscribed before the list is.
            val failuresJob =
                launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
                    repo.listRefreshFailures().collect { failures.add(it) }
                }
            val listJob =
                launch(Dispatchers.IO) {
                    repo.getContainers(all = true).collect { list -> emissions.add(list.map { it.id }) }
                }
            try {
                withTimeout(15_000) {
                    while (emissions.size < 1) delay(50)
                    // The refresh trigger has no replay, so keep nudging until the call has gone out.
                    while (containersRequests.get() < 2) {
                        repo.refreshContainers()
                        delay(100)
                    }
                    while (failures.size < 1) delay(50)
                    while (containersRequests.get() < 3) {
                        repo.refreshContainers()
                        delay(100)
                    }
                }
                // Room for a stray emission or a second report to show up.
                delay(500)
                assertEquals(listOf(listOf("c1")), emissions.toList())
                assertEquals(1, failures.size, "one report per failure streak: $failures")
                assertTrue("container list" in failures.single(), failures.single())
            } finally {
                listJob.cancel()
                failuresJob.cancel()
            }
        }

    @Test
    fun `a failure before the first list emits an empty list and reports nothing`() =
        runBlocking {
            failAfter.set(0)
            val emissions = CopyOnWriteArrayList<List<String>>()
            val failures = CopyOnWriteArrayList<String>()
            val failuresJob =
                launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
                    repo.listRefreshFailures().collect { failures.add(it) }
                }
            val listJob =
                launch(Dispatchers.IO) {
                    repo.getContainers(all = true).collect { list -> emissions.add(list.map { it.id }) }
                }
            try {
                withTimeout(15_000) { while (emissions.size < 1) delay(50) }
                delay(500)
                assertEquals(listOf(emptyList<String>()), emissions.toList())
                assertTrue(failures.isEmpty(), "nothing to keep, nothing to report: $failures")
            } finally {
                listJob.cancel()
                failuresJob.cancel()
            }
        }

    private companion object {
        const val CONTAINER_LIST =
            """[{"Id":"c1","Names":["/one"],"Image":"demo/web:1.0","ImageID":"sha256:0","Command":"sh","Created":0,"State":"running","Status":"Up 1 minute","Ports":[],"Labels":{}}]"""
    }
}
