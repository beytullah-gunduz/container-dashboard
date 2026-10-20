package com.containerdashboard.fakeengine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

const val DEFAULT_FAKE_ENGINE_PORT = 23750

/**
 * Runs [FakeDockerEngine] until the process is stopped: `./gradlew :composeApp:fakeDockerEngine`
 * (`-PfakeEnginePort=<port>` to move it off 23750).
 */
fun main(args: Array<String>) {
    val port =
        args
            .indexOf("--port")
            .takeIf { it >= 0 }
            ?.let { args.getOrNull(it + 1)?.toIntOrNull() }
            ?: DEFAULT_FAKE_ENGINE_PORT
    val engine = FakeDockerEngine(port)
    engine.start(daemon = false)
    println("fake docker engine listening on tcp://127.0.0.1:${engine.port}")
}

/**
 * A fake Docker Engine API for hot-reload sessions.
 *
 * Serves the part of the Engine API that Container Dashboard calls, with made-up containers,
 * images, volumes and networks, so the app can be run and driven (hot reload + its MCP server)
 * without ever reaching the developer's real engine. The made-up content is plain Engine API JSON
 * in `fake-data.json`, next to this class in the test resources; this file only holds behavior. The hotRun tasks point the app at it through
 * `-Dcontainerdashboard.dockerHost`. Every request is logged, so a check can assert what the app
 * asked for (for example the hijacked `exec start` behind the Console tab).
 *
 * Coverage: ping/version/info, container list/inspect/stats/logs, events, images, volumes,
 * networks, and exec (a TTY session answers with a tiny echoing shell; a non-TTY exec answers
 * `ls -la` with a per-directory listing). Anything else returns 404 with a message saying so.
 *
 * Plain sockets rather than the JDK's HttpServer: `exec start` has to switch the connection to a
 * raw two-way stream (`101 UPGRADED`), which HttpServer cannot do.
 */
