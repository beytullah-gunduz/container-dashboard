# Plan — port the Retro style from kubekubedashdash (kkdd)

Plan base: `feature/retro-theme-container-dashboard-826a2b`, branched from `main` at `11020c4`.
Source of truth for the look: kkdd `main` at `53762bea`.
Owner decision (2026-10-23): D1–D8 approved as written; run WS1–WS3 through the pipeline in inline
mode. The CRT effects (WS4) are a later, separate run.

Path shorthands:

- `<WORKTREE>` = this worktree's root. `<KKDD>` = the kkdd checkout. The real paths are given in
  each implementer prompt, not here.
- `K/` = `<KKDD>/composeApp/src/desktopMain/kotlin/com/kubekubedashdash/`
- `KT/` = `<KKDD>/composeApp/src/desktopTest/kotlin/com/kubekubedashdash/`
- `C/` = `composeApp/src/commonMain/kotlin/com/containerdashboard/` (this repo)
- `D/` = `composeApp/src/desktopMain/kotlin/com/containerdashboard/` (this repo)
- `T/` = `composeApp/src/desktopTest/kotlin/com/containerdashboard/` (this repo)

## What "Retro" is in kkdd

Retro is a **style**, a third setting next to the mode (light/dark/system) and the palette. The mode
still picks light or dark. The style controls fonts, corners and motion, and brings its own palette.

- **Model.** `enum class ThemeStyle { DEFAULT, RETRO }` is at `K/Theme.kt:67`. Every Retro branch
  reads `ThemeManager.isRetro` (`K/Theme.kt:117`), a global `object` backed by `mutableStateOf`
  (`K/Theme.kt:100-227`). The value is synced from DataStore key `"theme_style"`.
- **Palette.**
  - `RetroDarkColors` (`K/theme/BuiltInPalettes.kt:84-115`) is navy, with a cyan primary and a
    yellow accent.
  - `RetroLightColors` (`:121-152`) is a "paper terminal" palette: cream, with teal and plum.
  - Both have hand-written Material 3 schemes: `RetroDarkColorScheme` (`:158-186`) and
    `RetroLightColorScheme` (`:189-217`).
- **Fonts.** Departure Mono is the reading face and Sixtyfour the pixel chrome face. Both are SIL
  OFL 1.1 and live in `<KKDD>/composeApp/src/desktopMain/composeResources/font/`.
  - `kdTypography()` (`K/Theme.kt:482-520`) puts Departure on every slot except
    `headlineLarge`/`headlineMedium`, which get Sixtyfour.
  - `TextStyle.retroChrome(size)` (`K/Theme.kt:376-391`) gives chrome the pixel face.
  - `String.retroCaps()` (`K/Theme.kt:596`) upper-cases chrome.
- **Shapes.**
  - `RetroShapes` makes every M3 slot square (`K/Theme.kt:533-542`).
  - `Dp.kdCorner` (`:549`), `kdCorners` (`:552`), `kdRoundShape` (`:586`) and `kdStrokeCap`
    (`:593`) square the hand-written shapes.
- **Component chrome.** About 700 lines of `isRetro` branches:
  - a stepped busy ring (`K/ui/components/BusyIndicator.kt`)
  - a segmented gauge (`K/ui/components/Charts.kt:125-175`)
  - a stepped skeleton (`K/ui/components/ResourceTable.kt:880-905`)
  - pixel-caps headers, among others.
- **CRT effects** (`K/ui/crt/`, 879 lines). Out of scope for this run (D7).
- **Tests.** About 3,800 lines. The ones mirrored here are `KT/RetroPaletteTest.kt`,
  `KT/ui/RetroTypographyTest.kt` and `KT/ui/RetroShapeGuardTest.kt`.

## What container-dashboard has today

- `C/ui/theme/Theme.kt` (152 lines):
  - `AppColors` constants (`:18-52`)
  - `ExtendedColors` with 5 fields, provided through `LocalAppColors` (`:54-80`)
  - `ThemeMode { AUTO, DARK, LIGHT }` (`:82-86`)
  - `AppTheme.extended` (`:88-93`), a `@Composable @ReadOnlyComposable` getter
  - `monospaceMedium`/`monospaceSmall` (`:95-99`)
  - two M3 schemes (`:101-135`)
  - `ContainerDashboardTheme(darkTheme)` (`:137-152`), which uses the default `Typography()` and
    no `shapes`
- **Mode wiring.**
  - DataStore key `"theme_mode"`: `C/data/repository/PreferenceRepository.kt:73`, flow at
    `:153-170`, setter at `:172-174`.
  - Exposed as `AppViewModel.themeMode` (`C/ui/screens/viewmodel/AppViewModel.kt:121-124`).
  - Resolved at `C/App.kt:100-107` and applied at `C/App.kt:120`.
  - The About window has its own root: `ContainerDashboardTheme(darkTheme = true)` at
    `D/Main.kt:373`.
- **Settings.** `ThemeModeSelector` (`C/ui/screens/SettingsScreen.kt:838-878`) shows
  `ThemePreviewCard`s (`C/ui/screens/ThemePreviewCard.kt`; swatch sets
  `DarkPreviewColors`/`LightPreviewColors` at `:51-69`).
- **Tokens.** `C/ui/theme/DesignTokens.kt` holds `Spacing`, `Radius` (3/4/8/12 dp) and `IconSize`.
- **Generated resources.** The `Res` class is in package
  `com.dockerdashboard.composeapp.generated.resources` (see the imports of
  `C/ui/components/SectionHeader.kt:23-25`).

### What would stop Retro from showing everywhere (measured on `11020c4`)

