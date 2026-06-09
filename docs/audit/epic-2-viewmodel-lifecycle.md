# Epic 2 — ViewModel lifecycle & reconnect

Base path: `composeApp/src/commonMain/kotlin/com/containerdashboard/` unless noted.
S2.1 + S2.2 are the same root cause across five ViewModels — fix them together on one branch.

---

## S2.1 — Images/Networks/Volumes VMs capture the initial repository; dead after host reconnect

- **Severity:** High · **Effort:** M
- **Files:** `ui/screens/viewmodel/ImagesScreenViewModel.kt:22` (and the equivalent constructor lines in `NetworksScreenViewModel.kt`, `VolumesScreenViewModel.kt`)

These VMs pass `repoProvider().getImages()` (etc.) to `SortableListScreenViewModel`'s
constructor — `repoProvider()` is evaluated **once at construction**, capturing the initial
`DesktopDockerRepository`. When the user changes the engine host and `AppModule.reconnect()`
swaps (and closes) the repository, these screens keep streaming from the closed instance.
`AppViewModel` and `DashboardScreenViewModel` already do this correctly via
`repoFlow.flatMapLatest`.

**Fix:** inject `repoFlow: StateFlow<DockerRepository>` and build items as
`repoFlow.flatMapLatest { it.getImages() }`, matching the `AppViewModel` pattern.

**Acceptance:** change engine host in Settings; Images/Networks/Volumes screens show data
from the new host without app restart. Update/extend the existing VM tests.

---

## S2.2 — Containers/Monitoring VMs bind live flows to the initial repository

- **Severity:** High · **Effort:** M
- **Files:** `ui/screens/viewmodel/ContainersScreenViewModel.kt:37` (`containersFlow`), `ui/screens/viewmodel/MonitoringScreenViewModel.kt:110-113` (`rawStats`)

Same root cause as S2.1: `containersFlow = repo.getContainers(all = true)` is bound at init;
`MonitoringScreenViewModel.rawStats` re-subscribes on refresh-rate changes but not on
repository replacement. Action methods re-evaluate `repoProvider()` so mutations hit the new
repo, but the *data* keeps coming from the old one — a confusing split-brain after reconnect.

**Verification note (2026-06-09):** confirmed. Also verified the bug isn't moot via VM
recreation: all screens create their VMs with `viewModel { … }` (no key tied to the
repo/host), so instances survive `AppModule.reconnect()`. Monitoring nuance: because `repo`
is a `get() = repoProvider()` property inside `flatMapLatest`, changing the refresh rate
*after* a reconnect happens to rebind to the new repo — which makes the stale behavior
intermittent and extra confusing.

**Fix:** route both through `repoFlow.flatMapLatest { ... }` (combine with `_refreshRate`
for Monitoring).

**Acceptance:** after a host switch, Containers and Monitoring show the new host's
containers/stats without restart.

---

## S2.3 — `MonitoringScreenViewModel.systemInfo`: eager start + unconditional infinite retry

- **Severity:** Medium · **Effort:** S
- **File:** `ui/screens/viewmodel/MonitoringScreenViewModel.kt:96-101`

`systemInfo` is started `Eagerly` and `.retryWhen { _, _ -> delay(2_000); true }` — it polls
the daemon every 2 s forever when unavailable, even when the Monitoring screen is never
opened.

**Fix:** switch to `SharingStarted.WhileSubscribed(5_000)` (one success satisfies consumers;
it restarts if re-subscribed), and/or bound the retries.

**Acceptance:** with the daemon down and Monitoring not visible, no repeated
`getSystemInfo` calls (verify via log/breakpoint).

---

## S2.4 — `DashboardScreenViewModel.loadSystemInfo()` runs once; stays null forever on early failure

- **Severity:** Low · **Effort:** S
- **File:** `ui/screens/viewmodel/DashboardScreenViewModel.kt:83-90`

If the daemon isn't up at VM creation, `_systemInfo`/`_version` stay null permanently; the
per-card flows self-heal but these don't.

**Fix:** observe `connectionState` and re-trigger `loadSystemInfo()` on transition to
CONNECTED while `_systemInfo.value == null`.

**Acceptance:** start app with daemon stopped, then start daemon → version/system info
appear without restart.

---

## S2.5 — `AppViewModel` file-tree state: unsynchronized mutable collections across coroutines

- **Severity:** Medium · **Effort:** M
- **File:** `ui/screens/viewmodel/AppViewModel.kt:252-255` (collections), `:362` (`loadRoot`), `:377` (`loadChildren`), `:418` (`flattenTree`)

`childrenByPath`, `expandedPaths`, `loadingPaths`, `nodeErrors` are plain mutable
maps/sets mutated from multiple `viewModelScope.launch` coroutines while `flattenTree()`
reads them. The window is narrow (Main dispatcher) but the pattern is fragile and blocks
testing (see S8.4).

**Fix:** consolidate into a single immutable `TreeState` inside one `MutableStateFlow`,
updated via `update {}`.

**Acceptance:** rapid expand/refresh of many directories never produces an inconsistent
tree; behavior covered by the new tests in S8.4.

---

## S2.6 — `AppViewModel` non-atomic read-check-act on `_logsPaneState`

- **Severity:** Low · **Effort:** S
- **File:** `ui/screens/viewmodel/AppViewModel.kt:93-99` (init collector)

The collector reads `_logsPaneState.value` multiple times across a suspension-capable
block; `startFollowing` can interleave, acting on stale `isFollowing`.

**Fix:** snapshot `_logsPaneState.value` once at the top of the block, or use `update {}`.

**Acceptance:** code review — single read per decision.

---

## S2.7 — `InMemoryAppender.append()` add-evict-snapshot is not atomic

- **Severity:** Low · **Effort:** S
- **File:** `composeApp/src/desktopMain/kotlin/com/containerdashboard/logging/InMemoryAppender.kt:61-66`

Logback calls `doAppend` from arbitrary threads; the `addLast` → size-evict loop →
`_entriesFlow.value = entries.toList()` sequence on the `ConcurrentLinkedDeque` can publish
oversized/inconsistent snapshots.

**Fix:** wrap lines 61–66 in `synchronized(this)` (cheap at log rates).

**Acceptance:** snapshot size never exceeds `maxEntries`.

---

## S2.8 — Startup preferences block the main thread via `runBlocking`

- **Severity:** Medium · **Effort:** M
- **File:** `data/repository/PreferenceRepository.kt:97, 247, 274, 283, 291`

`initialEngineHost`, `windowBoundsSync`, `lastRouteSync`, `logsPaneRightWidthSync`,
`logsPaneBottomHeightSync` use `runBlocking { dataStore.data.firstOrNull() }` on the
main/Swing thread at startup — a visible freeze risk on slow filesystems/first launch.

**Fix:** load all startup-required prefs once on a background thread before the window is
created (single `runBlocking(Dispatchers.IO)` in `main` pre-Compose), then pass values in.

**Acceptance:** no `runBlocking` on the UI thread path; cold start still restores window
bounds/route.

---

## S2.9 — `LogsFilterState`: process-global `mutableStateOf` singleton

- **Severity:** Low · **Effort:** S
- **File:** `ui/state/LogsFilterState.kt:15-16`

A global `object` with `var` `mutableStateOf` fields is only safe if written from the main
thread; nothing enforces that.

**Fix:** convert to `MutableStateFlow` (safe by construction) or document the main-thread
contract. Low priority — current call sites are event handlers.

**Acceptance:** code review.
