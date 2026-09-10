# Trailkeeper Offgrid — Development Plan

Draft v1 · 2026-09-10 · derived from **Trailkeeper** (`/home/asni/Trailkeeper`,
`git@github.com:AndSni/Trailkeeper.git`).

A single-user, fully offline Android app for monitoring and maintaining trails
where there is no signal. Same field UX as Trailkeeper — map, live GPS, tasks,
route recording, structures, inspections, segment-timed work — but **no
backend, no account, no sync**. Everything lives on the phone. Maps are
pre-downloaded to phone storage. The only bridge to the outside world is
file export/import (GPX, GeoJSON, CSV, a backup zip).

Repo: `git@github.com:AndSni/TrailkeeperOffgrid.git` · local
`/home/asni/TrailkeeperOffgrid`.

---

## 0. Relationship to Trailkeeper

Trailkeeper is **Android field app + FastAPI backend + React web console**, a
crew-collaboration tool where the server owns the data and a hand-rolled
`change_log` sync engine reconciles offline edits.

Trailkeeper Offgrid keeps **only the Android app**, and turns its Room database
from a *sync cache* into the *source of truth*. Roughly:

| Trailkeeper | Trailkeeper Offgrid |
|---|---|
| `backend/` FastAPI + Postgres/PostGIS + Alembic | **deleted** |
| `console/` React + MapLibre GL JS | **deleted** |
| `deploy/` systemd, Cloudflare Tunnel, Caddy | **deleted** |
| JWT auth, invites, org/membership | **deleted** — app opens straight to the project list |
| `SyncRepository` — outbox, snapshot, `/sync/changes` loop | → `LocalRepository` — same method names, Room writes only |
| Room `fallbackToDestructiveMigration`, `exportSchema=false` | real migrations, `exportSchema=true` — data is irreplaceable |
| Derived values via PostGIS (`ST_Length`, `ST_Area`, nearest-trail, rollups) | computed on-device (haversine / shoelace / Room `GROUP BY`) |
| MapLibre style from `tiles.openfreemap.org` (online) | vendored `style.json` + glyphs + sprite, tiles from local `.pmtiles` |
| Task photos uploaded to server disk | photos kept in app storage; **DB cleanup** purges them for done tasks |
| "Your data on your server" | "Your data on your phone" + export/import + backup zip |
| FCM push (stubbed) + APScheduler `inspection_due` / `task_overdue` | local `WorkManager` reminders + notifications |
| Tester APKs via Diawi | signed APK committed to `dist/` in the repo + a `latest` GitHub Release |

**What is copied near-verbatim** (this is most of the app):

- All Compose UI under `android/app/src/main/java/com/asnidev/trailkeeper/ui/`
  — `ProjectListScreen`, `ProjectDetailScreen` (Tasks · Map · Route · Trails ·
  Work · Structures · Discussion tabs), `TaskPhotos`, `SegmentWorkTab` +
  `SegmentMeasureScreen`, `RouteTab`, `StructuresTab`, `PointPickerScreen`,
  `theme/`.
- Room entities / DAOs / mappers under `data/local/` (minus the sync tables).
- `record/TrackRecorder` + `record/TrackRecordingService` (foreground GPS) —
  unchanged.
- `ui/map/ProjectMapView` + `ui/map/MapGeo` — unchanged except the style source.
- `data/ImageCompress` — unchanged (photos now land in app storage, not a POST).
- Gradle wrapper, plugin versions, `compileSdk 36` / `minSdk 26`,
  `keystore.properties` release signing, MapLibre + `play-services-location`.

---

## 1. Product principles

- **Offline is not a mode, it is the whole app.** No code path requires the
  network. The one online moment is an opportunistic "check for a newer APK"
  and "download a map region" in Settings.
- **The phone is the only copy.** So: real Room migrations, WAL, a visible
  "last backup" line, one-tap full export, an auto-backup nag.
- **Field-first UX.** One-handed, glove-friendly, big targets, high contrast,
  keep-screen-on on Map and Record, haptic confirms. This is now the *only*
  client, so it gets an explicit polish pass.
