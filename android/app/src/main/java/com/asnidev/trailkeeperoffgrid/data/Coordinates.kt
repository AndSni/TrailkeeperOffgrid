package com.asnidev.trailkeeperoffgrid.data

import com.google.gson.JsonParser
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Coordinate display for the field: a GPS fix read off a screen is often
 * read out loud, copied into a radio message, or matched against a paper
 * map's grid — decimal degrees alone isn't always the right form. WGS-84
 * throughout (what GPS gives you). UTM is the Snyder/USGS formula,
 * cross-checked against `pyproj` to sub-millimetre accuracy at several
 * points (equator, both hemispheres, several zones) before porting here.
 */
object Coordinates {
    /** [lat, lon] from a GeoJSON `Point` geometry string, or null. */
    fun pointFromGeoJson(json: String?): Pair<Double, Double>? {
        if (json == null) return null
        return runCatching {
            val coords = JsonParser.parseString(json).asJsonObject.getAsJsonArray("coordinates")
            coords[1].asDouble to coords[0].asDouble
        }.getOrNull()
    }

    fun decimal(lat: Double, lon: Double): String =
        "%.6f, %.6f".format(lat, lon)

    /** e.g. `56°56'58.6"N 24°06'18.7"E`. */
    fun dms(lat: Double, lon: Double): String =
        "${dmsOne(lat, 'N', 'S')} ${dmsOne(lon, 'E', 'W')}"

    private fun dmsOne(value: Double, pos: Char, neg: Char): String {
        val hemi = if (value >= 0) pos else neg
        val abs = abs(value)
        val deg = floor(abs).toInt()
        val minFull = (abs - deg) * 60
        val min = floor(minFull).toInt()
        val sec = (minFull - min) * 60
        return "%d°%02d'%04.1f\"%c".format(deg, min, sec, hemi)
    }

    /** e.g. `35N 323940 6315505` (zone+hemisphere, easting, northing). */
    fun utm(lat: Double, lon: Double): String {
        val u = toUtm(lat, lon)
        return "${u.zone}${u.hemisphere} ${u.easting.toLong()} ${u.northing.toLong()}"
    }

    data class Utm(val zone: Int, val hemisphere: Char, val easting: Double, val northing: Double)

    // WGS-84 ellipsoid.
    private const val A = 6378137.0
    private const val F = 1.0 / 298.257223563
    private const val K0 = 0.9996

    fun toUtm(lat: Double, lon: Double): Utm {
        val e2 = F * (2 - F)
        val e2p = e2 / (1 - e2)

        val zone = floor((lon + 180) / 6).toInt() + 1
        val lon0 = (zone - 1) * 6 - 180 + 3

        val phi = Math.toRadians(lat)
        val lam = Math.toRadians(lon)
        val lam0 = Math.toRadians(lon0.toDouble())

        val sinPhi = sin(phi)
        val cosPhi = cos(phi)
        val tanPhi = tan(phi)

        val n = A / sqrt(1 - e2 * sinPhi * sinPhi)
        val t = tanPhi * tanPhi
        val c = e2p * cosPhi * cosPhi
        val aTerm = cosPhi * (lam - lam0)

        val m = A * (
            (1 - e2 / 4 - 3 * e2 * e2 / 64 - 5 * e2 * e2 * e2 / 256) * phi -
                (3 * e2 / 8 + 3 * e2 * e2 / 32 + 45 * e2 * e2 * e2 / 1024) * sin(2 * phi) +
                (15 * e2 * e2 / 256 + 45 * e2 * e2 * e2 / 1024) * sin(4 * phi) -
                (35 * e2 * e2 * e2 / 3072) * sin(6 * phi)
            )

        val easting = K0 * n * (
            aTerm +
                (1 - t + c) * aTerm.pow3() / 6 +
                (5 - 18 * t + t * t + 72 * c - 58 * e2p) * aTerm.pow5() / 120
            ) + 500_000.0

        var northing = K0 * (
            m + n * tanPhi * (
                aTerm.pow2() / 2 +
                    (5 - t + 9 * c + 4 * c * c) * aTerm.pow4() / 24 +
                    (61 - 58 * t + t * t + 600 * c - 330 * e2p) * aTerm.pow6() / 720
                )
            )
        if (lat < 0) northing += 10_000_000.0

        return Utm(zone, if (lat >= 0) 'N' else 'S', easting, northing)
    }

    private fun Double.pow2() = this * this
    private fun Double.pow3() = this * this * this
    private fun Double.pow4() = pow2() * pow2()
    private fun Double.pow5() = pow4() * this
    private fun Double.pow6() = pow3() * pow3()

    /** All three forms, labelled, for a detail-sheet display row. */
    fun allFormats(lat: Double, lon: Double): List<Pair<String, String>> = listOf(
        "Decimal" to decimal(lat, lon),
        "DMS" to dms(lat, lon),
        "UTM" to utm(lat, lon),
    )
}
