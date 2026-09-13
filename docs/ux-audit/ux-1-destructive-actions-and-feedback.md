# UX-1 — Destructive actions & feedback

Heuristics: *Error prevention*, *Visibility of system status*, *User control and freedom*.
Base path: `composeApp/src/commonMain/kotlin/com/containerdashboard/`.

The app has five different ways to delete a container (row icon, context menu, compact
overflow menu, group header, pane header, palette, Delete All, Delete Selected) and they
do not agree on whether to confirm, whether the *Confirm Before Delete* preference
applies, or where a failure is reported. Fix the policy once, then apply it everywhere.

| Entry point | Confirms? | Honours *Confirm Before Delete*? | Failure surfaced? |
|---|---|---|---|
| Row trash icon / context menu / compact menu | yes | yes | Containers screen banner |
| Delete Selected | yes | yes | Containers screen banner |
| Delete All (header) | always | n/a (always) | Containers screen banner |
| Compose group "Delete all" | always | ignored | Containers screen banner |
| **Pane header trash** (`ConsolePane.kt:178`) | **never** | **ignored** | **nowhere** (U1.2) |
| Palette "Delete: …" | always | ignored | Containers banner only if that screen is visible |
| Row Stop | never | — | banner |
| Group "Stop all" | always | — | banner |
| Pane Restart / Pause | never | — | **nowhere** |

---

## U1.1 — Pane header trash force-deletes with no confirmation (and deletes the whole group)

- **Severity:** P0 · **Effort:** S
- **Files:** `ui/components/ConsolePane.kt:172-190`, `App.kt:364-367`,
  `ui/screens/viewmodel/AppViewModel.kt:581-598`

The extra pane toolbar is `[download] [pause] [restart] [delete] [close]`. `onRemoveContainer`
in `App.kt:364` calls `viewModel.removeLogsContainer()` directly; that method removes with
`force = true` (`AppViewModel.kt:589`) for **every** container in `logsPaneState.containers`.
No `ConfirmActionDialog`, no check of `PreferenceRepository.confirmBeforeDelete()`.
(Correction on review: in group-logs mode the button is *disabled* — `LogsPaneState.container`
is `containers.singleOrNull()` and `ConsolePane.kt:180` gates `enabled` on it — so the
whole-project case is not reachable from the UI today. The ViewModel would still remove all
of them if that gate were relaxed, which is why the fix confirms unconditionally for >1.) Observed in the live app: the
trash sits at x≈1325 and Close at x≈1352 in a 1372-wide window — 27 px apart, same size,
same row, the trash tinted red being the only cue.

**Fix:** route through the same `askConfirm` policy as the row action (`ContainersScreen.kt`
`askConfirm`), naming the container: "Delete *name*?"; keep an always-confirm rule for the
>1 case so the ViewModel path is safe if the gate is ever relaxed. Move Delete out of the
pane toolbar into an overflow menu (`⋯`) or at minimum put a divider and a gap between
Delete and Close.

**Acceptance:** with *Confirm Before Delete* on, the pane trash shows the dialog; with it
off, single-container delete proceeds; Delete is not adjacent to Close.

**Plan:** [`docs/u1.1-pane-delete-confirm-plan.md`](../u1.1-pane-delete-confirm-plan.md).
**Status:** fixed (dialog via the shared `ConfirmActionDialog`, preference
honoured for a single container, always-confirm for >1, divider before Close). Live-smoked
on the packaged app. Result-review note for later: the confirmed action re-reads
`logsPaneState.containers` at confirm time rather than capturing the id at click time —
safe today because the pane target cannot change under the modal, but capture the id if
that ever changes.

---

## U1.2 — `AppViewModel.error` is never rendered; no app-wide feedback channel

- **Severity:** P0 · **Effort:** M
- **Files:** `ui/screens/viewmodel/AppViewModel.kt:125-126` (declared), `App.kt` (never
  collected); `grep -rn "Snackbar" commonMain` returns nothing.

`_error` is set by `pauseLogsContainer`, `unpauseLogsContainer`, `restartLogsContainer`,
`removeLogsContainer`, group log fetch (`:241`), `downloadFile` (`:370`), `saveAllLogs`
(`:575`). No composable collects `AppViewModel.error`. A failed restart from the pane
produces nothing on screen. Palette actions call `ContainersScreenViewModel`, whose error
banner only exists while the Containers screen is composed — trigger "Stop: x" from the
Dashboard and a failure is invisible.

