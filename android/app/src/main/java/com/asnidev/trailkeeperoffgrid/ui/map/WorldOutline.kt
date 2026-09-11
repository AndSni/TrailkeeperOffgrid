package com.asnidev.trailkeeperoffgrid.ui.map

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.gson.JsonArray
import com.google.gson.JsonParser

/**
 * A faint, static world-coastline/border sketch — country-scale detail
 * only (Natural Earth's 1:110m set, public domain, stripped to bare
 * geometry, `assets/world_outline.geojson`, ~170 KB) — drawn behind the
 * "no map for this area yet" message so a genuinely first-run device isn't
 * looking at a totally blank rectangle. This is plain Compose drawing, not
 * a MapLibre layer: it never touches the map's own style pipeline, so it
 * can't make an already-delicate code path (no device here to verify it on)
 * any more fragile.
 */
object WorldOutline {
    @Volatile private var cached: List<List<Pair<Float, Float>>>? = null

    /** [lon, lat] rings, parsed once and cached for the process lifetime. */
    fun rings(context: Context): List<List<Pair<Float, Float>>> {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val parsed = runCatching {
                val json = context.assets.open("world_outline.geojson")
                    .bufferedReader().use { it.readText() }
                val features = JsonParser.parseString(json).asJsonObject.getAsJsonArray("features")
                val rings = ArrayList<List<Pair<Float, Float>>>()
                features.forEach { el ->
                    val geom = el.asJsonObject.getAsJsonObject("geometry")
                    val coords = geom.getAsJsonArray("coordinates")
                    when (geom.get("type")?.asString) {
                        "Polygon" -> coords.forEach { ring -> rings.add(parseRing(ring.asJsonArray)) }
                        "MultiPolygon" -> coords.forEach { poly ->
                            poly.asJsonArray.forEach { ring -> rings.add(parseRing(ring.asJsonArray)) }
                        }
                    }
                }
                rings
            }.getOrDefault(emptyList())
            cached = parsed
            return parsed
        }
    }

    private fun parseRing(ring: JsonArray): List<Pair<Float, Float>> =
        ring.mapNotNull { pt ->
            val a = pt.asJsonArray
            if (a.size() >= 2) a[0].asFloat to a[1].asFloat else null
        }
}

/** Equirectangular-projected coastlines/borders, filling [modifier]'s bounds. */
@Composable
fun WorldOutlineBackdrop(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val rings = remember { WorldOutline.rings(context) }
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val strokeDp = 1.dp

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val strokePx = strokeDp.toPx()
        rings.forEach { ring ->
            if (ring.size < 2) return@forEach
            val path = Path()
            ring.forEachIndexed { i, (lon, lat) ->
                val x = (lon + 180f) / 360f * w
                val y = (90f - lat) / 180f * h
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(path, color = lineColor, style = Stroke(width = strokePx))
        }
    }
}
