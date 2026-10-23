package com.containerdashboard

import com.containerdashboard.data.repository.DesktopDockerRepository
import com.containerdashboard.fakeengine.FakeDockerEngine
import com.containerdashboard.terminal.DockerExecTtyConnector
import com.containerdashboard.util.DATA_DIR_PROPERTY
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The repository runs one-shot commands and long-lived streams on two separate HTTP clients
 * (review-2026-10 B8). One shared pool of 100 connections filled up with one stats stream per
 * running container, and the availability ping then waited minutes for a free connection. The
 * commands' read timeout also cut every stream that stayed silent for that long: an idle Console,
 * a quiet log follow, the event stream of a quiet engine.
 *
 * Calls that can block in docker-java (a pool lease, a read without timeout) run in [sideScope],
 * outside runBlocking, so a regression fails the test at its deadline instead of hanging it.
 */
class DesktopDockerRepositoryClientsTest {
    private val requests = CopyOnWriteArrayList<String>()
    private val engines = mutableListOf<FakeDockerEngine>()
    private val repos = mutableListOf<DesktopDockerRepository>()
    private val sideScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val silentServers = mutableListOf<ServerSocket>()
    private val heldSockets = CopyOnWriteArrayList<Socket>()

    @BeforeTest
    fun setUp() {
        // The repository's init reads PreferenceRepository (DataStore). The desktopTest task points
        // this property at build/; refuse to run against the developer's real settings.
        assertNotNull(System.getProperty(DATA_DIR_PROPERTY), "$DATA_DIR_PROPERTY must be set for tests")
    }

    @AfterTest
    fun tearDown() {
        sideScope.cancel()
        repos.forEach { it.close() }
        engines.forEach { it.close() }
        silentServers.forEach { it.close() }
        heldSockets.forEach { it.close() }
    }

    private fun startEngine(
        port: Int = 0,
        dataJson: String? = null,
        log: MutableList<String> = requests,
    ): FakeDockerEngine =
        FakeDockerEngine(port = port, dataJson = dataJson, log = { log += it }).also {
            it.start()
            engines += it
        }

    private fun newRepo(
        port: Int,
        commandResponseTimeout: Duration = Duration.ofSeconds(45),
    ): DesktopDockerRepository =
        DesktopDockerRepository("tcp://127.0.0.1:$port", commandResponseTimeout = commandResponseTimeout)
            .also { repos += it }

    /** A server that accepts connections and never answers. */
    private fun startSilentServer(): ServerSocket {
        val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        silentServers += server
        thread(isDaemon = true) {
            while (!server.isClosed) {
                try {
                    heldSockets += server.accept()
                } catch (_: IOException) {
                    break
                }
            }
        }
        return server
    }

    /** Requests the engine answered whose log line contains [fragment] (e.g. "/events"). */
    private fun count(
        fragment: String,
        log: List<String> = requests,
    ) = log.count { fragment in it }

    private suspend fun awaitCondition(
        timeoutMillis: Long,
        what: String,
        condition: () -> Boolean,
    ) {
        val met = withTimeoutOrNull(timeoutMillis) { while (!condition()) delay(50) } != null
        assertTrue(met, "timed out waiting for $what")
    }

    /** The bundled fake data with its first (running) container copied [count] times. */
    private fun dataWithRunningContainers(count: Int): String {
        val bundled =
            Json
                .parseToJsonElement(
                    FakeDockerEngine::class.java.getResource("fake-data.json")!!.readText(),
                ).jsonObject
        val template =
            bundled
                .getValue("containers")
                .jsonArray
                .first()
                .jsonObject
        val summary = template.getValue("summary").jsonObject
        val inspect = template.getValue("inspect").jsonObject
        val containers =
            (1..count).map { n ->
                val id = JsonPrimitive("%064x".format(n))
                val name = "/demo-$n"
                JsonObject(
                    mapOf(
                        "summary" to JsonObject(summary + mapOf("Id" to id, "Names" to JsonArray(listOf(JsonPrimitive(name))))),
                        "inspect" to JsonObject(inspect + mapOf("Id" to id, "Name" to JsonPrimitive(name))),
                    ),
                )
            }
        return JsonObject(bundled + ("containers" to JsonArray(containers))).toString()
    }

    @Test
    fun `commands still run while every running container streams its stats`() =
        runBlocking {
            // 101 stats streams and the event stream: more than the 100 connections the single
            // pool had, so the ping used to wait 3 minutes for a free one.
            val engine = startEngine(dataJson = dataWithRunningContainers(101))
            val repo = newRepo(engine.port)
            sideScope.launch { repo.getContainerStats().collect {} }
            awaitCondition(20_000, "99 stats streams") { count("/stats") >= 99 }
            delay(500) // let the remaining streams take their connections

            val ping = sideScope.async { repo.isDockerAvailable(60_000).first() }
            val version = sideScope.async { repo.getVersion() }

            assertEquals(true, withTimeoutOrNull(5_000) { ping.await() }, "the ping waited for a free connection")
            assertTrue(withTimeoutOrNull(5_000) { version.await() }?.isSuccess == true, "getVersion waited for a free connection")
            awaitCondition(10_000, "all 101 stats streams") { count("/stats") >= 101 }
        }

