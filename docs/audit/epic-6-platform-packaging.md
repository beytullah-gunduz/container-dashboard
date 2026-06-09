# Epic 6 — Desktop platform & packaging polish

Base path: `composeApp/src/desktopMain/kotlin/com/containerdashboard/`.
S6.2 shares files with S5.1 — coordinate.

---

## S6.1 — ProGuard keep rules missing for Apache HttpClient 5 transport stack

- **Severity:** Medium · **Effort:** S
- **File:** `composeApp/proguard-rules.pro:33` (currently only `-dontwarn org.apache.hc.core5.http2.**`)

`docker-java-transport-httpclient5` loads TLS providers and the Unix-socket connection
factory via ServiceLoader/class-name lookup; no `-keep` covers `org.apache.hc.**`, so
release builds can strip/rename classes the transport needs — a release-only
`ClassNotFoundException` class of failure. This matches the project's known pattern of
release-only breakage (see the JediTerm precedent in project memory).

**Fix:** add `-keep class org.apache.hc.client5.** { *; }` and
`-keep class org.apache.hc.core5.** { *; }` (plus `org.newsclub.net.unix.**` if present on
the classpath).

**Acceptance:** `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew createReleaseDistributable`
and the resulting .app connects to the Docker socket and streams stats. **Must be tested on
the release artifact — `./gradlew run` does not exercise ProGuard.**

---

## S6.2 — `FileDialog` shown without explicit EDT dispatch

- **Severity:** Medium · **Effort:** S
- **Files:** `ui/components/SaveLogsToFile.desktop.kt:11`, `ui/components/SaveBytesToFile.desktop.kt:11` (call sites: `App.kt:352-353, 378-380`)

`dialog.isVisible = true` runs on whatever thread the `viewModelScope.launch` callback uses.
Works by accident on macOS; off-EDT AWT dialogs are unsupported and can deadlock on
Linux/Windows.

**Fix:** wrap dialog creation/show in `withContext(Dispatchers.Main)` (or
`SwingUtilities.invokeAndWait`) inside the `actual fun` implementations. Coordinate with
S5.1, which edits the same functions.

**Acceptance:** save-logs and download-file still work on macOS; the dialog code provably
runs on the EDT.

---

## S6.3 — About dialog hardcodes "Version 1.0.0"

- **Severity:** Low · **Effort:** S
- **File:** `Main.kt:375`

`BuildConfig.VERSION` exists (used correctly in `SettingsScreen.kt:427`) but the About
dialog hardcodes `1.0.0`.

**Fix:** `"Version ${BuildConfig.VERSION}"`.

**Acceptance:** About dialog matches the `-Papp.version` build input.

---

## S6.4 — App data lives in `~/.container-dashboard` instead of `~/Library/Application Support` on macOS

- **Severity:** Low · **Effort:** S (plus migration thought)
- **File:** `util/SystemDirectories.kt:9-11`

The non-Windows fallback is a home-directory dotfolder; the macOS-correct location is
`~/Library/Application Support/container-dashboard`.

**Fix:** branch on macOS. **Important:** existing users have settings in the old path —
migrate (move file if old exists and new doesn't) or deliberately decide to keep the
current path and close this story as won't-fix. Don't silently reset preferences.

**Acceptance:** fresh install writes to Application Support; an existing settings file is
still honored.

---

## S6.5 — `NativeWindowDrag` calls `performWindowDragWithEvent:` without a main-thread guarantee

- **Severity:** Low · **Effort:** S
- **File:** `ui/chrome/NativeWindowDrag.kt:94-106`

The call is safe today because JBR dispatches Compose pointer events during AppKit event
processing, but that's an implementation detail; `NativeMacApp.performOnMain()` (same
package) shows the correct marshaling pattern.

**Fix:** document the JBR thread contract in place, and add a cheap `NSThread.isMainThread`
assertion (log-only) so a future JBR change surfaces loudly instead of as undefined
behavior.

**Acceptance:** window drag still works; assertion logs nothing in normal use.
