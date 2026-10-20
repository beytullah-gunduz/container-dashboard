# Plan — review 2026-10 fixes: list errors & log tail, Console, pane/palette

Source findings: [`docs/audit/review-2026-10.md`](audit/review-2026-10.md) — B3, B2, G3, G4, G2, G5,
G6, G7. G1, B1 and S5.3 are already fixed on this branch. Line numbers below are for the branch
HEAD this plan was written on (after the S5.3 fix and the audit doc commit).

## Problems and root causes (verified)

| ID | User-visible problem | Root cause |
|---|---|---|
| B3 | One failed container-list call closes the open logs/files pane, stops the follow, shows "No containers", reports 0 running | `DesktopDockerRepository.kt:388-419`: every failure of `listContainersCmd()` emits `emptyList()`; consumers treat it as "everything was deleted" (`AppViewModel.kt:95-100`). Same pattern for images (`:934-976`), volumes (`:1043-1103`), networks (`:1165-1205`) |
| B2 | When a container exits, the last ≤100 ms of its logs (often the stack trace) are missing from the followed pane | `DesktopDockerRepository.kt:783` and `:856`: `.conflate().sample(100)`; kotlinx `sample` drops the pending value when upstream completes. Live since `9f45245` made the follow flows complete |
| G3 | Each Console session leaves a non-daemon `JediTerm-fast-job-N` thread (and a cached pool) behind | `JediTermConsole.kt:89-97`: `onDispose` closes only the connector; `createTerminalWidget` (`:165-198`) drops the `JediTermWidget`, so `widget.close()` (which shuts its executors down, JediTerm 3.76 `JediTermWidget.java:270-277`) never runs |
| G4 | In the Console, `stty size` is `0 0`; `top`/`vim` draw at 80×24; resizing does nothing | `DockerExecTtyConnector.kt` does not override `TtyConnector.resize(TermSize)`; the default throws and JediTerm's executor swallows it, so no `resizeExecCmd` is sent. JediTerm posts the initial size itself (`TerminalPanel.java:654-672`, on `setTerminalStarter` and on every component resize), so overriding `resize` is enough |
| G2 | Esc hides the detail pane but the follow keeps running and the row stays highlighted | `App.kt:286`: `onCloseOverlays` → `navigator.hideExtraPane()` only; the Close button (`App.kt:402-405`) also calls `viewModel.clearLogs()` |
| G5 | Inspect shows the first result forever (a container stopped since still reads "running"); one view model per inspected resource is kept for the process | `ResourceDetailsDialog.kt:205-208`: `viewModel(key = detailsKey(target))` returns the window-store instance on every reopen; `load()` runs only in its `init` |
| G6 | The ⌘K palette acts on the action list from when it opened (e.g. "Stop: X" for an X that already stopped → 409) | `CommandPalette.kt:94-127`: four `remember { derivedStateOf { … } }` with no key; `filtered` captures the first `actions`, and the three chained ones capture the first `filtered` delegate. **Reproduced headlessly** (throwaway probe: after the list changed, the old label was still shown and the new one absent) |
| G7 | (reported) the logs pane keeps the previous container's scroll position and follow flag | **Not reproducible.** A throwaway headless probe scrolled container A to the top, switched to B through `isLoading` (as every switch does): B landed at its last line and followed an appended line. The reset effect sits in the branch that is re-entered on every switch. Pin the behaviour with a test; no code change |

## Decisions (decisions, not options — do not relitigate)

1. **B3 — keep the last good list.** A list call that fails *after that list has loaded once in
   this repository instance* emits nothing (consumers keep the last list). A failure *before* the
   first success still emits `emptyList()` (there is nothing to keep; unchanged behaviour). The
   first failure of a failure streak is reported once on a new `DockerRepository.listRefreshFailures()`
   flow; `AppViewModel` routes it to its existing `_error`, which `App.kt:238-242` already shows as
   an error snackbar. Applies to containers, images, volumes and networks alike. The `all = false`
   cold fallback (`:428-445`) is out of scope. Each fresh upstream subscription (the
   `WhileSubscribed` restart runs `onStart` again) resets the streak, so it can report again.
   Known behaviour, accepted: during an outage up to four messages (one per list type) can land
   at once; `_error` is a single value, so the snackbar shows the last one.
2. **B2 — throttle that flushes.** Replace `.conflate().sample(100)` with
   `.conflate().transform { snapshot -> emit(snapshot); delay(100) }` in both follow flows: first
   snapshot immediately, at most ~10/s afterwards, and the final snapshot always delivered.
