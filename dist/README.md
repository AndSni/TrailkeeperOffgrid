# Release APKs

`release.yml` builds a signed release APK on every push to `main` and drops it here:

| File | What |
|------|------|
| `TrailkeeperOffgrid.apk` | Always the latest build. Stable URL: |
| `TrailkeeperOffgrid-<versionName>-<versionCode>.apk` | One archival copy per version. |

**Install (latest):**
<https://github.com/AndSni/TrailkeeperOffgrid/raw/main/dist/TrailkeeperOffgrid.apk>

Also published as a rolling GitHub Release:
<https://github.com/AndSni/TrailkeeperOffgrid/releases/latest/download/TrailkeeperOffgrid.apk>

`update.json` carries the current `versionCode` / `versionName` / changelog so the
app can check for a newer build (Settings → About → Check for update).