| Leak | Count | Effect under Retro if left alone |
|---|---|---|
| `AppColors.*` read outside `Theme.kt` | 102 refs / 14 files | Status colours stay Material green and red; accents stay Docker blue. |
| `RoundedCornerShape(` | 84 refs / 25 files | Corners stay round. Of these, 65 are `RoundedCornerShape(Radius.X)`. |
| `CircleShape` | 8 refs / 4 files (not counting imports) | Dots stay round. |
| `FontFamily.Monospace` | 38 refs / 9 files outside `ui/theme/` | IDs and logs stay in the system mono font. |
| `CircularProgressIndicator(` | 26 calls / 14 files | Smooth M3 spinners stay. All are indeterminate (no `progress =`). |
| JediTerm default style | `D/terminal/JediTermConsole.kt:186-197` | The console stays `#1A1A1A`/`#D4D4D4`. |

`MaterialTheme.colorScheme` already has 476 uses, so most surfaces follow a new scheme unchanged.

## Design decisions (approved 2026-10-23 — decisions, not options)

- **D1.** `ThemeStyle { DEFAULT, RETRO }` is a separate setting from `ThemeMode`.
  - It is stored under DataStore key `"theme_style"`. A missing or unknown value means `DEFAULT`,
    so existing users see no change.
  - Retro works in AUTO, DARK and LIGHT: dark uses the `RetroDark*` values, light the `RetroLight*`
    values.
- **D2.** Style state lives in a global `object ThemeStyleState` backed by `mutableStateOf`, as in
  kkdd; it is not a CompositionLocal.
  - Reason: shape helpers can then read it from non-composable code (`Dp.appCorner` etc.).
  - The mode keeps its ViewModel → `App.kt` path. Only the style is global.
  - Tests that flip it reset it in `@AfterTest`.
- **D3.** Only Retro's own palette is ported. kkdd's other palettes and its CVD status colours are
  out of scope.
- **D4.** Tokens are migrated before Retro is added (WS1), with **no visual change** in Default dark
  or Default light.
- **D5.** Retro colours map onto this app's roles as in the WS2 table.
  - Retro-light `paused` = `775E03`. It measures 5.03:1 on `EFE7D2`, 5.55:1 on `F8F2E3` and
    4.63:1 on `E8DEC6`.
- **D6.** The fonts go in `composeApp/src/commonMain/composeResources/font/`, with their OFL texts.
  README gets a credits line. Default keeps the system fonts.
- **D7.** No CRT effect in this run.
- **D8.** A source-scanning guard test keeps the leaks closed after WS1.

## Global constraints (paste verbatim into every implementer prompt)

```
Working directory (git worktree): <WORKTREE>. Edit only files under it. Never touch the main
checkout or any other worktree. Branch: feature/retro-theme-container-dashboard-826a2b.
kkdd (read-only reference, NEVER edit it): <KKDD>.

Build and test commands — always use exactly these, from <WORKTREE>:
  JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :composeApp:compileKotlinDesktop
  JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :composeApp:desktopTest
  JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew spotlessApply
  JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew spotlessCheck
Plain ./gradlew fails on the system Java 25. Run spotless in its OWN invocation, never chained
with compile/test in one gradlew call (a configuration-cache flake). Run spotlessApply before
reporting, then spotlessCheck must pass.

Do NOT commit, push, merge, create branches or use `git stash`. The orchestrator commits.
Do NOT launch the app (no run/hotRun tasks). The orchestrator does the live smoke.

Kotlin Multiplatform: code in commonMain must not use JVM-only APIs (java.*, javax.*).
Match the surrounding code: comment density, naming, idiom. ktlint (via spotless) is enforced.

This repository is PUBLIC. Never write a real account email, org name, host name, internal URL,
IP, token, real container/project names, or an absolute path containing a username into code,
tests, comments, fixtures or log strings. Use obviously fake values only (example-app,
dev@example.com).
NO TEST MAY READ OR WRITE REAL USER STATE. The desktopTest task already points the DataStore at
build/test-data through the containerdashboard.dataDir system property; keep it that way. Never
read or write ~/Library, ~/.docker, ~/.colima or the app's real settings.

Report at the end: files changed, every deviation from the workstream text and why, the exact
test totals (from build/test-results/desktopTest/*.xml, not the console summary), and anything you
could not do.
```

## Workstreams

They run in order, WS1 → WS2 → WS3. Each one owns every file it touches until it is committed.

### WS1 — Make tokens style-aware (no visual change in Default)

**Non-goals.**
- Do not change any colour value, radius or font as rendered in Default.
- Do not add Retro colours, fonts or settings.
- Do not touch `D/terminal/JediTermConsole.kt:236-250` (`terminalErrorComponent`, Swing colours).
- Do not touch `AppIconPainter.kt` or `AppIconImage.kt`.
- Do not change `Color(0x…)` literals outside `ui/theme/` or `ThemePreviewCard.kt`.

**1.1 Style state** (new file `C/ui/theme/ThemeStyle.kt`):

```kotlin
package com.containerdashboard.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Orthogonal to [ThemeMode]: the mode picks light or dark, the style picks the look. */
enum class ThemeStyle { DEFAULT, RETRO }

/**
 * The active style, global so non-composable token helpers ([appCorner], [appRoundShape]) can read
 * it. Snapshot state: reads during composition or drawing update when it changes.
 */
object ThemeStyleState {
    var style: ThemeStyle by mutableStateOf(ThemeStyle.DEFAULT)

    val isRetro: Boolean get() = style == ThemeStyle.RETRO
}
```

**1.2 Shape tokens.** Add to `C/ui/theme/DesignTokens.kt`, with the imports they need:

```kotlin
/** A rounded shape of this radius; square in Retro. */
val Dp.appCorner: RoundedCornerShape
    get() = RoundedCornerShape(if (ThemeStyleState.isRetro) 0.dp else this)

/** [appCorner] with a radius per corner. */
fun appCorners(
    topStart: Dp = 0.dp,
    topEnd: Dp = 0.dp,
    bottomEnd: Dp = 0.dp,
    bottomStart: Dp = 0.dp,
): RoundedCornerShape =
    if (ThemeStyleState.isRetro) {
        RoundedCornerShape(0.dp)
    } else {
        RoundedCornerShape(topStart = topStart, topEnd = topEnd, bottomEnd = bottomEnd, bottomStart = bottomStart)
    }

/** A circle; a square in Retro. */
val appRoundShape: CornerBasedShape
    get() = if (ThemeStyleState.isRetro) RoundedCornerShape(0.dp) else CircleShape

/** Line-end cap for arcs and strokes: round in Default, flat in Retro. */
val appStrokeCap: StrokeCap
    get() = if (ThemeStyleState.isRetro) StrokeCap.Butt else StrokeCap.Round
```

**1.3 Shape migration**, in every file under `C/` and `D/` except `ui/theme/` and
`C/ui/screens/ThemePreviewCard.kt`. The preview card's mockup shapes stay literal so the thumbnails
stay truthful; WS2 2.8 gives it a `squared` switch.

| Old | New |
|---|---|
| `RoundedCornerShape(Radius.X)` | `Radius.X.appCorner` |
| `RoundedCornerShape(N.dp)` | `N.dp.appCorner` |
| `RoundedCornerShape(if (compact) 6.dp else Radius.md)` (`C/ui/components/SearchBar.kt:54`) | `(if (compact) 6.dp else Radius.md).appCorner` |
| `RoundedCornerShape(topStart = …, …)` (named corners, e.g. `C/ui/components/Sidebar.kt:204-209`) | `appCorners(topStart = …, …)` with the same arguments |
| `CircleShape` and `androidx.compose.foundation.shape.CircleShape` | `appRoundShape` |

- Remove imports that become unused.
- `SemiDonutGauge.kt:46,55` and `CircularSlider.kt:111,122`: change `cap = StrokeCap.Round` to
  `cap = appStrokeCap`.
- `CircularSlider.kt:140,146` (`drawCircle`) stays as is.
- `MonitoringScreen.kt` has three `drawRoundRect` calls, at `:1088`, `:1247` and `:1261`. Two of
  them write the radius fully qualified and split across lines
  (`androidx.compose.ui.geometry.CornerRadius(2f, 2f)`).
  - Import `androidx.compose.ui.geometry.CornerRadius` and
    `com.containerdashboard.ui.theme.ThemeStyleState`.
  - Write all three as
    `cornerRadius = if (ThemeStyleState.isRetro) CornerRadius.Zero else CornerRadius(2f, 2f)`.
  - These are draw lambdas; reading snapshot state there is fine.
- Anywhere a shape token would be evaluated inside `remember { }` or stored in a file-level `val`,
  move it out so it is read at composition time. Today there is no file-level shape `val`; keep it
  that way.

**1.4 Colour roles.** Extend `ExtendedColors` (`C/ui/theme/Theme.kt:54-60`) with these fields, after
the existing ones. In both `DarkExtendedColors` and `LightExtendedColors`, each new field gets the
current constant, so rendering does not change.

