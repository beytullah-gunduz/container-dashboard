# UX-3 — Shell, navigation & keyboard

Heuristics: *User control and freedom*, *Consistency and standards*, *Flexibility and
efficiency of use*.
Base path: `composeApp/src/`.

---

## U3.1 — Root key handler intercepts `?` in every text field

- **Severity:** P1 · **Effort:** S
- **File:** `commonMain/.../ui/shortcuts/AppShortcuts.kt:44-86`, specifically `:79`
  (`ev.isShiftPressed && ev.key == Key.Slash -> onShowCheatsheet()`)

`AppShortcutScope` wraps the whole content in `onPreviewKeyEvent`. Preview events are
delivered root-first, so Shift+/ is consumed before any `BasicTextField` inside the
scaffold sees it. Typing `?` into the container search, the log filter, the engine-host
field or the Colima quota fields opens the shortcuts dialog and drops the character.
Fields hosted in `Dialog`/`AlertDialog` (command palette, Create Volume/Network) render
in a separate layer and are probably unaffected — not verified. `Escape` is also consumed
at the root (`:75`), which hides the logs pane even when the user meant to clear a
field or close a menu.

**Fix:** only treat unmodified/shift-only keys as shortcuts when no text input has focus
(track `LocalTextInputService`/`FocusManager` state, or move `?` behind ⌘/ only —
which already exists at `:64`). Keep ⌘-chords global.

**Acceptance:** `?` can be typed in every field; the cheatsheet still opens via ⌘/ and
via `?` when a list (not a field) has focus.

---

## U3.2 — Command palette: keyboard selection scrolls off-screen; entries indistinguishable

- **Severity:** P1 · **Effort:** S
- **File:** `commonMain/.../ui/shortcuts/CommandPalette.kt:144-176` (key handling),
  `:246-268` (`LazyColumn(state = listState)` — `listState` is never scrolled),
  `:94-104` (substring filter), `App.kt:451-536` (`buildPaletteActions`)

With N containers the palette holds ~3N rows (View logs / Start|Stop+Restart / Delete
per container). ↓ moves `selectedIndex` but nothing calls
`listState.animateScrollToItem`, so after ~12 presses the highlight is below the fold and
Enter fires an invisible action — including "Delete: …". Observed rows truncate as
`View logs: example-job-20260101120000-1a2b3c4d-5e6f-4a7b-8…` — five consecutive
rows identical to the eye. Navigation rows show no ⌘1–7 hint (only Settings shows ⌘,).
Filtering is plain substring; no fuzzy/subsequence match. The container subtitle reads
"Stopped" for a *paused* container (`subtitle = if (c.isRunning) "Running" else "Stopped"`,
`App.kt` `buildPaletteActions`) while the list badge says Paused.

**Fix:** `LaunchedEffect(selectedIndex) { listState.animateScrollToItem(...) }`; show
the short ID as subtitle; middle-ellipsis labels; style "Delete:" rows as destructive
and put them last; add `keyboardHint` for nav rows; subsequence matching.

**Acceptance:** holding ↓ keeps the highlight visible; Enter never triggers an
off-screen row; each container row shows its ID.

---

## U3.3 — Opening the detail pane squashes the list to its minimum width by default

- **Severity:** P2 · **Effort:** S
- **File:** `commonMain/.../ui/components/ThreePaneScaffold.kt:147-154`
  (`extraWidth` defaults to `maxExtraWidth = availableWidth - 450.dp`)

First time a user opens logs, the pane takes everything but 450 dp, which is below
`COMPACT_THRESHOLD` (700 dp, `LayoutThresholds.kt:10`), so the containers table collapses
into the compact card list, names truncate, and the toolbar goes icon-only. Observed at
1372 pt: list = 290 pt, pane = 910 pt. Users must discover the 6 dp divider to fix it.
The transition also flashes a black region where the pane will be for ~300 ms
(`AnimatedContent` in `App.kt` runs concurrently with the pane resize).

**Fix:** default `extraWidth = (availableWidth * 0.45f).coerceIn(min, max)`; keep the
list ≥ 700 dp when the window allows; animate width and content together or not at all.

---

## U3.4 — Window chrome gaps

- **Severity:** P2 · **Effort:** M
- **Files:** `desktopMain/.../Main.kt:328` (`onCloseRequest = { hideWindow() }`),
  `:209-229` (no minimum size), `desktopMain/.../ui/chrome/WindowChrome.desktop.kt:227-245`
  (Windows/Linux `WindowControls` = Minimize + Close only), `:176-224` (traffic lights)

