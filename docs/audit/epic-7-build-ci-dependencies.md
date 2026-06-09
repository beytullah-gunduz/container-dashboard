# Epic 7 — Build, CI & dependency hygiene

Small, mostly independent stories; a single agent can take the whole epic in one branch.
Verify every change with `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew build` and a
separate `spotlessCheck` invocation (config-cache flake — see project memory).

---

## S7.1 — No JVM toolchain declared; Java 21 requirement is tribal knowledge

- **Severity:** High · **Effort:** S
- **File:** `composeApp/build.gradle.kts` (the `kotlin { }` block)

Nothing in the build enforces Java 21. Locally, a newer default JDK (e.g. 25) fails with a
Kotlin-compiler `IllegalArgumentException`, requiring the manual `JAVA_HOME` workaround
documented in CLAUDE.md/memory. CI only works because `setup-java` happens to install 21.

**Fix:** add `kotlin { jvmToolchain(21) }`. Note Gradle itself must still run on a
compatible JVM, but toolchain provisioning removes the compile-time dependency on the
ambient `JAVA_HOME`.

**Acceptance:** `./gradlew build` succeeds without the `JAVA_HOME` override on a machine
whose default JDK is newer (or document remaining limits in README). If fully fixed, update
the memory file `gradle_java_version.md`.

---

## S7.2 — Ktor common bundle declared but unused

- **Severity:** Medium · **Effort:** S
- **File:** `composeApp/build.gradle.kts:55` (`libs.bundles.ktor.common`)

No `io.ktor.*` import exists anywhere in `commonMain`; networking goes through
docker-java's Apache transport. Dead classpath weight and compile time. Check whether the
`ktor-client-cio` entry in `desktopMain` is likewise unused before removing it too.

**Fix:** remove the unused dependency declarations (keep version-catalog entries if Ktor is
genuinely planned).

**Acceptance:** `./gradlew build` and the full test suite pass; app runs.

---

## S7.3 — No Gradle caching in CI

- **Severity:** Medium · **Effort:** S
- **Files:** `.github/workflows/build.yml:35-36`, `.github/workflows/release.yml` (all matrix legs)

`gradle/actions/setup-gradle` is used without any cache configuration, so every run cold-
downloads the full dependency graph on every runner.

**Fix:** enable the action's built-in caching (it is on by default in current versions —
verify why it isn't effective here, e.g. pinned old action SHA or explicit disable) or add
an `actions/cache` step for `~/.gradle/caches` + `~/.gradle/wrapper`. Keep actions
SHA-pinned per repo convention.

**Acceptance:** second consecutive CI run shows cache hits and a measurably shorter build.

---

## S7.4 — `docker-compose.test.yml` is dead infrastructure

- **Severity:** Medium · **Effort:** M (or S if deleted)
- **File:** `docker-compose.test.yml`

Defines redis/postgres/nginx/mongo/adminer for "integration tests", but nothing references
it — no workflow, no script, no test. The suite is pure unit tests on
`FakeDockerRepository`. The file misleads contributors into assuming integration coverage
exists.

**Fix:** decide: wire it into a real integration-test job (compose up → run a
`desktopTest` integration suite → compose down), or delete it. Deleting is the honest
default until integration tests are actually planned.

**Acceptance:** either CI runs integration tests against it, or the file is gone.

---

## S7.5 — `gradle.properties`: dead Android/iOS flags; configuration cache not enabled

- **Severity:** Low · **Effort:** S
- **File:** `gradle.properties:3-8`

The project is desktop-only (`jvm("desktop")` is the sole target) but carries
`kotlin.native.cacheKind.ios*`, `android.*`, and `compose.experimental.uikit` properties.
`org.gradle.configuration-cache` is unset.

**Fix:** delete the six unused properties. Optionally trial
`org.gradle.configuration-cache=true` + `configuration-cache-problems=warn` — but test
Spotless behavior carefully given the known config-cache fingerprinting flake (project
memory: run spotless on its own invocation).

**Acceptance:** build + spotless (separate invocations) green after the cleanup.

---

## S7.6 — DataStore artifacts on mismatched versions

- **Severity:** Low · **Effort:** S
- **File:** `gradle/libs.versions.toml:19-20`

`androidx-datastore-core = 1.2.0` vs `androidx-datastore-preferences = 1.1.7` — same
release train, should be aligned to avoid subtle KMP API mismatches.

**Fix:** single `datastore` version alias, both at `1.2.0`.

**Acceptance:** build + tests green; preferences still load (window bounds, theme, host).

---

## S7.7 — `spotlessCheck` runs on all three release-matrix runners

- **Severity:** Low · **Effort:** S
- **File:** `.github/workflows/release.yml:77-79`

Formatting is verified per-PR in `build.yml`; re-running it on macOS+Ubuntu+Windows in the
release matrix triples cost for zero signal.

**Fix:** drop the step from the matrix (or hoist into a single ubuntu job gating the matrix
via `needs:`).

**Acceptance:** release workflow on a test tag still builds all three installers.