class FakeDockerEngine(
    port: Int = DEFAULT_FAKE_ENGINE_PORT,
    private val log: (String) -> Unit = { println("${LocalTime.now().format(TIME_FORMAT)} $it") },
) : AutoCloseable {
    private val server =
        ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), port))
        }

    val port: Int get() = server.localPort

    private val data = FakeData.load()
    private val execs = ConcurrentHashMap<String, FakeExec>()
    private val execCounter = AtomicInteger()

    /** Accepts connections on a background thread; [daemon] = false keeps the JVM alive for it. */
    fun start(daemon: Boolean = true) {
        thread(name = "fake-docker-engine", isDaemon = daemon) {
            while (!server.isClosed) {
                val socket =
                    try {
                        server.accept()
                    } catch (_: IOException) {
                        break
                    }
                thread(name = "fake-docker-engine-connection", isDaemon = true) { serve(socket) }
            }
        }
    }

    override fun close() = server.close()

    private fun serve(socket: Socket) {
        socket.use {
            val conn = HttpConnection(it, log, data.apiVersion)
            try {
                while (true) {
                    val request = conn.readRequest() ?: break
                    dispatch(conn, request)
                    if (!conn.keepAlive) break
                }
            } catch (_: IOException) {
                // The client went away mid-exchange.
            }
        }
    }

    // ---- routing -------------------------------------------------------------------------------

    private class Route(
        val method: String,
        pattern: String,
        val handler: (HttpConnection, Request, List<String>) -> Unit,
    ) {
        val regex = Regex(pattern)
    }

    private val routes =
        listOf(
            Route("HEAD", "/_ping") { conn, _, _ -> conn.respond(200, contentType = null, headOnly = true) },
            Route("GET", "/_ping") { conn, _, _ -> conn.respond(200, TEXT, "OK".encodeToByteArray()) },
            Route("GET", "/version") { conn, _, _ -> conn.respondJson(data.version) },
            Route("GET", "/info") { conn, _, _ -> conn.respondJson(data.info) },
            Route("GET", "/containers/json") { conn, req, _ ->
                val all = req.query["all"].isTrue()
                conn.respondJson(JsonArray(data.containers.filter { all || it.running }.map { it.summary }))
            },
            Route("GET", "/containers/([^/]+)/json") { conn, _, (ref) ->
                withContainer(conn, ref) { conn.respondJson(it.inspect) }
            },
            Route("GET", "/containers/([^/]+)/stats") { conn, req, (ref) ->
                withContainer(conn, ref) { streamStats(conn, req, it) }
            },
            Route("GET", "/containers/([^/]+)/logs") { conn, req, (ref) ->
                withContainer(conn, ref) { streamLogs(conn, req, it) }
            },
            Route("GET", "/events") { conn, _, _ -> holdStream(conn) },
            Route("GET", "/images/json") { conn, _, _ -> conn.respondJson(JsonArray(data.images)) },
            Route("GET", "/images/(.+)/json") { conn, _, (name) -> inspectImage(conn, name) },
            Route("GET", "/volumes") { conn, _, _ ->
                conn.respondJson(
                    buildJsonObject {
                        put("Volumes", JsonArray(data.volumes))
                        putJsonArray("Warnings") {}
                    },
                )
            },
            Route("GET", "/volumes/([^/]+)") { conn, _, (name) ->
                val volume = data.volumes.firstOrNull { it.string("Name") == name }
                if (volume != null) conn.respondJson(volume) else conn.notFound("get $name: no such volume")
            },
            Route("GET", "/networks") { conn, _, _ -> conn.respondJson(JsonArray(data.networks)) },
            Route("GET", "/networks/([^/]+)") { conn, _, (ref) ->
                val network = data.networks.firstOrNull { ref == it.string("Name") || ref == it.string("Id") }
                if (network != null) conn.respondJson(network) else conn.notFound("network $ref not found")
            },
            Route("GET", "/exec/([^/]+)/json") { conn, _, (id) -> inspectExec(conn, id) },
            Route("POST", "/containers/([^/]+)/exec") { conn, req, (ref) ->
                withContainer(conn, ref) { createExec(conn, req, it) }
            },
            Route("POST", "/exec/([^/]+)/start") { conn, req, (id) ->
                val exec = execs[id]
                if (exec == null) {
                    conn.notFound("No such exec instance: $id")
                } else {
                    startExec(conn, exec, hijack = req.headers["upgrade"].equals("tcp", ignoreCase = true))
                }
            },
            // Lifecycle actions are accepted and ignored: the fake containers never change state.
            Route("POST", "/containers/([^/]+)/(start|stop|restart|pause|unpause|kill)") { conn, _, _ ->
                conn.respond(204, contentType = null)
            },
        )

    private fun dispatch(
        conn: HttpConnection,
        req: Request,
    ) {
        for (route in routes) {
            if (route.method != req.method) continue
            val match = route.regex.matchEntire(req.path) ?: continue
            route.handler(conn, req, match.groupValues.drop(1))
            return
        }
        val message =
            if (req.method == "DELETE") {
                "fake engine: deletions are not supported"
            } else {
                "fake engine: ${req.method} ${req.path} is not implemented"
            }
        conn.notFound(message)
    }

    private fun withContainer(
        conn: HttpConnection,
        ref: String,
        block: (FakeContainer) -> Unit,
    ) {
        val container = data.containers.firstOrNull { it.id.startsWith(ref) || it.name == ref }
        if (container == null) conn.notFound("No such container: $ref") else block(container)
    }

    // ---- images and execs ----------------------------------------------------------------------

    private fun inspectImage(
        conn: HttpConnection,
        name: String,
    ) {
        val image =
            data.images.firstOrNull { image ->
                image.getValue("RepoTags").jsonArray.any { it.jsonPrimitive.content == name } ||
                    image.string("Id").endsWith(name)
            }
        if (image == null) conn.notFound("No such image: $name") else conn.respondJson(image)
    }

    private fun createExec(
        conn: HttpConnection,
        req: Request,
        container: FakeContainer,
    ) {
        if (!container.running) {
            conn.respondJson(message("container ${container.id.take(12)} is not running"), status = 409)
            return
        }
        val body =
            runCatching { Json.parseToJsonElement(req.body.decodeToString()).jsonObject }
                .getOrDefault(JsonObject(emptyMap()))
        val cmd =
            (body["Cmd"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }?.takeIf { it.isNotEmpty() }
                ?: listOf("/bin/sh")
        val tty = (body["Tty"] as? JsonPrimitive)?.booleanOrNull ?: false
        val id = "%x%04x".format(System.currentTimeMillis(), execCounter.getAndIncrement()).padEnd(64, 'e')
        execs[id] = FakeExec(cmd, tty, container.id)
        conn.respondJson(buildJsonObject { put("Id", id) }, status = 201)
    }

    private fun inspectExec(
        conn: HttpConnection,
        id: String,
    ) {
        val exec = execs[id]
        if (exec == null) {
            conn.notFound("No such exec instance: $id")
            return
        }
        conn.respondJson(
            buildJsonObject {
                put("ID", id)
                put("Running", exec.running)
                put("ExitCode", if (exec.running) JsonNull else JsonPrimitive(0))
                putJsonObject("ProcessConfig") {
                    put("tty", exec.tty)
                    put("entrypoint", exec.cmd.first())
                    putJsonArray("arguments") { exec.cmd.drop(1).forEach { add(JsonPrimitive(it)) } }
                }
                put("ContainerID", exec.containerId)
            },
        )
    }

    private fun startExec(
        conn: HttpConnection,
        exec: FakeExec,
        hijack: Boolean,
    ) {
        exec.running = true
        log("exec start cmd=${exec.cmd} tty=${exec.tty} hijack=$hijack")
        try {
            if (hijack) conn.upgrade(if (exec.tty) RAW_STREAM else MULTIPLEXED_STREAM)
            if (exec.tty) {
                fakeShell(conn)
            } else {
                // The Files tab runs `ls -la -- <path>`: answer that directory's listing.
                val output =
                    if (exec.cmd.firstOrNull() == "ls") data.lsFor(exec.cmd.last()) else "fake engine: ${exec.cmd.joinToString(" ")}\n"
                val payload = frame(output)
                if (hijack) {
                    conn.output.write(payload)
                    conn.output.flush()
                } else {
                    conn.startChunked(MULTIPLEXED_STREAM)
                    conn.chunk(payload)
                    conn.endChunked()
                }
            }
        } catch (_: IOException) {
            // The client closed the session.
        } finally {
            exec.running = false
            conn.keepAlive = false
        }
    }

    /** Raw TTY session: echo what is typed, answer a few commands, `exit` ends it. */
    private fun fakeShell(conn: HttpConnection) {
        val out = conn.output
        out.write("Fake engine shell: this is not a real container.\r\n/ # ".encodeToByteArray())
        out.flush()
        val line = StringBuilder()
        val buffer = ByteArray(1024)
        while (true) {
            val n = conn.input.read(buffer)
            if (n < 0) return
            log("exec stdin ${printable(buffer, n)}")
            for (i in 0 until n) {
                when (val b = buffer[i].toInt() and 0xff) {
                    '\r'.code, '\n'.code -> {
                        out.write("\r\n".encodeToByteArray())
                        val command = line.toString().trim()
                        line.clear()
                        when {
                            command == "exit" -> {
                                out.flush()
                                return
                            }

                            command == "ls" -> {
                                out.write("bin  etc  hello.txt  lib  tmp  usr\r\n".encodeToByteArray())
                            }

                            command.isNotEmpty() -> {
                                out.write("sh: $command: not found (fake engine)\r\n".encodeToByteArray())
                            }
                        }
                        out.write("/ # ".encodeToByteArray())
                    }

                    0x7f, 0x08 -> {
                        if (line.isNotEmpty()) {
                            line.setLength(line.length - 1)
                            out.write("\b \b".encodeToByteArray())
                        }
                    }

                    in 0x20..0x7e -> {
                        line.append(b.toChar())
                        out.write(b)
                    }

                    // Other control bytes (Ctrl-D, escape sequences) are ignored.
                    else -> {}
                }
            }
            out.flush()
        }
    }

    // ---- streams -------------------------------------------------------------------------------

    /** Open a chunked stream and keep it open, silent, until the client leaves. */
    private fun holdStream(conn: HttpConnection) {
        conn.startChunked(JSON)
        while (!conn.clientGone(1_000)) {
            // Nothing to send: the fake engine has no events.
        }
        conn.keepAlive = false
    }

    private fun streamStats(
        conn: HttpConnection,
        req: Request,
        container: FakeContainer,
    ) {
        if (!container.running) {
            conn.respondJson(statsSample(container, 0))
            return
        }
        if (req.query["stream"].isFalse()) {
            conn.respondJson(statsSample(container, 1))
            return
        }
        conn.startChunked(JSON)
        try {
            var tick = 1
            do {
                conn.chunk((statsSample(container, tick++).toString() + "\n").encodeToByteArray())
            } while (!conn.clientGone(1_000))
        } catch (_: IOException) {
            // The client closed the stream.
        }
        conn.keepAlive = false
    }

    private fun streamLogs(
        conn: HttpConnection,
        req: Request,
        container: FakeContainer,
    ) {
        val all = (1..20).map { "${container.name}: fake engine log line $it\n" }
        val lines = req.query["tail"]?.toIntOrNull()?.let { all.takeLast(it) } ?: all
        conn.startChunked(MULTIPLEXED_STREAM)
        try {
            lines.forEach { conn.chunk(frame(it)) }
            if (!req.query["follow"].isTrue() || !container.running) {
                conn.endChunked()
                return
            }
            var n = all.size
            while (!conn.clientGone(5_000)) {
                n++
                conn.chunk(frame("${container.name}: fake engine log line $n\n"))
            }
        } catch (_: IOException) {
            // The client closed the stream.
        }
        conn.keepAlive = false
    }

    private class FakeExec(
        val cmd: List<String>,
        val tty: Boolean,
        val containerId: String,
    ) {
        @Volatile var running = false
    }
}