- **A project is still the unit.** Even single-user, a project scopes the map
  extent, the task list, and every export. Keep projects.
- **Files are the interoperability layer.** GPX / GeoJSON / CSV in and out;
  a backup zip is the "server".

### Non-goals for v1

- No sync, no multi-device merge (GPX / GeoJSON / zip export is the bridge).
- No account, no cloud, no telemetry.
- No push notifications / FCM — local reminders only.
- No web console.
- No routing / navigation engine — display routes and do track-back, don't
  compute them.
- No live weather (needs a network).
- No iOS.

---

## 2. Repository & build

### 2.1 New repo, fresh history

```
git init /home/asni/TrailkeeperOffgrid
# copy android/ and docs/ from /home/asni/Trailkeeper, then strip (P0)
git remote add origin git@github.com:AndSni/TrailkeeperOffgrid.git
```

Not a fork of Trailkeeper — a clean first commit of the ported tree, so the
backend/console history doesn't come along.

Layout:

| Path | What |
|------|------|
| `android/` | the app — the only code |
| `docs/` | this plan, plus a short user guide (install by URL, download maps, backup) |
| `dist/` | **signed release APKs, committed** (see §2.3) |
| `tiles/` | Planetiler recipe + region manifest for the prebuilt `.pmtiles` (see §5) |
| `.github/workflows/` | `android.yml` (build + lint + unit test), `release.yml` (build → sign → publish APK), optional `tiles.yml` |

Port `.claude/settings.json` from Trailkeeper minus the Python/pytest/alembic
allow-list; keep `./gradlew`, `git`, `gh`, and the shell utilities.

### 2.2 App identity

- `applicationId` → `com.asnidev.trailkeeperoffgrid` (installs side-by-side with
  `com.asnidev.trailkeeper`).
- Package rename `com.asnidev.trailkeeper` → `com.asnidev.trailkeeperoffgrid`
  (one IDE refactor; ~40 files).
- New app label "Trailkeeper Offgrid", new launcher icon tint.
- New upload/signing key. Back it up next to the SharpRight / Trailkeeper
  keystores (same discipline as the APK-distribution setup).
- `versionCode` / `versionName` start at `1` / `0.1.0`.

### 2.3 APK-in-repo releases

Requirement: **a git push must carry the compiled, installable APK, un-archived,
so a user can point at a URL and download it.**

`release.yml`, on push to `main` (and on a `v*` tag):

1. Decode the base64 keystore from repo secret `RELEASE_KEYSTORE_B64`; write
   `android/keystore.properties` from `RELEASE_KEYSTORE_*` secrets.
2. `./gradlew :app:assembleRelease` (JBR/JDK pinned as in Trailkeeper).
3. Copy the signed APK to **both**:
   - `dist/TrailkeeperOffgrid.apk` (stable name) and
     `dist/TrailkeeperOffgrid-<versionName>-<versionCode>.apk` (archival name),
     then `git commit` + `git push` with `[skip ci]`.
   - a GitHub Release tagged `latest` (rolling) — asset
     `TrailkeeperOffgrid.apk`.
4. Write `dist/update.json` — `{ versionCode, versionName, apkUrl, changelog }`
   — so the app can self-check for updates against its own repo (see §7.6).

Install URLs documented in the README:

- Raw, always-in-git: `https://github.com/AndSni/TrailkeeperOffgrid/raw/main/dist/TrailkeeperOffgrid.apk`
- Release, versioned: `https://github.com/AndSni/TrailkeeperOffgrid/releases/latest/download/TrailkeeperOffgrid.apk`

Notes / decisions:

- **No Git LFS** — `raw.githubusercontent.com` serves an LFS *pointer*, not the
  binary, which breaks "point at a URL and download". Commit the real bytes.
- `.gitattributes`: `dist/*.apk binary` (no diff, no EOL munging).
- History bloat: the stable-name APK is overwritten each build; git still keeps
  every blob (~15–40 MB each). At a single-dev cadence this is fine for a long
  time; if history gets heavy, squash `dist/` history or drop the per-version
  archival copy and rely on Releases for old versions.
