# Plan — review 2026-10 B8: separate HTTP clients for commands and streams

Source: [`docs/audit/review-2026-10.md`](audit/review-2026-10.md) — B8, plus R4, found while
reviewing B8's design (recorded in the audit doc after this change). Anchors are for HEAD
`58980fa`. Paths are relative to `composeApp/src/<set>/kotlin/com/containerdashboard/`.

## Problem and root cause

The repository runs every Engine API call on one `ApacheDockerHttpClient`
(`desktopMain/.../data/repository/DesktopDockerRepository.kt:85-96`, `maxConnections(100)` at `:89`,
`responseTimeout(45 s)` at `:95`). Two defects follow from sharing it:

- **B8, the pool ceiling.** docker-java 3.7.1 applies `maxConnections` as both the total and the
  per-route limit, and a client has exactly one route (checked in the `ApacheDockerHttpClientImpl`
  bytecode). No connection-request timeout is set, so httpclient5's 3-minute default applies to a
  call waiting for a connection. Every running container holds one connection for its stats stream
  (`:1395`); events (`:287`), log follows (`:849`, `:913`) and each Console session hold one more.
  Past about 99 running containers, every one-shot call waits for a free connection, including the
  availability ping (`pingOnce`, `:250-257`). A design review measured the ping returning after
  182.9 s with 101 running containers; the availability flow can stay silent for minutes, then
  report the engine as unavailable, rebuild the client (dropping every stream, the Console
  included) and start over.
- **R4, silent streams cut after 45 s.** httpclient5 5.5.1 applies the response timeout as the
  socket read timeout for the whole exchange, not only until the headers
  (`InternalExecRuntime.execute`, lines 226-228: `if (responseTimeout != null)
  endpoint.setSocketTimeout(responseTimeout)`; nothing resets it). The comment at `:91-94`, which
  says the builder zeroes the socket timeout so streams are not interrupted, is wrong for this
  version. Any stream silent for 45 s is cut: an idle Console session (measured: disconnected at
  45.06 s against the fake engine), a quiet followed log (ends with "--- Stream ended ---"), and the
  event stream of a quiet engine (measured: re-subscribed at 50.2 s).

## Decisions (decisions, not options — do not relitigate)

1. Two HTTP clients per repository, each with its own `DockerClient`:
   - **commands:** `maxConnections(100)`, `connectionTimeout(10 s)`, `responseTimeout(45 s)` — as
     today.
   - **streams:** `maxConnections(1000)`, `connectionTimeout(10 s)`, **no** response timeout
     (`responseTimeout(null)`; the builder accepts null and httpclient5 then leaves the socket
     timeout at the pool's `SocketConfig` value, 0 = none — checked in the bytecode and in
     `RequestConfig`/`ConnectionConfig`, whose defaults are null).
2. On the stream client: `eventsCmd` (`:287`), `logContainerCmd(...).withFollowStream(true)` in
   `followContainerLogs` (`:849`) and `followMultipleContainerLogs` (`:913`), `statsCmd` (`:1395`),
   and the Console's exec session, through the public `val client` (`:122`, read by
   `JediTermConsole.kt:74`). The Console's one-shots (exec create, `DockerExecTtyConnector.kt:54`;
   resize, `:146`) run on the command client, through a new public `val commandClient`: on the
   stream client a Console opened against an engine that accepts but never answers would wait in
   "connecting" forever (the connect is not interruptible), where today it fails after 45 s.
   Everything else stays on the command client: ping, info, version, lists, get/inspect,
   start/stop/restart/pause/unpause/remove, volume/network create/remove, prune, the non-follow
   `getContainerLogs`, `pullImage`, and `execCapture`.
3. One immutable holder of all four objects behind the existing single `@Volatile var clients`.
   `rebuildClient` keeps its lock, its `closed` check (R1) and its `old !== stale` guard, swaps the
   holder and closes **both** old transports. The stream client must be rebuilt too: it has no read
   timeout, so a dead stream connection (a remote `tcp://` host that dropped off the network) is
   only closed by this rebuild.
4. The B4 rule (no rebuild on the first poll) and `withRetryOnPoolShutdown` (command calls only)
   are unchanged.
5. `close()` closes all four, each in its own try, so a failure on one cannot leave another open.
6. One new constructor parameter, `commandResponseTimeout: Duration = Duration.ofSeconds(45)`, so
   tests can shorten the command read timeout instead of waiting 45 s. No pool-size parameters.
