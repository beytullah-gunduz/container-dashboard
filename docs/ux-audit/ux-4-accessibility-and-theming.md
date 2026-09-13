# UX-4 — Accessibility & theming

Standards: WCAG 2.2 AA (1.4.3 contrast, 1.4.1 use of colour, 2.1.1 keyboard, 2.4.7 focus
visible, 2.5.8 target size), macOS accessibility (VoiceOver, Switch Control).
Base path: `composeApp/src/`.

Contrast figures below are computed from the literal values in
`commonMain/.../ui/theme/Theme.kt` (WCAG relative-luminance formula). AA requires
4.5:1 for text under ~18 pt, 3:1 for large text and UI components.

---

## U4.1 — Accessibility is disabled for the whole app

- **Severity:** P0 · **Effort:** M (investigation) 
- **File:** `desktopMain/.../Main.kt:152-153`
  (`System.setProperty("compose.accessibility.enable", "false")` — "Workaround for macOS
  accessibility crash (NPE in SemanticsOwnerAccessibility.onNodeRemoved)")

The window exposes **zero** accessibility elements (confirmed with the macOS AX API
during the walkthrough: the only nodes are the two Swing scrollbars of the JediTerm
console). Consequences: VoiceOver reads nothing; Switch Control and Voice Control cannot
target anything; UI-automation and test tooling cannot drive the app; every
`contentDescription`, `semantics { }` and `Role` in the codebase (dozens, several added by
earlier "A11y fix" commits) is unreachable.

The workaround predates the Compose Multiplatform 1.12.0 upgrade (`7640a3f`).

**Fix:** (1) remove the property, run the packaged app with VoiceOver on, and reproduce
the original crash path (rapid list churn — open/close the logs pane, filter the
container list); (2) if it still crashes, report upstream with a repro and scope the
workaround to the crashing composable instead of the process; (3) add a CI smoke test
that asserts the AX tree is non-empty.

**Acceptance:** VoiceOver announces sidebar items, rows and buttons; `contentDescription`
values are audible.

---

## U4.2 — Status colours are theme-independent and fail contrast in light theme

- **Severity:** P1 · **Effort:** S
- **Files:** `Theme.kt:17-39` (`AppColors.Running/Stopped/Paused/Warning/AccentBlue`),
  used directly for text in `StatusBadge.kt`, `DashboardScreen.kt:108-117` ("Connected to
  container engine"), `MonitoringScreen.kt:170-190` ("Live"), `ContainerRendering.kt:695-700`
  (port text), `LogsTabContent.kt` ("Live"), sidebar selection strip. (The Images tag
  column was listed in an earlier draft in error — it uses `colorScheme.primary`, which
  the light scheme does swap.)

| Colour | on white | on `#F5F5F5` | on dark `#252526` |
|---|---|---|---|
| Running `#4CAF50` | **2.8:1** | **2.5:1** | 5.5:1 |
| Stopped `#EF5350` | **3.5:1** | **3.2:1** | 4.4:1 |
| Paused `#FFCA3A` | **1.5:1** | **1.4:1** | 10.0:1 |
| Warning `#FF9800` | **2.2:1** | **2.0:1** | 7.1:1 |
| AccentBlue `#0DB7ED` | **2.3:1** | **2.1:1** | 6.6:1 |
| TextSecondary `#9E9E9E` | **2.7:1** | **2.5:1** | 5.7:1 |

The light scheme correctly swaps `primary` to `AccentBlueDark` (5.0:1), and
`ExtendedColors` already has per-theme `warningSurface`/`successSurface` — the status
palette just never went through the same treatment. Note `TextSecondary` is not in the
light scheme (it uses `#424242`, 10:1) but is referenced directly in a few places.

**Fix:** add `statusRunning/Stopped/Paused/Warning/accent` to `ExtendedColors` with
light-theme values around `#2E7D32`, `#C62828`, `#B26A00`, `#E65100`, `#0277BD`; replace
direct `AppColors.*` reads in text/icon tints with `AppTheme.extended.*`.

**Acceptance:** every status-coloured text ≥ 4.5:1 in both themes; badges keep their
tinted background.

---

## U4.3 — Dark-theme destructive buttons at 1.7:1

- **Severity:** P1 · **Effort:** S · See **U1.7** (same fix). Listed here for the
  contrast inventory.

---

## U4.4 — Hit targets well below desktop minimums

- **Severity:** P2 · **Effort:** S
- **Files:** `ui/components/CompactCheckbox.kt:47` (14 dp box, 14 dp clickable),
  `ContainerRendering.kt:1097`, `:1120`, `:1152` (24 dp `IconButton`, 14 dp glyph),
  `LogsTabContent.kt:374-410` (20 dp buttons), `WindowChrome.desktop.kt:199-224` (12 dp
  traffic lights), `SearchBar.kt` compact clear button 16 dp

WCAG 2.5.8 asks for 24×24 CSS px with spacing; desktop guidance (HIG) is ≥ 28 pt for
mouse targets. The checkbox is the worst: 14 dp visual *and* 14 dp interactive. Rows are
30 dp tall so there is vertical room for a 24–28 dp target without changing the look.

**Fix:** keep the glyph sizes, wrap in `Modifier.size(28.dp)` with the visual centred
(the app does not set `LocalMinimumInteractiveComponentSize`, so these are raw sizes — set it per component
instead); traffic lights: enlarge the hoverable row hit area to 20 dp per light.

**Acceptance:** no interactive element with a hit area under 24 dp.

---

## U4.5 — Colour is the only carrier of meaning in several places

- **Severity:** P2 · **Effort:** S
- **Files:** `MonitoringScreen.kt` (per-container bars and gauges are green at every
  value — no thresholds, no labels beyond the number), `ContainerRendering.kt:1108-1200`
  (Pause vs Stop vs Delete distinguished by yellow/red/red at 14 dp),
  `ComposeProjectHeader` (`ContainerRendering.kt:244-300`: same), `ui/components/StatusBadge.kt:86`
  (`StatusDot` colour-only; the Dashboard already replaced it with `StatusBadge`).

**Fix:** tooltips on all row icons (U1.10); stop/delete glyphs with distinct shapes and
neutral colour for reversible actions; gauge thresholds with a warning band and a text
state ("High").

---

## U4.6 — Custom controls are not keyboard-operable and have no focus state

- **Severity:** P2 · **Effort:** M
- **Files:** `ui/components/CompactCheckbox.kt` (plain `clickable`, no `Role.Checkbox`,
  no `toggleable`), `ui/components/CircularSlider.kt` (pointer-only, no
  `Role.Slider`/`progressSemantics`, no arrow keys), `ContainerRendering.kt:919-963`
  (`ColumnResizeHandle`), `ui/components/ThreePaneScaffold.kt:252-354`
  (`ResizableDivider`), `SectionHeader.kt`, `ComposeProjectHeader` (clickable rows without
  `Role.Button`)

Even once U4.1 is fixed, these controls will announce as generic groups and cannot be
reached or operated from the keyboard.

**Fix:** `Modifier.toggleable(role = Role.Checkbox)`, `focusable()` + `onKeyEvent` for
Space/Enter, `progressSemantics` + ←/→ on the slider (or replace it — U6.3), focus ring
on dividers with ←/→ resize.

---

## U4.7 — Type sizes below comfortable desktop minimums, no density setting

- **Severity:** P3 · **Effort:** M
- **Files:** `ContainerRendering.kt:1034-1049` (row primary text `bodySmall` = 12 sp),
  ports/IDs `labelSmall` = 11 sp monospace; `Theme.kt` uses default `Typography()`.

Primary content at 12 sp and secondary at 11 sp is IDE-dense; there is no
comfortable/compact toggle and no respect for the OS text-size setting. Users with
mild low vision have only the OS-level zoom.

**Fix:** a Density setting (Compact 12/11, Default 13/12, Comfortable 14/13) mapped
through `Typography`; make 13 sp the default.
