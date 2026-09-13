# UX-7 — Images / Volumes / Networks

Heuristics: *Match between system and the real world*, *Error prevention*, *Consistency*.
Base path: `composeApp/src/commonMain/kotlin/com/containerdashboard/`.

These three screens share a template (search → sections → table → per-row delete,
right-click menu, create dialog for volumes/networks). The template is sound; the
problem is which columns were chosen.

---

## U7.1 — Networks › CONTAINERS is always 0

- **Severity:** P1 · **Effort:** S
- **Files:** `data/models/DockerNetwork.kt:38` (`containerCount = containers?.size ?: 0`),
  `desktopMain/.../DockerJavaMappers.kt:317-350` (list mapper copies `Containers`),
  `ui/screens/NetworksScreen.kt:506` (column header)

Docker's `GET /networks` never populates `Containers` — only `inspect` does. Observed:
`example-stack_default` showed **0** while `docker network inspect` reports 7 attached
containers. A column that is always zero teaches users the app is wrong, and it is the
one column that answers "can I delete this?".

**Fix:** either drop the column, or populate it from `inspectNetwork` for the custom
networks (≤ a handful) after the list loads, or derive it from the containers flow
(`Container.networkSettings`) which the app already holds.

**Acceptance:** the count matches `docker network inspect` for every non-system network.

---

## U7.2 — No "in use" signal on images and volumes; delete fails after the fact

- **Severity:** P1 · **Effort:** M
- **Files:** `ui/screens/ImagesScreen.kt` (row: repository, tag, id, size),
  `ui/screens/VolumesScreen.kt` (row: name, driver, mountpoint); the containers flow
  exposes `imageId`/`image` and mounts via inspect.

Deleting `postgres:17.9` (in use by a running container) or `example-stack_postgres-data`
(mounted) only fails once the daemon rejects it, with the raw daemon message in a banner.
`docker` CLI users know this; GUI users expect the table to say "used by 1 container"
and the delete control to be disabled with a reason.

**Fix:** *Used by* column (count, tooltip with names) computed from the containers list;
disabled delete with tooltip when > 0; offer *Force* only in the context menu.

---

## U7.3 — Volumes › MOUNTPOINT is a path inside the VM

- **Severity:** P2 · **Effort:** S
- **File:** `ui/screens/VolumesScreen.kt:531` (`"MOUNTPOINT"` column)

On macOS/Windows (Colima, Docker Desktop, OrbStack) `/var/lib/docker/volumes/<name>/_data`
is not reachable from the host; it takes ~40% of the row width and is identical for every
row apart from the name it repeats. More useful: *Used by*, *Created*, *Size*
(`docker system df -v`).

**Fix:** move mountpoint into Inspect; replace with the columns above.

---

## U7.4 — Images screen details

- **Severity:** P2 · **Effort:** S
- **Files:** `ui/screens/ImagesScreen.kt:251` (search bar half-width — no trailing
  controls unlike Containers), `:624-631`/`:664-671` (tag rendered in `primary` — the
  app's link colour — but not interactive), `:496` (SIZE header; values left-aligned),
  `:355` ("Dangling Images" section with no section action), `data/repository/DockerRepository.kt:101`
  (`pullImage` exists, unused by UI)

- No *Created* column — the first thing `docker images` shows.
- Numeric SIZE column left-aligned; right-align for scanning.
- Tag in link blue with no click target (Inspect is right-click only).
- Dangling section: an obvious place for "Remove all dangling (<size>)".
- No Pull action although the repository supports it and the empty state promises it
  (U2.11).

---

## U7.5 — Context menus differ per resource without reason

- **Severity:** P2 · **Effort:** S
- **Files:** `ui/screens/components/ContainerContextMenu.kt` (logs/restart/pause/stop/
  inspect/copy id/delete), `ImageContextMenu.kt` (inspect/copy id/delete),
  `VolumeContextMenu.kt` (inspect/copy name/delete), `NetworkContextMenu.kt`
  (inspect/copy id/delete — correctly disabled for system networks, the only menu that
  does it right), container compact overflow (`ContainerRendering.kt:743-830`: no
  restart/inspect/copy)

Inconsistent item sets and inconsistent disabled handling (U1.6). Users learn one menu
and are surprised by the next.

**Fix:** one `ResourceContextMenu` builder with a shared order: *Open/Inspect · Copy ID ·
──── · state actions · ──── · Delete (disabled + reason when not allowed)*.

---

## U7.6 — Containers is the only screen without a constructive primary action

- **Severity:** P3 · **Effort:** — (design note, resolves with U1.4)
- **Files:** `VolumesScreen.kt:273` ("Create volume"), `NetworksScreen.kt:258`
  ("Create network") vs `ContainersScreen.kt:604` ("Delete All")

Volumes and Networks put a blue *Create* button top-right; Containers puts a red
*Delete All* in the same slot. Same position, opposite intent — the muscle memory from
one screen is dangerous on the other. Until a "Run container" flow exists, the slot on
Containers should be empty or hold a neutral action (Refresh, Prune stopped).
