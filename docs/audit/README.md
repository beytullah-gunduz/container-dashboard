# Code Audit — 2026-06-09

Full-codebase audit of `container-dashboard`, performed by six parallel Sonnet audit agents
(one per dimension: data/Docker layer, concurrency, Compose UI, desktop platform, security,
build/CI/tests). Findings were deduplicated and organized into 8 epics / 53 stories below,
sized so that individual stories (or whole epics) can be dispatched to separate agents.

**Scope:** main checkout only (`composeApp/src`, build files, `.github/workflows`).
Worktrees under `.claude/worktrees/` were excluded.

**Verification status:** all 11 originally-high findings were re-verified by direct code
reading on 2026-06-09 (see per-story verification notes): **10 confirmed, 1 downgraded to
Medium (S4.1 — log buffer is capped at 500–5000 lines, so the waste is bounded), 0 killed.**
Confirmed: S1.1, S1.2, S1.3, S2.1, S2.2, S3.1, S3.2, S7.1, S8.1, S8.2. Medium/low findings
were **not** re-verified — for those, every story starts with an implicit step 0: re-read
the cited code and confirm the problem exists before fixing. Line numbers are accurate as
of commit `63440c8`.

## Severity summary

| Severity | Count | Notes |
|---|---|---|
| Critical | 0 | — |
| High | 10 | all re-verified by direct code reading on 2026-06-09 |
| Medium | 24 | incl. S4.1, downgraded from High during verification |
| Low | 19 | |

## Epics

| Epic | Title | Stories | High | Theme |
|---|---|---|---|---|
| [E1](epic-1-docker-repository.md) | Docker repository correctness | 8 | 3 | `DesktopDockerRepository` races, wrong API usage, event-stream gaps |
| [E2](epic-2-viewmodel-lifecycle.md) | ViewModel lifecycle & reconnect | 9 | 2 | VMs bound to stale repo after host reconnect; state races; startup blocking |
| [E3](epic-3-engine-terminal-processes.md) | Engine manager & terminal processes | 5 | 2 | dead timeouts, exec-session races/leaks, platform guards |
| [E4](epic-4-compose-ui-performance.md) | Compose UI performance & correctness | 9 | 0 | log rendering, recomposition storms, density staleness |
| [E5](epic-5-security.md) | Security hardening | 5 | 0 | path traversal on save, TLS warning, secret display, CI injection |
| [E6](epic-6-platform-packaging.md) | Desktop platform & packaging polish | 5 | 0 | ProGuard keep rules, EDT, version string, macOS paths |
| [E7](epic-7-build-ci-dependencies.md) | Build, CI & dependency hygiene | 7 | 1 | jvmToolchain(21), dead deps, CI caching |
| [E8](epic-8-test-coverage.md) | Test coverage gaps | 5 | 2 | untested `DesktopDockerRepository`, Settings VM, EngineManager |

## Dispatch guidance for parallel agents

- **One worktree per story or per epic** (project convention — never edit main directly).
- **File-conflict clusters — do not run these in parallel against each other:**
  - E1 (all stories), S8.1 → `DesktopDockerRepository.kt`
  - E3 (S3.1, S3.4, S3.5), S8.3 → `EngineManager.kt`
  - E2 (S2.1, S2.2) and S8.2/S8.4 touch the same ViewModels
  - S5.1 and S6.2 both edit `SaveBytesToFile.desktop.kt` / `SaveLogsToFile.desktop.kt`
- **Suggested order:** E1 + E7 first (small, high-severity, unblock correctness), then
  E2/E3 (build on a stable repository layer), E4/E5/E6 anytime, E8 last (tests should
  target the *fixed* behavior; S8.1 refactors a file E1 edits).
- Before merging any story: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew spotlessCheck`
  (own invocation, not chained) and the test suite.
- Release-only risks (S6.1) must be validated with `createReleaseDistributable`, not
  `./gradlew run` — release builds are minified and behave differently.

## Story ID convention

`S<epic>.<n>` — e.g. S1.3. Each story in the epic files is self-contained: severity,
effort (S/M/L), exact files/lines, problem statement, suggested fix, acceptance criteria.
