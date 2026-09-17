package com.asnidev.trailkeeperoffgrid.ui.map

import com.asnidev.trailkeeperoffgrid.data.Geo
import com.asnidev.trailkeeperoffgrid.data.local.StructureEntity
import com.asnidev.trailkeeperoffgrid.data.local.TaskEntity
import com.asnidev.trailkeeperoffgrid.data.local.TrackEntity
import com.asnidev.trailkeeperoffgrid.data.local.TrailEntity
import com.asnidev.trailkeeperoffgrid.data.local.TrailReportEntity
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds

/** Turns the Room rows into GeoJSON the MapLibre `GeoJsonSource`s consume. */
object MapGeo {

    /** Which org-wide assets belong to a project's working area, plus the
     * camera extent to frame it. See [projectScope]. */
    data class ProjectScope(
        val trailIds: Set<String>,
        val structureIds: Set<String>,
        val bounds: LatLngBounds?,
    )

    fun trailFeatures(trails: List<TrailEntity>, dimmed: Set<String> = emptySet()): String =
        featureCollection(
            trails.mapNotNull { t ->
                t.geometryJson?.let { g ->
                    feature(
                        g,
                        """"id":${quote(t.id)},"kind":"trail","status":${quote(t.status)},"dim":${t.id in dimmed}""",
                    )
                }
            }
        )

    fun taskFeatures(tasks: List<TaskEntity>): String =
        featureCollection(
            tasks.mapNotNull { t ->
                t.geometryJson?.let { g ->
                    feature(
                        g,
                        """"id":${quote(t.id)},"kind":"task","priority":${quote(t.priority)},"status":${quote(t.status)}""",
                    )
                }
            }
        )

    fun structureFeatures(
        structures: List<StructureEntity>,
        dimmed: Set<String> = emptySet(),
    ): String =
        featureCollection(
            structures.mapNotNull { s ->
                s.geometryJson?.let { g ->
                    val colorProp = if (s.color.isNotBlank()) ""","color":${quote(s.color)}""" else ""
                    feature(
                        g,
                        """"id":${quote(s.id)},"kind":"structure","type":${quote(s.structureType)},"status":${quote(s.status)},"dim":${s.id in dimmed}$colorProp""",
                    )
                }
            }
        )

    fun reportFeatures(reports: List<TrailReportEntity>): String =
        featureCollection(
            reports.mapNotNull { r ->
                r.geometryJson?.let { g ->
                    feature(
                        g,
                        """"id":${quote(r.id)},"kind":"report","status":${quote(r.status)},"severity":${quote(r.severity)},"resolved":${r.resolvedAt != null}""",
                    )
                }
            }
        )

    fun trackFeatures(tracks: List<TrackEntity>): String =
        featureCollection(
            tracks.mapNotNull { t ->
                t.geometryJson?.let { g ->
                    feature(g, """"id":${quote(t.id)},"kind":"track","source":${quote(t.source)}""")
                }
            }
        )

    /** Bounds over every trail / track line, task point and structure point. */
    fun bounds(
        trails: List<TrailEntity>,
        tasks: List<TaskEntity>,
        structures: List<StructureEntity> = emptyList(),
        tracks: List<TrackEntity> = emptyList(),
    ): LatLngBounds? {
        val pts = ArrayList<LatLng>()
        (
            trails.mapNotNull { it.geometryJson } +
                tasks.mapNotNull { it.geometryJson } +
                structures.mapNotNull { it.geometryJson } +
                tracks.mapNotNull { it.geometryJson }
        ).forEach { collectPoints(it, pts) }
        return safeBounds(pts)
    }

