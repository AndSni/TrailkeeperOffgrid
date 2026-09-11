# Offline map tiles

Trailkeeper Offgrid renders MapLibre vector tiles from a local `.pmtiles`
file on the phone — there is no tile server. This directory holds the
recipe and the region manifest; the built `.pmtiles` files themselves are
published as GitHub **Release** assets (too large for git), referenced by
`regions.json`.

## Build a region (local, one-off)

```bash
# ~1 GB RAM, a few minutes for a country.
wget https://download.geofabrik.de/europe/latvia-latest.osm.pbf
java -Xmx1g -jar planetiler.jar \
  --download --osm-path=latvia-latest.osm.pbf \
  --output=latvia.pmtiles --force
gh release create tiles-latvia latvia.pmtiles --title "Tiles: Latvia" --notes "Planetiler build"
```

Then fill the `bytes`, `sha256` (`sha256sum latvia.pmtiles`) and
`tilesVersion` (an ISO date) fields in `regions.json` and commit.

## Still open (P2)

- Confirm `pmtiles://` works as a local file source in
  `org.maplibre.gl:android-sdk` 11.5.2; fallbacks in `docs/BLUEPRINT.md` §5.5.
- Vendor a self-contained `style.json` + glyphs + sprite into
  `android/app/src/main/assets/` (OpenFreeMap "liberty").
- A small z0–z7 starter `.pmtiles` bundled in the APK so the map isn't
  blank before the first download.

## Fallback world outline (no basemap yet)

`android/app/src/main/assets/world_outline.geojson` is a stripped-down copy
of Natural Earth's 1:110m admin-0 countries set (public domain, no
attribution required) — geometry only, coordinates rounded to 2dp, ~170 KB.
It backs `ui/map/WorldOutline.kt`'s `WorldOutlineBackdrop`, a plain Compose
`Canvas` drawing (not a MapLibre layer) shown behind the "No map for this
area yet" message on all three map screens, so a genuinely first-run device
sees faint coastlines/borders instead of a blank rectangle. To refresh it:

```bash
curl -sL -o /tmp/ne110.geojson \
  https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_110m_admin_0_countries.geojson
python3 - <<'PY'
import json
d = json.load(open('/tmp/ne110.geojson'))
def rnd(o, n=2):
    if isinstance(o, list):
        return [round(x, n) for x in o] if o and isinstance(o[0], (int, float)) else [rnd(x, n) for x in o]
    return o
out = {"type": "FeatureCollection", "features": [
    {"type": "Feature", "properties": {"name": f["properties"].get("ADMIN", "")},
     "geometry": {**f["geometry"], "coordinates": rnd(f["geometry"]["coordinates"])}}
    for f in d["features"]
]}
json.dump(out, open("android/app/src/main/assets/world_outline.geojson", "w"), separators=(",", ":"))
PY
```
