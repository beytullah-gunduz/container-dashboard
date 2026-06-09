# Audit follow-ups — discovered during implementation (2026-06-10)

Findings surfaced while implementing the main audit backlog (see [README](README.md)).
Each is **pinned by a test that asserts the current (wrong or quirky) behavior** — when
fixing one, update its pin test to assert the corrected behavior in the same commit.
Same story format as the audit epics; dispatchable individually.

## F1 — CPU% is always 0 when the daemon reports an empty `percpu_usage: []`

- **Severity:** Medium · **Effort:** S
- **File:** `composeApp/src/desktopMain/kotlin/com/containerdashboard/data/repository/DockerStatsMath.kt` (`calculateCpuPercent`)
- **Pin test:** `DockerStatsMathTest` — "empty percpuUsage list yields zero percent"

`numCpus = percpuUsage?.size ?: …` treats an empty (non-null) list as `0` CPUs, so the
multiplication yields a flat 0% instead of falling through to `onlineCpus` / host processor
count. Some cgroup-v2 daemons report `percpu_usage: []` rather than omitting the field.

**Fix:** `percpuUsage?.takeIf { it.isNotEmpty() }?.size ?: onlineCpus?.toInt() ?: hostProcessors`.

**Acceptance:** empty-list shape produces the same CPU% as the null shape; pin test updated.

## F2 — `mapShellError` reports "no shell" for permission-denied with exit code 126

- **Severity:** Low · **Effort:** S
- **File:** `composeApp/src/desktopMain/kotlin/com/containerdashboard/data/repository/DockerShellExec.kt` (`mapShellError`)
- **Pin test:** `DockerShellExecTest` — "exit code 126 wins over permission denied stderr"

The exit-code 126/127 check ("no shell/coreutils") precedes the stderr message patterns, so
a 126 paired with a "Permission denied" message misreports. Unlikely in practice (`ls`
permission failures exit 1/2) but the precedence is wrong.

**Fix:** evaluate the specific stderr message patterns (permission denied, no such file,
not running) before the generic exit-code fallback.

**Acceptance:** 126 + "Permission denied" maps to the permission error; existing no-shell
cases (126/127 with empty/irrelevant stderr) unchanged; pin test updated.

## F3 — `FormatUtils` is locale-dependent and has tier quirks

- **Severity:** Low · **Effort:** S
- **File:** `composeApp/src/commonMain/kotlin/com/containerdashboard/ui/util/FormatUtils.kt`
- **Pin tests:** `FormatUtilsTest` (expected strings are built via the same locale-dependent
  format call, plus "1048575 → 1024.0 KB" pinned explicitly)

Three issues: (a) `"%.1f".format(…)` uses `Locale.getDefault()` — a tr_TR/de_DE system
renders `1,0 KB`; (b) the tier check precedes rounding, so values just under a boundary
display as `1024.0 KB` instead of `1.0 MB`; (c) there is no TB tier (large volumes show
huge GB numbers).

**Fix:** format with `Locale.ROOT` (or manual decimal construction since commonMain may not
have java.util.Locale — check the actual source set; this file is commonMain compiled for
JVM only, so `String.format(Locale.ROOT, …)` via expect/actual or a desktop-only helper);
pick the tier after rounding; add a TB tier.

**Acceptance:** output identical on any default locale; `1048575` → `1.0 MB`; TB values
render; pin tests updated to literal expected strings (no longer built via the same call).

## F4 — `testConnection` leaks the throwaway repository if `getVersion()` throws

- **Severity:** Low · **Effort:** S
- **File:** `composeApp/src/commonMain/kotlin/com/containerdashboard/ui/screens/viewmodel/SettingsScreenViewModel.kt` (`testConnection`)
- **Pin test:** `SettingsScreenViewModelTest` asserts `close()` on the success and
  Result-failure paths (the throw path is currently unreachable via the Result contract)

No `finally` around the temp repository: a thrown (not Result-wrapped) exception skips
`close()`. Unreachable through the current contract, but one refactor away from a leak.

**Fix:** wrap in `try/finally { testRepo.close() }`.

