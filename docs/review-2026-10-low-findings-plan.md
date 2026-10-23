# Plan — review 2026-10, remaining Low findings

Source: [`docs/audit/review-2026-10.md`](audit/review-2026-10.md) — B4, B5, B6, B7, B8, B9,
B10/G12, G8, G9, G10, G11, G13, R1, R2. Every finding was re-read on HEAD `69a9d04` before this
plan; line numbers are for that commit. Paths are relative to
`composeApp/src/<set>/kotlin/com/containerdashboard/`.

## Scope and decisions (decisions, not options — do not relitigate)

| ID | Verdict on HEAD | Decision |
|---|---|---|
| B4 | Confirmed: `lastAvailable` starts `false` (`DesktopDockerRepository.kt:161`), so the first reachable poll calls `rebuildClient()` (`:203-205`) and closes the client the startup streams were opened on | Make it tri-state: `null` = unknown. Rebuild only on a real `false` → reachable transition |
| R1 | Confirmed: `rebuildClient` (`:126-138`) has no closed check; `close()` (`:1447-1456`) | A `closed` flag set under `clientRebuildLock`; `rebuildClient` is a no-op once closed |
| B6 | Confirmed: `execCapture` (`:705-757`) blocks in `awaitCompletion` (`:736-741`) and ignores cancellation | `runInterruptible` around the start+await (an interrupt makes `awaitCompletion` close the callback in its own `finally`); the three callers rethrow `CancellationException` instead of logging it as an error. Note: no UI path cancels these jobs today (the file-browser coroutines run in `viewModelScope` and are not cancelled on pane close) — wiring that is a follow-up, out of scope |
| B7 | Confirmed: `AppViewModel.kt:251`, `:589`, `:628` drop every blank line from one-shot fetches and exports | One helper that keeps blank lines and drops only the empty piece after a final newline |
| B10/G12 | Confirmed: single `_actionInProgress` id (`ContainersScreenViewModel.kt:63-64`, `:121-141`); `removeContainer` (`:153-160`) unguarded; group "remove all" fans out (`ContainersScreen.kt:1010`, `:1186`) | `actionInProgress` becomes a `Set<String>`; `removeContainer` rejects an id already pending (synchronously); a new `removeContainers(ids)` aggregates failures into one message |
| G9 | Confirmed: nothing prunes selections against the live list | A `retainSelection(liveIds)` / `retainChecked(liveKeys)` call driven by the screens when the list changes (only once loaded). The `StateFlow`s stay raw — existing tests read `.value` synchronously |
| G8 | Confirmed: `pointerInput(container.id)` + captured `density` (`ContainerRendering.kt:448`), `pointerInput(Unit)` + captured `density` (`:938`), and the context-menu blocks in `ImagesScreen.kt:577`, `VolumesScreen.kt:648`, `NetworksScreen.kt:592` | Add `density` to each `pointerInput` key, as `ThreePaneScaffold.kt:293` does. (`VolumesScreen.kt:564` passes raw px, not a captured density: out of scope) |
| G10 | Confirmed: (a) `App.kt:244-251` re-toasts an error already shown inline when the route changes; (b) `App.kt:238-251` emit before `AppSnackbarHost` (`:499`) subscribes; `UiMessages` has no replay (`UiMessages.kt:23-29`) | Extract the two bridges into one internal composable; (a) remember the error the Containers card showed and clear it on leaving instead of toasting; (b) `UiMessages.awaitCollector()` before emitting |
| G11 | Confirmed: `PreferenceRepository` functions build a new `dataStore.data.map {}` per call (13 of them) and screens `collectAsState()` the call result | Memoize every flow-returning function in `PreferenceRepository` (one `by lazy` per flow). The Settings host-field draft is **out of scope**: it is UX story U8.1 (persist-on-Save semantics), which stays open |
| G13 | Confirmed: `SettingsScreen.kt:526-528` `remember(colimaConfig)` resets the quota fields when the probe lands | Seed once; a `LaunchedEffect(colimaConfig)` fills only the fields the user has not edited |
| B5 | Confirmed: `SettingsScreenViewModel.kt:103-115` reconnects whatever `startEngine` returned | Return early on `false`, like `restartEngine`; seams for the start operation and the reconnect so a test can see it |
| B9 | Confirmed: `EngineManager.kt:94-97` bare drain thread | `try/catch` around the drain, as `runProcess` (`:383-392`) does. No test: the command is hard-coded `colima` and tests must not run it |
| R2 | Confirmed: `JediTermConsole.kt:62-90`: cancellation during `withContext(IO) { …start() }` discards the connector, so the catch cannot close it | Assign `conn` inside the `withContext` block before `start()`. No automated test (needs a fake exec start that blocks mid-upgrade); verified by review |
| B8 | Confirmed by bytecode: docker-java 3.7.1 `maxConnections(100)` sets both `setMaxTotal` and `setDefaultMaxPerRoute`; no connection-request timeout is configured (httpclient5 default: 3 min). One long-lived stats stream per running container holds a connection | **Not fixed in this plan** — needs a design choice from the user (separate client for long-lived streams, scoping stats streams, or a larger pool with a lease timeout). Recorded in the audit doc |

