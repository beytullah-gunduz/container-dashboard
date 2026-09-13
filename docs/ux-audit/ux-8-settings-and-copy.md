# UX-8 — Settings & copy

Heuristics: *Visibility of system status*, *Consistency and standards*, *Help users
recognize, diagnose, and recover from errors*.
Base path: `composeApp/src/commonMain/kotlin/com/containerdashboard/`.

---

## U8.1 — Engine host is persisted on every keystroke; "Save & Reconnect" doesn't save

- **Severity:** P1 · **Effort:** S
- **Files:** `ui/screens/SettingsScreen.kt:131` (`onValueChange = { viewModel.setEngineHost(it) }`),
  `ui/screens/viewmodel/SettingsScreenViewModel.kt:64-66` (writes `PreferenceRepository`
  immediately), `SettingsScreen.kt:224` ("Save & Reconnect"), `:163-168` (`saveAndReconnect`
  writes the same value again, then reconnects)

Start typing `tcp://loc`, get interrupted, quit → next launch tries to connect to
`tcp://loc` and lands on "Container engine is not available" with no hint why. The
button label promises a save step that has already happened; *Test Connection* tests
whatever is in the field, which is also already persisted. The field is also the only
setting on the page with explicit Save semantics — everything else applies instantly —
so the mental model is mixed.

**Fix:** keep a local draft in the ViewModel; persist only on *Save & Reconnect*; show a
"modified" state (button enabled only when draft ≠ saved); a *Revert* link.

**Acceptance:** quitting mid-edit leaves the previously saved host intact.

---

## U8.2 — "Show System Containers" does nothing

- **Severity:** P1 · **Effort:** S
- **Files:** `ui/screens/SettingsScreen.kt:295-300` (switch),
  `data/repository/PreferenceRepository.kt:169` (`showSystemContainers()` — the only
  readers are the Settings screen and its ViewModel; no list filters on it)

A visible toggle with a subtitle ("Display system containers in the list") and no
consumer. Also undefined: what a "system container" is for Docker (there is no such
concept in the API; Kubernetes/Colima helper containers?).

**Fix:** remove the switch, or implement it with a documented rule (e.g. hide
containers labelled `io.kubernetes.*` / `com.docker.*`) and say so in the subtitle.

---

## U8.3 — Engine Management shows "Stopped" + Start while the status probe is running

- **Severity:** P1 · **Effort:** S
- **Files:** `ui/screens/SettingsScreen.kt:552-566` (status label = `colimaConfig != null ?
  "Running" : "Stopped"`), `:505-760` (`EngineManagementSection`: fields default to
  4 / 8 / 60, primary button flips Start ↔ Restart on the same condition),
  `desktopMain/.../data/engine/EngineManager.kt:67-101` (`colima status` via
  `ProcessBuilder`, up to `TIMEOUT_STATUS_SECONDS`, returns `null` on any failure
  including "binary not on PATH")

Observed in the packaged app: ~3 s after opening Settings the section still read
**"Colima · Stopped"** with a full-width blue **Start** button and CPU/Memory/Disk =
4/8/60, while the sidebar 300 px to the left said **"Connected · Colima"**. On the next
visit (about a minute later) it read "Running", 6/12/60, Stop/Restart. How long the
window lasts was not measured; the probe (`colima status --json`) has a 15 s timeout
(`TIMEOUT_STATUS_SECONDS`), so it can be up to 15 s. The label conflates "we could not
read the config" with "the engine is stopped". Inferred, not observed: if `colima` is
not on the GUI PATH (a common `.app`-from-Finder situation) the probe returns `null`
and the section says "Stopped" for as long as the app runs, while connected. Clicking
Start in that state runs `colima start` against a running VM.

Also from the same expression (`:564`, `|| !isColima`): for any non-Colima engine the
section says **"Running" unconditionally** — no probe exists for Docker Desktop /
OrbStack / Rancher — so it reads "Running" even while the daemon is down.

**Fix:** a tri-state (`Checking…` / `Running` / `Stopped` / `Unavailable — colima not
found`) driven by the probe result, not by config presence; disable the action button
while checking; when the daemon is connected but the probe failed, say "Connected
(status tool unavailable)"; resolve the binary via `/opt/homebrew/bin:/usr/local/bin`
fallbacks.

