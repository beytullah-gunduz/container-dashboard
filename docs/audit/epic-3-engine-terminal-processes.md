# Epic 3 — Engine manager & terminal processes

Base path: `composeApp/src/desktopMain/kotlin/com/containerdashboard/`.
S3.1, S3.4, S3.5 all edit `EngineManager.kt` — run together or sequentially.

---

## S3.1 — `runProcess`/`getColimaStatus`: blocking stdout drain makes the 15 s timeout dead code

- **Severity:** High · **Effort:** M
- **Confidence:** independently reported by 2 audit agents
- **File:** `data/engine/EngineManager.kt:66-74` (`getColimaStatus`), `:188-205` (`runProcess`)

`runProcess` does `proc.inputStream.bufferedReader().forEachLine { ... }` — this blocks
until the process closes stdout, so the subsequent `proc.waitFor(15, SECONDS)` only runs
*after* the process already exited. Consequences: (a) a hung CLI blocks the IO coroutine
indefinitely with no cancellation point; (b) slow-but-healthy commands like `colima start`
(30–60 s) are not protected by any real budget, and the intended 15 s cap never applies
where it should. Same pattern in `getColimaStatus` via `readText()`.

**Fix:** drain stdout on a separate thread/coroutine concurrently with `waitFor(timeout)`;
on timeout, `destroyForcibly()` and surface a distinct error. Give `start` a realistically
larger budget than `status`.

**Acceptance:** a synthetic hanging command (e.g. `sleep 600`) fails after the budget with
the process killed; `colima start` is not falsely reported as timed out.

---

## S3.2 — `DockerExecTtyConnector.connected` race: error before assignment leaves it stuck `true`

- **Severity:** High · **Effort:** S
- **File:** `terminal/DockerExecTtyConnector.kt:85-92` (`onError` at :67)

`execStartCmd.exec(callback)` is async; `connected = true` is written *after* it returns
(:92), while the callback's `onError` (:67) writes `connected = false` from another thread.
If the error fires first (stopped container, missing shell), the later `true` write wins —
`write()` then silently writes into a dead pipe and reads block forever.

**Verification note (2026-06-09):** confirmed at the cited lines. The window is narrower
than the original report implied: `execCreateCmd` (earlier in `start()`) fails synchronously
for a non-running container, so the race needs the container to die — or the exec to error —
between create and the async start callback. Still a real ordering bug with a trivial fix.

**Fix:** use an `AtomicBoolean`/state enum with compare-and-set so a terminal `false` (error/
closed) can't be overwritten by the startup `true`; or set `true` before `exec()` and let
`onError` be the only later writer.

**Acceptance:** opening a console on a just-stopped container yields a visible error state,
not a frozen terminal. Validate in the **release** build too (JediTerm + ProGuard history —
see project memory).

---

## S3.3 — `JediTermConsole` leaks the exec session when disposed mid-connect

- **Severity:** Medium · **Effort:** M
- **File:** `terminal/JediTermConsole.kt:54-83`

`connect()` runs blocking `execCreateCmd`/`execStartCmd` inside `withContext(Dispatchers.IO)`.
If the composable leaves composition during that window, `onDispose` sees `connector ==
null` and closes nothing; when the IO block later completes, the connector is assigned with
no owner — the exec session leaks until process exit.

**Fix:** keep the `launch` Job; in `onDispose` cancel it, and inside the coroutine close the
locally created connector on cancellation (`try/finally` or `ensureActive()` after creation).

**Acceptance:** rapidly open/close the console tab during connection; no orphaned exec
sessions (`docker inspect --format '{{.ExecIDs}}' <container>`).

---

## S3.4 — Engine start/stop commands are macOS-only but emitted on all platforms

- **Severity:** Medium · **Effort:** S
- **File:** `data/engine/EngineManager.kt:161-182` (`buildCommand`)

`DOCKER_DESKTOP`/`ORBSTACK`/`RANCHER_DESKTOP` unconditionally use `open -a` / `osascript`,
which don't exist on Linux/Windows; failures surface as opaque IOExceptions.

**Fix:** guard on the current OS; return a distinct "not supported on this OS" result for
unsupported combos (and platform-appropriate commands where they exist, e.g. Docker
Desktop's CLI on Windows).

**Acceptance:** on a non-macOS platform the UI shows a clear unsupported message instead of
a generic failure. Unit-testable once S8.3's extraction lands.

---

## S3.5 — Colima profile from socket path used in argv without validation

- **Severity:** Low · **Effort:** S
- **File:** `data/engine/EngineManager.kt:151-152` (`colimaProfileFromHost`)

The profile substring of the user-supplied host URL goes straight into
`--profile <value>` argv. No shell injection (argv array), but garbage/oversized values
reach the `colima` binary unchecked.

**Fix:** validate against `^[a-zA-Z0-9_-]{1,64}$`; refuse the operation with a clear error
otherwise.

**Acceptance:** malformed profile in the host URL produces a user-visible validation error.