    /**
     * A project doesn't own trails or structures (they're org-wide, shared
     * between projects) - a trail/structure counts as "this project's" when:
     *  A. it was created while this project was active (its `projectId`
     *     matches, or a task attaches to it via `nearestTrailId`), or
     *  B. it's within [radiusM] metres of one of this project's own lines
     *     (its recorded/imported tracks, plus its own (A) trails).
     * [radiusM] is user-configurable ([com.asnidev.trailkeeperoffgrid.data.MapScopePrefs])
     * since how close is "close enough" depends on the terrain/trail density.
     */
    fun projectScope(
        projectId: String,
        trails: List<TrailEntity>,
        tasks: List<TaskEntity>,
        structures: List<StructureEntity>,
        tracks: List<TrackEntity>,
        radiusM: Double,
    ): ProjectScope {
        val attachedTrailIds = tasks.mapNotNull { it.nearestTrailId }.toSet()
        val ownTrailIds = trails.filter { it.projectId == projectId }.map { it.id }.toSet() + attachedTrailIds

        val ownLines =
            (
                tracks.map { it.geometryJson } +
                    trails.filter { it.id in ownTrailIds }.map { it.geometryJson }
            ).map { latLonPairs(it) }.filter { it.size >= 2 }

        fun withinRadius(points: List<Pair<Double, Double>>): Boolean =
            ownLines.isNotEmpty() &&
                points.any { (lat, lon) -> ownLines.any { Geo.pointToPolylineM(lat, lon, it) <= radiusM } }

        val trailIds =
            ownTrailIds +
                trails.filter { it.id !in ownTrailIds && withinRadius(latLonPairs(it.geometryJson)) }
                    .map { it.id }

        val ownStructureIds = structures.filter { it.projectId == projectId }.map { it.id }.toSet()
        val structureIds =
            ownStructureIds +
                structures.filter { it.id !in ownStructureIds && withinRadius(latLonPairs(it.geometryJson)) }
                    .map { it.id }

        // Camera extent: everything now considered in-scope, plus the
        // project's own tasks (so an empty-geometry task still gets framed).
        val boundsPts = ArrayList<LatLng>()
        (
            tasks.mapNotNull { it.geometryJson } +
                tracks.mapNotNull { it.geometryJson } +
                trails.filter { it.id in trailIds }.mapNotNull { it.geometryJson } +
                structures.filter { it.id in structureIds }.mapNotNull { it.geometryJson }
        ).forEach { collectPoints(it, boundsPts) }

        val bounds = safeBounds(boundsPts)?.let { padBounds(it, 0.15) }

        return ProjectScope(trailIds, structureIds, bounds)
    }

    // --- helpers ------------------------------------------------------------

    private fun feature(geometryJson: String, propsBody: String): String =
        """{"type":"Feature","geometry":$geometryJson,"properties":{$propsBody}}"""

    private fun featureCollection(features: List<String>): String =
        """{"type":"FeatureCollection","features":[${features.joinToString(",")}]}"""

    private fun quote(s: String) = "\"${s.replace("\"", "\\\"")}\""

    private fun padBounds(b: LatLngBounds, frac: Double): LatLngBounds {
        val north = b.latitudeNorth
        val south = b.latitudeSouth
        val east = b.longitudeEast
        val west = b.longitudeWest
        val latPad = (north - south) * frac + 0.0005
        val lonPad = (east - west) * frac + 0.0005
        return LatLngBounds.from(
            (north + latPad).coerceAtMost(90.0),
            east + lonPad,
            (south - latPad).coerceAtLeast(-90.0),
            west - lonPad,
        )
    }

    /**
     * `LatLngBounds.Builder().build()` throws for fewer than 2 points, even
     * though a single point is a perfectly valid (zero-size) bounds - so
     * build that degenerate case by hand instead of going through Builder.
     */
    private fun safeBounds(pts: List<LatLng>): LatLngBounds? = when {
        pts.isEmpty() -> null
        pts.size == 1 -> {
            val p = pts[0]
            LatLngBounds.from(p.latitude, p.longitude, p.latitude, p.longitude)
        }
        else -> LatLngBounds.Builder().includes(pts).build()
    }

    /** [lat,lon] pairs out of any geometry (Point / LineString / MultiLineString) -
     * unlike [Geo.lineLatLon], also handles the Point geometry structures use. */
    private fun latLonPairs(geometryJson: String?): List<Pair<Double, Double>> {
        if (geometryJson == null) return emptyList()
        val pts = ArrayList<LatLng>()
        collectPoints(geometryJson, pts)
        return pts.map { it.latitude to it.longitude }
    }

    private fun collectPoints(geometryJson: String, into: MutableList<LatLng>) {
        val geom = runCatching { JsonParser.parseString(geometryJson).asJsonObject }.getOrNull() ?: return
        val coords = geom.get("coordinates") as? JsonArray ?: return
        when (geom.get("type")?.asString) {
            "Point" -> pair(coords)?.let(into::add)
            "LineString" -> coords.forEach { p -> pair(p as? JsonArray)?.let(into::add) }
            "MultiLineString" ->
                coords.forEach { line ->
                    (line as? JsonArray)?.forEach { p -> pair(p as? JsonArray)?.let(into::add) }
                }
        }
    }

    private fun pair(a: JsonArray?): LatLng? {
        if (a == null || a.size() < 2) return null
        return LatLng(a[1].asDouble, a[0].asDouble) // GeoJSON is [lon, lat]
    }
}