// ---- HTTP/1.1 plumbing -------------------------------------------------------------------------

private class Request(
    val method: String,
    val target: String,
    val path: String,
    val query: Map<String, String>,
    val headers: Map<String, String>,
    val body: ByteArray,
)

private class HttpConnection(
    private val socket: Socket,
    private val log: (String) -> Unit,
    private val apiVersion: String,
) {
    val input = BufferedInputStream(socket.getInputStream())
    val output = BufferedOutputStream(socket.getOutputStream())
    var keepAlive = true
    private var current: Request? = null

    fun readRequest(): Request? {
        var requestLine: String
        do {
            requestLine = readLine() ?: return null
        } while (requestLine.isEmpty())
        val parts = requestLine.split(" ")
        if (parts.size < 2) return null
        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = readLine() ?: return null
            if (line.isEmpty()) break
            val colon = line.indexOf(':')
            if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
        }
        val body =
            if (headers["transfer-encoding"]?.contains("chunked", ignoreCase = true) == true) {
                readChunkedBody()
            } else {
                input.readNBytes(headers["content-length"]?.toIntOrNull() ?: 0)
            }
        if (headers["connection"].equals("close", ignoreCase = true)) keepAlive = false
        val target = parts[1]
        return Request(
            method = parts[0],
            target = target,
            path = target.substringBefore('?').replace(VERSION_PREFIX, ""),
            query = parseQuery(target.substringAfter('?', "")),
            headers = headers,
            body = body,
        ).also { current = it }
    }

    fun respond(
        status: Int,
        contentType: String?,
        body: ByteArray = ByteArray(0),
        headOnly: Boolean = false,
    ) {
        logStatus(status)
        writeHead(status, listOfNotNull(contentType?.let { "Content-Type" to it }, "Content-Length" to body.size.toString()))
        if (!headOnly) output.write(body)
        output.flush()
    }

    fun respondJson(
        json: JsonElement,
        status: Int = 200,
    ) = respond(status, JSON, json.toString().encodeToByteArray())

    fun notFound(text: String) = respondJson(message(text), status = 404)

    fun startChunked(contentType: String) {
        logStatus(200)
        writeHead(200, listOf("Content-Type" to contentType, "Transfer-Encoding" to "chunked"))
        output.flush()
    }

    fun chunk(data: ByteArray) {
        output.write("${data.size.toString(16)}\r\n".encodeToByteArray())
        output.write(data)
        output.write("\r\n".encodeToByteArray())
        output.flush()
    }

    fun endChunked() {
        output.write("0\r\n\r\n".encodeToByteArray())
        output.flush()
    }

    /** Switch to a raw two-way stream, as the engine does for an attached `exec start`. */
    fun upgrade(contentType: String) {
        logStatus(101)
        output.write(
            "HTTP/1.1 101 UPGRADED\r\nContent-Type: $contentType\r\nConnection: Upgrade\r\nUpgrade: tcp\r\n\r\n"
                .encodeToByteArray(),
        )
        output.flush()
        keepAlive = false
    }

    /** Waits up to [timeoutMs]; true once the client has closed the connection. */
    fun clientGone(timeoutMs: Int): Boolean {
        socket.soTimeout = timeoutMs
        return try {
            input.read() == -1
        } catch (_: SocketTimeoutException) {
            false
        } catch (_: IOException) {
            true
        } finally {
            socket.soTimeout = 0
        }
    }

    private fun writeHead(
        status: Int,
        headers: List<Pair<String, String>>,
    ) {
        val head = StringBuilder("HTTP/1.1 $status ${REASONS[status] ?: "Status"}\r\n")
        head.append("Api-Version: $apiVersion\r\nServer: FakeDockerEngine/1.0\r\n")
        headers.forEach { (name, value) -> head.append("$name: $value\r\n") }
        head.append("\r\n")
        output.write(head.toString().encodeToByteArray())
    }

    private fun logStatus(status: Int) {
        current?.let { log("${it.method} ${it.target} -> $status") }
    }

    /** One CRLF-terminated line, or null at end of stream. */
    private fun readLine(): String? {
        val bytes = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b == -1) return if (bytes.size() == 0) null else bytes.toString(Charsets.ISO_8859_1)
            if (b == '\n'.code) break
            bytes.write(b)
        }
        return bytes.toString(Charsets.ISO_8859_1).removeSuffix("\r")
    }

    private fun readChunkedBody(): ByteArray {
        val body = ByteArrayOutputStream()
        while (true) {
            val size = readLine()?.substringBefore(';')?.trim()?.toIntOrNull(16) ?: break
            if (size == 0) {
                while (!readLine().isNullOrEmpty()) {
                    // Skip trailer fields.
                }
                break
            }
            body.write(input.readNBytes(size))
            readLine()
        }
        return body.toByteArray()
    }

    private fun parseQuery(query: String): Map<String, String> =
        query
            .split('&')
            .filter { it.isNotEmpty() }
            .associate {
                URLDecoder.decode(it.substringBefore('='), Charsets.UTF_8) to
                    URLDecoder.decode(it.substringAfter('=', ""), Charsets.UTF_8)
            }
}

