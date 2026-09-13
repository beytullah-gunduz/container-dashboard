# UX-2 — Containers list: information design

Heuristics: *Match between system and the real world*, *Recognition rather than recall*,
*Flexibility and efficiency of use*, *Aesthetic and minimalist design*.
Base path: `composeApp/src/commonMain/kotlin/com/containerdashboard/ui/screens/`.

Context from the live dataset: a long container list in one compose project, most of them
exited batch jobs with names like `example-job-20260101120000-1a2b3c4d-5e6f-…`.
This is what the list looks like for anyone using Docker for CI-style workloads, and it
exposes every gap below.

---

## U2.1 — Status column discards exit code and uptime

- **Severity:** P1 · **Effort:** S
- **Files:** `ContainerRendering.kt:1052-1054` (`StatusBadge(container.state…)`),
  `ui/components/StatusBadge.kt:22-32` (maps every state to 8 labels);
  `Container.status` (`data/models/Container.kt:19`) is shown only on the Dashboard
  (`DashboardScreen.kt:460`).

Every exited row reads "Stopped". `docker ps -a` distinguishes e.g. `Exited (0) 2 days ago`
from `Exited (137) 5 minutes ago` — the single most important triage signal (did it crash?)
and the age. Neither is in the list, in the tooltip, or in the sort options. The
Dashboard's "Recent Containers" shows it, so the data is already parsed.

**Fix:** render `container.status` as secondary text under the badge (or as the badge
tooltip), tint the badge for non-zero exit codes ("Exited (137)" in warning colour), and
add an *Uptime/Age* sort. Consider a fourth filter chip *Failed*.

**Acceptance:** a crashed container is distinguishable from a finished one without
opening Inspect.

---

## U2.2 — Ports cell shows a random port from an unordered set

- **Severity:** P1 · **Effort:** S
- **Files:** `ContainerRendering.kt:1059` (expanded), `:695` (compact),
  `ContainersScreen.kt:225` (sort uses the same `firstOrNull()`)

`container.ports.firstOrNull()` on a container with dozens of exposed ports and one
published rendered a different unpublished port on each of five consecutive refreshes —
never the published mapping, the only port a user can connect to. The value
visibly jitters every stats tick. Sorting by Ports is therefore also unstable.

**Fix:** sort ports (published first, then by host port), show the first published
mapping, append "+N" with a tooltip listing all; sort key = same rule.

**Acceptance:** the Ports cell for a container is stable across refreshes and shows a
published mapping when one exists.

---

## U2.3 — Two competing groupings: status sections × compose projects

- **Severity:** P1 · **Effort:** M (design decision required)
- **Files:** `ContainersScreen.kt:114-131` (`groupContainers`), `:854-1037` (Running /
  Stopped / Other sections), `viewmodel/ContainersScreenViewModel.kt` (`expandedRunningProjects`,
  `expandedOtherProjects`)

The same project appears twice — "example-stack · Compose · N" under *Running* and
"example-stack · Compose · M" under *Stopped / Other* — and both groups are collapsed
by default, so the first paint of a long container list is **two rows and 80% empty
space**. Group-level actions ("Stop all", "Delete all") then operate on half a project.
The *All / Running / Stopped* filter chips duplicate what the section headers already do
(U2.8).

**Fix (owner decision):** pick one primary axis. Recommended: group by project, sort
running to the top *within* the group, show a "N running · M stopped" roll-up in the
header, expand by default when there is a single project, and keep the status chips as
the filter. Alternatively drop project grouping into a *Project* column + filter.

**Acceptance:** a project appears once; the default view shows containers, not two
collapsed rows.

---

## U2.4 — Search, filter and sort reset on every navigation

- **Severity:** P1 · **Effort:** S
- **Files:** `ContainersScreen.kt:144-147` (`remember { mutableStateOf }`), versus
  `ImagesScreen.kt` / `VolumesScreen.kt` (kept in their ViewModels); `App.kt:305-312`
  (`AnimatedContent` disposes the screen on route change)

Type a search, open Monitoring, come back → search, filter chips and sort are gone, but
selection (in the ViewModel) and expanded groups (also ViewModel) survive. Images and
Volumes keep their query. Search also matches only `displayName` and `image`
(`:210-211`) — not the short ID, compose service, or port, which are what people paste.

**Fix:** move the three states into `ContainersScreenViewModel` like the other screens;
extend the match to id/service/ports.