3. **G3 — close the widget when its SwingPanel leaves.** Keep a reference to the widget the factory
   built and close it (on the EDT) from a `DisposableEffect` keyed on the connector.
4. **G4 — override `resize(TermSize)`** in `DockerExecTtyConnector` to call
   `resizeExecCmd(execId).withSize(rows, columns)`, directly on JediTerm's calling thread (a fast
   job, off the EDT); ignore it when not connected; log failures at DEBUG. Accepted trade-off: that
   executor also delivers keystrokes, so a hung daemon would delay typing for up to the 45 s
   response timeout (the same trade-off JediTerm's own process connector makes with its ioctl).
5. **G2 — Esc does what Close does:** `hideExtraPane()` then `viewModel.clearLogs()`.
6. **G5 — no ViewModel.** A plain loader class remembered per open dialog, launched in the dialog's
   `rememberCoroutineScope()`; every open loads afresh and nothing outlives the dialog. The dialog
   gets a `repository` parameter (default: `AppModule.dockerRepository`) for tests.
7. **G6 — key all four derived states on `actions`.**
8. **G7 — test only**, and the audit doc records it as not reproducible.
9. Execution order: WS-A, then WS-B, then WS-C, one implementer at a time, each committed before
   the next starts (WS-A and WS-C both edit `commonTest/.../FakeDockerRepository.kt`).

## Global constraints (paste verbatim into every implementer prompt)

```
- Worktree: <worktree-path> — a git worktree on branch feature/fable-gui-backend-review-b8d318.
  Work only there, with absolute paths. Never touch the main checkout.
- Build/test (plain ./gradlew fails on the system Java 25):
    JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :composeApp:desktopTest --console=plain
  Targeted: append --tests 'com.containerdashboard.<TestClass>' (repeatable).
  The CI runs desktopTest on headless Linux. Also run your new tests once headless:
    JAVA_TOOL_OPTIONS="-Djava.awt.headless=true" JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :composeApp:desktopTest --tests '<your classes>' --rerun --console=plain
- Formatting: run each in its OWN invocation, never chained with compile/test (Gradle
  config-cache flake):
    JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew spotlessApply --console=plain
    JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew spotlessCheck --console=plain
- Read test totals from composeApp/build/test-results/desktopTest/*.xml (the tests/failures/errors
  attributes), not from the console summary.
- commonMain code must not use JVM-only APIs (java.*, javax.*); desktopMain and desktopTest may.
- Do NOT commit, stash, checkout, reset or rebase. The orchestrating agent commits.
- Do NOT run docker, colima or podman. Do NOT launch the app (no run / hotRun* tasks) and do not
  start the fakeDockerEngine Gradle task. Tests may start FakeDockerEngine in-process
  (FakeDockerEngine(port = 0, log = {})) or a com.sun.net.httpserver.HttpServer on 127.0.0.1:0.
- This repository is PUBLIC. Never write a real account email, org name, host name, internal URL,
  IP (other than 127.0.0.1), token, or an absolute path containing a username into code, tests,
  comments, fixtures or log strings. Use obviously fake values (demo-web, c1, example).
- NO TEST MAY READ OR WRITE REAL USER STATE. A test that builds a DesktopDockerRepository must
  first assertNotNull(System.getProperty(DATA_DIR_PROPERTY)) exactly like
  DesktopDockerRepositoryStreamsTest.setUp (the desktopTest task points it at build/test-data).
- Match the surrounding code: comment density, naming, idioms. One short comment per fix site
  saying why, citing "review-2026-10 <ID>"; no comment essays.
- Negative control is mandatory for every new test that pins a fix: revert only the fix line(s),
  run the targeted test, confirm it FAILS (quote the failure message), restore the fix, confirm it
  passes. Leave no reverted state behind.
- Final report: files changed; the full-suite totals from the XML; each negative control (what you
  reverted, the failure message); the headless run result; every deviation from this plan, with the
  reason. Do not paraphrase errors — quote them.
```

## WS-A — B3 (keep the last good list) + B2 (deliver the last log snapshot)

Files owned: `commonMain/.../data/repository/DockerRepository.kt`,
`desktopMain/.../data/repository/DesktopDockerRepository.kt`,
`commonMain/.../ui/screens/viewmodel/AppViewModel.kt`, `commonTest/.../FakeDockerRepository.kt`,
`commonTest/.../AppViewModelErrorTest.kt`, `desktopTest/.../DesktopDockerRepositoryStreamsTest.kt`,
new `desktopTest/.../DesktopDockerRepositoryListRefreshTest.kt`.
(Paths: `composeApp/src/<set>/kotlin/com/containerdashboard/...`.)

### A1. Interface (`DockerRepository.kt`)

Add, right after `suspend fun refreshNetworks()` (`:40`):

```kotlin
    /**
     * One message per failure streak of a list refresh (containers, images, volumes, networks)
     * that failed after that list had loaded: the list flows then keep their last list instead of
     * emitting an empty one, and this says so (review-2026-10 B3).
     */
    fun listRefreshFailures(): Flow<String> = emptyFlow()
```

Add `import kotlinx.coroutines.flow.emptyFlow` (keep imports sorted as the file has them).

### A2. Repository (`DesktopDockerRepository.kt`)

1. Next to the four `*RefreshTrigger` fields (`:354-357`), add:

```kotlin
    private val _listRefreshFailures =
        MutableSharedFlow<String>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override fun listRefreshFailures(): Flow<String> = _listRefreshFailures.asSharedFlow()

    /** Whether a shared list has loaded once in this repository, and whether its refreshes now fail. */
    private class ListRefreshState(
        val what: String,
    ) {
        @Volatile var loaded = false

        @Volatile var failing = false
    }

    private val containersListState = ListRefreshState("container")
    private val imagesListState = ListRefreshState("image")
    private val volumesListState = ListRefreshState("volume")
    private val networksListState = ListRefreshState("network")

    /**
     * One list call for a shared list flow. Success: the list. A failure before the list ever
     * loaded: an empty list, as there is nothing to keep. A failure after: null, which the flow
     * drops so subscribers keep the last list — an empty one reads as "everything was deleted"
     * and closes the detail pane (review-2026-10 B3) — and the first failure of a streak is
     * reported on [listRefreshFailures].
     */
    private inline fun <T> listOrKeepLast(
        state: ListRefreshState,
        list: () -> List<T>,
    ): List<T>? =
        try {
            list().also {
                state.loaded = true
                state.failing = false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn("Failed to list {}s: {}", state.what, e.message)
            if (!state.loaded) {
                emptyList()
            } else {
                if (!state.failing) {
                    state.failing = true
                    _listRefreshFailures.tryEmit(
                        "Couldn't refresh the ${state.what} list (${e.message ?: e::class.simpleName}); " +
                            "showing the last one received.",
                    )
                }
                null
            }
        }
```

   `CancellationException` is `kotlinx.coroutines.CancellationException`. New imports:
   `kotlinx.coroutines.CancellationException`, `kotlinx.coroutines.channels.BufferOverflow`,
   `kotlinx.coroutines.flow.asSharedFlow`, `kotlinx.coroutines.flow.filterNotNull`.

2. Extract the four list calls into private functions placed right above each shared flow,
   **moving the existing bodies unchanged** (the `withRetryOnPoolShutdown { … }` blocks currently
   duplicated in `.map {}` and `.onStart {}`):

   - `private fun listContainersNow(): List<Container>` — the `listContainersCmd().withShowAll(true)` block (`:390-396`).
   - `private fun listImagesNow(): List<DockerImage>` — the `listImagesCmd().withShowAll(true)` block.
   - `private fun listVolumesNow(): List<Volume>` — the `listVolumesCmd()` block, including its `?.map { … } ?: emptyList()` mapping.
   - `private fun listNetworksNow(): List<DockerNetwork>` — the `listNetworksCmd()` block.

3. Rewrite the tail of each of the four shared flows to this shape (containers shown; the others
   use their own state, function and log text, e.g. `"Image flow error"`):

```kotlin
        ).map { listOrKeepLast(containersListState) { listContainersNow() } }
            .onStart {
                // A fresh subscription may report a failure again.
                containersListState.failing = false
                emit(listOrKeepLast(containersListState) { listContainersNow() })
            }
            .filterNotNull()
            .catch { e ->
                logger.error("Container flow error", e)
                if (!containersListState.loaded) emit(emptyList())
            }.flowOn(Dispatchers.IO)
            .shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)
```

   Keep the `merge(...)` heads (events filter, refresh trigger, 15 s poll) exactly as they are.
   The old per-site `logger.warn("Failed to list … on start")` lines go away (the helper logs).

4. B2: in `followContainerLogs` (`:783`) and `followMultipleContainerLogs` (`:856`) replace
   `}.conflate().sample(100).flowOn(Dispatchers.IO)` with:

```kotlin
        }.conflate()
            // At most ~10 snapshots a second, but the last one always arrives: sample() dropped a
            // snapshot that came after its last tick when the stream ended (review-2026-10 B2).
            .transform { snapshot ->
                emit(snapshot)
                delay(100)
            }.flowOn(Dispatchers.IO)
```

   (Put the comment only on the first site; the second gets `// See followContainerLogs.`.)
   Remove `@OptIn(kotlinx.coroutines.FlowPreview::class)` from both functions and the
   `import kotlinx.coroutines.flow.sample` import if `sample` is no longer used in the file
   (`grep -n "\.sample(" DesktopDockerRepository.kt` must print nothing). Add
   `import kotlinx.coroutines.flow.transform`.

### A3. View model (`AppViewModel.kt`)

Directly after `fun clearError() { … }` (`:128-130`), add:

```kotlin
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val listRefreshFailures: Flow<String> = repoFlow.flatMapLatest { it.listRefreshFailures() }

    init {
        // A list refresh that failed after the list had loaded keeps the last list on screen;
        // say so, once per failure streak (review-2026-10 B3).
        viewModelScope.launch { listRefreshFailures.collect { _error.value = it } }
    }
```

(A second `init` block after `_error` on purpose: `_error` must be initialized first.) Add the
`Flow` import if missing. Do not change the containers collector (`:81-118`): with the repository
no longer emitting on a failed refresh, its `allGone` branch only sees real lists.

### A4. Fake (`FakeDockerRepository.kt`)

Add a constructor parameter next to the other flow overrides:
`var listRefreshFailuresFlowOverride: Flow<String>? = null,` and
`override fun listRefreshFailures(): Flow<String> = listRefreshFailuresFlowOverride ?: emptyFlow()`
(import `emptyFlow` if missing).

### A5. Tests

1. New `desktopTest/.../DesktopDockerRepositoryListRefreshTest.kt`, modelled on
   `DesktopDockerRepositoryStreamsTest` (same `setUp` guard, `HttpServer` on `127.0.0.1:0`,
   `respond` helper, `tearDown` closing the repository and stopping the server). Handler:
   `/_ping` → `OK`; `/events` → headers then end (as in the streams test); `/containers/json` →
   increments `containersRequests` (an `AtomicInteger`); while `failAfter` (an `AtomicInteger`)
   is ≥ the request number it answers `200` with
   `[{"Id":"c1","Names":["/one"],"Image":"demo/web:1.0","ImageID":"sha256:0","Command":"sh","Created":0,"State":"running","Status":"Up 1 minute","Ports":[],"Labels":{}}]`,
   afterwards `500` with `{"message":"fake failure"}`; anything else → 404.
   Tests (use `runBlocking`, `launch(Dispatchers.IO)` collectors into `CopyOnWriteArrayList`s,
   `withTimeout` polling loops with `delay(50)`, cancel collectors in `finally`):
   - `a failed refresh keeps the last container list and reports it once`: `failAfter = 1`.
     Start the failures collector, then the list collector; wait for 1 emission; call
     `repo.refreshContainers()` and wait until `containersRequests >= 2` and `failures.size == 1`;
     call `repo.refreshContainers()` again and wait until `containersRequests >= 3`, then
     `delay(500)`. Assert the emissions are exactly `[["c1"]]` (map to ids) and `failures.size == 1`
     and `failures.single()` contains `"container list"`.
   - `a failure before the first list emits an empty list and reports nothing`: `failAfter = 0`.
     Collect until 1 emission; `delay(500)`; assert emissions `== [[]]` and no failures.
   Negative control: revert `listOrKeepLast`'s `null` branch to return `emptyList()` → the first
   test must fail.
2. `DesktopDockerRepositoryStreamsTest.kt`: change the two follow tests to collect with
   `toList()` and assert the last snapshot:
   - single: `assertEquals(listOf("line from c1"), snapshots.last())`;
   - group: `assertEquals(setOf("[one] line from c1", "[two] line from c2"), snapshots.last().toSet())`.
   Keep the test names. Negative control: put `.sample(100)` back in one function → its test fails
   (`NoSuchElementException` or a wrong last snapshot).
3. `commonTest/.../AppViewModelErrorTest.kt`: add
   `a list refresh failure reaches the app error`: a `MutableSharedFlow<String>(extraBufferCapacity = 1)`
   as `listRefreshFailuresFlowOverride`; build the VM as the file's other tests do; `advanceUntilIdle()`;
   `tryEmit("Couldn't refresh the container list (x); showing the last one received.")`;
   `advanceUntilIdle()`; assert `vm.error.value` equals that string.
   Negative control: delete the new `init` block → the test fails.

### A6. Acceptance (WS-A)

- `grep -n "emptyList()" DesktopDockerRepository.kt` shows no `emptyList()` inside the four shared
  list flows' `.map {}`/`.onStart {}` (only in the helper, the `catch` blocks guarded by `!…loaded`,
  the `all = false` fallback and unrelated code).