// ---- fake data ---------------------------------------------------------------------------------

private const val JSON = "application/json"
private const val TEXT = "text/plain; charset=utf-8"
private const val RAW_STREAM = "application/vnd.docker.raw-stream"
private const val MULTIPLEXED_STREAM = "application/vnd.docker.multiplexed-stream"
private val VERSION_PREFIX = Regex("^/v[0-9.]+")
private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
private val REASONS = mapOf(200 to "OK", 201 to "Created", 204 to "No Content", 404 to "Not Found", 409 to "Conflict")

/** A fake container: its `/containers/json` entry and its `/containers/{id}/json` document. */
private class FakeContainer(
    val summary: JsonObject,
    val inspect: JsonObject,
) {
    val id = summary.string("Id")
    val name =
        summary
            .getValue("Names")
            .jsonArray
            .first()
            .jsonPrimitive.content
            .removePrefix("/")
    val running = summary.string("State") == "running"
}

/** The engine's made-up content, read from `fake-data.json` next to this class. */
private class FakeData(
    root: JsonObject,
) {
    val version = root.getValue("version").jsonObject
    val apiVersion = version.string("ApiVersion")
    val info = root.getValue("info").jsonObject
    val containers =
        root.objects("containers").map {
            FakeContainer(summary = it.getValue("summary").jsonObject, inspect = it.getValue("inspect").jsonObject)
        }
    val images = root.objects("images")
    val volumes = root.objects("volumes")
    val networks = root.objects("networks")

    /** `ls -la` output per directory: a merged-/usr layout, so `/bin` and `/lib` are symlinks into `/usr`. */
    private val ls: Map<String, String> =
        root.getValue("ls").jsonObject.mapValues { (_, lines) ->
            lines.jsonArray.joinToString("\n", postfix = "\n") { it.jsonPrimitive.content }
        }

    /** A directory the data does not describe lists as empty. */
    fun lsFor(path: String): String = ls[path] ?: "total 0\n"

    companion object {
        fun load(): FakeData {
            val text =
                FakeDockerEngine::class.java.getResource("fake-data.json")?.readText()
                    ?: error("fake-data.json is missing from the test resources")
            return FakeData(Json.parseToJsonElement(text).jsonObject)
        }
    }
}

