# Epic 8 — Test coverage gaps

Run this epic **after** E1–E3 land where possible: tests should pin the *fixed* behavior,
and S8.1 refactors the same file E1 edits. Existing test infra: `commonTest` with
`FakeDockerRepository`; there is currently no `desktopTest` source set — S8.1/S8.3 will
need to add one.

---

## S8.1 — `DesktopDockerRepository` (1700+ lines) has zero tests

- **Severity:** High · **Effort:** L
- **File:** test gap — `composeApp/src/desktopMain/kotlin/com/containerdashboard/data/repository/DesktopDockerRepository.kt`

The entire Docker integration — stats math (`calculateCpuPercent`, `extractDiskIo`,
`extractNetworkIo`), shell-error mapping (`mapShellError`), and all docker-java → model
mapping functions — is untested. The stats arithmetic (delta computation, cgroup-v2
empty-list handling, per-CPU scaling) and error-message dispatch directly drive what users
see.

**Fix:** extract the pure pieces (stats math, `mapShellError`, mapping extensions) into
internal top-level functions/files, add a `desktopTest` source set, and test them with
fixed docker-java response objects. Coordinate with E1: do this refactor after (or
together with) the E1 fixes to avoid churn.

**Acceptance:** stats math covered incl. cgroup v1/v2 shapes and the S1.5 fallback; error
mapping covered for no-shell / permission-denied / not-running / generic.

---

## S8.2 — `SettingsScreenViewModel` mutation paths untested

- **Severity:** High · **Effort:** M
- **File:** test gap — `ui/screens/viewmodel/SettingsScreenViewModel.kt` (`pruneAll`, `stopAllContainers`, `testConnection`)

The most destructive operations in the app (prune-all, stop-all) have no regression
protection. Partial-failure aggregation in `pruneAll` (some ops succeed, some fail) and the
cold-flow `.first()` assumption in `stopAllContainers` are the risky spots. `testConnection`
constructs a real repository — needs a factory seam to be testable.

**Fix:** add `SettingsScreenViewModelTest` in `commonTest` driving partial failures through
`FakeDockerRepository`; inject a fake repository factory for `testConnection`.

**Acceptance:** tests assert `ActionState` transitions for full-success, partial-failure,
and total-failure of `pruneAll`; `stopAllContainers` verified against a fake with mixed
running/stopped containers.

---

## S8.3 — `EngineManager.buildCommand` untested

- **Severity:** Medium · **Effort:** S
- **File:** test gap — `data/engine/EngineManager.kt:161-182`

Pure command construction for five engine types × start/stop, with easy-to-break branches
(`profile != "default"` guard, conditional cpu/memory/disk flags). Also the `runProcess`
timeout path is never exercised (relevant to S3.1).

**Fix:** extract `buildCommand` to a testable internal function (pairs naturally with S3.4's
OS guards) and assert exact argv lists per engine/action combo in `desktopTest`.

**Acceptance:** all engine/action combinations asserted, including Colima profile and
resource flags, and (post-S3.4) the unsupported-OS results.

---

## S8.4 — `AppViewModel` file-browser logic untested

- **Severity:** Medium · **Effort:** M
- **File:** test gap — `ui/screens/viewmodel/AppViewModel.kt` (`flattenTree`, `toggleNode`, `loadRoot`, `loadChildren`, `resolveListPath`, `sortEntries`)

~200 lines of stateful tree logic over four mutable collections, including symlink path
resolution — only the container-action error paths are covered today. Pairs with S2.5: the
`TreeState` consolidation makes this directly testable.

**Fix:** `AppViewModelFileBrowserTest` in `commonTest`: seed `FakeDockerRepository` directory
listings, drive `openFilesTab`/`toggleNode`/`openFile`, assert `filesPaneState` (depth,
expansion, errors, symlink resolution).

**Acceptance:** expansion/collapse, error propagation, and relative-vs-absolute symlink
targets covered.

---

## S8.5 — `FormatUtils` untested

- **Severity:** Low · **Effort:** S
- **File:** test gap — `ui/util/FormatUtils.kt` (`formatBytes`, `formatBytesPerSecond`)

Pure functions with boundary logic (1024 thresholds, tier selection, rounding) used across
every stats display. Trivial to cover in `commonTest`.

**Fix:** `FormatUtilsTest` covering all tiers, exact boundaries (1023/1024), zero, and
negative inputs.

**Acceptance:** tests green; boundaries pinned.