- CI must not loop: the APK-commit step uses `[skip ci]` and only runs when the
  triggering commit isn't itself the bot's.

---

## 3. Data layer — de-server it (the bulk of the work)

### 3.1 Delete

- `network/ApiClient`, `network/TrailkeeperApi`, most of `network/Dtos` (keep
  the DTO shapes only where they still model something, e.g. GPX import).
- `data/AuthRepository`, `data/TokenStore`, `data/Session`, `data/IdentityStore`.
- `ui/auth/` entirely.
- Room: `OutboxEntity` + `OutboxDao`, `SyncStateEntity` + `SyncStateDao`,
  `ProjectSyncEntity`. Remove `withTransaction` outbox drains.
- Retrofit / OkHttp / gson-for-network / coil's authenticated `ImageLoader`
  (Coil now loads `file://` from app storage — no auth needed).

### 3.2 `SyncRepository` → `LocalRepository`

Keep the **public method surface identical** (`createTask`, `createTaskAt`,
`setTaskStatus`, `editTask`, `moveTask`, `createTrail`, `saveWalkedTrail`,
`logSegmentWork`, `segmentRollup`, `postMessage`, `deleteMessage`,
`updateStructure`, `deleteStructure`, `createTrack`, …) so the ViewModels barely
change. Bodies collapse to: mint a UUID, write Room, return. No outbox, no
`base_updated_at`, no conflict handling.

- **IDs** — keep client-side minting (already the case for offline). Use a
  time-ordered UUID (UUIDv7-style) so list ordering stays stable; a helper in
  `data/Ids.kt`.
- **`org_id` / `created_by`** — *keep the columns*, default `org_id = "local"`
  and `created_by = <username-or-"me">` (§6). Stripping them touches every
  entity + mapper + query for no user-visible gain; keep-with-defaults is the
  smaller, safer diff. A later cleanup pass can remove `org_id` if wanted.
- **Timestamps** — keep `updatedAt`; it now just orders local edits and feeds
  "recently changed".

### 3.3 On-device derived values (previously PostGIS)

| Value | Was | Now |
|---|---|---|
| Trail / track length | `ST_Length(geog)` | haversine sum over vertices — `MapGeo` helper (already used in `SegmentMeasureScreen`) |
| Polygon area (m² job types) | `ST_Area(geog)` | shoelace on an equirectangular projection — already in `SegmentMeasureScreen` |
| Task → nearest trail (75 m auto-attach) | `ST_Distance` | point-to-polyline distance in Kotlin; iterate trails in the project, keep min < 75 m |
| Segment-work rollups (`group_by=job_type\|trail\|member\|week`) | SQL view + aggregate query | Room `@Query` with `GROUP BY`, or in-Kotlin fold in `SegmentWorkViewModel`; derived rate / throughput / person-hours / vs-expected computed per row as today |
| Task progress % (closed / total, weighted by estimate) | server | Room aggregate |

### 3.4 Room hygiene

- `version = 1`, `exportSchema = true`, schema JSON committed under
  `android/app/schemas/`.
- Drop `fallbackToDestructiveMigration()`. Every future schema change ships a
  `Migration` + a migration test (`MigrationTestHelper`, Robolectric or
  instrumented). Add this test harness in P1 so it's never skipped.
- Enable WAL (`setJournalMode(WRITE_AHEAD_LOGGING)` — Room default on modern
  API, assert it).
- Seed the 8 default job types locally on first run (Trailkeeper seeds them on
  `register`; move that list into a `DefaultJobTypes.kt` and insert on empty DB).

### 3.5 Photos

- `data/ImageCompress` unchanged. Compressed JPEG is written to
  `context.getExternalFilesDir("photos")/<taskId>/<photoId>.jpg` instead of a
  multipart POST.
- `TaskPhotoEntity` gains `localPath` (drop `serverKey` / `sha256` upload
  bookkeeping, or keep `sha256` for dedupe + export manifest).
- Coil loads `file://` paths directly.
- Stamp capture GPS + time + bearing into the JPEG's EXIF (land-manager reports
  want self-locating photos) — Trailkeeper only *read* EXIF for rotation.

