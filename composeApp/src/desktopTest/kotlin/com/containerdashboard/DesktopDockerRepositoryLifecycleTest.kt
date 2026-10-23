package com.containerdashboard

import com.containerdashboard.data.repository.DesktopDockerRepository
import com.containerdashboard.util.DATA_DIR_PROPERTY
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Client lifecycle of the real repository against a tiny in-process engine: the first availability
 * poll must not replace the client the app's streams already run on, a closed repository must not
 * build a new client, cancelling a file transfer must free the caller, and a transfer or stream
 * cancelled before the engine answers must not keep running.
 */
class DesktopDockerRepositoryLifecycleTest {
    private lateinit var server: HttpServer
    private lateinit var repo: DesktopDockerRepository
    private val eventsRequests = AtomicInteger()
    private val execCreates = AtomicInteger()
    private val execStarts = AtomicInteger()
    private val release = CountDownLatch(1)
    private val holdExecStart = AtomicBoolean(false)

    // Answer exec start only after a delay, then stream until the client goes away.
    private val delayExecStartHeaders = AtomicBoolean(false)
    private val execStreamClosedByClient = CountDownLatch(1)

    // A followed log whose headers come late, streaming until the client goes away.
    private val logRequests = AtomicInteger()
    private val logStreamClosedByClient = CountDownLatch(1)

    @BeforeTest
    fun setUp() {
        // The repository's init reads PreferenceRepository (DataStore). The desktopTest task points
        // this property at build/; refuse to run against the developer's real settings.
        assertNotNull(System.getProperty(DATA_DIR_PROPERTY), "$DATA_DIR_PROPERTY must be set for tests")

        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        // Handlers must not run on the dispatcher thread: a held-open /events would block every other request.
        server.executor = Executors.newCachedThreadPool()
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
        release.countDown()
        repo.close()
        server.stop(0)
        (server.executor as ExecutorService).shutdownNow()
    }