Execution: WS-D, then WS-E, then WS-F, one implementer at a time, each committed before the next
starts. `FakeDockerRepository.kt` is edited by WS-D only.

Accepted behaviour changes and known gaps (so the result audit does not file them as regressions):
- G10(b): an `AppViewModel` error set while the engine is disconnected is now shown once the
  connected UI is back (before, it could be cleared without ever being shown).
- B10: `deleteSelectedContainers` / `deleteAllContainers` still add ids to `_pendingDeleteIds`
  without checking it, and their `finally` can clear an id that `removeContainers` still has in
  flight. Pre-existing; do not change them in this plan.

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
- NO TEST MAY READ OR WRITE REAL USER STATE. A test that builds a DesktopDockerRepository, renders
  a composable that reads PreferenceRepository, or calls PreferenceRepository must first
  assertNotNull(System.getProperty(DATA_DIR_PROPERTY)) exactly like
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

## WS-D — repository and view model: B4, R1, B6, B7

Files owned: `desktopMain/.../data/repository/DesktopDockerRepository.kt`,
`commonMain/.../ui/screens/viewmodel/AppViewModel.kt`, `commonTest/.../FakeDockerRepository.kt`,
new `desktopTest/.../DesktopDockerRepositoryLifecycleTest.kt`, new
`commonTest/.../AppViewModelLogLinesTest.kt`.

### D1. B4 — no rebuild on the first poll

- `:161` `@Volatile private var lastAvailable = false` →
  `@Volatile private var lastAvailable: Boolean? = null // null until the first poll` .
- In `determineAvailability()` (`:196-209`): replace `if (!lastAvailable) { rebuildClient() }` with
  `if (lastAvailable == false) { rebuildClient() }`, and update the comment above it to: "Reachable
  again after a poll that found it unreachable: rebuild the (likely stale) client once. Not on the
  first poll — the client was just created, and closing it killed the streams the app had already
  opened on it (review-2026-10 B4)." The two `lastAvailable = …` writes stay.

### D2. R1 — no rebuild after close

- Add next to `clientRebuildLock` (`:113`): `@Volatile private var closed = false`.
- `rebuildClient` (`:126-138`): first line inside `synchronized(clientRebuildLock) {` becomes
  `if (closed) return // closed by a host switch: a new client here would never be closed (review-2026-10 R1)`.
- `close()` (`:1447-1456`): replace `val current = clients` with
  `val current = synchronized(clientRebuildLock) { closed = true; clients }` (format as ktlint wants).

### D3. B6 — cancellable `execCapture`

In `execCapture` (`:705-757`), replace the `val completed = dockerClient.execStartCmd(execId)…awaitCompletion(…)`
statement with:

```kotlin
                // Cancellable (review-2026-10 B6): awaitCompletion blocks this IO thread until the
                // exec ends or times out, and cancelling the calling coroutine used to leave it
                // blocked. runInterruptible turns cancellation into an interrupt, and an interrupted
                // awaitCompletion closes the callback in its own finally.
                val completed =
                    runInterruptible {
                        dockerClient
                            .execStartCmd(execId)
                            .withDetach(false)
                            .exec(callback)
                            .awaitCompletion(timeoutMillis, java.util.concurrent.TimeUnit.MILLISECONDS)
                    }
```

(`import kotlinx.coroutines.runInterruptible`; `CancellationException` is already imported from
`kotlinx.coroutines`.) In `listContainerDirectory`, `readContainerFile` and `downloadContainerFile`
add `catch (e: CancellationException) { throw e }` immediately before their existing
`catch (e: Exception)`.

### D4. B7 — keep blank lines in fetched logs

In `AppViewModel.kt`, add a private top-level function at the end of the file:

```kotlin
/**
 * Lines of a fetched log text. Keeps blank lines, as the follow view does, and drops only the
 * empty piece after a final newline (review-2026-10 B7).
 */
internal fun logTextLines(text: String): List<String> {
    if (text.isEmpty()) return emptyList()
    val lines = text.lines()
    return if (text.endsWith("\n")) lines.dropLast(1) else lines
}
```

Replace the three `logs.lineSequence().filter { it.isNotEmpty() }` uses (`:251`, `:589`, `:628`)
with `logTextLines(logs)` (keep the surrounding `.forEach { … }` / `.toList()` shape: `:251` and
`:589` become `logTextLines(logs).forEach { line -> … }`, `:628` becomes
`val lines = logTextLines(logs)`).