---

## 4. Screens & navigation changes

- **App entry** — `MainActivity` drops `Session.start()` / auth gating.
  `TrailkeeperApp` goes straight to `ProjectListScreen`; the `AuthState` `when`
  is removed. First run with no projects shows a "Create your first project"
  empty state.
- **Settings screen — new.** Reached from the project list overflow. Sections:
  - **Profile** — optional display name (§6).
  - **Offline maps** — region list, download / update / delete, storage used
    (§5).
  - **Storage & cleanup** — DB size, photo dir size, per-region tile size;
    **"Clear photos of completed tasks"**, **"Trim old recorded tracks"** (§7).
  - **Backup & restore** — export workspace zip, restore, "last backup N days
    ago", weekly nag toggle (§7).
  - **Recording** — GPS fix cadence (1 s / 5 s / 10 s / 30 s low-power),
    keep-screen-on toggle, auto-checkpoint interval.
  - **Reminders** — inspection-due / task-overdue local reminder toggles (§7.5).
  - **About** — version, "check for update" (§7.6), licenses.
- **Notifications** — `NotificationsScreen` + `NotificationRepository` are
  repurposed as a **local reminder inbox** (no polling a server); the bell +
  `BadgedBox` on the project list stays.
- **Discussion tab** — keep it. It becomes a **single-user field notebook /
  log** per project and per task (still useful; still exports). "Members" name
  resolution just uses the display name or "Me".
- **Map** — style source swap only (§5); all layers (trails / tracks / tasks /
  structures / puck / project-scope dimming) unchanged.

---

## 5. Offline maps

Blueprint §9 of Trailkeeper already chose **MapLibre + self-hosted `.pmtiles`
built with Planetiler**. Offgrid keeps that, minus the Caddy range-server —
the file lives on the phone.

### 5.1 Renderer & style

- MapLibre Native Android (bump to latest 11.x if the PMTiles protocol needs
  it — spike first, see §5.5).
- **Vendor a self-contained `style.json`** into `assets/` — OpenFreeMap
  "liberty" is a fine base (OpenMapTiles schema); its glyphs (PBF font ranges)
  and sprite (PNG + JSON) must also be bundled in `assets/` and referenced with
  `asset://` / `file://` URLs. License check: OpenMapTiles schema (BSD-3),
  liberty style (MIT/BSD), fonts (OFL) — all redistributable.
- One style, `pmtiles://` (or `mbtiles://`) source pointing at the active
  region file in app storage.

### 5.2 "Offline maps" settings screen

- A **region manifest** — `tiles/regions.json`, bundled in `assets/` and
  optionally refreshable from the repo raw URL when online:
  `[{ id, name, bbox, url, bytes, sha256, tilesVersion }]`.
- Prebuilt regions shipped as **raw-URL release assets** in this same repo
  (built by `tiles.yml` or a documented local Planetiler run against a
  Geofabrik extract): **Latvia** first, then **Baltics**.
- Download via `WorkManager` (or `DownloadManager`) — resumable, sha256-checked,
  writes to `getExternalFilesDir("tiles")/<id>.pmtiles`. Progress + cancel in
  the UI. Update = re-download if `tilesVersion` changed. Delete = remove file +
  free-space readout.
- Optional: **import a raster `.mbtiles`** the user made in QGIS / Mobile Atlas
  Creator, for regions with no vector build. MapLibre raster source from a local
  file. Cheap, and a common off-grid escape hatch.

### 5.3 Terrain (optional, P5)

- A separate **contour / hillshade `.pmtiles`** overlay toggled as a layer
  (Planetiler contour profile, or a prebuilt Baltics contour set). Off-grid
  trail work wants relief; keep it a toggle so the base download stays small.

### 5.4 Bundled starter tiles

- Ship a **small low-zoom world or Baltics-region `.pmtiles`** inside the APK
  (z0–z7, a few MB) so the map isn't blank on first run before any download.

### 5.5 Spike (do this first in P2)