    private fun handle(exchange: HttpExchange) {
        exchange.requestBody.readBytes()
        val path = exchange.requestURI.path.replace(Regex("^/v[0-9.]+"), "")
        when (path) {
            "/_ping" -> {
                respond(exchange, "text/plain", "OK".toByteArray())
            }

            "/events" -> {
                // A stream the engine keeps open until the test ends.
                eventsRequests.incrementAndGet()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, 0)
                exchange.responseBody.flush()
                release.await(30, TimeUnit.SECONDS)
            }

            "/containers/json" -> {
                respond(exchange, "application/json", "[]".toByteArray())
            }

            "/containers/c1/exec" -> {
                execCreates.incrementAndGet()
                respond(exchange, "application/json", """{"Id":"e1"}""".toByteArray(), status = 201)
            }

            "/exec/e1/start" -> {
                execStarts.incrementAndGet()
                if (delayExecStartHeaders.get()) {
                    streamUntilClientCloses(exchange, execStreamClosedByClient)
                    return
                }
                exchange.responseHeaders.add("Content-Type", "application/vnd.docker.multiplexed-stream")
                exchange.sendResponseHeaders(200, 0)
                exchange.responseBody.flush()
                // Held open only when a test wants a transfer that does not finish by itself.
                if (holdExecStart.get()) release.await(30, TimeUnit.SECONDS)
            }

            "/containers/c1/logs" -> {
                logRequests.incrementAndGet()
                streamUntilClientCloses(exchange, logStreamClosedByClient)
            }

            "/exec/e1/json" -> {
                respond(exchange, "application/json", """{"ID":"e1","Running":false,"ExitCode":0}""".toByteArray())
            }

            else -> {
                respond(exchange, "application/json", """{"message":"not found"}""".toByteArray(), status = 404)
            }
        }
    }

    /** A transfer that never ends by itself: headers after 400 ms, then a frame every 50 ms. */
    private fun streamUntilClientCloses(
        exchange: HttpExchange,
        closedByClient: CountDownLatch,
    ) {
        Thread.sleep(400)
        exchange.responseHeaders.add("Content-Type", "application/vnd.docker.multiplexed-stream")
        exchange.sendResponseHeaders(200, 0)
        val payload = "x".repeat(1024).toByteArray()
        val frame =
            ByteBuffer
                .allocate(8 + payload.size)
                .put(1)
                .put(ByteArray(3))
                .putInt(payload.size)
                .put(payload)
                .array()
        val deadline = System.currentTimeMillis() + 20_000
        try {
            while (System.currentTimeMillis() < deadline) {
                exchange.responseBody.write(frame)
                exchange.responseBody.flush()
                Thread.sleep(50)
            }
        } catch (_: IOException) {
            closedByClient.countDown()
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
    fun `the first availability poll keeps the streams already open`() =
        runBlocking {
            // The container list subscribes to the event stream at startup. The first poll used to
            // rebuild the client, which closed that stream and made it re-subscribe 5 s later
            // (review-2026-10 B4).
            val listJob = launch(Dispatchers.IO) { repo.getContainers(all = true).collect {} }
            try {
                withTimeout(10_000) { while (eventsRequests.get() < 1) delay(100) }
                assertTrue(withTimeout(10_000) { repo.isDockerAvailable().first() })
                // Longer than the 5 s delay between two event subscriptions.
                delay(7_000)
                assertEquals(1, eventsRequests.get())
            } finally {
                listJob.cancel()
            }
        }

    @Test
    fun `a closed repository does not rebuild its client`() =
        runBlocking {
            // On a closed client, execCreateCmd().exec() throws IllegalStateException("Connection pool
            // shut down") before any request. withRetryOnPoolShutdown matches "shut down" and asks for
            // a rebuild; that is now a no-op, so the retry throws again and nothing reaches the server
            // (review-2026-10 R1).
            repo.close()

            val result = repo.listContainerDirectory("c1", "/")

            assertEquals(0, execCreates.get())
            assertTrue(result.isFailure)
        }

    @Test
    fun `cancelling a download frees the caller`() =
        runBlocking {
            // The exec never ends by itself. Cancelling the caller used to leave its IO thread blocked
            // in awaitCompletion until the 5-minute download timeout (review-2026-10 B6).
            holdExecStart.set(true)
            val job = launch(Dispatchers.IO) { repo.downloadContainerFile("c1", "/big") }
            withTimeout(10_000) { while (execStarts.get() < 1) delay(50) }

            withTimeout(5_000) { job.cancelAndJoin() }
        }

    @Test
    fun `a download cancelled before the engine answers does not keep streaming`() =
        runBlocking {
            // docker-java hands the callback its stream only after the response headers, and that
            // onStart also resets the callback's closed flag: a cancel that landed earlier was undone
            // and the transfer ran to its end into a discarded buffer (review-2026-10 B6).
            delayExecStartHeaders.set(true)
            val job = launch(Dispatchers.IO) { repo.downloadContainerFile("c1", "/big") }
            withTimeout(10_000) { while (execStarts.get() < 1) delay(20) }
            job.cancelAndJoin() // well inside the 400 ms before the headers

            assertTrue(
                execStreamClosedByClient.await(5, TimeUnit.SECONDS),
                "the transfer must stop once its caller is cancelled",
            )
        }

    @Test
    fun `a log follow cancelled before the engine answers does not keep streaming`() =
        runBlocking {
            // The same race on the stream client, which has no read timeout: a follow cancelled
            // before the headers kept its stream open until the engine ended it (review-2026-10 B8).
            val job = launch(Dispatchers.IO) { repo.followContainerLogs("c1", tail = 10).collect {} }
            withTimeout(10_000) { while (logRequests.get() < 1) delay(20) }
            job.cancelAndJoin() // well inside the 400 ms before the headers

            assertTrue(
                logStreamClosedByClient.await(5, TimeUnit.SECONDS),
                "the log stream must stop once its follower is gone",
            )
        }
}