**Fix:** add one `SnackbarHost` at the `App` scaffold level fed by a shared
`UiMessage` flow (error + success + undo). Route `AppViewModel.error`, palette results and
danger-zone results through it. Keep the per-screen inline banners for load failures.

**Acceptance:** stop the daemon mid-action → a visible message; palette actions give
visible feedback on whatever screen is showing.

**Plan:** [`docs/u1.2-app-feedback-plan.md`](../u1.2-app-feedback-plan.md).
**Status:** fixed — `UiMessages` bus + `AppSnackbarHost` (latest-wins,
error styling), bridges for `AppViewModel.error` and off-screen
`ContainersScreenViewModel.error`, in-progress acknowledgements for palette actions
(deliberately not success toasts — see U1.3). Live-smoked on the packaged app.
Follow-ups from the result review:
- **Snackbar is occluded by the Console tab.** JediTerm is a heavyweight `SwingPanel`
  (`JediTermConsole.kt:113`) and the repo does not enable `compose.interop.blending`, so
  with the Console tab active the bottom-centre snackbar draws *under* the terminal —
  exactly for pane-toolbar errors. Anchor the host to the detail pane or evaluate
  interop blending on macOS. (Inferred from source; not observed live.)
- Daemon errors surface as raw docker-java text (`Status 409: {"message":…}`) — map to a
  human sentence at the repository boundary.
- Palette subtitle reads "Stopped" for a paused container (`App.kt`, `buildPaletteActions`:
  `if (c.isRunning) "Running" else "Stopped"`).

---

## U1.3 — "✓ Paused / Started / Stopped" flashes before the action runs

- **Severity:** P1 · **Effort:** S
- **File:** `ui/screens/ContainerRendering.kt:1116-1120`, `:1148-1152`, `:1175-1179`

`successMessage = "Paused"` is assigned synchronously in the click handler, before the
repository call, regardless of outcome. A failed stop shows "✓ Stopped" for 2 s in the row
*and* an error banner at the top of the screen — contradictory feedback. It also survives
the in-progress spinner and re-appears when the spinner clears.

**Fix:** derive the flash from the ViewModel result (e.g. a `lastActionResult: Map<id,
Result>` flow), or drop the flash and rely on the status badge changing.

**Acceptance:** a failed action never shows a ✓; a successful one shows it after the
state actually changed.

---

## U1.4 — *Delete All* is the screen's only permanent primary action

- **Severity:** P1 · **Effort:** S (move) / M (redesign)
- **File:** `ui/screens/ContainersScreen.kt:576-606`, dialog count at `:257`

Top-right, filled `error` colour, always visible when any container exists. That position
is where users look for the constructive primary ("Run", "Create"), and the Containers
screen has no constructive action at all, so the eye lands on the destructive one. It
deletes **all** containers — `containerCount = containers.size` (`:257`), not the filtered
set — including the running ones. With a long list the dialog says "N container(s)
will be deleted", one click from the most-prominent button on the page.

