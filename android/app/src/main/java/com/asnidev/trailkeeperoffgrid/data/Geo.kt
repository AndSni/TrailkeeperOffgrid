package com.asnidev.trailkeeperoffgrid.data

import com.asnidev.trailkeeperoffgrid.data.local.TrailEntity
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * On-device replacements for the geometry the FastAPI backend used to compute
 * with PostGIS: line length (`ST_Length`), polygon area (`ST_Area`) and
 * nearest-trail attach (`ST_Distance` / `ST_DWithin`). Everything works on
 * WGS-84 lat/lon; distances are metres.
 */
object Geo {
    private const val EARTH_R = 6_371_000.0

    /** Great-circle distance between two points, in metres. */
    fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a =
            sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        return EARTH_R * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    /** Length of a polyline given as [lat,lon] pairs, in metres. */
    fun lineLengthM(points: List<Pair<Double, Double>>): Double {
        var m = 0.0
        for (i in 1 until points.size) {
            val (la1, lo1) = points[i - 1]
            val (la2, lo2) = points[i]
            m += haversineM(la1, lo1, la2, lo2)
        }
        return m
    }

    /** Area of a polygon given as [lat,lon] pairs (ring auto-closed), m².
     * Shoelace on a local equirectangular projection about the centroid. */
    fun polygonAreaM2(points: List<Pair<Double, Double>>): Double {
        if (points.size < 3) return 0.0
        val lat0 = points.sumOf { it.first } / points.size
        val mPerDegLat = 111_320.0
        val mPerDegLon = 111_320.0 * cos(Math.toRadians(lat0))
        val xy = points.map { (la, lo) -> lo * mPerDegLon to la * mPerDegLat }
        var s = 0.0
        for (i in xy.indices) {
            val (x1, y1) = xy[i]
            val (x2, y2) = xy[(i + 1) % xy.size]
            s += x1 * y2 - x2 * y1
        }
        return abs(s) / 2.0
    }

    /** GeoJSON Point string for a lat/lon. */
    fun pointJson(lat: Double, lon: Double): String =
        """{"type":"Point","coordinates":[$lon,$lat]}"""

    /** GeoJSON LineString from [lat,lon] pairs. */
    fun lineStringJson(points: List<Pair<Double, Double>>): String {
        val coords = points.joinToString(",") { (la, lo) -> "[$lo,$la]" }
        return """{"type":"LineString","coordinates":[$coords]}"""
    }

    /**
     * The id of the trail whose line passes within [withinM] metres of
     * ([lat],[lon]), nearest first. Mirrors the backend's 75 m task
     * auto-attach. Null when nothing is close enough.
     */
    fun nearestTrailId(
        lat: Double,
        lon: Double,
        trails: List<TrailEntity>,
        withinM: Double = 75.0,
    ): String? {
        var bestId: String? = null
        var bestDist = withinM
        for (t in trails) {
            val line = t.geometryJson?.let { parseLineLatLon(it) } ?: continue
            val d = pointToPolylineM(lat, lon, line)
            if (d < bestDist) {
                bestDist = d
                bestId = t.id
            }
        }
        return bestId
    }

    /** Minimum distance from a point to a polyline, in metres. */
    fun pointToPolylineM(lat: Double, lon: Double, line: List<Pair<Double, Double>>): Double {
        if (line.isEmpty()) return Double.MAX_VALUE
        if (line.size == 1) return haversineM(lat, lon, line[0].first, line[0].second)
        // Local equirectangular projection so we can do planar segment math.
        val lat0 = lat
        val mPerDegLat = 111_320.0
        val mPerDegLon = 111_320.0 * cos(Math.toRadians(lat0))
        fun proj(la: Double, lo: Double) = Pair((lo - lon) * mPerDegLon, (la - lat) * mPerDegLat)
        var best = Double.MAX_VALUE
        for (i in 1 until line.size) {
            val (ax, ay) = proj(line[i - 1].first, line[i - 1].second)
            val (bx, by) = proj(line[i].first, line[i].second)
            best = min(best, pointSegDist(0.0, 0.0, ax, ay, bx, by))
        }
        return best
    }

    private fun pointSegDist(
        px: Double, py: Double,
        ax: Double, ay: Double,
        bx: Double, by: Double,
    ): Double {
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0.0, 1.0)
        val cx = ax + t * dx
        val cy = ay + t * dy
        return sqrt((px - cx) * (px - cx) + (py - cy) * (py - cy))
    }

    /** Pull [lat,lon] pairs out of a GeoJSON LineString / MultiLineString string. */
    private fun parseLineLatLon(geometryJson: String): List<Pair<Double, Double>> {
        val geom = runCatching { JsonParser.parseString(geometryJson).asJsonObject }.getOrNull()
            ?: return emptyList()
        val coords = geom.get("coordinates") as? JsonArray ?: return emptyList()
        val out = ArrayList<Pair<Double, Double>>()
        when (geom.get("type")?.asString) {
            "LineString" -> coords.forEach { p ->
                (p as? JsonArray)?.let { if (it.size() >= 2) out.add(it[1].asDouble to it[0].asDouble) }
            }
            "MultiLineString" -> coords.forEach { line ->
                (line as? JsonArray)?.forEach { p ->
                    (p as? JsonArray)?.let { if (it.size() >= 2) out.add(it[1].asDouble to it[0].asDouble) }
                }
            }
        }
        return out
    }

    /** Convenience: bounding box of some [lat,lon] pairs, or null if empty. */
    fun bbox(points: List<Pair<Double, Double>>): DoubleArray? {
        if (points.isEmpty()) return null
        var minLa = points[0].first; var maxLa = minLa
        var minLo = points[0].second; var maxLo = minLo
        for ((la, lo) in points) {
            minLa = min(minLa, la); maxLa = max(maxLa, la)
            minLo = min(minLo, lo); maxLo = max(maxLo, lo)
        }
        return doubleArrayOf(minLa, minLo, maxLa, maxLo)
    }
}