7. Out of scope: the tray, `ContainerStatsManager`, Monitoring, stats cadence; a lease timeout
   (docker-java's builder has no setter for it); scoping stats streams to visible containers.

Accepted behaviour changes and known gaps (so the result audit does not file them as regressions):
- A stream to a remote `tcp://` host whose connection dies silently (no FIN/RST) now hangs until the
  next unreachable → reachable transition rebuilds the clients; before, the 45 s read timeout cut
  it (and also cut every healthy silent stream). For the event stream the 15 s list poll bounds the
  staleness; a stats stream freezes on its last sample. Unix sockets (local engines) are not
  affected. TCP keepalive is not reachable through docker-java's builder.
- `execCapture` and `pullImage` keep the 45 s read timeout: an exec or a pull that prints nothing
  for 45 s still fails, as today.
- `StrictConnPool.close()` drops pending lease requests without failing them (httpcore5 5.3.6
  `StrictConnPool.java:136-151`: `pendingRequests.clear()` at `:147`), so a call queued on a pool
  that a rebuild closes waits out the 3-minute lease timeout. Pre-existing; rarer after this
  change because command calls no longer queue behind streams.
- Thread and socket cost per stream is unchanged (one socket and one `docker-java-stream-*` thread
  each); the second pool has no threads of its own.

## Global constraints (paste verbatim into every implementer prompt)

```
- Worktree: <worktree-path> — a git worktree on branch feature/fable-gui-backend-review-b8d318. Work only there, with absolute paths.
  Never touch the main checkout.
- Build/test (plain ./gradlew fails on the system Java 25):
    JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :composeApp:desktopTest --console=plain
  Targeted: append --tests 'com.containerdashboard.<TestClass>' (repeatable). Add --rerun to force a
  run when nothing changed.
  The CI runs desktopTest on headless Linux. Also run the new test class once headless:
    JAVA_TOOL_OPTIONS="-Djava.awt.headless=true" JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :composeApp:desktopTest --tests 'com.containerdashboard.DesktopDockerRepositoryClientsTest' --rerun --console=plain
- Formatting: run each in its OWN invocation, never chained with compile/test (Gradle
  config-cache flake):
    JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew spotlessApply --console=plain
    JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew spotlessCheck --console=plain
- Read test totals from composeApp/build/test-results/desktopTest/*.xml (the tests/failures/errors
  attributes), not from the console summary.
- Do NOT commit, stash, checkout, reset or rebase. The orchestrating agent commits.
- Do NOT run docker, colima or podman. Do NOT launch the app (no run / hotRun* tasks) and do not
  start the fakeDockerEngine Gradle task. Tests may start FakeDockerEngine in-process or a server
  socket on 127.0.0.1.
- This repository is PUBLIC. Never write a real account email, org name, host name, internal URL,
  IP (other than 127.0.0.1), token, or an absolute path containing a username into code, tests,
  comments, fixtures or log strings. Use obviously fake values (demo-web, c1, example).
- NO TEST MAY READ OR WRITE REAL USER STATE. A test that builds a DesktopDockerRepository must
  first assertNotNull(System.getProperty(DATA_DIR_PROPERTY)), as every existing repository test does
  (the desktopTest task points it at build/test-data).
- Match the surrounding code: comment density, naming, idioms. One short comment per fix site
  saying why, citing "review-2026-10 B8"; no comment essays.
- Negative controls are mandatory (the list is in the workstream): each time revert only the named
  line(s), run the targeted test, confirm it FAILS and quote the failure message from the XML,
  restore, confirm it passes. Leave no reverted state behind.
- Final report: files changed; the full-suite totals from the XML; each negative control (what you
  changed, the failure message); the headless run result; every deviation from this plan, with the
  reason. Do not paraphrase errors — quote them.
```

## WS-G — two clients (`DesktopDockerRepository.kt`, `DockerExecTtyConnector.kt`)

The acceptance tests are already written, by the planner, in
`desktopTest/kotlin/com/containerdashboard/DesktopDockerRepositoryClientsTest.kt` (untracked).
They were run against a variant of HEAD with only the `commandResponseTimeout` parameter added (one
client): tests 1 and 2 failed as expected ("the ping waited for a free connection expected:<true>
but was:<null>"; "the silent event stream was cut and subscribed again expected:<1> but was:<2>"),
tests 3-5 passed. Test 6 was added after the plan review (it needs G9's `commandClient`, so it
could not run before). **Do not change their assertions, waits or deadlines.** Fix only compile or
formatting problems (`spotlessApply` may rewrap long lines), and report any such change.

| # | Test | Pins |
|---|---|---|
| 1 | `commands still run while every running container streams its stats` | 101 stats streams + events; ping and `getVersion` answer within 5 s; all 101 stats streams open |
| 2 | `silent streams stay open past the command read timeout` | command read timeout 1 s; over 8 s the event stream is not re-subscribed, the Console stays connected, a single and a group log follow do not end |
| 3 | `a command the engine never answers still fails` | command read timeout 1 s; `getVersion` against a server that never answers fails within 10 s |
| 4 | `a reconnect replaces the stream connections too` | engine stops accepting, a second engine takes the port; events and stats re-open on the second engine |
| 5 | `a closed repository opens no stream` | after `close()`, a log follow ends and no `/logs` request reaches the engine |
| 6 | `a Console the engine never answers still fails to connect` | command read timeout 1 s; a Console connect against a server that never answers fails within 10 s |

### G1. Constants and constructor (`DesktopDockerRepository.kt:67-76`)

After `STARTUP_PING_RETRY_MS` (`:72`) add:

```kotlin

// One-shot calls and long-lived streams use separate HTTP clients (review-2026-10 B8). A stream holds
// its connection for as long as it runs — one per running container for stats — and on a shared
// pool one-shot calls, the availability ping included, waited minutes for a free connection.
private const val COMMAND_POOL_SIZE = 100
private const val STREAM_POOL_SIZE = 1000
```

Constructor, old → new:

```kotlin
class DesktopDockerRepository(
    private val dockerHost: String,
) : DockerRepository {
```

```kotlin
class DesktopDockerRepository(
    private val dockerHost: String,
    // Read timeout of one-shot calls; tests shorten it.
    private val commandResponseTimeout: Duration = Duration.ofSeconds(45),
) : DockerRepository {
```

`createDockerRepository` (`DockerRepositoryFactory.kt:3`) keeps calling `DesktopDockerRepository(dockerHost)`.

### G2. `createHttpClient` (`:85-96`)

Replace the whole function with:

```kotlin
    private fun createHttpClient(
        maxConnections: Int,
        responseTimeout: Duration?,
    ): ApacheDockerHttpClient =
        ApacheDockerHttpClient
            .Builder()
            .dockerHost(config.dockerHost)
            .maxConnections(maxConnections)
            .connectionTimeout(Duration.ofSeconds(10))
            // httpclient5 keeps the response timeout as the socket read timeout for the whole
            // exchange, not only until the headers, so a response silent for that long is cut. It
            // guards one-shot calls against a dead connection; streams get none (null), or an idle
            // Console, a quiet log follow and a quiet engine's event stream were cut after 45 s.
            .responseTimeout(responseTimeout)
            .build()
```

### G3. Holder and accessors (`:98-122`)

Replace `:98-114` (the `ClientPair` KDoc, class, `createClientPair()` and `@Volatile private var
clients = createClientPair()`) with:

```kotlin
    /**
     * Immutable holder of both HTTP transports and the [DockerClient] built on each, so all four are
     * swapped with a single volatile write: separate volatile fields would let a concurrent reader
     * pair a fresh transport with a stale client mid-rebuild.
     *
     * [commands] runs one-shot calls, with a read timeout. [streams] runs the long-lived streams
     * (events, stats, log follows, the Console's exec session), with none, in its own pool so
     * streams never leave one-shot calls waiting for a connection (review-2026-10 B8).
     */
    private class Clients(
        val commandsHttp: ApacheDockerHttpClient,
        val commands: DockerClient,
        val streamsHttp: ApacheDockerHttpClient,
        val streams: DockerClient,
    )

    private fun createClients(): Clients {
        val commandsHttp = createHttpClient(COMMAND_POOL_SIZE, commandResponseTimeout)
        val streamsHttp = createHttpClient(STREAM_POOL_SIZE, responseTimeout = null)
        return Clients(
            commandsHttp = commandsHttp,
            commands = DockerClientImpl.getInstance(config, commandsHttp),
            streamsHttp = streamsHttp,
            streams = DockerClientImpl.getInstance(config, streamsHttp),
        )
    }

    @Volatile private var clients = createClients()
```

Keep `clientRebuildLock` (`:116`) and `closed` (`:118`). Replace `:120-122`:

```kotlin
    private val dockerClient: DockerClient get() = clients.dockerClient

    val client: DockerClient get() = clients.dockerClient
```

with:

```kotlin
    private val dockerClient: DockerClient get() = clients.commands

    private val streamClient: DockerClient get() = clients.streams

    /** For the Console: its exec session is a long-lived stream, so it runs on the stream client. */
    val client: DockerClient get() = clients.streams

    /** For the Console's one-shots (exec create, resize): they keep the command read timeout. */
    val commandClient: DockerClient get() = clients.commands
```

### G4. `rebuildClient` (`:124-143`)

Replace with:

```kotlin
    /**
     * Atomically replace both clients, then close the old transports (after the swap, so no reader
     * can pick them up from [clients] anymore). [stale] is the holder the caller observed failing:
     * if a concurrent caller already swapped it out, the rebuild is skipped so we never close a
     * client a sibling just installed. Both call paths (`withRetryOnPoolShutdown` and
     * `determineAvailability`) serialize on [clientRebuildLock]. The stream client is replaced too:
     * it has no read timeout, so a dead stream connection is only closed here.
     */
    private fun rebuildClient(stale: Clients = clients) {
        synchronized(clientRebuildLock) {
            if (closed) return // closed by a host switch: a new client here would never be closed (review-2026-10 R1)
            val old = clients
            if (old !== stale) return // a concurrent caller already rebuilt; reuse its client
            clients = createClients()
            closeQuietly(old.commandsHttp, "replaced command HTTP client")
            closeQuietly(old.streamsHttp, "replaced stream HTTP client")
        }
    }

    private fun closeQuietly(
        closeable: java.io.Closeable,
        what: String,
    ) {
        try {
            closeable.close()
        } catch (e: Exception) {
            logger.debug("Error closing {}: {}", what, e.message)
        }
    }
```

`ApacheDockerHttpClient` and `DockerClient` are both `java.io.Closeable`.

### G5. `withRetryOnPoolShutdown` (`:169-183`)

Only the comment at `:170-171` changes: "Capture the pair in use so a failure only rebuilds THIS
pair" → "Capture the clients in use so a failure only rebuilds THESE clients". The code is
unchanged; `used` is now a `Clients`.

### G6. Stream call sites — exactly four, `dockerClient` → `streamClient`

- `:287` `dockerClient.eventsCmd().exec(callback)` → `streamClient.eventsCmd().exec(callback)`
- `:848` in `followContainerLogs`: `dockerClient` → `streamClient` (the receiver of
  `.logContainerCmd(id)` on `:849`)
- `:912` in `followMultipleContainerLogs`: `dockerClient` → `streamClient` (the receiver of
  `.logContainerCmd(containerId)` on `:913`)
- `:1395` `dockerClient.statsCmd(containerId)` → `streamClient.statsCmd(containerId)`

Every other `dockerClient` use stays. Do not move `getContainerLogs` (`:592`, no follow),
`execCapture` (`:727`, `:770`, `:793`, `:795`) or `pullImage` (`:1095`).

### G7. `execCapture` KDoc (`:713-718`)

The last sentence is wrong now (it was already wrong for httpclient5 5.5.1). Old:

```
     * type. Bounded by [timeoutMillis] via the callback's built-in awaitCompletion — the HTTP client
     * deliberately has no socket read timeout for streaming endpoints, so this is the only ceiling.
```

New:

```
     * type. Bounded by [timeoutMillis] via the callback's built-in awaitCompletion, and by the
     * command client's read timeout when the exec prints nothing for that long.
```

### G8. `close()` (`:1489-1502`)

Replace with:

```kotlin
    override fun close() {
        try {
            scope.cancel()
            val current =
                synchronized(clientRebuildLock) {
                    closed = true
                    clients
                }
            // One by one: a failure closing the command pair must not leave the stream pair open.
            closeQuietly(current.commands, "command client")
            closeQuietly(current.commandsHttp, "command HTTP client")
            closeQuietly(current.streams, "stream client")
            closeQuietly(current.streamsHttp, "stream HTTP client")
        } catch (e: Exception) {
            logger.warn("Error during DockerRepository shutdown", e)
        }
    }
```

### G9. The Console (`DockerExecTtyConnector.kt`, `JediTermConsole.kt`)

**Constructor** (`DockerExecTtyConnector.kt:16-20`), old → new:

```kotlin
class DockerExecTtyConnector(
    private val dockerClient: DockerClient,
    private val containerId: String,
    private val cmd: List<String> = listOf("/bin/sh"),
) : TtyConnector {
```

```kotlin
class DockerExecTtyConnector(
    private val dockerClient: DockerClient,
    private val containerId: String,
    private val cmd: List<String> = listOf("/bin/sh"),
    // Exec create and resize are one-shots: they run on the command client, with its read timeout.
    // Only the exec session itself streams on [dockerClient] (review-2026-10 B8).
    private val commandClient: DockerClient = dockerClient,
) : TtyConnector {
```

**Receivers:** `:54` `dockerClient` (the receiver of `.execCreateCmd(containerId)` on `:55`) →
`commandClient`; `:146` `dockerClient.resizeExecCmd(id)` → `commandClient.resizeExecCmd(id)`. `:96`
(the receiver of `.execStartCmd(...)`) stays `dockerClient`.

**`JediTermConsole.kt:72-75`**, old:

```kotlin
                    withContext(Dispatchers.IO) {
                        DockerExecTtyConnector(
                            dockerClient = (AppModule.dockerRepository as DesktopDockerRepository).client,
                            containerId = containerId,
                        ).also {
```

New:

```kotlin
                    withContext(Dispatchers.IO) {
                        val repository = AppModule.dockerRepository as DesktopDockerRepository
                        DockerExecTtyConnector(
                            dockerClient = repository.client,
                            containerId = containerId,
                            commandClient = repository.commandClient,
                        ).also {
```

`DockerExecTtyConnectorTest` keeps the default (`commandClient = dockerClient`); do not change it.

**Comment at `DockerExecTtyConnector.kt:92-95`**, old:

```kotlin
            // NOTE: execStartCmd has no built-in read/connection timeout exposed by docker-java's
            // fluent API. Adding a socket-level timeout here would require configuring the
            // DockerClient itself (e.g. DockerClientConfig.withReadTimeout). That lives in
            // DockerRepository, which is owned by another agent — left as a cross-lane concern.
```

New:

```kotlin
            // No read timeout, on purpose: the repository hands the Console its stream client, so
            // an idle session stays open (review-2026-10 B8).
```

### G10. Negative controls (each alone, then restore)

| NC | Revert | Run | Must fail with |
|---|---|---|---|
| 1 | `:1395` back to `dockerClient.statsCmd` | test 1 | "the ping waited for a free connection" |
| 2 | `:287` back to `dockerClient.eventsCmd` | test 2 | "the silent event stream was cut and subscribed again" |
| 3 | `val client` back to `clients.commands` | test 2 | "the idle Console session was cut" |
| 4 | `:848` back to `dockerClient` | test 2 | "the quiet log follow ended" |
| 5 | `:912` back to `dockerClient` | test 2 | "the quiet group log follow ended" |
| 6 | in `createClients`, the command client built with `responseTimeout = null` | test 3 | "getVersion did not give up after the read timeout" |
| 7 | in `rebuildClient`, drop `closeQuietly(old.streamsHttp, …)` | test 4 | "timed out waiting for the event and stats streams on the second engine" |
| 8 | in `close()`, drop the two stream lines | test 5 | "a log follow on a closed repository did not end" or "a closed repository opened a log stream" |
| 9 | `DockerExecTtyConnector.kt:54` back to `dockerClient` | test 6 | "the Console's exec create did not give up after the read timeout" |

NC 1 leaves the test's two `Dispatchers.IO` workers (the ping and `getVersion`) parked in
httpclient5's connection-request wait for up to 3 minutes after the test fails; they are daemon
threads and do not block the Gradle run. NC 9 leaves one parked in a read without timeout until the
test JVM exits.

### G11. Acceptance (WS-G)

- `DesktopDockerRepositoryClientsTest`: 6 tests, 0 failures, 0 errors (XML), normal and headless.
- Full `:composeApp:desktopTest` green, totals from the XML: 313 before + 6 new = 319 expected (if
  the count differs, report the per-class totals).
- `spotlessCheck` green (own invocation).
- `grep -n "streamClient\." composeApp/src/desktopMain/kotlin/com/containerdashboard/data/repository/DesktopDockerRepository.kt`
  → exactly 2 lines (`:287`-area `eventsCmd`, `:1395`-area `statsCmd`); the two follow call sites
  have `streamClient` alone on its line followed by `.logContainerCmd(...)`:
  `grep -n -A1 "^ *streamClient$" …` → 2 matches, each followed by `.logContainerCmd`.
- `grep -n "ClientPair\|createClientPair\|httpClient\b" …DesktopDockerRepository.kt` → no match.
- `grep -n "zeroes the socket" …DesktopDockerRepository.kt` → no match.
- `grep -n "commandClient" composeApp/src/desktopMain/kotlin/com/containerdashboard/terminal/JediTermConsole.kt`
  → 1 match; in `DockerExecTtyConnector.kt`, `commandClient` appears in the constructor and on the
  exec create and resize lines only.
- Run the greps with this shell's `grep` (it accepts `\|` alternation and `\b`) or with `grep -E`
  and `|`. A bare `ugrep` without `-G` treats `\|` as a literal, and a "no match" check would then
  pass for the wrong reason.
- All 9 negative controls reported with their failure message.

## Live check (orchestrating agent) — REQUIRED

The Console's connection state in the running app is runtime-only. Under hot reload against the
fake engine: open the Console on `demo-web`, type nothing for 60 s, confirm it still shows the
session (no disconnected state, no reconnect button) and that the fake engine's log shows one
`exec start` only. Negative control: with the old single-client code the session drops at ~45 s
(already measured with a probe; repeat only if the in-app view is ambiguous). Owed to the user: the
same on Docker Desktop or Colima, and a quiet followed log on a real engine.

## Risks for the reviewer

1. `responseTimeout(null)`: is there any other place httpclient5 5.5.1 or docker-java's transport
   sets a socket timeout on a leased connection (connection config, the hijacking executor, the
   `ApacheResponse` body stream)? Test 2 says no for the event stream, the Console's hijacked exec
   and log follows; check the source.
2. Does anything else read `client` or assume one client: `JediTermConsole.kt:74` (the Console
   takes the stream client at connect time; after a rebuild its session is closed and a reconnect
   reads the new one). (Answered by the review: the connector's one-shots moved to the command
   client, G9.)
3. Rebuild semantics: both transports are closed on a rebuild. Any path where the command client
   needs a rebuild but the stream client should be kept (or the reverse)? `withRetryOnPoolShutdown`
   only fires when the command pool is shut down, which only happens after a rebuild (then
   `old !== stale` skips) or `close()` (then `closed` skips).
4. Test 4 binds a second `FakeDockerEngine` on the port the first one just stopped listening on,
   while the first engine's accepted connections are still open. `FakeDockerEngine` sets
   `reuseAddress = true`. Passes on macOS; will it on the Linux CI runner?
5. Test 1 starts 101 stats streams (101 server threads, 101 client threads). Any resource limit on
   the CI runner? Under a regression it leaves threads blocked in a lease for up to 3 minutes.
6. Test timing: test 2 waits 8 s against a 1 s command timeout and a 5 s event re-subscribe delay;
   test 4's 15 s deadline covers the 5 s event pause plus the stats retry backoff.
7. Does the extra `DockerClientImpl` per repository share any state with the first (both built from
   the same immutable `config`)?
8. Anything in the plan's snippets that would not compile or would fail ktlint
   (`Duration?` passed to the Java builder's `responseTimeout(Duration)`; `closeQuietly` taking
   `java.io.Closeable`).

## Review log

Plan review by a fresh Fable subagent: **APPROVE WITH AMENDMENTS** (0 BLOCKER, 2 MAJOR, 6 MINOR).
It confirmed the library claims from source and bytecode and with a runtime probe on two clients
built like the plan's (101 stats streams, events and a hijacked exec alive 3 s past a sibling
client's 1 s timeout; the ping answered in 20 ms with 103 streams open; a silent command failed
after 1005 ms; closing the stream pool ended all 103 stream threads). It compile- and ktlint-checked
the plan's snippets (probes deleted, untracked files checksum-identical). Each MAJOR was re-checked
against the cited source before applying.

- **Applied — MAJOR 1:** the Global constraints block named the worktree by its absolute path
  (with a username); now `<worktree-path>`, as in the two earlier plans.
- **Applied — MAJOR 2:** on the stream client the Console's exec create (`DockerExecTtyConnector.kt:54`)
  and resize (`:146`; the review said `:150`) lost their read timeout, and the connect is not
  interruptible, so a Console opened against an engine that accepts but never answers would wait
  forever. The connector takes a `commandClient` for its one-shots (G9); test 6 and NC 9 pin it.
- **Applied — MINOR 3:** the planner's test file failed `spotlessCheck` (one chained call); reflowed.
- **Applied — MINOR 4:** the follow receivers are on `:848` and `:912`, one line above the anchors.
- **Applied — MINOR 5, 6, 7, 8:** NC 1's note names the right threads; the `close()` comment says
  what the per-call tries buy; the pending-lease gap cites `StrictConnPool.java:147`; a note on
  running the acceptance greps.

## Result audit
