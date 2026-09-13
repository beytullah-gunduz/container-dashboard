# UX-6 — Dashboard & Monitoring

Heuristics: *Visibility of system status*, *Aesthetic and minimalist design*, *Flexibility
and efficiency of use*.
Base path: `composeApp/src/commonMain/kotlin/com/containerdashboard/ui/screens/`.

---

## U6.1 — The Dashboard shows totals, not anything to act on

- **Severity:** P1 · **Effort:** M (owner decision on purpose)
- **File:** `DashboardScreen.kt:70-420`; "Recent Containers" = `containers.take(5)` (`:400`)
  in API order, rows not clickable (`RecentContainerItem`, `:436+`)

On the audit dataset the landing screen showed: resource counts, running/paused/stopped
totals, engine version, and five *stopped* batch containers from over a day earlier (the
five most recently created — none of the running ones appear). The lower 40% of the screen is empty. Nothing tells the user what needs
attention: containers that exited non-zero, restart loops, dangling images, reclaimable
disk (`docker system df`), a paused container someone forgot.

**Fix:** keep the four stat cards; replace "Recent Containers" with an *Attention*
list (exited ≠ 0 in the last 24 h, restarting, paused, unhealthy) and a *Disk* card
(images / containers / volumes reclaimable, with a Prune link); make rows navigate to
the container with its logs open.

**Acceptance:** a crashed container is visible from the Dashboard without opening the
list.

---

## U6.2 — Monitoring history is discarded when you leave, and every visit starts at "0 running"

- **Severity:** P2 · **Effort:** S
- **Files:** `viewmodel/MonitoringScreenViewModel.kt:188-210`
  (`runningFold(UsageHistory())` + `stateIn(WhileSubscribed(5_000))`), `MonitoringScreen.kt:158`
  (`"${stats.size} running containers"`), `:170-190` ("Idle")

Because the history fold lives inside a `WhileSubscribed` flow, five seconds off the
screen resets it to empty. Coming back, the subtitle reads "0 running containers" and the
status says "Idle" until the first sample — directly under a sidebar that says Connected
with containers running. The one thing a monitoring tab is for ("what happened while I was on
another screen?") is impossible.

**Fix:** `SharingStarted.Eagerly` (or a long `stopTimeoutMillis`) for the history fold,
sized to the max sample count; subtitle from the containers flow, not the stats flow;
"Collecting…" instead of "Idle" before the first sample.

---

## U6.3 — Radial "Refresh" knob for five discrete values

- **Severity:** P2 · **Effort:** S
- **Files:** `MonitoringScreen.kt:192-212` (`CircularSlider` 1–5 s),
  `components/CircularSlider.kt` (pointer-only, 72 dp)

A 270° arc with a 6 dp thumb, showing "1s" in the middle. Nothing signals it is a
control; it is not keyboard-operable (U4.6); the label "Refresh" above it reads as a
button. It also makes the header ~10 dp taller than every other screen, so the page
title sits lower on Monitoring than elsewhere (observed: title baseline y=40 vs y=30).

**Fix:** a segmented control `1s · 2s · 5s` (or a small dropdown) aligned with the
other screens' header row; same for the tray refresh rate in Settings (already a
segmented row there — be consistent).

---

## U6.4 — Chart scaling and encoding

- **Severity:** P2 · **Effort:** M
- **File:** `MonitoringScreen.kt:977-1200` (`UsageHistoryGraph`, dual-series IO graphs,
  `SemiDonutGauge`), per-container bars `:720+` (`ContainerBarRow`)

- History graphs have no time axis and no indication of the window length; bars fill
  from the right.
- Disk/Network graphs re-scale the Y axis to the current max on every tick, so a single
  spike squashes everything for the rest of the window and the axis labels flicker.
- Per-container memory bars are proportional to **host** memory, so a
  container using a few hundred MB is a 2–3% sliver — every bar looks empty.
- Gauges and bars are green at every value; there are no thresholds.

**Fix:** fixed or slowly-decaying Y max with a "peak" marker; bars relative to the
largest container (or the container's own limit when set); amber ≥ 70 %, red ≥ 90 %;
a "last 60 s" caption.

---

## U6.5 — Dashboard card polish

- **Severity:** P3 · **Effort:** S
- **File:** `DashboardScreen.kt:186` (`subtitle = "${volumes.size} total"` under the
  volume count), `:108-117` (green "Connected to container engine" duplicates the
  sidebar status), `:237-335` (Container Status / System Information cards in a `Row`
  without `IntrinsicSize.Max` — observed 53 dp height mismatch), `components/StatsCard.kt:96`
  (clickable `Card` with no hover/chevron affordance)

Also: the Container Status card counts only `isRunning` / `isPaused` / `isStopped`
(`:256-258`, where stopped = exited|stopped), so *created*, *restarting*, *dead* and
*removing* containers are in the total but in none of the three numbers — the card can
read 2 / 0 / 5 under a "9" tile.

**Fix:** drop the redundant subtitle (or make it "2 anonymous"); remove the green
subtitle; `Modifier.height(IntrinsicSize.Max)` on the row; hover elevation + trailing
chevron on clickable stat cards; a fourth "Other" number (or fold non-running states
into Stopped with a tooltip).
