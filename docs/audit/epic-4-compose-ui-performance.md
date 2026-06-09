# Epic 4 — Compose UI performance & correctness

Base path: `composeApp/src/commonMain/kotlin/com/containerdashboard/`.
Stories here are mostly independent files and can be parallelized, except S4.1 + S4.8
(both in `LogsTabContent.kt`) and S4.3 + S4.9 (both touch the command palette).

---

## S4.1 — `LogsTabContent`: full log buffer re-joined into one giant `Text` on every line

- **Severity:** Medium (downgraded from High after verification) · **Effort:** M
- **File:** `ui/components/LogsTabContent.kt:121-129`

`displayedLogs = remember(state.logs, …) { …joinToString("\n") }` re-concatenates the
entire buffer into a single `String` on **every appended line**, and the result renders as
one non-virtualized `Text` inside `verticalScroll`.

**Verification note (2026-06-09):** the original finding assumed unbounded growth, but the
buffer is capped — the repository trims at `cachedMaxLogLines`
(`DesktopDockerRepository.kt:698, 793`) and Settings offers only 500/1000/2000/5000 lines.
Worst case is ~5000 lines re-joined and re-laid-out per appended line: real waste on chatty
containers, but bounded, hence Medium.

**Fix:** render lines in a `LazyColumn` with stable per-line keys; keep `joinToString` only
for the save/export path.

**Acceptance:** following a chatty container at the 5000-line setting stays smooth (no
visible jank per appended line). Coordinate with S4.8 (same file).

---

## S4.2 — `ContainersScreen`: unstable lambdas + collection params defeat row skipping

- **Severity:** Medium · **Effort:** L
- **File:** `ui/screens/ContainersScreen.kt:960-1015` (items block)

Row lambdas (`onRemove = { … askConfirm(…) … }`) capture local funs and screen-level state,
so every stats tick (1–5 s) recreates them and recomposes every row; `selectedContainerIds`
/ `pendingDeleteIds` are passed as `Set`/`List` values with the same effect. O(N) lambda
allocations + recompositions per tick with many containers.

**Fix:** hoist callbacks into stable remembered holders (`rememberUpdatedState` for mutable
captures); give `ContainerRowByMode`/`ComposeProjectCard` `@Stable` parameter holders so
Compose skipping works. Measure before/after with composition counts.

**Acceptance:** with N containers and stats ticking, unchanged rows do not recompose
(verify with Layout Inspector / composition counters or temporary recomposition logging).

---

## S4.3 — `buildPaletteActions` rebuilt every composition

- **Severity:** Medium · **Effort:** S
- **File:** `App.kt:226-242` (function at :446+)

`buildPaletteActions(containers, …)` runs unguarded in composition; every stats tick
rebuilds the full action list (3+ allocations per container).

**Fix:** `remember(containers, …) { buildPaletteActions(…) }` with stable callback refs.

**Acceptance:** action list rebuilds only when the container set/state actually changes.

---

## S4.4 — Images/Volumes screens: filter+sort runs unmemoized in composition

- **Severity:** Medium · **Effort:** S
- **Files:** `ui/screens/ImagesScreen.kt:124-165`, `ui/screens/VolumesScreen.kt:151-185`

`filteredImages`/`filteredVolumes` (O(N log N) sort) recompute on every recomposition,
including ones unrelated to the data. `ContainersScreen.kt:202-231` already shows the
correct `remember(...)` pattern.

**Fix:** wrap in `remember(items, searchQuery, sortColumn, sortDirection) { … }`.

**Acceptance:** hover/selection changes no longer re-run the sort (spot-check via a counter
during development; remove before merge).

---

## S4.5 — `AppLogsScreen`: keyless `items(entries.size)` + per-row animation/scroll state

- **Severity:** Medium · **Effort:** S
- **File:** `ui/screens/AppLogsScreen.kt:218-251`

The `LazyColumn` uses the index overload without `key`, so filtering/clearing rescrambles
item identity; each `LogEntryRow` carries `animateColorAsState` and its own
`rememberScrollState()`, which get discarded/recreated on identity churn.

**Fix:** `items(entries, key = { stable id })`; decide whether horizontal scroll is
per-row (keep, now stable) or shared (hoist one state).

**Acceptance:** filtering preserves row identity (no flicker/animation restarts).

---

## S4.6 — `ThreePaneScaffold`: stale `density` captured by `pointerInput(isVertical)`

- **Severity:** Medium · **Effort:** S
- **File:** `ui/components/ThreePaneScaffold.kt:260-294`

`val density = LocalDensity.current` (:260) is captured by a `pointerInput` keyed only on
`isVertical` (:283) and used for px→dp conversion (:290-293). Moving the window between a
retina (2x) and external (1x) display changes density but does not restart the gesture
block — divider drags then move at the wrong ratio. This is the multi-monitor
density-staleness class of bug this codebase has hit before.

**Fix:** add density to the key: `.pointerInput(isVertical, density)`.

**Acceptance:** after dragging the window across displays with different scale factors,
divider drag distance matches pointer movement 1:1.

---

## S4.7 — `MonitoringScreen`: per-container bar rows rendered eagerly, all recompose per tick

- **Severity:** Low · **Effort:** M
- **File:** `ui/screens/MonitoringScreen.kt:397-405, 439-449` (also `filteredStats.forEach` ~:515)

CPU/Memory cards render all containers via `forEach` inside a `verticalScroll` Column —
no virtualization, full recompose per stats tick. Low severity at typical container counts.

**Fix:** cheapest: make `ContainerBarRow` take only stable primitives so unchanged rows
skip. Optionally virtualize if container counts are expected to be large.

**Acceptance:** unchanged rows skip recomposition during stats ticks.

---

## S4.8 — `LogsTabContent`: auto-scroll fires even when user scrolled up

- **Severity:** Low · **Effort:** S
- **File:** `ui/components/LogsTabContent.kt:72-74`

`LaunchedEffect(state.logs) { scrollTo(max) }` unconditionally snaps to bottom on every new
line — the user cannot read scrollback while logs flow. `LogsPaneState.isFollowing` already
exists (used at :353 for the live indicator).

**Fix:** gate the auto-scroll on `state.isFollowing` (and/or on "was already at bottom").
Coordinate with S4.1 (same file; a `LazyColumn` changes the scrolling API).

**Acceptance:** scrolling up while logs stream holds position; returning to bottom resumes
following.

---

## S4.9 — `CommandPalette`: `runningIndex` counter mutated during lazy item building

- **Severity:** Low · **Effort:** S
- **File:** `ui/shortcuts/CommandPalette.kt:237-267`

A `var runningIndex` incremented across `sectioned.forEach { items(…) }` blocks assumes a
strict build order for selection highlighting (`localIndex == selectedIndex`) — fragile
under recomposition/reordering.

**Fix:** derive a remembered `action.id → flatIndex` map from the existing `flatActions`
(:113) and look indices up by id.

**Acceptance:** keyboard selection highlight stays correct while the action list updates.