**Acceptance:** navigating away and back preserves query/filter/sort; pasting a
12-char ID finds the container.

---

## U2.5 — Name and ID are concatenated before truncation, so the ID is lost first

- **Severity:** P2 · **Effort:** S
- **File:** `ContainerRendering.kt:1034` (`"${displayName} · ${shortId}"`)

Observed: `example-job-20260101120000-1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d · 3f9e1…`
— the only unique, short token is the one that gets cut. Compact mode (`:665-685`) already
renders the ID separately and dimmed; expanded mode should too.

**Fix:** separate `Text` for the ID with `Modifier.weight(1f, fill = false)` on the name,
or middle-ellipsis the name.

---

## U2.6 — No Created / age column, no sort by age

- **Severity:** P2 · **Effort:** S
- **Files:** `ContainersScreen.kt:91-96` (`SortColumn` has NAME/IMAGE/STATUS/PORTS);
  `Container.created` is available.

With dozens of exited job containers the natural question is "which are old enough to prune". Name
sort puts them in timestamp order only because the names happen to embed one.

**Fix:** add *Created* (relative, tooltip absolute) and make it a sort column; default
sort for the Stopped section = newest first.

---

## U2.7 — Restart is hidden, and silently a no-op inside compose groups

- **Severity:** P2 · **Effort:** S
- **Files:** `ContainerRendering.kt:439` (`onRestart: () -> Unit = {}`), `:401-419`
  (`ComposeProjectCard` calls `ContainerRowByMode` without `onRestart`),
  `ContainersScreen.kt:1008`, `:1183` (standalone rows do pass it)

Row actions have no Restart; the compact overflow menu (`:743-830`) has no Restart,
Inspect or Copy ID; the right-click menu has all three. For any container inside a compose
project (i.e. every container in the audit dataset), right-click → Restart does nothing because the default lambda
is empty. Inspect has no visible affordance anywhere — right-click only.

**Fix:** thread `onRestart` through `ComposeProjectCard`; add Restart to the compact menu;
add an *Inspect* (ⓘ) row action or make the name open Inspect and a separate icon open
logs.

**Acceptance:** Restart works from every menu for grouped containers; Inspect is
discoverable without right-click.

---

## U2.8 — Filter chips duplicate the section collapse

- **Severity:** P2 · **Effort:** S · (resolves with U2.3)
- **File:** `ContainersScreen.kt:751-787` (chips), `:854-862` / `:1028-1037` (section
  toggles)

Two controls, one concept: "Stopped" chip hides the Running section; collapsing the
Running section does the same. Keep the chips (they can also carry counts) and drop the
collapsible section headers, or vice-versa.

---

## U2.9 — Bulk actions are asymmetric

- **Severity:** P2 · **Effort:** S
- **File:** `ContainersScreen.kt:317-575`

Selection offers *Stop N* and *Delete N* only. No *Start N*, *Restart N*, *Pause N*.
Selecting a batch of stopped job containers to restart them is impossible; the only bulk path
for stopped containers is delete.

---

## U2.10 — Only the Status column is resizable, with a crosshair cursor

- **Severity:** P3 · **Effort:** S
- **File:** `ContainerRendering.kt:919-963` (`ColumnResizeHandle`, `PointerIcon.Crosshair`)

One 12 dp handle between STATUS and PORTS; NAME/IMAGE are weighted, PORTS is fixed 200 dp.
Users who discover the handle expect the others to resize too. Compose Desktop lacks a
built-in horizontal-resize cursor; a custom `PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR))`
is available on desktop.

---

## U2.11 — Empty-state copy promises actions the app does not have

- **Severity:** P3 · **Effort:** S
- **Files:** `ContainersScreen.kt:845` ("Run a container from the Images tab"),
  `ImagesScreen.kt:288` ("Pull an image or build one")

There is no run, pull or build anywhere in the UI (`pullImage` exists in the repository
interface but is unused). Say what is true: "Containers created with `docker run` or
`docker compose up` will appear here."

---

## U2.12 — Section expand animation briefly draws the group card over the header

- **Severity:** P3 · **Effort:** S
- **File:** `ContainersScreen.kt:928-934`, `:1105-1111` (`animateItem(placementSpec = tween(500))` +
  inner `AnimatedVisibility`)

Observed on expanding *Running*: for ~400 ms the "Stopped / Other" header and its table
header were overlapped by the moving compose card. Two animations (item placement and
visibility) on the same rows fight. Drop the placement animation or the inner
`AnimatedVisibility`.