### D5. Fake

`FakeDockerRepository.kt`: add constructor parameter `var containerLogs: String = "",` (next to the
other data parameters) and make `getContainerLogs` return `Result.success(containerLogs)`.

### D6. Tests

1. `DesktopDockerRepositoryLifecycleTest` (desktopTest). Same guard and `respond` helper as
   `DesktopDockerRepositoryStreamsTest`, but the server must run handlers off its dispatcher thread
   (the JDK default executor runs them inline, so one held-open `/events` would block every other
   request): right after `HttpServer.create(...)` set `server.executor = Executors.newCachedThreadPool()`;
   in `tearDown`: `release.countDown(); repo.close(); server.stop(0); (server.executor as ExecutorService).shutdownNow()`.
   Handler counts requests per path: `/_ping` → `OK`; `/events` → increments `eventsRequests`, sends
   headers, then holds the exchange open until `val release = CountDownLatch(1)` is counted down
   (await up to 30 s); `/containers/json` → `[]`; `/containers/c1/exec` → increments `execCreates`,
   answers 201 with `{"Id":"e1"}`; `/exec/e1/start` → increments `execStarts`, sends 200 headers
   (`sendResponseHeaders(200, 0)`), then holds open on `release` only when
   `val holdExecStart = AtomicBoolean(false)` is set (the B6 test sets it), otherwise returns at once
   (end of stream); `/exec/e1/json` → `{"ID":"e1","Running":false,"ExitCode":0}`; anything else → 404.
   Tests:
   - `the first availability poll keeps the streams already open` (B4): launch a collector of
     `repo.getContainers(all = true)` on `Dispatchers.IO`; wait until `eventsRequests == 1`; then
     `withTimeout(10_000) { repo.isDockerAvailable().first() }` must be `true`; then `delay(7_000)`
     (longer than the 5 s event re-subscribe delay); assert `eventsRequests == 1`.
     Negative control: seed `lastAvailable` with `false` again → check with
     `withTimeout(10_000) { while (eventsRequests.get() < 2) delay(100) }` that a second `/events`
     request arrives (it lands about 5 s after the poll).
   - `a closed repository does not rebuild its client` (R1): `repo.close()`, then
     `repo.listContainerDirectory("c1", "/")` must be a failure and `execCreates == 0`. (Comment in
     the test: on a closed client `execCreateCmd().exec()` throws `IllegalStateException("Connection
     pool shut down")` before any request; `withRetryOnPoolShutdown` matches "shut down", the rebuild
     is now a no-op, the retry throws again.)
     Negative control: remove the `if (closed) return` line → `execCreates == 1` (the rebuilt client
     reaches the server; the immediately-ending exec start and `/exec/e1/json` let it finish fast).
   - `cancelling a download frees the caller` (B6): `holdExecStart.set(true)`, then
     `val job = launch(Dispatchers.IO) { repo.downloadContainerFile("c1", "/big") }`;
     wait until `execStarts == 1`; then `withTimeout(5_000) { job.cancelAndJoin() }` must complete.
     Negative control: remove the `runInterruptible { … }` wrapper (keep the inner call) → the
     `withTimeout` throws `TimeoutCancellationException`.
2. `AppViewModelLogLinesTest` (commonTest, `StandardTestDispatcher` setup as in
   `AppViewModelErrorTest`): `logTextLines("a\n\nb\n") == listOf("a", "", "b")`,
   `logTextLines("") == emptyList<String>()`, `logTextLines("a\r\nb") == listOf("a", "b")`; and a view-model
   case: `FakeDockerRepository(containerLogs = "a\n\nb\n")`, `vm.showContainerLogs(<a stopped container>)`,
   `advanceUntilIdle()`, `assertEquals(listOf("a", "", "b"), vm.logsPaneState.value.logs)`.
   Negative control: put `.filter { it.isNotEmpty() }` back on `:628` → the view-model case fails.

### D7. Acceptance (WS-D)

- `grep -n "isNotEmpty() }" AppViewModel.kt` prints nothing.
- `grep -n "runInterruptible" DesktopDockerRepository.kt` prints one line; `grep -n "if (closed) return" DesktopDockerRepository.kt` one line.
- Full suite + headless run green; four negative controls quoted.

## WS-E — list screens and error routing: B10/G12, G9, G8, G10

Files owned: `commonMain/.../ui/screens/viewmodel/ContainersScreenViewModel.kt`,
`commonMain/.../ui/screens/viewmodel/ListScreenViewModel.kt`,
`commonMain/.../ui/screens/ContainersScreen.kt`, `ContainerRendering.kt`, `ImagesScreen.kt`,
`VolumesScreen.kt`, `NetworksScreen.kt`, `commonMain/.../App.kt`,
`commonMain/.../ui/state/UiMessages.kt`, new `commonMain/.../ui/components/ErrorSnackbarBridges.kt`,
tests: `commonTest/.../ReentrancyGuardTest.kt` (extend), new `commonTest/.../ContainerActionsTest.kt`,
`commonTest/.../ContainersSelectionTest.kt` (extend), new `commonTest/.../ListSelectionPruneTest.kt`,
new `desktopTest/.../ErrorSnackbarBridgesUiTest.kt`.