- `grep -n "\.sample(" DesktopDockerRepository.kt` prints nothing.
- Full suite green from the XML; new tests present in the XML; headless run green; all three
  negative controls quoted.

## WS-B — G3 (close the JediTerm widget) + G4 (send the terminal size)

Files owned: `desktopMain/.../terminal/JediTermConsole.kt`,
`desktopMain/.../terminal/DockerExecTtyConnector.kt`,
`desktopTest/.../fakeengine/FakeDockerEngine.kt`, new `desktopTest/.../DockerExecTtyConnectorTest.kt`,
new `desktopTest/.../TerminalWidgetCloseTest.kt`.

### B1. `DockerExecTtyConnector.kt`

1. Add `import com.jediterm.core.util.TermSize` and override:

```kotlin
    // JediTerm calls this with the panel's size in characters when the session starts and on
    // every resize. Not overriding it left the default, which throws (swallowed by JediTerm), so
    // the shell never learned its size: `stty size` said 0 0 (review-2026-10 G4).
    override fun resize(termSize: TermSize) {
        val id = execId ?: return
        if (!isConnected()) return
        try {
            dockerClient.resizeExecCmd(id).withSize(termSize.rows, termSize.columns).exec()
        } catch (e: Exception) {
            logger.debug("Failed to resize exec session {}: {}", id, e.message)
        }
    }
```

   (`ResizeExecCmd.withSize(int height, int width)`, docker-java 3.7.1.)
