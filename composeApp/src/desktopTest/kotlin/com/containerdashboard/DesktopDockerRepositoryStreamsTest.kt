package com.containerdashboard

import com.containerdashboard.data.repository.DesktopDockerRepository
import com.containerdashboard.util.DATA_DIR_PROPERTY
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The Docker streams (events, log follow) are callbackFlows fed by docker-java callbacks. When the
 * engine ends a stream, the flow must end too: the event subscription then re-subscribes, and a
 * followed log pane learns that its stream ended. These tests run the real repository against a
 * tiny in-process engine whose streams end right away.
 */
class DesktopDockerRepositoryStreamsTest {
    private lateinit var server: HttpServer
    private lateinit var repo: DesktopDockerRepository
    private val eventsRequests = AtomicInteger()

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
        when {
            path == "/_ping" -> {
                respond(exchange, "text/plain", "OK".toByteArray())
            }

            path == "/containers/json" -> {
                respond(exchange, "application/json", "[]".toByteArray())
            }

            path == "/events" -> {
                // A stream that the engine ends at once, as on a daemon restart.
                eventsRequests.incrementAndGet()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, 0)
            }

            path.matches(Regex("/containers/[^/]+/logs")) -> {
                // One stdout frame, then the end of the stream (the container stopped).
                val id = path.split("/")[2]
                exchange.responseHeaders.add("Content-Type", "application/vnd.docker.multiplexed-stream")
                exchange.sendResponseHeaders(200, 0)
                exchange.responseBody.write(stdoutFrame("line from $id\n"))
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

    private fun stdoutFrame(text: String): ByteArray {
        val payload = text.toByteArray()
        return ByteBuffer
            .allocate(8 + payload.size)
            .put(1)
            .put(ByteArray(3))
            .putInt(payload.size)
            .put(payload)
            .array()
    }

    @Test
    fun `followContainerLogs ends when the engine ends the stream`() =
        runBlocking {
            // The last snapshot must survive the end of the stream (review-2026-10 B2).
            val snapshots = withTimeout(15_000) { repo.followContainerLogs("c1", tail = 10).toList() }
            assertEquals(listOf("line from c1"), snapshots.last())
        }

    @Test
    fun `followMultipleContainerLogs ends when every stream has ended`() =
        runBlocking {
            val snapshots =
                withTimeout(15_000) {
                    repo.followMultipleContainerLogs(listOf("c1" to "one", "c2" to "two"), tail = 10).toList()
                }
            assertEquals(setOf("[one] line from c1", "[two] line from c2"), snapshots.last().toSet())
        }

    @Test
    fun `the event subscription re-subscribes after the engine ends it`() =
        runBlocking {
            // The container list is what subscribes to the event stream.
            val listJob = launch(Dispatchers.IO) { repo.getContainers(all = true).collect {} }
            try {
                // The repository waits 5 s between two subscriptions.
                withTimeout(15_000) {
                    while (eventsRequests.get() < 2) delay(100)
                }
            } finally {
                listJob.cancel()
            }
        }
}
