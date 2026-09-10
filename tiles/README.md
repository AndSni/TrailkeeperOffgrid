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
