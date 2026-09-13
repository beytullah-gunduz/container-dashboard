# UX / GUI Audit — 2026-09-12

Expert-review UX audit of the `container-dashboard` desktop app (Compose Multiplatform,
macOS build). Complements the engineering audit in [`docs/audit/`](../audit/README.md),
which covered races, lifecycle, security and performance — **not** usability. Nothing in
that audit is repeated here unless it has a user-visible consequence.

**Method.** (1) Full read of the UI layer (`composeApp/src/commonMain/.../ui`, `App.kt`,
`Main.kt`, window chrome) against Nielsen's 10 heuristics, WCAG 2.2 AA, and platform
conventions (macOS HIG, Material 3). (2) Live walkthrough of every screen in the packaged
`.app` (`createDistributable`, commit `7640a3f`) against the local Colima engine with a
realistic dataset: **a long container list, mostly exited batch jobs, in one compose
project, plus images, volumes and networks** — long job-style container names, a container
exposing dozens of ports. Dark and light themes. Window 1372×888 pt. (3) Contrast ratios computed from the
palette in `Theme.kt`.

**Not exercised** (see "Limits" below): destructive actions (they would have hit the
user's real containers), keyboard-only flows (the app exposes no accessibility tree, so
they could not be driven from the automation tools — verified from source instead), the
disconnected-engine screens, and Windows/Linux window chrome.

## Severity scale

| | Meaning |
|---|---|
| **P0** | Can destroy data without consent, hides failures, or excludes a class of users |
| **P1** | Misleads the user or materially slows the primary task (managing containers) |
| **P2** | Friction, inconsistency, missing expected capability |
| **P3** | Polish |

## Summary

| Severity | Count |
|---|---|
| P0 | 3 |
| P1 | 17 |
| P2 | 29 |
| P3 | 12 |

61 unique stories. Two stories are cross-referenced in a second epic (U5.1 → U1.1,
U4.3 → U1.7) and counted once.

## Fix these first

1. **[U1.1](ux-1-destructive-actions-and-feedback.md)** The trash icon in the logs/console
   pane header **force-deletes the container with no confirmation**, ignores the
   *Confirm Before Delete* setting, and sits 27 px from the Close button. In group mode it
   deletes every container in the compose project.
2. **[U1.2](ux-1-destructive-actions-and-feedback.md)** `AppViewModel.error` is never
   rendered anywhere. Pause / resume / restart / delete from the pane, group-log fetch,
   file download and log export **fail silently**. There is no app-wide notification
   channel at all (no snackbar/toast), so palette actions fired from another screen also
   have nowhere to report.
3. **[U4.1](ux-4-accessibility-and-theming.md)** Accessibility is switched off globally
   (`compose.accessibility.enable=false`, `Main.kt:153`). VoiceOver, Switch Control and any
   automation see an empty window; every `contentDescription` in the codebase is dead code.
4. **[U1.4](ux-1-destructive-actions-and-feedback.md)** *Delete All* is the only
   permanent primary button on the Containers screen — top-right, filled red, and it
   deletes every container including running ones, regardless of the active filter.
5. **[U2.1](ux-2-containers-list.md)** The list never shows exit code or uptime: every
   exited row reads "Stopped" whether it exited 0 or 137. `docker ps` shows this by default.
6. **[U2.2](ux-2-containers-list.md)** The Ports cell shows an arbitrary port from an
   unordered set — for a container exposing dozens of ports it showed a different unpublished
   port on each refresh and never the one published port.
7. **[U3.1](ux-3-shell-navigation-keyboard.md)** Typing `?` in **any** text field
   (search, log filter, engine host, palette) opens the shortcuts dialog and eats the
   character — the root key handler intercepts Shift+/.
8. **[U8.3](ux-8-settings-and-copy.md)** Settings › Engine Management shows
   "Colima · Stopped" with a *Start* button while the status probe runs (observed ~3 s
   in, probe timeout 15 s), directly under a sidebar that says "Connected · Colima" —
   and "Running" unconditionally for every non-Colima engine.