**Acceptance:** close() guaranteed on all paths; add a fake whose `getVersion` throws to
cover it.

## F5 — `stopAllContainers` can hang forever in `InProgress`

- **Severity:** Low · **Effort:** S
- **File:** `composeApp/src/commonMain/kotlin/com/containerdashboard/ui/screens/viewmodel/SettingsScreenViewModel.kt` (`stopAllContainers`)
- **Pin test:** `SettingsScreenViewModelTest` (behavior exercised via the fake; no hang pin)

`getContainers(all = false).first()` on a cold flow has no timeout — if the repository
flow never emits (daemon wedged mid-call), the action state sits in `InProgress`
indefinitely with no way out in the UI.

**Fix:** `withTimeoutOrNull(…)` around the `.first()` (the file already imports
`withTimeoutOrNull` for other actions — follow that pattern), surfacing a timeout failure
in the ActionState.

**Acceptance:** a never-emitting fake flow yields a failure state, not a hang; test added.

---

# Remaining manual validation (needs a live engine / packaged app)

Run through after any related change, or once as a post-audit smoke pass:

**Dev build (`JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew run`):**
- [ ] Start/stop a container from the UI → list refreshes via the event path (not the 15 s poll).
- [ ] Switch engine host in Settings → Containers/Images/Volumes/Networks/Monitoring all
      stream from the new host without restart; no "No containers" flash mid-switch.
- [ ] Start app with daemon stopped, then start daemon → Dashboard version/system info
      populate without restart.
- [ ] Logs pane: follow a chatty container at the 5000-line setting — smooth; scroll up
      while streaming → position holds; return to bottom → following resumes; word-wrap
      toggle, filter, and text selection still work; a container printing blank lines shows them.
- [ ] Kill one of several followed containers → other streams keep going.
- [ ] Files tab: expand/collapse rapidly, unreadable dir shows inline error, download a file
      whose name contains `../` → lands in the chosen dir under a sanitized name.
- [ ] Console tab on a container that exits immediately → clear disconnected state, no
      frozen terminal; open/close console rapidly → no orphaned exec sessions
      (`docker inspect --format '{{.ExecIDs}}' <id>`).
- [ ] Inspect a container with `POSTGRES_PASSWORD`-style env vars → masked with reveal toggle.
- [ ] Enter `tcp://…` as engine host → warning row appears; `unix://…` → disappears.
- [ ] Busy multi-threaded container shows >100% CPU on a multi-core host (matches `docker stats`).
- [ ] Prune flow still reports success (note: deleted counts aren't rendered anywhere yet).
- [ ] Command palette: arrow-key highlight tracks correctly while stats tick.
- [ ] Monitoring: sort by CPU while ticking — no row glitches.
- [ ] Multi-monitor (retina ↔ external): drag the logs-pane divider after moving the window
      across displays — tracks the pointer 1:1.

**Release build (`./gradlew :composeApp:createReleaseDistributable`, then launch the .app):**
- [ ] S6.1 ProGuard: packaged app connects to the Docker socket, lists containers, streams
      stats (the keep rules are only exercised here, never in `gradlew run`).
- [ ] S6.4 migration: on a machine with `~/.container-dashboard`, first launch moves data to
      `~/Library/Application Support/container-dashboard` and settings survive; fresh
      install writes to the new location directly.
- [ ] About dialog shows the real version when built with `-Papp.version=X`.

**CI (observe on next push / tag):**
- [ ] Second consecutive CI run shows Gradle cache hits (S7.3).
- [ ] Next `v*` tag: all 3 installers build; `APP_VERSION` env passes through (S5.4/S7.7).

# Deferred scope (conscious decisions, revisit if needed)

- TLS cert-path/verify UI for remote hosts (S5.2 — warning-only was implemented).
- Full `@Stable` holder rework of ContainersScreen rows (S4.2 — pragmatic version landed).
- Gradle configuration cache (S7.5 — blocked on the Spotless config-cache flake).
- Integration tests against real containers (S7.4 — `docker-compose.test.yml` was deleted;
  revive from git history if ever built).