2. Make `close()` release once: add `private val released = AtomicBoolean(false)`; in `close()`,
   keep `closed.set(true)` and `doneLatch.countDown()` first, then
   `if (!released.compareAndSet(false, true)) return`, then the existing stream closing and the
   "session closed" log. (`JediTermWidget.close()` closes the connector again through
   `TerminalStarter.close()`.)

### B2. `JediTermConsole.kt`

1. `createTerminalWidget`: make it `internal`, change the parameter type to
   `connector: TtyConnector` (`import com.jediterm.terminal.TtyConnector`), return the
   `JediTermWidget`: keep the settings object, then

```kotlin
    val widget = JediTermWidget(settings)
    widget.setTtyConnector(connector)
    widget.start()
    // Black backdrop … (keep the existing comment)
    widget.component.background = java.awt.Color.BLACK
    return widget
```

2. Add below it:

```kotlin
/**
 * Stops [widget]'s session, its executors (a single-thread scheduler and a cached pool per
 * widget) and its repaint timer; Compose disposing the SwingPanel does none of that
 * (review-2026-10 G3). Runs on the EDT.
 */
internal fun closeTerminalWidget(widget: JediTermWidget) {
    SwingUtilities.invokeLater {
        try {
            widget.close()
        } catch (e: Exception) {
            logger.warn("Failed to close the terminal widget", e)
        }
    }
}
```

   (`import javax.swing.SwingUtilities`.)