9. **[U7.1](ux-7-images-volumes-networks.md)** Networks › CONTAINERS column is always 0
   (the list API doesn't populate it) — it read 0 for a network with 7 attached containers.
10. **[U8.1](ux-8-settings-and-copy.md)** The engine host is written to preferences
    on **every keystroke**; a half-typed host survives quit and breaks the next launch.
    "Save & Reconnect" only reconnects — it doesn't save anything.

## Epics

| Epic | Title | Stories | P0 | P1 | Theme |
|---|---|---|---|---|---|
| [UX-1](ux-1-destructive-actions-and-feedback.md) | Destructive actions & feedback | 10 | 2 | 3 | Confirmation policy, silent failures, false success, dangerous defaults |
| [UX-2](ux-2-containers-list.md) | Containers list — information design | 12 | 0 | 4 | Missing exit codes/age, jittery ports, double grouping, lost filter state |
| [UX-3](ux-3-shell-navigation-keyboard.md) | Shell, navigation & keyboard | 9 | 0 | 2 | `?` intercept, palette can't scroll, pane width, window chrome, dead ⌘R |
| [UX-4](ux-4-accessibility-and-theming.md) | Accessibility & theming | 6 (+1 xref) | 1 | 1 | A11y disabled, light-theme contrast, hit targets, focus |
| [UX-5](ux-5-detail-pane.md) | Detail pane — Logs / Files / Console | 5 (+1 xref) | 0 | 1 | Global filter state, blank pane, no path, console cleanup |
| [UX-6](ux-6-dashboard-monitoring.md) | Dashboard & Monitoring | 5 | 0 | 1 | Totals-only dashboard, history lost, radial slider, chart scaling |
| [UX-7](ux-7-images-volumes-networks.md) | Images / Volumes / Networks | 6 | 0 | 2 | Always-zero column, no in-use signal, VM paths, false link styling |
| [UX-8](ux-8-settings-and-copy.md) | Settings & copy | 8 | 0 | 3 | Keystroke persistence, dead toggle, loading-as-stopped, clipped control |

## What already works well

Being candid cuts both ways — these are genuinely good and should be protected while
fixing the above:

- Responsive layout: compact rows/cards below 700 dp, icon-only toolbar below 900 dp, and
  a logs pane that flips right/bottom automatically.
- Skeleton loaders and real empty states with a primary action ("Clear search", "Show all").
- Persistence of the right things: window bounds, pane sizes, column widths, theme, route.
- Confirmation dialogs default Enter to *Cancel*; the "cannot be undone" line is present.
- Command palette + ⌘1–7 + ⌘K + ⌘F exist and are advertised in the sidebar.
- Compose-project grouping with group-level actions and CPU/Mem roll-up in the header.
- Connection status is always visible; the connecting state is calm, not alarming.
- Danger Zone is visually quarantined and every action in it confirms.
- Theme previews in Settings; light theme is fully implemented, not an afterthought.
- Right-click context menus on every resource row; column resize; tooltips on almost all
  icon-only controls.

## Dispatch guidance

- Stories use the same format as `docs/audit/` (severity, effort, files, evidence,
  recommendation, acceptance) and the ID convention `U<epic>.<n>`.
- **Order:** UX-1 first (U1.1, U1.2 are one-file fixes with outsized risk reduction), then
  U4.1 (needs an experiment: re-enable accessibility on Compose 1.12 and see whether the
  original NPE still reproduces), then UX-2/UX-8 P1s. UX-3 U3.1/U3.2 are small.
- **Design decisions that need the owner, not an agent:** U1.4 (where *Delete All* goes),
  U2.3 (status sections vs project grouping as the primary axis), U3.5 (App Logs in the
  main nav), U6.1 (what the Dashboard is for), U8.5 (Settings information architecture).
- **File-conflict clusters:** U1.1/U5.x → `ConsolePane.kt`, `App.kt`, `AppViewModel.kt`;
  U1.3/U1.6/U1.10/U2.2/U2.5/U2.7 → `ContainerRendering.kt`; U8.x → `SettingsScreen.kt`.
- Re-run the live walkthrough on the **packaged** app, not `gradlew run` — U8.3 and the
  console behaviour differ between the two (PATH, ProGuard).

## Review log — 2026-09-13

Second pass re-verified every story against source. Changes made:

- **U1.6** P1 → P2: the default *Confirm Before Delete* does interpose a dialog; the
  original text implied an immediate delete.
- **U1.9** P2 → P3 and reworded: Compose Desktop does Tab-traverse dialog buttons, so a
  keyboard confirm path exists (Tab → Space); the real defect is that Enter cancels even
  with the Delete button focused.
- **U3.1** narrowed: fields inside `Dialog`/`AlertDialog` layers (palette, Create
  dialogs) are probably not affected; they were listed without verification.
- **U4.2** dropped the Images tag column from the failing-contrast list — it uses
  `colorScheme.primary`, which the light scheme does swap.
- **U8.3** "~10 s" was an estimate presented as a measurement; replaced with what was
  observed (still "Stopped" ~3 s in; resolved by the next visit) plus the 15 s probe
  timeout, and the PATH scenario is now marked as inferred. Added the unconditional
  "Running" for non-Colima engines.
- **U6.5** added the Container Status count gap (created/restarting/dead excluded).
- **U3.9** new (P3): no manual refresh on list screens; the `onRefresh` plumbing in
  `AppShortcutScope` has no key bound to it.

Everything else stood up. The three P0s and the "fix these first" list are unchanged.

## Limits of this audit

- No destructive action was executed against the live engine; U1.x confirmation flows are
  verified from source, not by clicking through.
- Keyboard flows (U3.1, U3.2, U1.9, U4.6) are verified from source. Because the app exposes
  no accessibility tree (U4.1), background automation could not type into it.
- Windows/Linux chrome (U3.4) is from source only.
- Screenshots from the walkthrough were viewed live and are not committed; every
  observation cites the code path that produces it.
- One side effect of the walkthrough: opening the Console tab on
  `example-stack-postgres-1` left a `/bin/sh` exec process running in that container
  after the pane was closed (see U5.5). It is idle and harmless; it goes away on
  container restart or `docker exec <id> kill <pid>`.
