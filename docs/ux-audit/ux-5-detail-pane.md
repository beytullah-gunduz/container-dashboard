# UX-5 — Detail pane: Logs / Files / Console

Heuristics: *Visibility of system status*, *Consistency*, *Recognition rather than recall*.
Base path: `composeApp/src/commonMain/kotlin/com/containerdashboard/ui/`.

The pane is the best part of the app functionally (follow mode, filter, word-wrap, file
tree with preview and download, a real terminal). The issues are about state that leaks
between containers and about context the pane never shows.

---

## U5.1 — Delete is one click, unconfirmed, next to Close

- **Severity:** P0 · See **U1.1**. Listed here because the fix lives in
  `components/ConsolePane.kt:172-195`.

---

## U5.2 — Log filter state is process-global and never reset

- **Severity:** P1 · **Effort:** S
- **Files:** `state/LogsFilterState.kt:15-18` (`object` with `filterText`,
  `selectedService`; documented as intentional), `components/LogsTabContent.kt:139-150`
  (filter applied), `:119-127` ("No logs available" only when the *buffer* is empty),
  `:370` (footer shows `state.logs.size`, the unfiltered count)

Sequence: open logs for A, type `error`, close, open logs for B → B's pane shows an
**empty list with no message** (the buffer is non-empty so the empty state doesn't
trigger, and no lines match). The footer still says "505 lines". In group mode a service
filter chosen in project A persists into project B: the dropdown shows a service name
that isn't in the list and every line is hidden. Nothing on screen says a filter is active
except the small text in the filter box.

**Fix:** scope filter state to the container/group id (reset on change, or keep a
per-id map); add a "No lines match *error* — Clear filter" empty state; footer
"12 of 505 lines"; highlight the filter box when non-empty.

**Acceptance:** switching containers never yields a silently blank pane.

---

## U5.3 — Pane header gives no context beyond the name

- **Severity:** P2 · **Effort:** S
- **File:** `components/ConsolePane.kt:86-118` (header), `:131-195` (toolbar)

Only `displayName` is shown. No status badge, image, uptime or short ID — so after
the list collapses to compact mode (U3.3) the pane is the only wide area and it says
nothing about *what* is being viewed. Toolbar icons: download glyph = "Save all logs",
which reads as "download file"; Pause/Restart/Delete apply to the container, not the
logs, but sit in the *logs* toolbar without a visual group boundary.

**Fix:** header line 2: `StatusBadge · image · Up 3 hours · 483e7bc47919`; split the
toolbar into "logs" (save) and "container" (pause/restart/more) groups with a divider;
put Delete in an overflow menu.

---

## U5.4 — Files tab has no current-path indicator

- **Severity:** P2 · **Effort:** S
- **File:** `components/FilesTabContent.kt:95-135` (header always renders `"/"`, line 114),
  `:298-330` (viewer shows `selected.name` only)

The header reads `/` regardless of depth; the tree relies on 16 dp indent per level.
Opening a file shows its name but not its directory; there is no "copy path". Symlinked
directories (`bin -> usr/bin`) sort after regular files.

**Fix:** breadcrumb of the deepest expanded node, or show the full path in the viewer
header with a copy action; sort symlinks-to-directories with directories.

---

## U5.5 — Console: long silent connect, unthemed scrollbar, orphaned shell on close

- **Severity:** P2 (UX) / engineering follow-up · **Effort:** S–M
- **Files:** `desktopMain/.../terminal/JediTermConsole.kt`,
  `desktopMain/.../terminal/DockerExecTtyConnector.kt`, `ConsolePane.kt:296-306`
  (console kept composed at 0 dp when hidden)

Observed: "Connecting to container…" for ~8–10 s with no cancel and no timeout copy;
then a black JediTerm surface with a **light Swing scrollbar** (native look, arrows at
both ends) on the dark theme; background pure black rather than the app surface.
After closing the pane, App Logs showed "Docker exec session closed", but
`docker top example-stack-postgres-1` still listed the `/bin/sh` from that session
minutes later — closing the stream does not terminate the exec'd shell. (The earlier
engineering fix `0f45890` covered the early-dispose path; the normal close path leaks.)

**Fix:** show elapsed time / "still connecting" after 5 s with Cancel; theme the JediTerm
`TerminalPanel` (scrollbar UI, background) from `MaterialTheme`; on close send `exit`
or kill the exec'd process before closing the stream.

**Acceptance:** `docker inspect --format '{{.ExecIDs}}'` is empty after closing the pane
(this is already in the manual checklist in `docs/audit/follow-ups.md`).

---

## U5.6 — Missing table-stakes log-viewer capabilities

- **Severity:** P2 · **Effort:** M
- **File:** `components/LogsTabContent.kt`; repository already supports
  `getContainerLogs(timestamps = …)` (`DesktopDockerRepository.kt:506-529`)

No timestamps toggle, no stdout/stderr distinction, no ANSI handling (SGR escape
sequences render as raw bracket-codes — nothing in the codebase strips or interprets
them), no tail-size control in the pane (only in Settings), no copy-all, no match count
or jump-to-next-match, no per-service colour in group mode beyond the `[service]` prefix.

**Fix (priority order):** ANSI strip-or-colour; timestamps toggle; match count with
next/previous; stderr tint; per-service colour swatch in group mode.