Confirm `org.maplibre.gl:android-sdk` renders a local `pmtiles://` file source
on Android. If the built-in PMTiles protocol isn't there in the pinned version:
bump MapLibre; or convert PMTiles → MBTiles and use the MBTiles file source; or
run a tiny in-process content-provider / localhost shim that range-serves the
PMTiles file. Decide before building the download UI.

---

## 6. Optional username

- DataStore preference `display_name` (single string, no validation, may be
  empty).
- Empty → all attributions read **"Me"**.
- Threaded through: `created_by` on task / trail / structure / track /
  segment-work, `WorkLog` author, `Message` author, GPX `<author>` /
  `<metadata><author>`, export file names and the backup manifest.
- It is purely a label — for exported files and for when a GPX / report is
  handed to someone else. No identity, no accounts.

---

## 7. Data safety, cleanup, portability

### 7.1 DB cleanup — "clear photos of completed tasks"

Settings → Storage & cleanup → **Clear photos of completed tasks**:

- Scan `TaskEntity WHERE status = 'done'` (optional "older than N days" filter).
- For each: delete the JPEG files under `photos/<taskId>/`, delete the
  `TaskPhotoEntity` rows.
- **The task, its work logs, comments, geometry, job type, history all remain.**
  Only the images go.
- Show a pre-confirm summary ("38 photos across 12 tasks · 290 MB"). Destructive,
  so it uses the project's existing press-and-hold / two-step confirm rule.

### 7.2 Trim old recorded tracks

- `TrackEntity` keeps a big `points` JSON (lat/lon/ele/t). Trailkeeper already
  keeps that out of its sync stream; here, offer **"Trim recorded tracks older
  than N days"** — drop the `points` JSON, keep the `LineString` geometry +
  stats (distance, moving time, elevation). Map still draws them; GPX re-export
  falls back to the simplified line.

### 7.3 Storage readout

- DB file size · `photos/` size · each `tiles/*.pmtiles` size, each individually
  deletable. Plain list, always visible in Settings.

### 7.4 Backup & restore (this replaces "your data on your server")