3. In the composable, inside `if (connector != null) {`, replace the `SwingPanel(...)` block with:

```kotlin
                val conn = connector!!
                // The widget the factory builds for this connector, closed when it leaves.
                val widgetRef = remember(conn) { AtomicReference<JediTermWidget?>(null) }
                DisposableEffect(conn) {
                    onDispose { widgetRef.getAndSet(null)?.let(::closeTerminalWidget) }
                }
                SwingPanel(
                    modifier = Modifier.fillMaxSize(),
                    factory = {
                        // (keep the existing comment about uncaught throwables)
                        try {
                            createTerminalWidget(conn, darkTheme).also { widgetRef.set(it) }.component
                        } catch (throwable: Throwable) {
                            logger.error("Failed to create terminal widget for container {}", containerId, throwable)
                            terminalErrorComponent(throwable)
                        }
                    },
                )
```

   (`import java.util.concurrent.atomic.AtomicReference`.) Leave the outer
   `DisposableEffect(containerId)` unchanged.

### B3. `FakeDockerEngine.kt`

1. Add a public property near `execs`:

```kotlin
    /** Every exec resize the engine received, as "<exec id> <rows> <columns>". */
    val execResizes: MutableList<String> = CopyOnWriteArrayList()
```

   (`import java.util.concurrent.CopyOnWriteArrayList`.)
2. Add a route after the `POST /exec/([^/]+)/start` route:

