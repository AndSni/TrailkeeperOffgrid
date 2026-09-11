# Trailkeeper Offgrid

A **single-user, fully offline** Android app for monitoring and maintaining
trails where there is no signal. Map, live GPS, tasks with photos, route
recording, structures & inspections, and segment-timed work — the whole
Trailkeeper field app, but with **no backend, no account, no sync**.
Everything lives on the phone. Maps are pre-downloaded to phone storage. The
only bridge to the outside world is file export/import (GPX, GeoJSON, CSV, a
backup zip).

Derived from [Trailkeeper](https://github.com/AndSni/Trailkeeper). Full plan
and rationale: [`docs/BLUEPRINT.md`](docs/BLUEPRINT.md).

## Install

No Play Store. Download the APK and open it (allow "install unknown apps"):

- **Latest:** <https://github.com/AndSni/TrailkeeperOffgrid/raw/main/dist/TrailkeeperOffgrid.apk>
- **Release page:** <https://github.com/AndSni/TrailkeeperOffgrid/releases/latest/download/TrailkeeperOffgrid.apk>

`applicationId com.trailkeeperoffgrid.app` — matches the `com.<name>.app`
convention SharpRight (`com.sharpright.app`) and XCMr (`com.xcmr.app`)
already use on Play; the internal code package stays
`com.asnidev.trailkeeperoffgrid` (Gradle `namespace`, untouched by this).

## Status

**v0.2.1** — fixes a crash: a project whose only geo-tagged item was a
single task (e.g. right after adding its first task) threw
`InvalidLatLngBoundsException` on every attempt to open it, since MapLibre's
`LatLngBounds.Builder` refuses fewer than 2 points; `MapGeo` now builds a
degenerate single-point bounds by hand instead. GPX import is now reachable
directly from the Route and Trails tabs (scoped to the open project), not
just Settings. Also a simpler, bolder app icon.

All of P0, P1, P4 and P5 are done; P2 and P3 are substantially done.

| Phase | State |
|-------|-------|
| **P0 — fork & strip** | ✅ repo, package rename, build, CI (`android.yml` + `release.yml`) |
| **P1 — de-server the data layer** | ✅ auth + network + sync removed; Room is the source of truth; on-device length/area/nearest-trail/rollups; opens straight to the project list |
| **P2 — offline maps** | ◐ Settings → Offline maps: a quick list (Rīga & Vidzeme / Latvia / Estonia / Lithuania) plus **"Browse all countries"** — every country in the world, grouped by continent, searchable. Any of them downloads to phone storage via MapLibre's offline store (tiles + style + fonts + sprite — a downloaded region is fully self-contained), with progress, delete, storage readout. A genuinely first-run device (never online, nothing downloaded) now gets a clear "connect once and download a region" message over a faint world-coastline backdrop, instead of a blank rectangle. A self-hosted `pmtiles://` file (needs an external Planetiler run) is the one item still open — see `tiles/README.md`. |
| **P3 — settings: username, DB cleanup, backup/restore** | ◐ Settings: optional display name, "Clear photos of completed tasks", storage breakdown, **full-workspace backup/restore zip** (data + GeoJSON + photos; merge or replace). GPX import/export still to come. |
| **P4 — off-grid field features** | ✅ local reminders (inspection-due / task-overdue → notification inbox) · multi-format coordinates (decimal / DMS / UTM, copy-to-clipboard) · **track-back** — live distance+bearing to a route's start while recording, and a one-shot "bearing to start" check on any saved route. |
| **P5 — trail condition reporting** | ✅ `trail_reports` entity (status / kind / severity / note / GPS / photos), logged from the Route tab, shown as a coloured map layer with its own tap-to-open detail sheet, resolve/reopen/delete, a matching "clear resolved report photos" cleanup action. First Room migration (v1→v2), with a real plain-JVM test harness (`app/src/test/`) — no Robolectric, runs in CI. |

Bumped to MapLibre 11.8 (has `pmtiles://` support for the next map step).
The map tab still points at an online style, but any region you download in
Settings is then served from local storage with no network.

On the project list, **hold a project card for 1 second** to rename it,
change its activity, or delete it (two-step confirm; trails, structures and
trail reports are shared and stay even if the project is deleted).

## Layout

| Path | What |
|------|------|
| `android/` | the app — Kotlin / Jetpack Compose, `minSdk 26` / `targetSdk 36` |
| `android/app/schemas/` | exported Room schema (committed; version bumps need a migration) |
| `dist/` | **committed** signed release APKs + `update.json` |
| `tiles/` | Planetiler recipe + offline-region manifest |
| `docs/` | the development plan |

## Build

```bash
cd android
./gradlew :app:assembleDebug        # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease      # debug-signed unless keystore.properties is present
```

JDK 17+ (CI uses Temurin 21). Android SDK via `android/local.properties`
(`sdk.dir=...`).

### Release signing

`release.yml` writes `android/keystore.properties` from repo secrets
(`RELEASE_KEYSTORE_B64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`,
`RELEASE_KEY_PASSWORD`). Without them the release APK is debug-signed but
still installs. Locally, drop a `keystore.properties` next to
`android/settings.gradle.kts`:

```
storeFile=/path/to/trailkeeper-offgrid.jks
storePassword=…
keyAlias=…
keyPassword=…
```