**Fix:** move to an overflow/`⋯` menu next to the layout toggle or into the Danger Zone
in Settings (which already has *Stop All*); scope it to the current filter ("Delete N
stopped containers"); for N > 10 require typing the count or the word DELETE.

**Acceptance:** no filled-error button in the default Containers header; Delete All is
two deliberate steps away and states exactly what set it will remove.

**Plan:** [`docs/u1.4-delete-all-plan.md`](../u1.4-delete-all-plan.md).
**Status:** fixed — "More actions" overflow (Refresh + scoped delete), the
item acts on the visible set, the dialog states scope and running count and requires
the count to be typed above 10. Live-smoked; found **U1.11** in the process.

---

## U1.5 — Confirmation policy is inconsistent across entry points

- **Severity:** P1 · **Effort:** S
- **Files:** see table above: `ContainersScreen.kt:181-192` (`askConfirm`),
  `ContainerRendering.kt:117-146` (group dialogs), `ConsolePane.kt`, `App.kt:254-262`
  (palette)

The *Confirm Before Delete* toggle governs row/selected deletes only. Group delete and
palette delete always confirm; pane delete never does. Stop confirms at group level but
not per row. Users cannot form a model of "when will this ask me".

**Fix:** one policy function (`confirmPolicy(action, count)`) used by every entry point:
destructive single → honour preference; destructive bulk (>1) → always; stop → never
(reversible). Document it in the Settings toggle subtitle.

**Acceptance:** table above collapses to two rows.

---

## U1.6 — Context-menu *Delete* looks disabled but is live for running containers

- **Severity:** P2 · **Effort:** S
- **Files:** `ui/screens/components/ContainerContextMenu.kt:141-163`,
  `ui/screens/ContainerRendering.kt:806-830` (compact overflow menu)

When `container.isRunning`, the "Delete" label is drawn at `onSurface.copy(alpha = 0.38f)`
— the Material disabled colour — but `DropdownMenuItem` has no `enabled = false`, and the
click proceeds. With *Confirm Before Delete* on (the default) the user lands in the
confirm dialog; with it off, the click force-stops and removes immediately. Either way a
disabled look on an active destructive item invites the "click to see why it's greyed
out" reflex — downgraded from P1 on review because the default preference does
interpose a dialog.

**Fix:** either actually disable it with a tooltip ("Stop the container first") or render
it as a normal destructive item ("Force delete") in `error` colour.

**Acceptance:** visual state and enabled state match.

---

## U1.7 — Dark-theme destructive buttons: white text on pale pink (1.7:1)

- **Severity:** P2 (P1 for the confirm dialog) · **Effort:** S
- **Files:** `ui/theme/Theme.kt:101-118` (`darkColorScheme` does not set `error`/`onError`),
  `ContainersScreen.kt:460`, `:538`, `:592`; `ui/components/ConfirmActionDialog.kt:83-88`

`Button(colors = ButtonDefaults.buttonColors(containerColor = colorScheme.error))` leaves
`contentColor` at its default, `onPrimary` (white). In the dark scheme `error` is M3's
default `#F2B8B5`; white on it is **1.7:1** (AA needs 4.5:1). Observed: "Delete All",
"Stop N selected", "Delete N selected" and the confirm button of every
`ConfirmActionDialog` render as white-on-pink. `ErrorStateCard` gets it right by passing
`contentColor = onError`.

**Fix:** set `contentColor = MaterialTheme.colorScheme.onError` at those call sites, or
define `error`/`onError` in `DarkColorScheme` to match `AppColors.Stopped`.

**Acceptance:** ≥4.5:1 on every filled-error button in both themes.

---

## U1.8 — Long-running destructive operations give no progress or result detail

- **Severity:** P2 · **Effort:** S
- **Files:** `ui/components/DeletingAllContainersDialog.kt:39` (no count, no cancel),
  `ui/screens/viewmodel/SettingsScreenViewModel.kt:185` ("All unused resources pruned"),
  `:206` ("All containers stopped")

Deleting a long list of containers shows an undismissable spinner with "Please wait…" —
no "n of N", no cancel. Prune reports success without the counts or reclaimed bytes that
`PruneResult` already carries (noted as unrendered in `docs/audit/follow-ups.md`).

**Fix:** progress "n of N" driven from `pendingDeleteIds`; Cancel that stops issuing new
removes; prune result "Removed <n> containers, <n> images, <n> volumes — <size> reclaimed".

---

## U1.9 — Enter cancels even when the Delete button is focused; Delete All dialog has no key handling

- **Severity:** P3 · **Effort:** S
- **Files:** `ui/components/ConfirmActionDialog.kt:34-53`,
  `ui/components/DeleteAllContainersDialog.kt` (no key handling)

Enter → Cancel is a defensible default, but the `onPreviewKeyEvent` on the dialog
intercepts Enter *before* the focused button sees it, so Tab → Delete → Enter still
cancels. The only keyboard confirm is Tab → Delete → Space, which nobody will discover.
(Corrected on review: an earlier draft said there was no keyboard path at all; Compose
Desktop does Tab-traverse the dialog buttons.) `DeleteAllContainersDialog` handles
neither Enter nor Escape explicitly; it relies on `onDismissRequest`.

**Fix:** Escape = cancel, Enter = cancel (keep), ⌘⏎ or Tab-to-button = confirm; share one
dialog composable so both get the same handling.

---

## U1.10 — Row action cluster: Stop and Delete are both red, both 24 dp, 6 px apart, no tooltips

- **Severity:** P2 · **Effort:** S
- **File:** `ui/screens/ContainerRendering.kt:1090-1200`

`[logs] [pause|start] | [stop] [delete]` — 14 dp glyphs in 24 dp buttons on a 30 dp row.
Stop is `AppColors.Stopped` red, Delete is `error` red; the only separation is a 14 dp
divider. Row buttons have `contentDescription` but no `AppTooltip`, unlike the group
header buttons directly above them (`:230-300`) which do. Since accessibility is off
(U4.1), the description is not reachable by any user.

**Fix:** tooltips on row actions; make Stop neutral-coloured (it is reversible) and keep
red for Delete only; ≥8 dp gap after the divider; or move Delete into the row's context
menu and keep only reversible actions inline.

**Acceptance:** exactly one red glyph per row; hovering any row icon names it.

---

## U1.11 — `enabled = false` is not a safety boundary under the desktop accessibility bridge

- **Severity:** P1 (platform) · **Effort:** S per control
- **Found:** 2026-09-13, during the U1.4 live smoke, on the first build with accessibility
  re-enabled (U4.1).

Compose Multiplatform 1.12.0 registers the `OnClick` semantics action on every
`clickable` regardless of `enabled` (`foundation` `Clickable.kt:2009-2025`;
`performClick()` at `:1703-1706` has no enabled check). The desktop bridge exposes it as
`AccessibleAction.CLICK` and `ComposeAccessible.doAccessibleAction` (`ui-desktop`
`ComposeAccessible.kt:329-335`) invokes it **without consulting
`SemanticsProperties.Disabled`**; `isEnabled()` only feeds the state set. JBR's
`CAccessibility.doAccessibleAction` adds no gate. M3 `Button`, `IconButton` and
`DropdownMenuItem` all route through `clickable(enabled = …)`, so every control in the
app is affected.

Reproduced live: an AXPress on the *disabled* typed-count **Delete** button of the U1.4
dialog ran `onConfirm` and removed 11 containers without the count ever being typed; an
AXPress on the disabled overflow item opened a "Delete 0 matching containers?" dialog.
Any AX client — VoiceOver, Accessibility Inspector, macOS automation — can do this.

**Rule:** for any destructive or irreversible handler, `enabled` is presentation only.
Re-check the precondition inside the handler (`onClick = { if (precondition) act() }`, as
U1.4 now does in `DeleteAllContainersDialog.kt` and `ContainersScreen.kt`) and/or make the
ViewModel operation re-entrancy-guarded (`if (_isDeletingSelected.value) return`).

**Apply to (highest impact first):**
1. Settings › Engine **Restart / Start / Stop** (`SettingsScreen.kt:703-745`,
   `!isBusy && quotasValid`): a restart kills every running container and the quota
   validation is bypassed; `EngineManager.startEngine` has no busy guard.
2. *Delete N selected* / *Stop N selected* on all four list screens
   (`ContainersScreen.kt:439,464,518,542`, `ImagesScreen.kt:200`, `VolumesScreen.kt:249`,
   `NetworksScreen.kt:234`) and the ViewModel methods behind them
   (`ContainersScreenViewModel.{stopSelectedContainers,deleteSelectedContainers,deleteAllContainers}`,
   `ListScreenViewModel.deleteSelected`): no re-entrancy guard → duplicate `rm -f`,
   spurious "Failed to delete" errors, stale selection.
3. System-network Delete (`NetworksScreen.kt:798`, `NetworkContextMenu.kt:64`) and
   `CompactCheckbox` on system / pending-delete rows: the daemon refuses today; make the
   boundary ours.
4. Pane actions, Prune / Stop-all: a null-return or confirm dialog already stands behind
   them — low priority.

**Upstream:** report to JetBrains (compose-multiplatform) — the bridge should refuse
actions on nodes carrying `Disabled`, as the Android delegate does.

**Acceptance:** an AXPress on any disabled destructive control is a no-op (verify with
Accessibility Inspector); the ViewModel methods above ignore re-entrant calls.