    @Test
    fun `silent streams stay open past the command read timeout`() =
        runBlocking {
            // The fake engine's event stream never sends anything, its Console shell waits for
            // input, and a followed log gets a new line every 5 s.
            val engine = startEngine()
            val repo = newRepo(engine.port, commandResponseTimeout = Duration.ofSeconds(1))
            sideScope.launch { repo.getContainers(all = true).collect {} } // subscribes to the event stream
            val logs = sideScope.async { repo.followContainerLogs("demo-web", tail = 1).collect {} }
            val groupLogs = sideScope.async { repo.followMultipleContainerLogs(listOf("demo-web" to "web"), tail = 1).collect {} }
            val console = DockerExecTtyConnector(repo.client, containerId = "demo-web", commandClient = repo.commandClient)
            console.start()
            try {
                awaitCondition(10_000, "the event stream") { count("/events") >= 1 }
                assertTrue(console.isConnected(), "the Console did not connect")

                // Well past the 1 s command read timeout, and past the 5 s the repository waits
                // before it subscribes to events again.
                delay(8_000)

                assertEquals(1, count("/events"), "the silent event stream was cut and subscribed again")
                assertTrue(console.isConnected(), "the idle Console session was cut")
                assertFalse(logs.isCompleted, "the quiet log follow ended")
                assertFalse(groupLogs.isCompleted, "the quiet group log follow ended")
            } finally {
                console.close()
            }
        }

    @Test
    fun `a command the engine never answers still fails`() =
        runBlocking {
            // An engine that accepts connections and never answers: the commands' read timeout
            // must still apply.
            val repo = newRepo(startSilentServer().localPort, commandResponseTimeout = Duration.ofSeconds(1))
            val version = sideScope.async { repo.getVersion() }

            val result = withTimeoutOrNull(10_000) { version.await() }

            assertNotNull(result, "getVersion did not give up after the read timeout")
            assertTrue(result.isFailure)
        }

    @Test
    fun `a Console the engine never answers still fails to connect`() =
        runBlocking {
            // The exec session streams without a read timeout, but creating it is a one-shot call
            // on the command client: an engine that never answers must still fail the connect.
            val repo = newRepo(startSilentServer().localPort, commandResponseTimeout = Duration.ofSeconds(1))
            val connect =
                sideScope.async {
                    runCatching {
                        DockerExecTtyConnector(repo.client, containerId = "demo-web", commandClient = repo.commandClient).start()
                    }
                }

            val result = withTimeoutOrNull(10_000) { connect.await() }

            assertNotNull(result, "the Console's exec create did not give up after the read timeout")
            assertTrue(result.isFailure)
        }

    @Test
    fun `a reconnect replaces the stream connections too`() =
        runBlocking {
            // A host that drops off the network and comes back: the connections already open stay
            // up but may be dead, so the repository rebuilds its clients. Here the first engine
            // stops accepting connections (the open ones keep working), and a second engine takes
            // over the same port.
            val first = startEngine()
            val port = first.port
            val repo = newRepo(port)
            val availability = Channel<Boolean>(Channel.UNLIMITED)
            sideScope.launch { repo.isDockerAvailable(200).collect { availability.send(it) } }
            assertEquals(true, withTimeout(10_000) { availability.receive() })
            sideScope.launch { repo.getContainers(all = true).collect {} } // subscribes to the event stream
            sideScope.launch { repo.getContainerStats().collect {} }
            awaitCondition(10_000, "the event and stats streams") { count("/events") >= 1 && count("/stats") >= 1 }

            first.close()
            assertEquals(false, withTimeout(10_000) { availability.receive() })
            val secondLog = CopyOnWriteArrayList<String>()
            startEngine(port = port, log = secondLog)
            assertEquals(true, withTimeout(10_000) { availability.receive() })

            // The streams still open on the first engine were closed by the rebuild, so the repository
            // opens them again, this time on the second engine (events after a 5 s pause).
            awaitCondition(15_000, "the event and stats streams on the second engine") {
                count("/events", secondLog) >= 1 && count("/stats", secondLog) >= 1
            }
        }

    @Test
    fun `a closed repository opens no stream`() =
        runBlocking {
            val engine = startEngine()
            val repo = newRepo(engine.port)
            repo.close()

            val snapshots = withTimeoutOrNull(10_000) { repo.followContainerLogs("demo-web", tail = 1).toList() }

            assertNotNull(snapshots, "a log follow on a closed repository did not end")
            assertEquals(0, count("/logs"), "a closed repository opened a log stream")
        }
}