| New field | Default dark and light value (today's constant) |
|---|---|
| `running` | `AppColors.Running` (`4CAF50`) |
| `stopped` | `AppColors.Stopped` (`EF5350`) |
| `paused` | `AppColors.Paused` (`FFCA3A`) |
| `warning` | `AppColors.Warning` (`FF9800`) |
| `accent` | `AppColors.AccentBlue` (`0DB7ED`) |
| `accentSecondary` | `AppColors.AccentBlueDark` (`086DD7`) |
| `accentTertiary` | `AppColors.AccentBlueLight` (`4FC3F7`) |
| `textMuted` | `AppColors.TextSecondary` (`9E9E9E`) |
| `consoleForeground` | dark `Color(0xFFD4D4D4)`, light `Color(0xFF1A1A1A)` |
| `consoleBackground` | dark `Color(0xFF1A1A1A)`, light `Color(0xFFF5F5F5)` |

**1.5 Colour migration.** In every file under `C/` and `D/` except `ui/theme/Theme.kt` and
`C/ui/screens/ThemePreviewCard.kt`:

| Old | New |
|---|---|
| `AppColors.Running` | `AppTheme.extended.running` |
| `AppColors.Stopped` | `AppTheme.extended.stopped` |
| `AppColors.Paused` | `AppTheme.extended.paused` |
| `AppColors.Warning` | `AppTheme.extended.warning` |
| `AppColors.AccentBlue` | `AppTheme.extended.accent` |
| `AppColors.AccentBlueDark` | `AppTheme.extended.accentSecondary` |
| `AppColors.AccentBlueLight` | `AppTheme.extended.accentTertiary` |
| `AppColors.TextSecondary` | `AppTheme.extended.textMuted` |

`AppTheme.extended` is `@Composable`. These sites are not in a composable scope and need changes:

- `C/ui/screens/AppLogsScreen.kt:380-388` `levelColor(level)`: add a parameter,
  `levelColor(level: String, ext: ExtendedColors)`. Callers at `:171`, `:288` and `:299` pass
  `AppTheme.extended`.
- `C/ui/screens/MonitoringScreen.kt:1308-1320` `getCpuColor`/`getMemoryColor`: add a parameter
  `ext: ExtendedColors`.
  - The callers at `:408`, `:454`, `:822`, `:833`, `:889` and `:897` pass `AppTheme.extended`.
  - At `:225` and `:236` (`barColor = { getCpuColor(it) }`, a non-composable lambda), first hoist
    `val ext = AppTheme.extended` into the enclosing composable, then write
    `barColor = { getCpuColor(it, ext) }`.
- `C/ui/components/StatusBadge.kt:86-90` `StatusDot` default parameters `AppColors.Running` and
  `AppColors.Stopped`: use `AppTheme.extended.running` and `AppTheme.extended.stopped`. Default
  parameter expressions of a `@Composable` function may call composables.
- Any other site the compiler rejects ("@Composable invocations can only happen from the context of
  a @Composable function"): hoist `val ext = AppTheme.extended` to the nearest composable scope
  and use `ext.<field>`.

`AppColors` itself stays: `Theme.kt` and `ThemePreviewCard.kt` still read it.

**1.6 Monospace token.** In `C/ui/theme/Theme.kt`:

```kotlin
/** The app's monospace face: the platform monospace in Default (Retro's face comes in WS2). */
@Composable
fun appMonoFamily(): FontFamily = FontFamily.Monospace
```

- Make `monospaceMedium`/`monospaceSmall` (`:95-99`) composable getters that use it:

  ```kotlin
  val Typography.monospaceMedium: TextStyle
      @Composable get() = bodySmall.copy(fontFamily = appMonoFamily(), lineHeight = 18.sp)
  ```

  Do the same for `monospaceSmall`.
- Replace every `FontFamily.Monospace` and `androidx.compose.ui.text.font.FontFamily.Monospace`
  outside `ui/theme/` with `appMonoFamily()`. Hoist the call (`val mono = appMonoFamily()`) where
  the site is not composable.

**1.7 Console colours.** In `D/terminal/JediTermConsole.kt`:

1. Change `createTerminalWidget(connector: TtyConnector, darkTheme: Boolean)` (`:180-183`) to
   `createTerminalWidget(connector: TtyConnector, foreground: androidx.compose.ui.graphics.Color, background: androidx.compose.ui.graphics.Color)`.
2. `getDefaultStyle()` returns one `TextStyle` built from those two colours. Convert each with
   `com.jediterm.core.Color((c.red * 255).roundToInt(), (c.green * 255).roundToInt(), (c.blue * 255).roundToInt())`.
3. At `:137`, the composable `JediTermConsole` passes `AppTheme.extended.consoleForeground` and
   `AppTheme.extended.consoleBackground`. Read both in the composable body before the `factory`
   lambda.
4. The `darkTheme` parameter of the `JediTermConsole` composable (`:51`) and the
   `consoleContent(containerId, darkTheme)` signature (`C/App.kt:94`, `:439`; `D/Main.kt:358`)
   stay unchanged.
   - Above the now-unused `darkTheme` parameter, add a one-line comment: it is kept for the
     `consoleContent` signature and the colours come from the theme.
   - Accept the resulting UNUSED_PARAMETER compiler warning. Do not `@Suppress` it.
5. `T/TerminalWidgetCloseTest.kt:46` passes `Color(0xFFD4D4D4), Color(0xFF1A1A1A)`. Add
   `import androidx.compose.ui.graphics.Color` there.

A widget that is already open keeps its colours until the console is reopened. That is today's
behaviour for a mode change too.

**1.8 Guard test** (new file `T/ui/ThemeTokenGuardTest.kt`). It is a masking source scanner
modelled on `KT/ui/RetroShapeGuardTest.kt`, simplified:

1. Walk the two source sets `commonMain` and `desktopMain`. Resolve each root the way kkdd's
   `sourceRoot()` does (`KT/ui/RetroShapeGuardTest.kt:380-384`): `src/<set>/kotlin` if it is a
   directory, otherwise `composeApp/src/<set>/kotlin`. Assert both are directories and that there
   are at least 100 `.kt` files in total.
2. Copy `RetroShapeScanner.mask` and `lineStarts` verbatim from `KT/ui/RetroShapeGuardTest.kt:532-653`,
   with these changes:
   - Rename the object `ThemeTokenScanner`.
   - Drop the `keepComments` parameter.
   - Do not write a new masker.

   Skip a line whose masked text starts with `import ` after trimming.
3. Flag these patterns (regex, on the masked text):
   - `RoundedCornerShape\(`
   - `\bCircleShape\b`
   - `\bAppColors\.`
   - `FontFamily\.Monospace`
4. Exempt files, by path relative to the source root:
   - all four patterns: `com/containerdashboard/ui/theme/Theme.kt`,
     `com/containerdashboard/ui/theme/DesignTokens.kt` and
     `com/containerdashboard/ui/screens/ThemePreviewCard.kt` (its mockup stays literal, see 1.3)
5. On failure, list `relative/path.kt:line: pattern`. Use the path relative to the source root
   (`relativeTo(root).invariantSeparatorsPath`), never an absolute path.
6. Add canary tests that run the scanner on synthetic sources. Pin these cases:
   - (a) each of the four patterns, on its own code line, fires with that line number;
   - (b) none fires inside any of these:
     - `// …`
     - a `/* … */` spanning two lines
     - `"…"`
     - `"\"RoundedCornerShape(\""` (an escaped quote)
     - a `"""…"""` raw string spanning two lines (build it with kkdd's `TQ` constant trick,
       `KT/ui/RetroShapeGuardTest.kt:388`)
     - a `'"'` char literal
     - an `import …` line
   - (c) these do not fire: `Radius.md.appCorner`, `appRoundShape`, `appStrokeCap`,
     `AppTheme.extended.running`, `appMonoFamily()` and `LocalAppColors.current`.

   This way a broken scanner cannot pass vacuously.

**WS1 gate (orchestrator):**

- Compile succeeds, `desktopTest` passes (XML totals = previous total + the new tests), and
  `spotlessCheck` passes in a separate invocation.
- Acceptance greps outside `ui/theme/` all return nothing:
  - `grep -rn "RoundedCornerShape(\|CircleShape\|FontFamily.Monospace" composeApp/src/{commonMain,desktopMain}/kotlin | grep -v import | grep -v ui/theme/ | grep -v ThemePreviewCard`
  - `AppColors\.` outside `Theme.kt` and `ThemePreviewCard.kt`
- **Live smoke, REQUIRED.** Hot run against the fake engine. Take screenshots of Dashboard,
  Containers, Monitoring and Settings in Default dark and Default light, before (on the plan
  commit) and after. Compare them pixel by pixel. Any difference is a WS1 bug, except time-varying
  content: clocks, live stats, spinner phase.

### WS2 — Retro style core

**Non-goals.** No change to WS1 tokens' Default values. No CRT. No component-specific Retro branches
(that is WS3).

**2.1 Persistence.** In `C/data/repository/PreferenceRepository.kt`, mirror `themeMode`
(`:73`, `:153-174`):

- `private val THEME_STYLE by lazy { stringPreferencesKey("theme_style") }`
- `private val themeStyleFlow: Flow<ThemeStyle> by lazy { dataStore.data.map { prefs -> ThemeStyle.entries.firstOrNull { it.name == prefs[THEME_STYLE] } ?: ThemeStyle.DEFAULT } }`
- `fun themeStyle(): Flow<ThemeStyle> = themeStyleFlow`
- `suspend fun setThemeStyle(value: ThemeStyle) { dataStore.edit { it[THEME_STYLE] = value.name } }`

Add `themeStyle` to `T/PreferenceFlowsTest.kt`'s same-instance list.

**2.2 ViewModels.**

- `AppViewModel`: add `val themeStyle: StateFlow<ThemeStyle>`, built exactly like `themeMode`
  (`:121-124`), with initial value `ThemeStyle.DEFAULT`.
- `SettingsScreenViewModel`: add `fun themeStyle(): Flow<ThemeStyle> = PreferenceRepository.themeStyle()`
  and `fun setThemeStyle(value: ThemeStyle)`, mirroring `:52` and `:72-74`.

**2.3 Root sync.** In `C/App.kt`, next to `themeMode` (`:100`):

```kotlin
val themeStyle by viewModel.themeStyle.collectAsState()
LaunchedEffect(themeStyle) { ThemeStyleState.style = themeStyle }
```

Then call `ContainerDashboardTheme(darkTheme = darkTheme, style = ThemeStyleState.style)` (`:120`).
The theme root reads the same global as the tokens, so every frame is self-consistent: one Default
frame, then Retro. `LaunchedEffect` is already imported in `App.kt`.

- The About window (`D/Main.kt:373`) passes `style = ThemeStyleState.style`.
- Known limitation: the first frame after launch renders Default until the stored style arrives,
  the same as `themeMode`.

**2.4 Retro palette** (new file `C/ui/theme/RetroPalette.kt`, `internal`):

1. Copy `RetroDarkColorScheme` and `RetroLightColorScheme` from `K/theme/BuiltInPalettes.kt:155-217`.
   Inline every `RetroDarkColors.x` / `RetroLightColors.x` reference as the hex literal from the
   tables in `K/theme/BuiltInPalettes.kt:84-152`.
2. Add `RetroDarkExtendedColors` and `RetroLightExtendedColors`:

| Field | Retro dark | Retro light |
|---|---|---|
| `dividerStrong` | `3B4275` | `CBBE9E` |
| `warningSurface` | `FFA300` | `8F5300` |
| `warningSurfaceLight` | `0x1AFFA300` | `0x148F5300` |
| `successSurface` | `3CE66B` | `2E6B2F` |
| `infoSurface` | `5CBDFF` | `1D5A9E` |
| `running` | `3CE66B` | `2E6B2F` |
| `stopped` | `FF5C7A` | `A8231C` |
| `paused` | `FFD23E` | `775E03` |
| `warning` | `FFA300` | `8F5300` |
| `accent` | `7FD8EA` | `00606B` |
| `accentSecondary` | `5CBDFF` | `1D5A9E` |
| `accentTertiary` | `FFD23E` | `6B2F5B` |
| `textMuted` | `ADB3D6` | `5A4F3C` |
| `consoleForeground` | `E3E6F5` | `2B2418` |
| `consoleBackground` | `12142B` | `EFE7D2` |

(All values are opaque `0xFF…` except the two `warningSurfaceLight` alphas shown.)

**2.5 Fonts.**

1. Copy these files from `<KKDD>/composeApp/src/desktopMain/composeResources/font/` to
   `composeApp/src/commonMain/composeResources/font/`:
   - `departure_mono_regular.otf`
   - `departure-mono-OFL.txt`
   - `sixtyfour_regular.ttf`
   - `sixtyfour-OFL.txt`
2. In `C/ui/theme/Theme.kt`, add `retroReadingFamily()` and `retroPixelFamily()`. Copy them from
   `kdRetroFamily()` (`K/Theme.kt:343-346`) and `kdPixelFamily()` (`K/Theme.kt:357-360`), using
   `Font` from `org.jetbrains.compose.resources` and `Res.font.*` from
   `com.dockerdashboard.composeapp.generated.resources`.
3. `appMonoFamily()` becomes
   `if (ThemeStyleState.isRetro) retroReadingFamily() else FontFamily.Monospace`.
4. Under the existing "## Acknowledgments" heading at the end of `README.md`, add two credit lines:
   - "Departure Mono © 2022–2024 Helena Zhang, SIL OFL 1.1."
   - "Sixtyfour © 2021 The Sixtyfour Project Authors, SIL OFL 1.1."

   Both lines link to their OFL file in `composeApp/src/commonMain/composeResources/font/`.

**2.6 Typography, shapes and chrome helpers** (in `C/ui/theme/Theme.kt`):

- `@Composable internal fun retroTypography(): Typography`: a port of the Retro branch of
  `kdTypography()` (`K/Theme.kt:482-520`), with `base = Typography()`. It covers all 30 slots,
  including the 15 `*Emphasized` ones, in the same order. `headlineLarge` (20.sp) and
  `headlineMedium` (16.sp) use the pixel face exactly as kkdd.
- `private val RetroShapes`: copy `K/Theme.kt:533-542`.
- `const val RETRO_TYPE_SCALE = 0.72f`, `TextStyle.retroChrome(size)` and `String.retroCaps()`:
  copy `K/Theme.kt:368-391` and `:596`, with `ThemeManager.isRetro` → `ThemeStyleState.isRetro`
  and `kdPixelFamily()` → `retroPixelFamily()`.

**2.7 Theme root.** `ContainerDashboardTheme(darkTheme: Boolean = isSystemInDarkTheme(), style: ThemeStyle = ThemeStyle.DEFAULT, content)`:

- Retro: the scheme is `RetroDark/LightColorScheme`, the extended colours are
  `RetroDark/LightExtendedColors`, the typography is `retroTypography()` and the shapes are
  `RetroShapes`.
- Default: unchanged. Add `private val DefaultShapes = Shapes()` next to `RetroShapes` (as at
  `K/Theme.kt:546`) and pass `shapes = if (style == ThemeStyle.RETRO) RetroShapes else DefaultShapes`.
  `Shapes()` equals the library default.
- Choose by `style`, not by `ThemeStyleState`, so a test can render either one.

**2.8 Settings.**

- In `SettingsScreen` (`C/ui/screens/SettingsScreen.kt:281-287`), add a `ThemeStyleSelector`
  under `ThemeModeSelector`, inside the same "Appearance" section.
  - Title "Style", subtitle "Default or Retro (pixel fonts, square corners)".
  - Two `ThemePreviewCard`s: "Default" with `DarkPreviewColors`, and "Retro" with a new
    `RetroPreviewColors`.
  - `RetroPreviewColors` lives in `ThemePreviewCard.kt` next to `:51-69`: chrome `1E2240`, sidebar
    `0A0B1A`, background `12142B`, surface `272C50`, accent `7FD8EA`, text `E3E6F5`.
  - Wire `viewModel.themeStyle()` / `setThemeStyle`.
  - Give `ThemePreviewCard` a `squared: Boolean = false` parameter. When it is true, every shape in
    the mockup is `RoundedCornerShape(0.dp)`, including the outer card frame and the `CircleShape`
    swatches. The Retro card passes `squared = true`; all other cards keep `false`.
- Collect it with `collectAsState(initial = ThemeStyle.DEFAULT)`, as at `:86`.

**2.9 Tests** (new, in `T/ui/`):

- **`RetroPaletteTest`.**
  - Every `RetroDark/LightExtendedColors` text-role field reaches ≥ 4.5:1 WCAG contrast on that
    scheme's `background`, `surface` and `surfaceVariant`. The text-role fields are `running`,
    `stopped`, `paused`, `warning`, `accent`, `accentSecondary`, `accentTertiary` and `textMuted`.
  - The scheme's `onSurface` and `primary` reach ≥ 4.5:1 on the same three backgrounds.
  - Implement the WCAG relative-luminance formula in the test. Pin the D5 table values.
- **`RetroTypographyTest`.** Port the slot assertions of `KT/ui/RetroTypographyTest.kt`.
  - Render `retroTypography()` inside `runComposeUiTest`.
  - Every slot uses the reading family except `headlineLarge`/`headlineMedium`.
  - `appMonoFamily()` returns `FontFamily.Monospace` in Default and the reading family in Retro.
  - Reset `ThemeStyleState.style = ThemeStyle.DEFAULT` in `@AfterTest`. Add a `@BeforeTest` that
    asserts it is `DEFAULT`, so a leak from another test is caught at its source.
  - The tests run sequentially, on JUnit4 with one fork.
  - None of these tests touch `PreferenceRepository`.
- **`ThemeStyleTokensTest`.**
  - `appCorner`, `appCorners` and `appRoundShape` are square in Retro and unchanged in Default.
  - `appStrokeCap` is Butt in Retro and Round in Default.
  - `retroCaps()` upper-cases only in Retro.
  - Same reset.

**WS2 gate (orchestrator):**

- Compile, `desktopTest` (XML totals) and `spotlessCheck` pass.
- **Live smoke, REQUIRED.** Hot run with the fake engine.
  - Settings → Style → Retro. In Retro dark and Retro light, every screen must show: no round
    corners (Switch, Checkbox and Radio excepted), no Docker blue, no Material green/red, Departure
    in body text and Sixtyfour in headlines.
  - Switch back to Default: identical to the WS1 screenshots (negative control).
- Persistence: restart the hot run. The style survives the restart.

### WS3 — Retro component chrome

**Non-goals.** No CRT, no screen-transition or dialog animation, no change in Default rendering.
The window chrome bar has no title text (`C/App.kt:505-528`), so kkdd's title-bar treatment has no
counterpart here and is skipped.

**3.1 Busy indicator.**

1. New file `C/ui/components/BusyIndicator.kt`: port `K/ui/components/BusyIndicator.kt` (170 lines)
   whole, with these changes:
   - `ThemeManager.isRetro` → `ThemeStyleState.isRetro`
   - drop the `highContrast` parameter from `RetroBusyRing`, `busyCellAlpha` and `trailAlpha`,
     keeping the non-high-contrast branch (1, 0.55, 0.30, 0.14); drop the `ThemePalette` import
   - `kdStrokeCap` → `appStrokeCap`
   - drop any `kd-shape-exempt` marker
2. Replace all 26 `CircularProgressIndicator(` calls in `C/` and `D/` with `BusyIndicator(`. Keep
   `modifier`, `color` and `strokeWidth` arguments.
   - If a call passes another argument (`trackColor`, `strokeCap`), stop and report it instead of
     dropping it.
   - Remove unused imports.
3. Add `CircularProgressIndicator(` to the WS1 guard's patterns, exempting
   `com/containerdashboard/ui/components/BusyIndicator.kt`.
4. Port the cell-geometry and step tests from kkdd's `BusyIndicatorTest`:
   - `busyRingCells` sizes and order
   - `frameStep` wrapping
   - Default delegates to M3
   - "trail and resting opacities", adapted to no high contrast

   Do not port "High Contrast drops the resting cells". Under an auto-advancing test clock,
   `rememberFrameStep` stays at 0. A test that reads the step sets `mainClock.autoAdvance = false`
   first, as kkdd does (`BusyIndicatorTest:251-271`).

**3.2 Segmented gauge.** `C/ui/components/SemiDonutGauge.kt`:

- In Retro, draw 18 segments with 3° gaps instead of the two arcs, as in `K/ui/components/Charts.kt:96-102,141-153`.
  - Lit segments use `color` and the rest `trackColor`, with `cap = appStrokeCap`.
  - Port `litCells` (`K/ui/components/UsageBar.kt:93-96`) as an `internal` function in the same
    file.
- Default stays as is.
- Add a test for `litCells`: 0 → 0, NaN → 0, a tiny positive → 1, and 1 → 18.

**3.3 Stepped skeleton.** `C/ui/components/ListRowSkeleton.kt`:

- In Retro, `ListRowSkeleton` makes one row at a time bright (0.8) and the others dim (0.4). The
  bright row steps down every 180 ms, using `rememberFrameStep(180L, rowCount.coerceAtLeast(1))`
  from 3.1. Port `skeletonRowAlpha` from `K/ui/components/ResourceTable.kt:880-883`.
- Default keeps `rememberSkeletonAlpha()`.
- `rememberSkeletonAlpha()` itself is unchanged; its other callers keep the smooth pulse.
- Test `skeletonRowAlpha`.

**3.4 Pixel-caps headers.**

- Table header cells: in the header `Text` of `SortableHeaderCell` (`C/ui/screens/ContainerRendering.kt:527`),
  `SortableHeaderCell` (`C/ui/screens/ImagesScreen.kt:524`), `NetworkSortableHeaderCell`
  (`C/ui/screens/NetworksScreen.kt:538`) and `VolumeSortableHeaderCell`
  (`C/ui/screens/VolumesScreen.kt:591`), change `style = MaterialTheme.typography.labelSmall` to
  `style = MaterialTheme.typography.labelSmall.retroChrome(8.sp)`. In the same four `Text`s, change
  `fontWeight = FontWeight.SemiBold` to
  `fontWeight = if (ThemeStyleState.isRetro) FontWeight.Normal else FontWeight.SemiBold`. The
  labels are already upper-case.
- `C/ui/components/SectionHeader.kt:52-56`: the title `Text` becomes
  `text = title.retroCaps()` and `style = MaterialTheme.typography.titleSmall.retroChrome(10.sp)`.
  Keep `fontWeight = FontWeight.SemiBold`: `retroChrome` sets Normal in the style, but an explicit
  `fontWeight` argument wins. So pass `fontWeight = if (ThemeStyleState.isRetro) FontWeight.Normal else FontWeight.SemiBold`.

**3.5 Hairline instead of shadow.**

- The `Surface`s with `shadowElevation` are:
  - `C/ui/components/ResourceDetailsDialog.kt:240`
  - `C/ui/components/TruncatingText.kt:57`
  - `C/ui/components/AppTooltip.kt:31`
  - `C/ui/shortcuts/CommandPalette.kt:188`
- On each, set `shadowElevation = if (ThemeStyleState.isRetro) 0.dp else <current>` and
  `border = if (ThemeStyleState.isRetro) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else <current border or null>`.
- `tonalElevation` is unchanged.
- Add `import androidx.compose.foundation.BorderStroke` to `TruncatingText.kt`, `AppTooltip.kt` and
  `CommandPalette.kt`. `ResourceDetailsDialog.kt` already has it.

**3.6 Button shapes** (added 2026-10-23 after the WS2 live smoke).

In Retro, the M3 buttons and the segmented buttons in Settings stayed pill-shaped. Their default
shape is the `CornerFull` token. In material3 `1.12.0-alpha03`, `ShapesKt.fromToken` maps that token
straight to `CircleShape`, not through `MaterialTheme.shapes`, so `RetroShapes` never reaches them.
kkdd handles this by giving every M3 button an explicit `shape`.

1. Pass `shape = appRoundShape` on every call of `Button(`, `OutlinedButton(`, `TextButton(` and
   `IconButton(` in `C/` and `D/`. Today that is 19, 10, 4 and 42 calls.
   - In Default, `appRoundShape` is `CircleShape`, the same value as the `CornerFull` default, so
     rendering does not change.
   - If a call already passes `shape =`, keep its value and wrap it as
     `if (ThemeStyleState.isRetro) RoundedCornerShape(0.dp) else <value>`.
2. Every `SegmentedButtonDefaults.itemShape(index = …, count = …)` call (`C/ui/screens/SettingsScreen.kt`,
   three calls) gains `baseShape = appRoundShape`.
3. Extend `T/ui/ThemeTokenGuardTest.kt` with kkdd's three rules:
   - "button-shape": an M3 button call without `shape =`;
   - "m3-default-shape": `ButtonDefaults`/`IconButtonDefaults` `*Shape(s)` references;
   - `SegmentedButtonDefaults.itemShape` without `baseShape =`.

   Port the matching regexes, the `closeParen` argument-span logic and their canaries from
   `KT/ui/RetroShapeGuardTest.kt` (rules around `:420-470`, `closeParen` after `lineStarts`,
   canaries `:230-310`, `:361-370`), with the `kd*` token names replaced by the `app*` ones. Do not
   port the TabRow or `LinearProgressIndicator` rules: neither is used here.

**WS3 gate (orchestrator):**

- Compile, `desktopTest` (XML totals) and `spotlessCheck` pass.
- **Live smoke, REQUIRED.** In Retro: the spinner steps in square cells (Settings engine actions, or
  a loading list), the gauges are segmented (Dashboard/Monitoring), the skeleton steps, the headers
  are pixel caps, and the tooltip and command palette have a hairline border.
- In Retro, the Settings buttons and the segmented buttons are square.
- Switch to Default: Dashboard and Containers are identical to the WS1 screenshots, and Settings is
  identical to the WS2 Default screenshot (negative control).

## Risks for the reviewer

1. **Snapshot reads outside composition.** `appCorner` and the others read `ThemeStyleState`.
   A read inside `remember {}`, or at class initialisation, would freeze the style. Check every
   migrated site that sits in a `remember` block or a non-composable helper.
2. **`AppTheme.extended` is `@Composable @ReadOnlyComposable`.** The plan lists the
   non-composable sites found by a heuristic scan. Check for any it missed: default parameters,
   lambdas passed to `Canvas`/`drawBehind`, `buildAnnotatedString` inside `remember`.
3. **`monospaceMedium` becomes a `@Composable` getter.** Check its three call sites
   (`FilesTabContent.kt:390`, `LogsTabContent.kt:327`, `:465`) are in composable scope.
4. **Retro typography `copy` signature.** It must exist with all 30 slots in material3
   `1.12.0-alpha03` (`gradle/libs.versions.toml:6`). kkdd uses the same version.
5. **Compose resources fonts in `commonMain`.** Check the font loading API and the generated
   accessor names (`Res.font.departure_mono_regular`, `Res.font.sixtyfour_regular`) for this
   project's `Res` package.
6. **Determinism.** `rememberFrameStep` runs through `withInfiniteAnimationFrameMillis`, so UI
   tests with an auto-advancing clock must not hang. kkdd's tests rely on this.
7. **Real user state.** Confirm that no new test, and no hot run, touches the developer's real
   DataStore.

## Review log

Plan review: a fresh Fable subagent, 2026-10-23. Verdict: APPROVE WITH AMENDMENTS, no blocker.
Every anchor was confirmed on HEAD. Library APIs were checked against the pinned jars:

- `Typography.copy` with 30 slots and the 8-slot `Shapes`
- `Font(FontResource, …)`
- the ui-test infinite-animation policy

Each MAJOR was re-verified against its cited source before applying.

- **M1 (applied).** Style sync now uses `LaunchedEffect`, and the theme root reads
  `ThemeStyleState.style`. Verified: with `SideEffect`, the theme's `style` argument and the tokens'
  global differ for one frame.
- **M2 (applied).** The guard test copies kkdd's masker verbatim. Canaries are pinned, the pattern
  is `\bAppColors\.`, roots are resolved in two candidates and failure paths are relative. Verified
  `KT/ui/RetroShapeGuardTest.kt:380-384`, `:388` and `:532-653`.
- **m1 (applied).** Header cells set `fontWeight = Normal` in Retro. Verified
  `ContainerRendering.kt:545-551`.
- **m2 (applied, orchestrator's call, following kkdd's precedent).** `ThemePreviewCard` is exempt
  from the token migration and gets a `squared` switch in WS2.
- **m3, m4, m5, m6, m9, m10, m11, m12 (applied).**
  - m3: `DefaultShapes`
  - m4: `highContrast` cascade
  - m5: qualified `CornerRadius`
  - m6: `BorderStroke` imports
  - m9: `@BeforeTest` leak check
  - m10: README "Acknowledgments", verified at `README.md:200`
  - m11: corrected counts
  - m12: test import
- **m7 (applied).** Keep the unused `darkTheme` parameter with a comment and accept the warning.
- **m8 (not applied).** Optional style change. The `roundToInt` conversion is exact for these
  literals.

Orchestrator amendment, 2026-10-23, after the WS2 live smoke:

- **Added WS3 step 3.6 (button shapes).** In Retro, the M3 buttons and segmented buttons were still
  pills, which breaks the WS2 gate's "no round corners" check.
- **Cause:** `ShapesKt.fromToken(CornerFull)` returns `CircleShape` directly in material3
  `1.12.0-alpha03`, verified with javap on the jar.
- **Why WS3:** the fix is component chrome, so it went into WS3 rather than reopening WS2.
