package com.asnidev.trailkeeperoffgrid.data

import android.content.Context
import com.asnidev.trailkeeperoffgrid.ui.map.MapStyle
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition

/**
 * Pre-download map areas to phone storage so the map works with no signal
 * (Settings → Offline maps). Uses MapLibre's own offline store: a region is
 * a bounding box + zoom range of the shared [MapStyle], fetched once while
 * online and then served from local SQLite forever.
 *
 * A self-hosted `pmtiles://file://` vector file is the planned next step
 * (docs/BLUEPRINT.md §5) — it needs a vendored style and prebuilt tiles.
 */
object OfflineMaps {
    /** A named area the user can tap to download. Loaded from
     * `assets/offline_regions.json`; bbox is [west, south, east, north].
     * [continent] is blank for the small hand-picked "quick" entries. */
    data class Preset(
        val id: String,
        val name: String,
        val continent: String,
        val west: Double,
        val south: Double,
        val east: Double,
        val north: Double,
    ) {
        val bounds: LatLngBounds
            get() = LatLngBounds.from(north, east, south, west)
    }

    data class DownloadedRegion(
        val id: Long,
        val name: String,
        val complete: Boolean,
        val completedResources: Long,
        val requiredResources: Long,
        val sizeBytes: Long,
    )

    sealed interface Event {
        data class Progress(
            val completed: Long,
            val required: Long,
            val bytes: Long,
            val precise: Boolean,
        ) : Event
        data object Complete : Event
        data class Failed(val message: String) : Event
        data class TileLimit(val limit: Long) : Event
    }

    // Zoom 4..14 keeps a country-sized area to a few hundred MB while still
    // being detailed enough for trail navigation.
    private const val MIN_ZOOM = 4.0
    private const val MAX_ZOOM = 14.0
    private const val TILE_LIMIT = 200_000L
    private val gson = Gson()

    /** The short curated list shown directly in Settings → Offline maps. */
    fun quickPresets(context: Context): List<Preset> = presetArray(context, "quick")

    /** Every country (Natural Earth 1:110m admin-0, public domain), for the
     * "Browse all countries" screen. Already sorted continent-then-name in
     * the asset, with the app's home continent first. */
    fun countries(context: Context): List<Preset> = presetArray(context, "countries")

    private var cachedJson: com.google.gson.JsonObject? = null

    private fun regionsRoot(context: Context): com.google.gson.JsonObject? {
        cachedJson?.let { return it }
        val json = runCatching {
            context.assets.open("offline_regions.json").bufferedReader().use { it.readText() }
        }.getOrNull() ?: return null
        val root = runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull()
        cachedJson = root
        return root
    }

    private fun presetArray(context: Context, key: String): List<Preset> {
        val arr = regionsRoot(context)?.getAsJsonArray(key) ?: return emptyList()
        return runCatching {
            arr.map { el ->
                val o = el.asJsonObject
                val bb = o.getAsJsonArray("bbox")
                Preset(
                    id = o.get("id").asString,
                    name = o.get("name").asString,
                    continent = o.get("continent")?.asString ?: "",
                    west = bb[0].asDouble,
                    south = bb[1].asDouble,
                    east = bb[2].asDouble,
                    north = bb[3].asDouble,
                )
            }
        }.getOrDefault(emptyList())
    }

    suspend fun downloaded(context: Context): List<DownloadedRegion> =
        suspendCancellableCoroutine { cont ->
            val mgr = OfflineManager.getInstance(context.applicationContext)
            mgr.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
                override fun onList(offlineRegions: Array<OfflineRegion>?) {
                    val list = offlineRegions?.toList().orEmpty()
                    if (list.isEmpty()) {
                        if (!cont.isCompleted) cont.resumeWith(Result.success(emptyList()))
                        return
                    }
                    val out = ArrayList<DownloadedRegion>(list.size)
                    var pending = list.size
                    fun maybeFinish() {
                        if (--pending == 0 && !cont.isCompleted) {
                            cont.resumeWith(Result.success(out.sortedBy { it.name }))
                        }
                    }
                    for (r in list) {
                        r.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
                            override fun onStatus(status: OfflineRegionStatus?) {
                                if (status != null) {
                                    out.add(
                                        DownloadedRegion(
                                            id = r.id,
                                            name = nameOf(r),
                                            complete = status.isComplete,
                                            completedResources = status.completedResourceCount,
                                            requiredResources = status.requiredResourceCount,
                                            sizeBytes = status.completedResourceSize,
                                        )
                                    )
                                }
                                maybeFinish()
                            }

                            override fun onError(error: String?) {
                                maybeFinish()
                            }
                        })
                    }
                }

                override fun onError(error: String) {
                    if (!cont.isCompleted) cont.resumeWith(Result.success(emptyList()))
                }
            })
        }

    fun download(context: Context, name: String, bounds: LatLngBounds): Flow<Event> = callbackFlow {
        val app = context.applicationContext
        val mgr = OfflineManager.getInstance(app)
        mgr.setOfflineMapboxTileCountLimit(TILE_LIMIT)

        val definition = OfflineTilePyramidRegionDefinition(
            MapStyle.URL,
            bounds,
            MIN_ZOOM,
            MAX_ZOOM,
            app.resources.displayMetrics.density,
        )
        val metadata = gson.toJson(mapOf("name" to name)).toByteArray()

        var region: OfflineRegion? = null

        mgr.createOfflineRegion(
            definition,
            metadata,
            object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(offlineRegion: OfflineRegion) {
                    region = offlineRegion
                    offlineRegion.setObserver(object : OfflineRegion.OfflineRegionObserver {
                        override fun onStatusChanged(status: OfflineRegionStatus) {
                            trySend(
                                Event.Progress(
                                    completed = status.completedResourceCount,
                                    required = status.requiredResourceCount,
                                    bytes = status.completedResourceSize,
                                    precise = status.isRequiredResourceCountPrecise,
                                )
                            )
                            if (status.isComplete) {
                                trySend(Event.Complete)
                                close()
                            }
                        }

                        override fun onError(error: OfflineRegionError) {
                            trySend(Event.Failed(error.message.ifBlank { error.reason }))
                            close()
                        }

                        override fun mapboxTileCountLimitExceeded(limit: Long) {
                            trySend(Event.TileLimit(limit))
                            close()
                        }
                    })
                    offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
                }

                override fun onError(error: String) {
                    trySend(Event.Failed(error))
                    close()
                }
            },
        )

        awaitClose {
            region?.setDownloadState(OfflineRegion.STATE_INACTIVE)
            region?.setObserver(null)
        }
    }

    suspend fun delete(context: Context, id: Long): Unit = suspendCancellableCoroutine { cont ->
        val mgr = OfflineManager.getInstance(context.applicationContext)
        fun done() { if (!cont.isCompleted) cont.resumeWith(Result.success(Unit)) }
        mgr.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                val target = offlineRegions?.firstOrNull { it.id == id }
                if (target == null) { done(); return }
                target.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                    override fun onDelete() = done()
                    override fun onError(error: String) = done()
                })
            }

            override fun onError(error: String) = done()
        })
    }

    suspend fun totalBytes(context: Context): Long =
        downloaded(context).sumOf { it.sizeBytes }

    private fun nameOf(region: OfflineRegion): String =
        runCatching {
            JsonParser.parseString(String(region.metadata)).asJsonObject.get("name")?.asString
        }.getOrNull() ?: "Region ${region.id}"
}