### E1. B10/G12 (`ContainersScreenViewModel.kt`)

- `:63-64` → `private val _actionInProgress = MutableStateFlow(emptySet<String>())` and
  `val actionInProgress: StateFlow<Set<String>> = _actionInProgress.asStateFlow()`, with the comment
  "One entry per container with an action running: a group action runs one per container
  (review-2026-10 B10/G12)."
- `containerAction` (`:121-141`): `_actionInProgress.update { it + id }` as the first statement
  inside the existing `viewModelScope.launch` (where `_actionInProgress.value = id` is today); wrap
  the rest of the coroutine body in `try { … } finally { _actionInProgress.update { it - id } }`.
- `removeContainer` (`:153-160`):

```kotlin
    fun removeContainer(id: String) {
        // Rejected while this id's removal is in flight: a second press used to force-remove a
        // container already going away and report its "No such container" (review-2026-10 B10).
        if (id in _pendingDeleteIds.value) return
        _pendingDeleteIds.update { it + id }
        viewModelScope.launch {
            try {
                repo.removeContainer(id, force = true).onFailure { _error.value = it.message }
                repo.refreshContainers()
            } finally {
                _pendingDeleteIds.update { it - id }
            }
        }
    }

    /** A compose group's "Delete all": one message for all failures, like Delete Selected. */
    fun removeContainers(ids: List<String>) {
        val toRemove = ids.filterNot { it in _pendingDeleteIds.value }
        if (toRemove.isEmpty()) return
        _pendingDeleteIds.update { it + toRemove }
        viewModelScope.launch {
            val errors = mutableListOf<String>()
            try {
                for (id in toRemove) {
                    repo.removeContainer(id, force = true).onFailure {
                        errors.add(it.message ?: "Failed to delete container")
                    }
                }
                repo.refreshContainers()
            } finally {
                _pendingDeleteIds.update { it - toRemove.toSet() }
            }
            when {
                errors.size == 1 -> _error.value = errors.single()
                errors.size > 1 -> _error.value = "Failed to delete ${errors.size} container(s)"
            }
        }
    }
```

- `ContainersScreen.kt:174` → `val effectiveActionInProgress = if (paneActionContainerId != null) actionInProgress + paneActionContainerId else actionInProgress`
  (check the type of `paneActionContainerId` first; it is a `String?`). `:1023` and `:1199` →
  `isActionInProgress = item.container.id in effectiveActionInProgress`. `:1010` and `:1186` →
  `onRemoveAll = { ids -> viewModel.removeContainers(ids) },`.
- `ContainerRendering.kt:315` parameter `actionInProgress: Set<String>`; `:407` →
  `isActionInProgress = container.id in actionInProgress,`. Fix every other compile error the type
  change causes the same way (membership instead of equality); report each site.

### E2. G9 — prune selections against the live list

- `ContainersScreenViewModel.kt`, after `clearSelection()`:

```kotlin
    /** Drops selected ids whose container is gone (removed elsewhere) (review-2026-10 G9). */
    fun retainSelection(liveIds: Set<String>) {
        _selectedContainerIds.update { selected ->
            if (liveIds.containsAll(selected)) selected else selected.filterTo(HashSet()) { it in liveIds }
        }
    }
```

- `ListScreenViewModel.kt`, after `clearChecked()`: the same as `fun retainChecked(liveKeys: Set<String>)`
  on `_checkedKeys`.
- `ContainersScreen.kt`, after `:175`:

```kotlin
    // Keeps the selection to containers that exist: the toolbar counts and Delete Selected use it.
    // Driven by list emissions, not by the composed list, whose first frame is the empty
    // collectAsState() placeholder — that would wipe the selection on every return to this screen
    // (review-2026-10 G9).
    LaunchedEffect(viewModel) {
        viewModel.containers.collect { list -> viewModel.retainSelection(list.mapTo(HashSet()) { it.id }) }
    }
```

- `ImagesScreen.kt` after `:93`, `VolumesScreen.kt` after `:103`, `NetworksScreen.kt` after `:92`: the
  same block collecting `viewModel.images` / `viewModel.volumes` / `viewModel.networks` with
  `list.mapTo(HashSet()) { it.id }` / `{ it.name }` / `{ it.id }` and `viewModel.retainChecked(...)`.
  No `hasLoaded` gate (it stays `true` across navigation, so it cannot tell the placeholder apart).