- **Export workspace** → a `.zip` (via SAF, user picks the location):
  - `trails.geojson`, `tasks.geojson`, `structures.geojson`, `tracks.geojson`
  - `work_logs.csv`, `segment_work.csv`, `segment_rollup.csv`,
    `inspections.csv`, `structures.csv` (mirror the console's export sets)
  - `photos/<taskId>/…`
  - `manifest.json` (app version, schema version, display name, counts, export
    timestamp)
- **Restore** — read that zip back into an empty (or merged) DB.
- **Per-entity** GPX export for any trail / track; GeoJSON export for any layer.
- **Import** — GPX → Trail or Track (parity with the console's
  `POST /trails/import-gpx`, reimplemented with a small Kotlin GPX
  reader/writer); GeoJSON import.
- Settings shows **"Last backup: 6 days ago"** and, past a threshold, a
  one-line nag on the project list. Optional weekly auto-backup to the
  SAF-picked folder.
- `android:allowBackup="true"` stays as a secondary net; document the
  `adb backup` path in the user guide.

### 7.5 Local reminders (replaces APScheduler jobs)

- `WorkManager` periodic worker (~daily) + local notifications:
  - **inspection due** — a structure past its inspection interval.
  - **task overdue** — an open high / urgent task older than N days.
- Feeds the repurposed local reminder inbox (`NotificationsScreen`).
- Toggles per type in Settings.

### 7.6 Self-update check

- "About → Check for update" (and an optional silent check when the app notices
  it's online): GET `dist/update.json` from the repo raw URL, compare
  `versionCode`, offer to open the APK URL in the browser / installer.
- Never blocks anything; purely opportunistic.

---

## 8. Off-grid field features worth adding (research-driven)

From a scan of what backcountry / field-data apps (onX Backcountry, Gaia GPS,
BackCountry Navigator, Trailforks trail-reporting, Felt/Fulcrum field
collection) treat as table stakes and Trailkeeper lacks:

### 8.1 High value, low cost — do in P4

- **Track-back / return-to-start.** For a recorded or imported route, a map
  overlay + a compass arrow + a live "off-route by 40 m" / "1.2 km to start"
  readout. `TrackRecorder` already holds the point list.
- **Go-to-coordinates + multi-format coordinate display** — decimal, DMS, UTM,
  MGRS. A formatter + a "jump to coords" box on the map. Land managers and
  crews communicate in these.
- **Single-waypoint share** — export one task / structure / dropped pin as a
  tiny GPX or a `geo:` URI / plus-code, so a crew member on another phone gets
  the exact spot with no network. Pairs with import.
- **Standalone map ruler** — ad-hoc distance / area measurement not tied to a
  segment-work record. `SegmentMeasureScreen` already has the geometry math;
  expose it as its own mode.
- **Bearing & distance to a selected feature** — "navigate to this task" without
  a routing engine.
- **Recording robustness** — configurable fix cadence, a low-power
  point-every-30 s mode, periodic auto-checkpoint of the in-progress track to
  Room (survive a process kill), unsaved-track guard on Stop.
- **Keep-screen-on + a field-contrast pass** on Map and Record — big targets,
  high-contrast palette variant, haptic confirms. This is now the only client;
  it earns the polish.
- **Elevation profile** on the track / trail detail (from recorded GPS
  elevation).

### 8.2 Core to "monitoring" (vs "work") — P5

- **Trail condition report**, decoupled from a task: a quick log on a trail or a
  point — status `passable / caution / closed`, type
  `blowdown / washout / bridge-out / overgrown / erosion / sign`, severity,
  photo, GPS, note — recorded even when no work is planned. Trailkeeper only
  models this as a Task-with-a-job-type; add a light `TrailReport` entity (or a
  task `kind = report`) with its own map layer, list, and export column. This is
  arguably the heart of "trail *monitoring*".

### 8.3 Nice, optional

- **Contour / hillshade overlay** (§5.3).
- **Sun / daylight-remaining** for the current location + date (offline
  formula) — helps decide whether to start another segment.
- **Coordinate graticule + scale bar** on the map (for map-screenshot reports).
- **Offline search** of your own data (task / structure / trail names) — a Room
  `LIKE` query; matters with no console to browse.

### 8.4 Explicitly skipped

- Live weather, avalanche forecasts, SOS / satellite messaging (hardware +
  network), online basemap fallback.

---

## 9. Roadmap

Each phase compiles, installs on a real device, and is usable.

| Phase | Focus | Ships | ~Size |
|---|---|---|---|
| **P0** | Fork & strip | New repo + fresh history; copy `android/` + `docs/`; delete backend/console/deploy; package + `applicationId` rename; new signing key; `android.yml` + `release.yml` (build → sign → commit `dist/*.apk` + `latest` Release + `update.json`); README with the two install URLs. Auth/network stubbed; app still builds. | 2–4 d |
| **P1** | De-server the data layer | Rip out auth + `network/` + outbox/sync tables. `SyncRepository` → `LocalRepository` (same surface, Room-only). On-device derived values (lengths, areas, nearest-trail, rollups). Real migrations + `exportSchema=true` + a migration-test harness. Local job-type seed. App opens straight to the project list; full local CRUD across tasks / trails / structures / segment-work / tracks / notebook. | 1–2 wk |
| **P2** | Offline maps | PMTiles spike (§5.5); vendored `style.json` + glyphs + sprite; local `pmtiles://` source; **Settings → Offline maps** (region manifest, resumable sha256-checked download, list/update/delete, storage readout); Latvia + Baltics prebuilt as raw-URL release assets (`tiles.yml` or documented Planetiler run); raster `.mbtiles` import fallback; bundled low-zoom starter tiles. | 1–2 wk |
| **P3** | Settings: identity, cleanup, backup | DataStore `display_name` threaded through attributions + GPX author + export names. **DB cleanup** (purge photos of done tasks, trim old track points, storage breakdown). **Backup/Restore** (workspace zip export/restore via SAF, "last backup" line + weekly nag). Per-entity GPX/GeoJSON export + GPX/GeoJSON import. | 1 wk |
| **P4** | Off-grid field features | Track-back / return-to-start; go-to-coords + DMS/UTM/MGRS display; standalone map ruler; bearing-to-feature; single-waypoint GPX/`geo:` share; recording robustness (cadence, checkpoint, unsaved guard); keep-screen-on + field-contrast pass; elevation profile; local inspection-due / task-overdue reminders (`WorkManager`). | 1–2 wk |
| **P5** | Trail condition reporting | Lightweight `TrailReport` (status / type / severity / photo / GPS), its own map layer + list + export column. Optional contour/hillshade overlay toggle; optional daylight-remaining, graticule, scale bar, offline search. | 3–5 d |
| **P6** | Harden & field beta | Real-device runs (the Sony XQ-CC54 / HQ-series devices already in rotation); battery soak on a live recording; migration tests green; tag `v0.1.0`; user guide (install by URL, download a region, back up). | ongoing |

**MVP = end of P3** — fully offline: local data, offline maps, username,
cleanup, backup. P4–P5 are what make it better than a paper notebook + a
generic GPS app in the field.

---

## 10. Risks & decisions to lock

| # | Item | Recommendation |
|---|---|---|
| 1 | **PMTiles on MapLibre Native Android** in the pinned SDK | Spike in P2 before building the download UI. Fallbacks: bump MapLibre; PMTiles→MBTiles; localhost/content-provider range shim. |
| 2 | **Self-contained style packaging** — style + glyphs + sprite must all be local | Vendor OpenFreeMap "liberty" assets into `assets/`; confirm licenses (BSD/MIT/OFL — OK). |
| 3 | **APK-in-git history bloat** | Overwrite the stable-name APK in `dist/` each build; no LFS (breaks raw URL); accept blob growth, revisit with a history squash if needed. Keep old versions as GitHub Release assets. |
| 4 | **Room migrations from here on** — data is the only copy | `exportSchema=true`, committed schema JSON, a `MigrationTestHelper` harness added in P1, a tested `Migration` for every change. |
| 5 | **`org_id` / `created_by`** — keep or strip | Keep with local defaults (`"local"` / display-name) for a small port diff; optional schema cleanup later. |
| 6 | **Region tile hosting** | GitHub Release assets in this same repo (one place, raw-URL friendly). Reconsider the user's static box only if asset size limits bite. |
| 7 | **Trail condition report** — new entity vs. `task.kind` | New light `TrailReport` entity; it has a different lifecycle (no assignee, no estimate) and is the product's "monitoring" core. |
| 8 | **CI release loop** | `[skip ci]` on the APK-commit; guard on committer. |
| 9 | **Package rename scope** | ~40 files, one IDE refactor + `applicationId` + manifest + Gradle. Do it in P0 while the tree is small. |

---

## 11. First concrete steps (P0 checklist)

1. `git init /home/asni/TrailkeeperOffgrid`; copy `android/` and `docs/` from
   `/home/asni/Trailkeeper`; drop `backend/`, `console/`, `deploy/`,
   `scripts/`, `.github/workflows/backend.yml`, `.github/workflows/console.yml`.
2. Package refactor `com.asnidev.trailkeeper` → `com.asnidev.trailkeeperoffgrid`;
   `applicationId`, `namespace`, manifest, `settings.gradle.kts`
   `rootProject.name`.
3. Strip `buildConfigField` API URLs; delete `network/ApiClient` usages behind a
   temporary no-op so it compiles.
4. New launcher label + icon tint; `versionName = "0.1.0"`.
5. Generate the release keystore; add `RELEASE_KEYSTORE_B64` +
   `RELEASE_KEYSTORE_*` GitHub secrets; back the keystore up with the others.
6. `android.yml` (assembleDebug + lint + unit tests) and `release.yml`
   (assembleRelease → sign → `dist/` commit + `latest` Release + `update.json`).
7. README: what it is, the two install URLs, "download a map region first",
   "back up regularly".
8. First push → confirm `dist/TrailkeeperOffgrid.apk` is fetchable at the raw
   URL and installs on a device.