- Close hides to the tray with no first-time notice; on macOS this is defensible, but
  there is no menu bar at all, so ⌘Q is the only quit besides the tray, and there is no
  Edit/Window menu (users expect ⌘W to close the window, ⌘M to minimise).
- No `window.minimumSize` — the window can be shrunk below the 220 dp sidebar + 450 dp
  detail minimum and content clips.
- Custom traffic lights do not dim when the window is inactive (native ones do).
- Windows/Linux chrome has no Maximize button and no double-click-to-maximise
  (`WindowDraggableArea` path lacks the double-click handling the macOS path has).
- The 32 dp title bar carries no title text and no window title anywhere in the
  content; on a multi-window desktop the app is identified only by its traffic lights.

**Fix:** `MenuBar` with App/Edit/View/Window; `minimumSize = 900×600`; inactive-state
tint for traffic lights; add Maximize to `WindowControls`; optional centred title.

---

## U3.5 — "App Logs" sits in the primary navigation

- **Severity:** P2 · **Effort:** S (owner decision)
- **Files:** `commonMain/.../ui/navigation/Screen.kt:64-69`, `:79` (`mainScreens`),
  `AppLogsScreen.kt:99` ("Application Logs")

A developer diagnostics view (logback entries with thread names) is a peer of
Containers/Images and takes ⌘7. The sidebar says "App Logs", the page says
"Application Logs". Users looking for *container* logs try it first.

**Fix:** move under Settings › Advanced (or Help › Diagnostics); align the label; free
⌘7.

---

## U3.6 — Screen transitions overlay old and new content

- **Severity:** P2 · **Effort:** S
- **File:** `App.kt:307-314` (`fadeIn + scaleIn(0.96) togetherWith fadeOut`), `:119-127`

The crossfade keeps the outgoing screen legible while the incoming one scales in — text
over text for ~300 ms on every sidebar click (captured repeatedly during the walkthrough).
Combined with a pane close it produced a compact-width Dashboard next to a black region.
Sibling navigation in desktop tools is instant or fade-through-background.

**Fix:** `fadeIn(tween(120)) togetherWith fadeOut(tween(90))` with `SizeTransform` off,
or no transition for sidebar navigation; keep the scale for modal/overlay states only.

---

## U3.7 — No keyboard model inside screens

- **Severity:** P3 · **Effort:** M
- **Files:** `ContainerRendering.kt` (rows are not `focusable`),
  `ui/components/SearchBar.kt` (no focus indication — no `onFocusChanged`, no border),
  `ui/components/Sidebar.kt:105-118` (palette button tooltip overlaps the connection card)

⌘F focuses search, but there is no visible focus ring, no ↑/↓ through rows, no Enter to
open logs, no Space to select. Tab order is undefined. The palette-button tooltip renders
over the "Connected · Colima" card.

**Fix:** focus ring on SearchBar; rows `focusable()` with ↑/↓/Enter/Space; tooltip
placement above for bottom-anchored controls.

---

## U3.8 — About dialog is forced dark and has no close button

- **Severity:** P3 · **Effort:** S
- **File:** `desktopMain/.../Main.kt:354-390` (`ContainerDashboardTheme(darkTheme = true)`)

In light theme the About window is a dark box; it has only the OS close control. Minor,
but it is the one place the theme setting is ignored.

---

## U3.9 — No manual refresh on list screens; the ⌘R plumbing is dead code

- **Severity:** P3 · **Effort:** S
- **Files:** `App.kt:263-269` (`onRefresh` wired to `refreshContainers()`),
  `commonMain/.../ui/shortcuts/AppShortcuts.kt:33` (parameter accepted, no `Key.R` — or
  any key — routes to it), `AppShortcut.kt` (no REFRESH entry in the cheatsheet);
  Containers / Images / Volumes / Networks / Dashboard headers have no refresh control
  (only the error-state *Retry*).

Lists update from the event stream and a 15 s poll, which is right most of the time —
but when a user doubts the view ("I just ran `docker rm` in a terminal") there is
nothing to press. The code clearly intended ⌘R; the binding was never added.

**Fix:** bind ⌘R (and add it to the cheatsheet) to refresh the current screen's
resource; a small refresh icon next to each list title with a "last updated 4 s ago"
tooltip.