```kotlin
            Route("POST", "/exec/([^/]+)/resize") { conn, req, (id) ->
                if (execs[id] == null) {
                    conn.notFound("No such exec instance: $id")
                } else {
                    execResizes.add("$id ${req.query["h"]} ${req.query["w"]}")
                    log("exec resize $id h=${req.query["h"]} w=${req.query["w"]}")
                    conn.respond(200, contentType = null)
                }
            },
```

3. In the class KDoc "Coverage:" sentence, add "exec resize" to the list.

### B4. Tests

1. `DockerExecTtyConnectorTest` (desktopTest, package `com.containerdashboard`): `setUp` asserts
   `DATA_DIR_PROPERTY` like the streams test, starts `FakeDockerEngine(port = 0, log = {})` with
   `start()`, builds `DesktopDockerRepository("tcp://127.0.0.1:${engine.port}")`; `tearDown` closes
   the repository and the engine.
   - `resize sends the terminal size to the exec session`: `DockerExecTtyConnector(repo.client,
     containerId = "demo-web")`, `start()`, `resize(TermSize(120, 40))`, then
     `assertEquals(1, engine.execResizes.size)` and `assertTrue(engine.execResizes.single().endsWith(" 40 120"))`;
     `close()` in `finally`.
   - `resize after close sends nothing`: `start()`, `close()`, `resize(TermSize(120, 40))`,
     assert `engine.execResizes` is empty.
   Negative control: remove the `resize` override → the first test fails.
2. `TerminalWidgetCloseTest` (desktopTest): a private stub
   `object : TtyConnector` whose `read` returns `-1`, `write` does nothing, `isConnected()` is
   `false`, `waitFor()` returns `0`, `ready()` is `false`, `getName()` is `"test"`, `close()` does
   nothing. Test `closing the widget stops its executors`:

```kotlin
        var widget: JediTermWidget? = null
        SwingUtilities.invokeAndWait { widget = createTerminalWidget(stub, darkTheme = true) }
        val executor = widget!!.executorServiceManager.singleThreadScheduledExecutor
        closeTerminalWidget(widget!!)
        SwingUtilities.invokeAndWait {} // let the queued close run
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "the fast-job executor must terminate")
```

   Negative control: make `closeTerminalWidget` a no-op → the test fails. Run it in the headless
   run too; if JediTerm cannot be constructed headless, report the exact exception and STOP (do not
   add JVM flags or skip logic — the orchestrating agent decides).

### B5. Acceptance (WS-B)

- `grep -n "override fun resize" DockerExecTtyConnector.kt` prints one line.
- `grep -n "closeTerminalWidget" JediTermConsole.kt` prints the definition and one call.
- Full suite + headless run green; both negative controls quoted.
- **Live smoke REQUIRED (orchestrating agent, not the implementer)** under hot reload against the
  fake engine: opening the Console for demo-web makes the fake engine log `exec resize … h=<rows>
  w=<cols>` with non-zero values, and a window resize logs another; after five open/close cycles
  of the Console, `jcmd <app-pid> Thread.print` shows no accumulating `JediTerm-fast-job-` threads
  (and does with `closeTerminalWidget` reverted, via hot reload).

## WS-C — G2 (Esc), G5 (Inspect reloads), G6 (palette follows its actions), G7 (pin)

Files owned: `commonMain/.../App.kt`, `commonMain/.../ui/components/ResourceDetailsDialog.kt`,
`commonMain/.../ui/shortcuts/CommandPalette.kt`, `commonTest/.../FakeDockerRepository.kt`,
new `desktopTest/.../ResourceDetailsDialogUiTest.kt`, new `desktopTest/.../CommandPaletteUiTest.kt`,
new `desktopTest/.../LogsTabContentUiTest.kt`. Do not touch `LogsTabContent.kt`.

### C1. G2 (`App.kt:282-288`)

```kotlin
                            onCloseOverlays = {
                                when {
                                    showPalette -> showPalette = false
                                    showCheatsheet -> showCheatsheet = false
                                    // Same as the pane's Close button: hiding alone left the
                                    // follow running and the row highlighted (review-2026-10 G2).
                                    else -> {
                                        navigator.hideExtraPane()
                                        viewModel.clearLogs()
                                    }
                                }
                            },
```

No test (the lambda lives in `App()`, which needs the real `AppModule`; the hot-reload MCP cannot
press Esc). Acceptance by grep and review.

### C2. G5 (`ResourceDetailsDialog.kt`)

1. Rename `private class ResourceDetailsViewModel(private val target: DetailsTarget) : ViewModel()`
   to:

```kotlin
/**
 * Loads one inspect for [target]. Remembered per open dialog and run in the dialog's scope, so
 * every open loads afresh and nothing outlives the dialog — a window-scoped ViewModel showed the
 * first inspect forever (review-2026-10 G5).
 */
private class ResourceDetailsLoader(
    private val target: DetailsTarget,
    private val repo: DockerRepository,
    private val scope: CoroutineScope,
) {
```

   Remove the `private val repo get() = AppModule.dockerRepository` line; in `load()` replace
   `viewModelScope.launch` with `scope.launch`. Everything else in the class stays.
2. `ResourceDetailsDialog` signature becomes
   `fun ResourceDetailsDialog(target: DetailsTarget, onDismiss: () -> Unit, repository: () -> DockerRepository = { AppModule.dockerRepository })`
   and its first lines:

```kotlin
    val scope = rememberCoroutineScope()
    val loader = remember(target) { ResourceDetailsLoader(target, repository(), scope) }
    val state by collectStateAsState(loader.state)
```

   Replace `vm.load()` (Retry) with `loader.load()`. Delete `detailsKey` if nothing else uses it.
   Remove now-unused imports (`ViewModel`, `viewModelScope`, `viewModel`); add `CoroutineScope`,
   `launch`, `rememberCoroutineScope`, `DockerRepository` as needed. The four call sites
   (`ContainerRendering.kt:514`, `ImagesScreen.kt:732`, `NetworksScreen.kt:843`,
   `VolumesScreen.kt:795`) stay unchanged.
3. `FakeDockerRepository.kt`: add `var inspectContainerCalls = 0` with `private set`, incremented
   at the start of `inspectContainer`.
4. Test `ResourceDetailsDialogUiTest` (desktopTest, `androidx.compose.ui.test.v2.runComposeUiTest`,
   `@OptIn(ExperimentalTestApi::class)`): wrap the content in
   `CompositionLocalProvider(LocalViewModelStoreOwner provides owner)` with
   `val owner = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }`
   (so the pre-fix code also runs, for the negative control). Content:
   `if (open) ResourceDetailsDialog(DetailsTarget.ContainerTarget("c1", "one"), onDismiss = { open = false }, repository = { fake })`
   with `var open by mutableStateOf(true)`. `waitForIdle()`, assert `fake.inspectContainerCalls == 1`;
   `open = false`, `waitForIdle()`, `open = true`, `waitForIdle()`, assert `== 2`.
   Negative control: in `ResourceDetailsDialog.kt` temporarily add a file-level
   `private val loaderCache = mutableMapOf<DetailsTarget, ResourceDetailsLoader>()` and replace
   `val loader = remember(target) { ResourceDetailsLoader(target, repository(), scope) }` with
   `val loader = loaderCache.getOrPut(target) { ResourceDetailsLoader(target, repository(), scope) }`.
   Run the test: `inspectContainerCalls` must stay 1 (quote the assertion message). Remove the
   cache and the replacement; `git diff` on the file must show only the planned change. Do NOT
   re-introduce `viewModel(...)` or `AppModule` in the dialog for this control: the old view model
   reads `AppModule.dockerRepository`, i.e. the developer's real engine.

### C3. G6 (`CommandPalette.kt:94-127`)

Change the four `remember {` that wrap `derivedStateOf` (for `filtered`, `sectioned`,
`flatActions`, `actionIndexById`) to `remember(actions) {`. Add one comment above `filtered`:
`// Keyed on actions: unkeyed, the palette kept the list it opened with (review-2026-10 G6).`

Test `CommandPaletteUiTest`: `var actions by mutableStateOf(listOf(PaletteAction(id = "a", label = "Stop: alpha", section = "Containers", onRun = {})))`,
`setContent { CommandPalette(actions = actions, onClose = {}) }`, assert one "Stop: alpha" node;
set `actions = listOf(PaletteAction(id = "b", label = "Start: alpha", section = "Containers", onRun = {}))`,
`waitForIdle()`, assert one "Start: alpha" and zero "Stop: alpha" (`onAllNodesWithText(...).assertCountEquals(...)`).
Negative control: revert the four keys → the test fails.

### C4. G7 (pin only)

`LogsTabContent` reads the preferences DataStore (word wrap, max lines): start the test class with
the same `assertNotNull(System.getProperty(DATA_DIR_PROPERTY), …)` guard as
`DesktopDockerRepositoryStreamsTest` (add it to `ResourceDetailsDialogUiTest` and
`CommandPaletteUiTest` too).

