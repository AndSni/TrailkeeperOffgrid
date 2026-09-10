package com.asnidev.trailkeeperoffgrid.ui.map

/**
 * The one MapLibre style every map surface uses, and the one the offline
 * downloader ([com.asnidev.trailkeeperoffgrid.data.OfflineMaps]) caches.
 *
 * For now this is OpenFreeMap's hosted "liberty" (free, no key). When there
 * is signal it streams; where a region has been pre-downloaded in Settings
 * it renders from MapLibre's offline store with no network.
 *
 * A fully self-contained vendored style + glyphs + sprite + a local
 * `pmtiles://file://` source is the next step (docs/BLUEPRINT.md §5); the
 * app is on MapLibre 11.8, which supports the `pmtiles://` protocol.
 */
object MapStyle {
    const val URL = "https://tiles.openfreemap.org/styles/liberty"
}
