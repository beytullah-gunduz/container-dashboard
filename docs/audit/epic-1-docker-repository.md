# Epic 1 — Docker repository correctness

All stories target `composeApp/src/desktopMain/kotlin/com/containerdashboard/data/repository/DesktopDockerRepository.kt`
unless noted. **These stories share one file — run them sequentially or in one combined branch.**

---

## S1.1 — `rebuildClient()` is not synchronized; concurrent callers can tear the client pair

- **Severity:** High · **Effort:** S
- **Confidence:** independently reported by 2 audit agents
- **File:** `DesktopDockerRepository.kt:112-117` (call sites: `withRetryOnPoolShutdown` ~:141, `determineAvailability()` ~:173)

`rebuildClient()` writes `httpClient` then `dockerClient` as two separate `@Volatile` writes
and closes the old HTTP client, with no lock. Concurrent coroutines (e.g. simultaneous
list-containers + list-images + stats all hitting "pool shut down", racing with the
availability poll) can observe a new `httpClient` paired with the old `dockerClient`, or
close a client a sibling just installed.

**Fix:** make the swap atomic — either `@Synchronized`/`synchronized(this)` around the whole
rebuild (covering both call paths), or hold both clients in a single `@Volatile val pair:
ClientPair` immutable holder so the swap is one write. Close the old client after the swap.

**Acceptance:** no torn-state window (review under the assumption of N concurrent callers);
existing behavior preserved; tests still pass.

---

## S1.2 — `dockerEvents` uses `SharingStarted.Lazily`; events are silently dropped

- **Severity:** High · **Effort:** S
- **Confidence:** independently reported by 2 audit agents
- **File:** `DesktopDockerRepository.kt:214-251` (the `shareIn` at :251)

`dockerEvents` is `shareIn(scope, SharingStarted.Lazily)` with no replay. The downstream
`containersSharedAll`/`imagesShared`/`volumesShared`/`networksShared` flows are `by lazy`
and use `WhileSubscribed(5_000)` — so events firing before the first subscriber, or in
gaps between subscribers, are lost. Mutating actions (start/stop/pull) then don't reliably
trigger the event-driven refresh; the UI is stale until the 15-second poll fallback.

**Verification note (2026-06-09):** confirmed at the cited lines, with one precision:
`Lazily` never *stops* the upstream once the first subscriber has attached, so the event
connection stays open for the repo's lifetime. The actual loss windows are (a) before the
first-ever subscriber and (b) any moment a downstream `WhileSubscribed` consumer is
detached — no replay means those events are gone either way. The 15 s poll bounds staleness,
so the user-visible impact is "refresh is delayed up to 15 s", not permanent staleness.

**Fix:** change to `SharingStarted.WhileSubscribed(5_000)` to match the consumers, and
document the remaining gap (covered by the 15 s poll). `Eagerly` also works but keeps a
connection open with no UI consumers.

**Acceptance:** starting/stopping a container from the UI refreshes the list via the event
path (verify with a log line or breakpoint), not only via the 15 s poll.

---

## S1.3 — Prune results report bytes-reclaimed as item count

- **Severity:** High · **Effort:** S
- **File:** `DesktopDockerRepository.kt:1353-1355, 1369-1371, 1385-1387`

`pruneContainers`/`pruneImages`/`pruneVolumes` all set
`PruneResult.deletedCount = response.spaceReclaimed?.toInt() ?: 0`. `spaceReclaimed` is
**bytes freed**, not an item count — the UI shows garbage ("X items deleted" = a byte
count), and `.toInt()` silently truncates above 2 GiB.

**Fix:** use the deleted-items list from the docker-java prune responses
(e.g. `response.containersDeleted?.size ?: 0`, `imagesDeleted?.size`), keep
`reclaimedSpace = response.spaceReclaimed ?: 0`.

**Acceptance:** prune a known number of stopped containers; the reported count matches.

---

## S1.4 — `getImage(id)` filters by name, never matches a SHA id

- **Severity:** Medium · **Effort:** S
- **File:** `DesktopDockerRepository.kt:931-946`

`getImage(id)` uses `.withImageNameFilter(id)`, but callers pass `DockerImage.id` (a
sha256 string). The name filter doesn't accept IDs, so the list comes back empty and the
method throws `Exception("Image not found")` for every lookup by id.

**Fix:** use `inspectImageCmd(id)` (the `inspectImage` method already does this correctly)
or match by id over `listImagesCmd().withShowAll(true)`.

**Acceptance:** image context-menu inspect works for an image referenced by id; add a
mapping test if feasible.

---

## S1.5 — CPU% wrong on cgroup v2: `numCpus` falls back to 1

- **Severity:** Medium · **Effort:** S
- **File:** `DesktopDockerRepository.kt:1342`

`val numCpus = cpuStats.cpuUsage?.percpuUsage?.size ?: cpuStats.onlineCpus?.toInt() ?: 1`.
On cgroup v2 (modern kernels, OrbStack) `percpuUsage` is null and `onlineCpus` can be null
too, so CPU% is computed against 1 core — capped at 100% on multi-core machines.

**Fix:** final fallback `Runtime.getRuntime().availableProcessors()` instead of `1`.

**Acceptance:** a busy multi-threaded container reports >100% CPU on a multi-core host
(consistent with `docker stats`).

---

## S1.6 — `getContainerStats(ids: Flow<Set<String>>)` creates a new `shareIn` per call

- **Severity:** Medium · **Effort:** M
- **Confidence:** independently reported by 2 audit agents
- **File:** `DesktopDockerRepository.kt:1235-1243`

Every call creates a new `shareIn(scope, WhileSubscribed(5_000), replay = 1)` whose job is
registered on the repo-lifetime `scope` and never cancelled — idle shared-flow jobs
accumulate over a long session (and after reconnects). `ContainerStatsManager` dedups the
underlying Docker streams, but the coroutine overhead still accrues.

**Fix:** drop the inner `shareIn` and return the cold flow, letting callers `stateIn` it on
their ViewModel scope (which has a real lifecycle); or cache/lazily share a single instance
like `containerStatsShared`.

**Acceptance:** repeated expand/collapse of compose groups does not grow the number of live
jobs on the repo scope.

---

## S1.7 — Log-follow drops blank lines; one failing stream cancels all siblings

- **Severity:** Medium · **Effort:** S
- **Files:** `DesktopDockerRepository.kt:695, 784` (blank-line filter), `:805` area (multi-follow structure)

Both `followContainerLogs` and `followMultipleContainerLogs` apply
`.filter { it.isNotEmpty() }`, silently discarding intentional blank lines from container
output. Separately, in `followMultipleContainerLogs` the per-container streams are plain
`launch`es inside `channelFlow` — one stream throwing cancels all the others via structured
concurrency.

**Fix:** remove the `isNotEmpty()` filter (or document why blanks are dropped); wrap the
per-container launches in `supervisorScope` so one container's stream failure doesn't kill
the rest.

**Acceptance:** a container printing empty lines shows them in the logs pane; killing one
container while following several keeps the other streams alive.

---

## S1.8 — `VolumeInspect.scope`/`createdAt` are hardcoded to empty strings

- **Severity:** Low · **Effort:** S
- **File:** `DesktopDockerRepository.kt:1659-1669`

`InspectVolumeResponse.toVolumeInspect()` hardcodes `scope = ""` and `createdAt = ""`
although the response exposes (at least) scope. The UI renders blanks.

**Fix:** populate from the response where available; if docker-java's version doesn't
expose `createdAt`, document that in place.

**Acceptance:** volume details dialog shows a non-empty scope for a local volume.