Test `LogsTabContentUiTest`: inside `Box(Modifier.height(300.dp))` render
`LogsTabContent(state = state, onRefresh = {})` with `state` a `mutableStateOf` of
`LogsPaneState(containers = listOf(a), logs = (1..200).map { "a-line-$it" }, isFollowing = true)`
(`Container(id = "a", names = listOf("/a"), image = "demo", state = "running")`, same for `b`).
`onNode(hasScrollToIndexAction()).performScrollToIndex(0)` (only the lazy list has that action;
`hasScrollAction()` can also match the horizontal scroller when word wrap is off), `waitForIdle()`,
assert "a-line-200" is not
shown (count 0). Then `state = LogsPaneState(containers = listOf(b), isLoading = true)`, `waitForIdle()`,
`state = LogsPaneState(containers = listOf(b), logs = (1..200).map { "b-line-$it" }, isFollowing = true)`,
`waitForIdle()`, assert one "b-line-200"; append `"b-line-201"` to `logs`, `waitForIdle()`, assert one
"b-line-201". This passes on the current code. Negative control (to prove the test can see the
reported failure): temporarily delete the `LaunchedEffect(selectedService, filterText) { … }` block in
`LogsTabContent.kt` → the test fails; restore the file byte for byte (`git diff --stat` on it must be
empty afterwards).

### C5. Acceptance (WS-C)

- `grep -n "viewModel(" ResourceDetailsDialog.kt` prints nothing; `grep -n "remember(actions)" CommandPalette.kt` prints four lines.
- `grep -n "viewModel.clearLogs()" App.kt` prints two lines (Close button and Esc).
- Full suite + headless run green; three negative controls quoted.
- Owed to the user (manual; the hot-reload MCP has no keyboard or right-click): with a logs pane
  open and following, open Inspect and press Esc — the dialog closes, the pane and its Live badge
  stay; then press Esc again — the pane closes and the row highlight goes.

## Risks for the reviewer

1. B3: does keeping the last list strand any consumer? Initial-load failure, a host switch (new
   repository → fresh state), `WhileSubscribed` stop/restart with the replay cache, the
   `catch` → completion path. Is a snackbar per failure streak the right volume?
2. B3: `ListRefreshState` is written from the shared flow's single upstream collector (`map` and
   `onStart` run in one coroutine) — any concurrent writer?
3. B2: `conflate()` + `transform { emit; delay }` + `flowOn(IO)` — can the final snapshot still be
   lost on completion or cancellation?
4. G3: `widget.close()` on the EDT after the SwingPanel is gone; it closes the connector a second
   time through `TerminalStarter.close()`; headless construction of `JediTermWidget` in CI (Linux,
   no display).
5. G4: `resize` runs on JediTerm's fast-job thread and makes a blocking HTTP call; behaviour when
   the exec has ended; docker-java's `withSize(height, width)` argument order.
6. G5: `remember(target)` + `rememberCoroutineScope()` inside a composable that renders a `Dialog`;
   cancellation of an in-flight inspect on close; does anything else rely on `detailsKey`?
7. G6: the chained derived states and their captured delegates.
8. Tests: timing waits (`delay(500)`) in the B3 test; any test touching real user state.

## Review log

Plan review by a fresh Fable subagent: **APPROVE WITH AMENDMENTS** (0 BLOCKER, 2 MAJOR, 7 MINOR).
It verified the B2 operator chain, the JediTerm close path, `Dialog` in `runComposeUiTest` v2 and
the `ViewModelStoreOwner` setup with a throwaway probe (macOS, normal and headless; deleted).
Each MAJOR was re-checked against the cited source before applying.

- **Applied — MAJOR-1:** the G5 negative control could have restored the old view model, which
  reads `AppModule.dockerRepository` (the real engine). Replaced with a file-level cache revert.
- **Applied — MAJOR-2:** G7 test uses `hasScrollToIndexAction()`; the UI tests that read the
  DataStore get the `DATA_DIR_PROPERTY` guard.
- **Applied — MINOR-1:** fake engine answers exec resize with 200, as the Engine API does.
- **Applied — MINOR-2:** `onStart` resets `failing`, so a fresh subscription can report again.
- **Recorded — MINOR-3, MINOR-6:** accepted behaviours, written into Decisions 1 and 4.
- **Applied — MINOR-4:** `createTerminalWidget` anchor corrected to `:165-198`.
- **Recorded — MINOR-5:** Esc-inside-a-dialog check owed to the user (WS-C acceptance).
- **Recorded — MINOR-7:** `TerminalWidgetCloseTest` was only run headless on macOS; Linux CI
  (fontconfig for AWT's "Monospaced") is unproven until the branch's first CI run.