private fun JsonObject.objects(key: String) = getValue(key).jsonArray.map { it.jsonObject }

private fun statsSample(
    container: FakeContainer,
    tick: Int,
): JsonObject {
    val previous = maxOf(tick - 1, 0)

    fun cpu(t: Int) =
        buildJsonObject {
            putJsonObject("cpu_usage") { put("total_usage", 1_000_000_000L + t * 50_000_000L) }
            put("system_cpu_usage", 100_000_000_000L + t * 1_000_000_000L)
            put("online_cpus", 4)
        }
    return buildJsonObject {
        put("id", container.id)
        put("name", "/${container.name}")
        put("read", Instant.now().toString())
        put("preread", "0001-01-01T00:00:00Z")
        putJsonObject("pids_stats") { put("current", 3) }
        put("cpu_stats", cpu(tick))
        put("precpu_stats", cpu(previous))
        putJsonObject("memory_stats") {
            put("usage", 64L * 1024 * 1024 + tick * 4096L)
            put("limit", 2L * 1024 * 1024 * 1024)
            putJsonObject("stats") {}
        }
        putJsonObject("networks") {
            putJsonObject("eth0") {
                put("rx_bytes", 10_000L + tick * 1500L)
                put("tx_bytes", 8_000L + tick * 900L)
            }
        }
        putJsonObject("blkio_stats") {
            putJsonArray("io_service_bytes_recursive") {
                addJsonObject {
                    put("major", 8)
                    put("minor", 0)
                    put("op", "read")
                    put("value", 4096L * tick)
                }
            }
        }
    }
}

private fun message(text: String) = buildJsonObject { put("message", text) }

/** One multiplexed (non-TTY) stdout frame: stream type, 3 zero bytes, big-endian length, payload. */
private fun frame(text: String): ByteArray {
    val payload = text.encodeToByteArray()
    return ByteBuffer
        .allocate(8 + payload.size)
        .put(1)
        .put(ByteArray(3))
        .putInt(payload.size)
        .put(payload)
        .array()
}

private fun printable(
    bytes: ByteArray,
    length: Int,
): String =
    (0 until length).joinToString("") { i ->
        val b = bytes[i].toInt() and 0xff
        if (b in 0x20..0x7e) b.toChar().toString() else "\\x%02x".format(b)
    }

private fun String?.isTrue() = this == "1" || this.equals("true", ignoreCase = true)

private fun String?.isFalse() = this == "0" || this.equals("false", ignoreCase = true)

private fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.content
