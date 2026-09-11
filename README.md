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

Installs alongside Trailkeeper (`applicationId com.asnidev.trailkeeperoffgrid`).

## Status

All of P0, P1, P4 and P5 are done; P2 and P3 are substantially done.

| Phase | State |
|-------|-------|
| **P0 — fork & strip** | ✅ repo, package rename, build, CI (`android.yml` + `release.yml`) |
| **P1 — de-server the data layer** | ✅ auth + network + sync removed; Room is the source of truth; on-device length/area/nearest-trail/rollups; opens straight to the project list |
| **P2 — offline maps** | ◐ Settings → Offline maps: pick a region (Rīga & Vidzeme / Latvia / Baltics), download it to phone storage via MapLibre's offline store (tiles + style + fonts + sprite — a downloaded region is fully self-contained), progress, delete, storage readout. A genuinely first-run device (never online, nothing downloaded) now gets a clear "connect once and download a region" message instead of a silently blank map. A vendored/bundled starter basemap and a self-hosted `pmtiles://` file are still open — see `tiles/README.md`. |
| **P3 — settings: username, DB cleanup, backup/restore** | ◐ Settings: optional display name, "Clear photos of completed tasks", storage breakdown, **full-workspace backup/restore zip** (data + GeoJSON + photos; merge or replace). GPX import/export still to come. |
| **P4 — off-grid field features** | ✅ local reminders (inspection-due / task-overdue → notification inbox) · multi-format coordinates (decimal / DMS / UTM, copy-to-clipboard) · **track-back** — live distance+bearing to a route's start while recording, and a one-shot "bearing to start" check on any saved route. |
| **P5 — trail condition reporting** | ✅ `trail_reports` entity (status / kind / severity / note / GPS / photos), logged from the Route tab, shown as a coloured map layer, resolve/reopen/delete, a matching "clear resolved report photos" cleanup action. First Room migration (v1→v2); both schema JSONs committed, migration DDL verified against the exported `createSql`. |

Bumped to MapLibre 11.8 (has `pmtiles://` support for the next map step).
The map tab still points at an online style, but any region you download in
Settings is then served from local storage with no network.

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
