# Epic 5 — Security hardening

Context: local desktop tool; severity is calibrated to that (the main attack vectors are
malicious container images and remote `tcp://` hosts).
S5.1 shares files with S6.2 (`Save*ToFile.desktop.kt`) — coordinate.

---

## S5.1 — Path traversal via container-supplied filename in save dialogs

- **Severity:** Medium · **Effort:** S
- **Files:** `composeApp/src/desktopMain/kotlin/com/containerdashboard/ui/components/SaveBytesToFile.desktop.kt:17`, `SaveLogsToFile.desktop.kt:17`

`entry.name` (parsed from `ls -la` output **inside the container**) is passed verbatim as
the suggested filename to `FileDialog`, and the result is written via `File(directory,
file)`. A crafted image can host a file named `../../../Library/LaunchAgents/evil.plist`;
if the user confirms the pre-filled dialog, the write lands outside the chosen directory.

**Fix:** sanitize the suggestion (`substringAfterLast('/')`, strip `\\` and control chars)
and, after the dialog returns, verify
`File(directory, file).canonicalPath.startsWith(File(directory).canonicalPath)` before
writing.

**Acceptance:** a file named with `../` segments downloads into the chosen directory under
a sanitized name; a regression test for the sanitizer.

---

## S5.2 — No TLS enforcement or warning for remote `tcp://` Docker hosts

- **Severity:** Medium · **Effort:** M
- **File:** `composeApp/src/desktopMain/kotlin/com/containerdashboard/data/repository/DesktopDockerRepository.kt:87-91`; UI in `SettingsScreen.kt`

The client config is just `withDockerHost(dockerHost)` — a `tcp://` host means all API
traffic (including container env vars full of secrets) goes unencrypted, with no warning.

**Fix:** show a warning in Settings for `tcp://`/`http://` schemes; optionally expose TLS
cert-path/verify fields wired to `withDockerTlsVerify`/`withDockerCertPath`; at minimum log
a WARN on non-TLS TCP connect.

**Acceptance:** entering a `tcp://` host shows the warning; TLS options (if implemented)
round-trip through preferences.

---

## S5.3 — Container env vars (often secrets) rendered unmasked

- **Severity:** Low · **Effort:** M
- **File:** `composeApp/src/commonMain/kotlin/com/containerdashboard/ui/components/ResourceDetailsDialog.kt:715-726`

The Environment tab (and Raw JSON tab) shows `POSTGRES_PASSWORD`-style values in plaintext.
Screen-share/shoulder-surf exposure only — the audit confirmed env values are *not* written
to `AppLogStore` or disk logs.

**Fix:** mask values whose key matches `PASSWORD|SECRET|TOKEN|KEY|CREDENTIAL` (etc.) behind
a per-row reveal toggle.

**Acceptance:** a container with `POSTGRES_PASSWORD` shows `••••` until revealed; non-secret
vars stay visible.

---

## S5.4 — `release.yml` interpolates the tag-derived version into a `run:` block

- **Severity:** Low · **Effort:** S
- **File:** `.github/workflows/release.yml:80` (version derived at :61)

`run: ./gradlew … -Papp.version=${{ steps.version.outputs.version }}` — a malicious tag
name is shell-injected into the runner. Requires push-tag access, hence low for a
single-owner repo, but it's the standard GitHub-hardening fix.

**Fix:** pass through `env:` (`APP_VERSION: ${{ steps.version.outputs.version }}`) and use
`"$APP_VERSION"` in the script.

**Acceptance:** workflow builds a normal `vX.Y.Z` tag identically; no `${{ }}` inside
`run:` for derived values.

---

## S5.5 — Host URL (potentially with embedded credentials) stored plaintext; container paths accept control characters

- **Severity:** Low · **Effort:** M
- **Files:** `composeApp/src/desktopMain/kotlin/com/containerdashboard/data/datastore/DataStorePreferences.desktop.kt:19`; `DesktopDockerRepository.kt:518, 543, 577` + `data/util/ContainerPathUtil.kt` (`normalizePath`)

Two small hardening items: (a) the `engine_host` preference (which could contain
`tcp://user:pass@host`) is stored world-readable-by-default in
`~/.container-dashboard/settings_preferences.preferences_pb`; (b) `normalizePath` resolves
`..` correctly but lets NUL/control characters through to `execCreateCmd`.

**Fix:** (a) `chmod 600` the preferences file on creation and document that credentials in
the URL are stored plaintext; (b) reject paths containing NUL (\u0000) or other control characters with a
`Result.failure`.

**Acceptance:** prefs file is `-rw-------`; a path with an embedded NUL returns a clean
error.