### E3. G8 — density in `pointerInput` keys

`ContainerRendering.kt:448` `pointerInput(container.id)` → `pointerInput(container.id, density)`;
`:938` `pointerInput(Unit)` → `pointerInput(density)`; `ImagesScreen.kt:577`
`pointerInput(image.id)` → `pointerInput(image.id, density)`; `VolumesScreen.kt:648`
`pointerInput(volume.name)` → `pointerInput(volume.name, density)`; `NetworksScreen.kt:592`
`pointerInput(network.id)` → `pointerInput(network.id, density)`. Before each, confirm a
`val density = LocalDensity.current` is in scope and used inside the block; add one comment per file:
`// density in the key: the block converts px to dp with it, and it changes on a move to a display with another scale (review-2026-10 G8).`
Leave `VolumesScreen.kt:564` alone (raw px, no density).

### E4. G10 — error bridges

1. `UiMessages.kt`: add

```kotlin
    /**
     * Suspends until the snackbar host collects. A message posted before that is dropped (no
     * replay), and the host subscribes one frame after the effects that post (review-2026-10 G10).
     */
    suspend fun awaitCollector() {
        _events.subscriptionCount.first { it > 0 }
    }
```

   (`import kotlinx.coroutines.flow.first`; `_events` is a `MutableSharedFlow`, which has `subscriptionCount`.)
2. New `ui/components/ErrorSnackbarBridges.kt`:

```kotlin
/**
 * Shows view-model errors that have no inline home as snackbars (UX audit U1.2). An error the
 * Containers screen already showed in its inline card is cleared, not toasted again, when the user
 * leaves that screen (review-2026-10 G10).
 */
@Composable
internal fun ErrorSnackbarBridges(
    appError: String?,
    onAppErrorShown: () -> Unit,
    containersError: String?,
    onContainersErrorHandled: () -> Unit,
    onContainersScreen: Boolean,
) {
    LaunchedEffect(appError) {
        val message = appError ?: return@LaunchedEffect
        UiMessages.awaitCollector()
        UiMessages.error(message)
        onAppErrorShown()
    }
    var shownInline by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(containersError, onContainersScreen) {
        val message = containersError
        if (message == null) {
            shownInline = null // dismissed inline, or already handled: nothing is "already shown"
            return@LaunchedEffect
        }
        if (onContainersScreen) {
            shownInline = message
        } else {
            if (message != shownInline) {
                UiMessages.awaitCollector()
                UiMessages.error(message)
            }
            shownInline = null
            onContainersErrorHandled()
        }
    }
}
```

3. `App.kt:238-251`: replace the two `val … by …collectAsState()` + `LaunchedEffect` pairs with

```kotlin
                    val appError by viewModel.error.collectAsState()
                    val containersError by containersVm.error.collectAsState()
                    ErrorSnackbarBridges(
                        appError = appError,
                        onAppErrorShown = { viewModel.clearError() },
                        containersError = containersError,
                        onContainersErrorHandled = { containersVm.clearError() },
                        onContainersScreen = currentRoute == Screen.Containers.route,
                    )
```

   Keep the U1.2 comment above it. Check `containersError` is not used elsewhere in `App.kt`
   before removing anything.

### E5. Tests

1. `ContainerActionsTest` (commonTest): a `FakeDockerRepository` whose `stopContainer` suspends on a
   `CompletableDeferred` per id (add nothing to the fake: use `containersFlowOverride` to keep the
   list stable and wrap the fake in a subclass/`object : DockerRepository by fake` that overrides
   `stopContainer` — `FakeDockerRepository` is a class; delegate with `DockerRepository by fake`).
   Call `stopContainer("a")`, `stopContainer("b")`, `advanceUntilIdle()`: `actionInProgress.value == setOf("a", "b")`;
   complete "a", `advanceUntilIdle()` → `setOf("b")`; complete "b", `advanceUntilIdle()` → empty
   (the stop path then spends a virtual 5 s in `withTimeoutOrNull` plus 400 ms before releasing the
   id; `advanceUntilIdle()` covers it). Negative control: back to a single id → fails.
   Second case: `removeContainers(listOf("a", "b"))` with a fake whose `removeContainerResult` is a
   failure → `error.value == "Failed to delete 2 container(s)"`.