**Acceptance:** the engine section never contradicts the sidebar; no Start button while
connected.

---

## U8.4 — "Usage aggregation" control is clipped at the right edge

- **Severity:** P2 · **Effort:** S
- **File:** `ui/screens/SettingsScreen.kt:832-880` (`MonitoringAggregationSelector`,
  `SingleChoiceSegmentedButtonRow` in a `Row` with `SpaceBetween`, no `weight`/wrap)

Observed at 1372 pt window: "Engine | Per containe" — the second segment is cut. The
segmented row is unconstrained and the label + icon exceed the remaining width.

**Fix:** put the label column in `weight(1f)` and let the control take intrinsic width,
or stack label above control below ~1000 dp.

---

## U8.5 — Settings information architecture

- **Severity:** P2 · **Effort:** S (owner decision)
- **File:** `ui/screens/SettingsScreen.kt:128-260` ("Log Buffer Size" inside *Container
  Engine*, `:237`), `:294-345` (*Behavior* holds "Tray Stats Refresh Rate" and two
  unrelated toggles), `:848-850` ("Usage aggregation — How CPU and memory are totaled in
  the Monitoring gauges")

Grouping is by where the code lives, not by what the user is doing. Suggested sections:
*Connection* (host, test, save), *Engine* (Colima/Docker Desktop management),
*Appearance* (theme, density), *Logs* (buffer, word wrap, timestamps), *Monitoring*
(refresh rate, aggregation with plain-language options: "Share of host" / "Sum of
containers"), *Tray*, *Safety* (confirm before delete), *Danger Zone*, *About*.

---

## U8.6 — Engine "Restart" is the filled primary

- **Severity:** P2 · **Effort:** S
- **File:** `ui/screens/SettingsScreen.kt:712-733` (Stop outlined, Restart filled)

Restarting the VM drops every running container. The most disruptive action in the
section carries the strongest visual weight; there is no confirmation. Make both
outlined, or confirm Restart with the running-container count.

---

## U8.7 — Microcopy inconsistencies

- **Severity:** P3 · **Effort:** S

| Where | Text | Issue |
|---|---|---|
| `ContainersScreen.kt:604` vs `ContainerRendering.kt:291` | "Delete All" / "Delete all" | casing |
| `ContainersScreen.kt:507`, `DeleteAllContainersDialog.kt:41` | "container(s)" | use plural rules ("1 container" / "3 containers") — a plural helper already exists in `Main.kt:294` for the tray |
| `Screen.kt:66` vs `AppLogsScreen.kt:99` | "App Logs" / "Application Logs" | label mismatch |
| `ContainersScreen.kt:1033` | "Stopped / Other" | "Other" is undefined; it means created/exited/dead/restarting |
| `MonitoringScreen.kt:183` | "Idle" | means "no data yet", reads as "engine idle" |
| `SettingsScreen.kt` Danger Zone | "Each asks for confirmation before running." | true here, false for the pane trash (U1.1) — the sentence sets an expectation the app breaks elsewhere |
| `SettingsScreen.kt:297` | "Display system containers in the list" | "system" undefined (U8.2) |
| `App.kt` disconnected state | "Please start your container engine and the dashboard will connect automatically." | no indication *which* socket/host it is trying; add the host and a Retry |
| `ConfirmActionDialog.kt` | "This action cannot be undone." | shown for every `destructive = true` dialog including *Stop all* (`ContainerRendering.kt:117-131`), which can be undone by starting again |

---

## U8.8 — Locale-dependent number formatting in UI strings

- **Severity:** P3 · **Effort:** S · (extends `docs/audit/follow-ups.md` F3)
- **Files:** `ContainerRendering.kt:210` (`"CPU %.1f%%".format(it)`), `Main.kt:269-270`
  (tray tooltip), `MonitoringScreen.kt` (`"%.1f%%"` labels)

Renders `24,1%` on de_DE/tr_TR systems next to `4.88 GB` from the byte formatter —
mixed separators in one row. Use `Locale.ROOT` (or the shared formatter once F3 lands).