2. `ReentrancyGuardTest`: add `removeContainer twice while the first is in flight removes once`
   (the fake's `removeContainer` already yields; count its calls through `removedContainerIds` or the
   fake's existing recorder — read the fake first). Negative control: delete the `if (id in …) return` line.
3. `ContainersSelectionTest`: `retainSelection drops ids that are gone` (select aaa, bbb; retain
   {aaa} → {aaa}). `ListSelectionPruneTest`: same for `ImagesScreenViewModel.retainChecked`.
4. `ErrorSnackbarBridgesUiTest` (desktopTest, v2 `runComposeUiTest`, `DATA_DIR_PROPERTY` guard):
   - `an app error present on the first frame is shown`: `setContent { ErrorSnackbarBridges(appError = "boom", …, onContainersScreen = false); AppSnackbarHost() }` (bridges first, as in `App.kt`);
     `waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("boom").fetchSemanticsNodes().isNotEmpty() }`
     (named argument: the first positional parameter is a description string).
     Negative control: remove the `awaitCollector()` call in the app-error effect → times out.
   - `an error already shown inline is not toasted on leaving Containers`: state
     `onContainersScreen` true, `containersError = "inline"`; `waitForIdle()`; set
     `onContainersScreen = false`; `waitForIdle()`; assert zero "inline" nodes and that
     `onContainersErrorHandled` ran once. Negative control: drop the `message != shownInline` check → a
     snackbar shows.
   - `an error raised off Containers is toasted`: `onContainersScreen = false` from the start,
     `containersError = "elsewhere"` → snackbar shown, handled once.
   - `an error dismissed inline and raised again off Containers is toasted`: on Containers with
     `containersError = "x"`; set `containersError = null` (the card's X); then
     `onContainersScreen = false` and `containersError = "x"` → snackbar "x" appears.
     Negative control: remove the `if (message == null) { shownInline = null … }` reset → no snackbar.

### E6. Acceptance (WS-E)

- `grep -n "actionInProgress == " ContainersScreen.kt ContainerRendering.kt` prints nothing.
- `grep -n "pointerInput(.*density)" ContainerRendering.kt ImagesScreen.kt VolumesScreen.kt NetworksScreen.kt` prints five lines.
- `grep -n "LaunchedEffect(containersError" App.kt` prints nothing; `grep -n "ErrorSnackbarBridges(" App.kt` one line.
- Full suite + headless run green; negative controls quoted.

## WS-F — settings, engine, console, preferences: B5, B9, R2, G11, G13

Files owned: `commonMain/.../ui/screens/viewmodel/SettingsScreenViewModel.kt`,
`commonMain/.../ui/screens/SettingsScreen.kt`, `desktopMain/.../data/engine/EngineManager.kt`,
`desktopMain/.../terminal/JediTermConsole.kt`, `commonMain/.../data/repository/PreferenceRepository.kt`,
tests: `commonTest/.../SettingsScreenViewModelTest.kt` (extend), new
`desktopTest/.../PreferenceFlowsTest.kt`, new `desktopTest/.../EngineQuotaFieldsUiTest.kt`.

### F1. B5 (`SettingsScreenViewModel.kt`)

- Add two constructor parameters after `testRepoFactory`:

```kotlin
    // Seams for the engine start (review-2026-10 B5); production uses the singletons.
    private val startEngineOp: suspend (EngineType, String?, Int?, Int?, Int?) -> Boolean =
        { type, profile, cpu, memory, disk -> EngineOperations.startEngine(type, profile, cpu, memory, disk) },
    private val reconnect: (String) -> Unit = { host -> AppModule.reconnect(host) },
```

- `startEngine` (`:103-115`): `if (!startEngineOp(type, profile, cpu, memory, disk)) return@launch`
  then `reconnect(host)` (comment: "A rejected or failed start used to reconnect anyway, swapping the
  repository while the engine was down (review-2026-10 B5).").
- `restartEngine` (`:130-142`): use `reconnect(host)` instead of `AppModule.reconnect(host)` (no other change).
- Test (`SettingsScreenViewModelTest`): construct the VM with `repoProvider`/`testRepoFactory` as the
  file already does plus a fake `startEngineOp` returning `false` and a `reconnect` that records
  hosts → after `startEngine()` + `advanceUntilIdle()` no host recorded; returning `true` → exactly
  one. `engineType.value` / `colimaProfile.value` are `UNKNOWN` / `null` without a subscriber; the
  injected `startEngineOp` ignores them — do not add a collector. The VM reads
  `PreferenceRepository.engineHost().first()`, and this is the first test in the class to touch the
  DataStore: add the `DATA_DIR_PROPERTY` guard to its `setUp`. Negative control: remove the early return → the `false` case records one host.

### F2. B9 (`EngineManager.kt:94-97`)

Keep `val drainThread =` (it is used at `:101-102` and `:106`); only the lambda body changes:

```kotlin
                    thread(name = "colima-status-drain") {
                        try {
                            textHolder[0] = proc.inputStream.bufferedReader().readText()
                        } catch (_: Exception) {
                            // Stream closed when the timeout destroys the process (review-2026-10 B9).
                        }
                    }
```

No test (see Decisions).

### F3. R2 (`JediTermConsole.kt:62-90`)

Replace `conn = withContext(Dispatchers.IO) { DockerExecTtyConnector(…).also { it.start() } }` with:

```kotlin
                    // Assigned before start(): if the coroutine is cancelled while start() runs,
                    // withContext discards its result, and the catch below must still close the
                    // exec session start() opened (review-2026-10 R2).
                    withContext(Dispatchers.IO) {
                        DockerExecTtyConnector(
                            dockerClient = (AppModule.dockerRepository as DesktopDockerRepository).client,
                            containerId = containerId,
                        ).also {
                            conn = it
                            it.start()
                        }
                    }
```

`connector = conn` and the catches stay. No test (see Decisions).

### F4. G11 (`PreferenceRepository.kt`)

For each of the 13 functions returning `Flow<…>` (`engineHost` `:130`, `themeMode` `:139`,
`monitoringAggregation` `:159`, `showSystemContainers` `:169`, `confirmBeforeDelete` `:178`,
`logsPaneLayout` `:187`, `trayRefreshRateSeconds` `:196`, `logsWordWrap` `:205`, `logsMaxLines`
`:214`, `containerColumnWidths` `:238`, `volumeColumnWeights` `:257`, `logsPaneRightWidth` `:310`,
`logsPaneBottomHeight` `:319`): move the body into a `private val <name>Flow: Flow<…> by lazy { <body> }`
placed right above the function, and make the function `= <name>Flow`. Bodies unchanged. One comment
above the first one: "One flow per preference, built once: `collectAsState()` restarts its collection
whenever it is handed a new flow instance, and every call used to build one (review-2026-10 G11)."
Test `PreferenceFlowsTest` (desktopTest, `DATA_DIR_PROPERTY` guard): for each of the 13 functions,
`assertSame(PreferenceRepository.x(), PreferenceRepository.x())`. Negative control: revert one of them
→ that assertion fails.

### F5. G13 (`SettingsScreen.kt:526-528` and `:613`, `:631`, `:649`)

```kotlin
    // Seeded once; when the Colima probe lands it fills only the fields the user has not typed in
    // (review-2026-10 G13). remember(colimaConfig) used to reset them all.
    var cpu by remember { mutableStateOf(colimaConfig?.cpu?.toString() ?: "4") }
    var memory by remember { mutableStateOf(colimaConfig?.memoryGB?.toString() ?: "8") }
    var disk by remember { mutableStateOf(colimaConfig?.diskGB?.toString() ?: "60") }
    var cpuEdited by remember { mutableStateOf(false) }
    var memoryEdited by remember { mutableStateOf(false) }
    var diskEdited by remember { mutableStateOf(false) }
    LaunchedEffect(colimaConfig) {
        val config = colimaConfig ?: return@LaunchedEffect
        if (!cpuEdited) cpu = config.cpu.toString()
        if (!memoryEdited) memory = config.memoryGB.toString()
        if (!diskEdited) disk = config.diskGB.toString()
    }
```

and the three `onValueChange` lambdas become `{ cpu = it.filter { c -> c.isDigit() }; cpuEdited = true }`
(and the same for memory, disk; format as ktlint wants). `SettingsScreen.kt` uses the fully qualified
`androidx.compose.runtime.LaunchedEffect` elsewhere — match whatever import style the file uses.
Make `EngineManagementSection` `internal` for the test.
Test `EngineQuotaFieldsUiTest` (desktopTest, v2 `runComposeUiTest`, `DATA_DIR_PROPERTY` guard): render
`EngineManagementSection(engineType = EngineType.COLIMA, colimaProfile = "default", colimaConfig = config, actionStatus = EngineActionStatus.Idle, commandOutput = "", onStart = { _, _, _ -> }, onRestart = { _, _, _ -> }, onStop = {}, onClearState = {})`
with `config` a `mutableStateOf<ColimaConfig?>(null)`. The three quota fields have no label,
placeholder or tag (the "CPU (cores)" etc. texts are siblings): address them by composition order.
`onAllNodes(hasSetTextAction()).assertCountEquals(3)`; index 0 is CPU, 1 Memory, 2 Disk.
`onAllNodes(hasSetTextAction())[0].performTextReplacement("6")`; set
`config = ColimaConfig(cpu = 2, memoryGB = 4, diskGB = 100)`; `waitForIdle()`;
`onAllNodes(hasSetTextAction())[0].assert(hasText("6"))`, `[1].assert(hasText("4"))`,
`[2].assert(hasText("100"))`. Negative control: restore `remember(colimaConfig)` on `cpu` → index 0
shows "2".

### F6. Acceptance (WS-F)

- `grep -n "remember(colimaConfig)" SettingsScreen.kt` prints nothing.
- `grep -c "by lazy" PreferenceRepository.kt` is at least 13 more than before (report before/after).
- `grep -n "AppModule.reconnect" SettingsScreenViewModel.kt` prints only the default of the `reconnect` parameter.
- Full suite + headless run green; negative controls quoted.

## Live checks (orchestrating agent)

Under hot reload against the fake engine, after WS-D: open a log pane at startup and confirm it does
not end with "--- Stream ended ---" within 10 s (B4). After WS-E: none possible for G8 (needs two
displays) and G10 (needs a failing action); rely on the tests. After WS-F: none (Settings engine
actions run real `colima`). Owed to the user: G8 on two displays; B8 decision.

## Risks for the reviewer

1. B4: does anything rely on the startup rebuild (a stale pool at launch, e.g. after the app was
   restored from sleep)? The pool was created a few ms earlier, so it cannot be stale.
2. B6: `runInterruptible` inside the `withRetryOnPoolShutdown` inline lambda inside
   `withContext(IO)`: does the interrupt leave the docker-java callback or the HTTP connection in a
   bad state; does `CancellationException` escape `withRetryOnPoolShutdown`'s `catch (e: Exception)`
   unchanged (its message does not contain "shut down")?
3. B10/G12: `removeContainers` and `deleteSelectedContainers` share `_pendingDeleteIds`; any
   double-count or lost update?
4. G9: the `LaunchedEffect` runs on every list emission; any flicker of the toolbar, and is
   `hasLoaded` the right gate for each screen?
5. G10: `awaitCollector()` when the host is never composed (disconnected branch): the effect waits;
   it is cancelled when the branch leaves. Any path where the app error is now never cleared?
6. G11: does any caller rely on getting a fresh flow (e.g. `first()` semantics)? `by lazy` on an
   `object` with a lazily created DataStore.
7. G13: `LaunchedEffect` + flags: the first config arriving before any typing still fills all three.
8. Tests: the 7 s wait in the B4 test; timing in the B6 test; real-state exposure.

## Review log

Plan review by a fresh Fable subagent: **APPROVE WITH AMENDMENTS** (1 BLOCKER, 4 MAJOR, 10 MINOR).
It probed the JDK `HttpServer` executor, `runInterruptible` around `awaitCompletion`, the closed-pool
exception and the B4 timing, and compile-checked the plan's Kotlin shapes (probes deleted). Each
BLOCKER/MAJOR was re-checked against the cited source before applying.

- **Applied — BLOCKER-1:** the test server needs a thread-pool executor (the default runs handlers
  on the dispatcher thread, so a held `/events` blocked every other request); exec start is held
  only for the B6 test; `/exec/e1/json` is served so the R1 negative control finishes fast.
- **Applied — MAJOR-1:** G9 prunes from list emissions, not from the composed list (whose first frame
  is the empty placeholder and would have wiped the selection on every return to a screen).
- **Applied — MAJOR-2:** G10 resets `shownInline` when the error is dismissed; a fourth test case.
- **Applied — MAJOR-3:** G13 test addresses the unlabelled fields by composition order.
- **Applied — MAJOR-4:** B6 comment no longer claims UI cancellation; the redundant
  close-on-cancel wrapper is dropped (`awaitCompletion` closes the callback in its `finally`);
  wiring pane-close cancellation is recorded as a follow-up.
- **Applied — MINOR-1, 2, 3, 4, 6, 8, 9, 10:** `waitUntil(timeoutMillis = …)`, anchors, placement of
  the progress update, keep `val drainThread`, KDoc wording, a timing-safe negative control, test
  comments, the Settings test guard.
- **Recorded — MINOR-5, MINOR-7:** in "Accepted behaviour changes and known gaps".

## Result audit

Fresh Opus subagent after the four code commits: **PASS WITH FINDINGS** (0 BLOCKER, 0 MAJOR,
4 MINOR). Six full-suite runs green (311 tests), every declared deviation justified, the real
settings directory untouched, and a probe (B4/R1 transitions and close races, B6 interrupt timing,
the group-log ordering fix under cancellation, G9 through the real Containers screen).

- **Fixed — MINOR-1:** a B6 cancel that landed before the engine's response headers was undone by
  docker-java's `onStart`; the callback now closes a stream that arrives after it was abandoned. A
  lifecycle test reproduced it first.
- **Fixed — MINOR-2:** the G9 screen wiring had no test; `ContainersScreenSelectionUiTest` renders
  the real screen and fails both without the wiring and with the composed-list version this plan's
  review rejected.
- **Recorded — MINOR-3:** the group-log race fix (`e4ca5bc`) is correct by construction and the
  delay probe shows the mechanism, but the original flake's log was overwritten, so it is not proven
  to be that flake's cause.
- **Fixed — MINOR-4:** the audit doc now carries a Status line for every finding of this batch.

Process note: the WS-F commit was created by a command chain whose full-suite run had that one
failing test; the failure was the pre-existing race above, fixed in the next commit.
