package com.asnidev.trailkeeperoffgrid.ui.map

import android.annotation.SuppressLint
import android.graphics.PointF
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.asnidev.trailkeeperoffgrid.data.local.StructureEntity
import com.asnidev.trailkeeperoffgrid.data.local.TaskEntity
import com.asnidev.trailkeeperoffgrid.data.local.TrackEntity
import com.asnidev.trailkeeperoffgrid.data.local.TrailEntity
import com.asnidev.trailkeeperoffgrid.data.local.TrailReportEntity
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.OnCameraTrackingChangedListener
import org.maplibre.android.location.engine.LocationEngineRequest
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

private val STYLE_URL get() = com.asnidev.trailkeeperoffgrid.ui.map.MapStyle.URL
private const val TRAIL_SRC = "tk-trails"
private const val TRACK_SRC = "tk-tracks"
private const val TASK_SRC = "tk-tasks"
private const val STRUCTURE_SRC = "tk-structures"
private const val REPORT_SRC = "tk-reports"
private val LATVIA = LatLng(56.95, 24.6)

/** (lat, lon, nonce) - bump the nonce to re-trigger a fly-to. */
typealias MapFocus = Triple<Double, Double, Long>

/** Holds the map + style handles once they're ready, plus a one-shot flag so
 * the camera only auto-fits the first time data arrives. */
private class MapHolder {
    var map: MapLibreMap? = null
    var style: Style? = null
    var fittedCamera = false
    var lastFocusNonce = 0L
}

@Composable
fun ProjectMap(
    trails: List<TrailEntity>,
    tasks: List<TaskEntity>,
    structures: List<StructureEntity>,
    tracks: List<TrackEntity>,
    reports: List<TrailReportEntity> = emptyList(),
    hasLocationPermission: Boolean,
    modifier: Modifier = Modifier,
    focus: MapFocus? = null,
    projectTrailIds: Set<String> = emptySet(),
    projectStructureIds: Set<String> = emptySet(),
    projectBounds: LatLngBounds? = null,
    showAllAssets: Boolean = true,
    onFeatureTap: (kind: String, id: String) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val holder = remember { MapHolder() }
    val onTapHolder = remember { arrayOfNulls<(String, String) -> Unit>(1) }
    onTapHolder[0] = onFeatureTap

    // Set once the style has genuinely failed to load and never succeeded
    // before (no signal yet, and no offline region has ever been downloaded
    // for any area). Cleared for good the first time a style loads — after
    // that, MapLibre's own offline store serves any previously-downloaded
    // area with no network, so a later failure outside that area correctly
    // just renders a blank map there instead of this message.
    var mapUnavailable by remember { mutableStateOf(false) }
    var trackingMode by remember { mutableIntStateOf(CameraMode.NONE) }
    var accuracyM by remember { mutableStateOf<Float?>(null) }

    // Polls the same location stream that draws the blue dot / accuracy
    // circle, rather than a separate raw-GNSS registration - that way the
    // indicator can never disagree with what's actually on the map, and
    // there's no second location subscription to keep alive.
    LaunchedEffect(hasLocationPermission) {
        if (!hasLocationPermission) {
            accuracyM = null
            return@LaunchedEffect
        }
        while (true) {
            accuracyM = holder.map?.locationComponent?.lastKnownLocation
                ?.takeIf { it.hasAccuracy() }?.accuracy
            delay(1_000L)
        }
    }

    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
            addOnDidFailLoadingMapListener { if (holder.style == null) mapUnavailable = true }
            getMapAsync { map ->
                holder.map = map
                map.cameraPosition = CameraPosition.Builder().target(LATVIA).zoom(6.0).build()
                map.addOnMapClickListener { latLng ->
                    val pt: PointF = map.projection.toScreenLocation(latLng)
                    val hit =
                        map.queryRenderedFeatures(
                            pt,
                            "$TASK_SRC-dot",
                            "$STRUCTURE_SRC-dot",
                            "$TRACK_SRC-line",
                            "$TRAIL_SRC-line",
                            "$REPORT_SRC-dot",
                        ).firstOrNull { it.hasProperty("id") && it.hasProperty("kind") }
                    if (hit != null) {
                        onTapHolder[0]?.invoke(
                            hit.getStringProperty("kind"),
                            hit.getStringProperty("id"),
                        )
                        true
                    } else {
                        false
                    }
                }
                map.setStyle(Style.Builder().fromUri(STYLE_URL)) { style ->
                    holder.style = style
                    mapUnavailable = false
                    style.addSource(GeoJsonSource(TRAIL_SRC))
                    style.addSource(GeoJsonSource(TRACK_SRC))
                    style.addSource(GeoJsonSource(TASK_SRC))
                    style.addSource(GeoJsonSource(STRUCTURE_SRC))
                    style.addSource(GeoJsonSource(REPORT_SRC))
                    style.addLayer(
                        LineLayer("$TRAIL_SRC-line", TRAIL_SRC).withProperties(
                            PropertyFactory.lineColor("#3C5A31"),
                            PropertyFactory.lineWidth(dimSwitch(1.5f, 3f)),
                            PropertyFactory.lineOpacity(dimSwitch(0.28f, 1f)),
                            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                        )
                    )
                    style.addLayer(
                        LineLayer("$TRACK_SRC-line", TRACK_SRC).withProperties(
                            PropertyFactory.lineColor("#6D4C9C"),
                            PropertyFactory.lineWidth(3f),
                            PropertyFactory.lineDasharray(arrayOf(1.5f, 1f)),
                            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                        )
                    )
                    style.addLayer(
                        CircleLayer("$TASK_SRC-dot", TASK_SRC).withProperties(
                            PropertyFactory.circleRadius(6f),
                            PropertyFactory.circleColor("#D6A64B"),
                            PropertyFactory.circleStrokeWidth(2f),
                            PropertyFactory.circleStrokeColor("#FFFFFF"),
                        )
                    )
                    style.addLayer(
                        CircleLayer("$STRUCTURE_SRC-dot", STRUCTURE_SRC).withProperties(
                            PropertyFactory.circleRadius(dimSwitch(4f, 6f)),
                            PropertyFactory.circleOpacity(dimSwitch(0.35f, 1f)),
                            PropertyFactory.circleColor(
                                Expression.toColor(
                                    Expression.coalesce(
                                        Expression.get("color"),
                                        Expression.literal("#2F6D7A"),
                                    )
                                )
                            ),
                            PropertyFactory.circleStrokeWidth(dimSwitch(0.5f, 2f)),
                            PropertyFactory.circleStrokeColor("#FFFFFF"),
                            PropertyFactory.circleStrokeOpacity(dimSwitch(0.35f, 1f)),
                        )
                    )
                    style.addLayer(
                        CircleLayer("$REPORT_SRC-dot", REPORT_SRC).withProperties(
                            PropertyFactory.circleRadius(5f),
                            PropertyFactory.circleColor(
                                Expression.match(
                                    Expression.get("status"),
                                    Expression.literal("passable"), Expression.color(0xFF4C6B3C.toInt()),
                                    Expression.literal("caution"), Expression.color(0xFFD6A64B.toInt()),
                                    Expression.literal("impassable"), Expression.color(0xFFB23B3B.toInt()),
                                    Expression.color(0xFFB23B3B.toInt()),
                                )
                            ),
                            PropertyFactory.circleOpacity(
                                Expression.switchCase(
                                    Expression.get("resolved"), Expression.literal(0.3f),
                                    Expression.literal(1f),
                                )
                            ),
                            PropertyFactory.circleStrokeWidth(2f),
                            PropertyFactory.circleStrokeColor("#FFFFFF"),
                        )
                    )
                    enableLocation(context, map, style, hasLocationPermission) { trackingMode = it }
                    pushData(
                        holder, trails, tasks, structures, tracks, reports,
                        projectTrailIds, projectStructureIds, projectBounds, showAllAssets,
                    )
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    Box(modifier) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = {
                pushData(
                    holder, trails, tasks, structures, tracks, reports,
                    projectTrailIds, projectStructureIds, projectBounds, showAllAssets,
                )
                if (focus != null && focus.third != holder.lastFocusNonce) {
                    holder.lastFocusNonce = focus.third
                    holder.map?.easeCamera(
                        CameraUpdateFactory.newLatLngZoom(LatLng(focus.first, focus.second), 16.0),
                        600,
                    )
                }
            },
        )
        if (mapUnavailable) {
            WorldOutlineBackdrop(Modifier.fillMaxSize())
            Surface(
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
            ) {
                Text(
                    "No map for this area yet.\nConnect once and download a region in " +
                        "Settings → Offline maps, then it works with no signal.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        if (hasLocationPermission) {
            GpsSignalBars(
                accuracyM = accuracyM,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
            )
        }
        LocateButton(
            mode = trackingMode,
            enabled = hasLocationPermission,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            onClick = {
                holder.map?.locationComponent?.let { lc ->
                    val next = when (trackingMode) {
                        CameraMode.NONE -> CameraMode.TRACKING
                        CameraMode.TRACKING -> CameraMode.TRACKING_GPS
                        else -> CameraMode.NONE
                    }
                    lc.renderMode = if (next == CameraMode.TRACKING_GPS) RenderMode.GPS else RenderMode.COMPASS
                    lc.cameraMode = next
                    trackingMode = next
                }
            },
        )
    }
}

/**
 * Cycles NONE -> TRACKING (center, north-up) -> TRACKING_GPS (center +
 * rotate map to the direction of travel) -> NONE. Also updated externally
 * when the user pans/rotates away (MapLibre drops tracking on gesture, via
 * [OnCameraTrackingChangedListener]), so the icon always reflects reality.
 */
@Composable
private fun LocateButton(mode: Int, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val active = mode != CameraMode.NONE
    Surface(
        modifier = modifier.size(48.dp).clip(CircleShape),
        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
    ) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(
                if (mode == CameraMode.TRACKING_GPS) Icons.Filled.Navigation
                else if (mode == CameraMode.TRACKING) Icons.Filled.MyLocation
                else Icons.Filled.GpsNotFixed,
                contentDescription = "Centre on my location",
                tint = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * Full bars at the phone's best realistic fix (≤10m); each bar down
 * doubles the radius. A linear meters-to-bars scale would pin almost
 * every real fix at "full bars" and never show the difference that
 * actually matters out on the trail, so this follows accuracy's own
 * exponential character instead.
 */
private fun accuracyBars(accuracyM: Float?): Int = when {
    accuracyM == null -> 0
    accuracyM <= 10f -> 4
    accuracyM <= 20f -> 3
    accuracyM <= 40f -> 2
    accuracyM <= 80f -> 1
    else -> 0
}

/** Nokia-coverage-bars-style GPS indicator (0-4 bars from [accuracyBars])
 * with the actual precision in meters underneath. */
@Composable
private fun GpsSignalBars(accuracyM: Float?, modifier: Modifier = Modifier) {
    val bars = accuracyBars(accuracyM)
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        shape = RoundedCornerShape(6.dp),
        tonalElevation = 4.dp,
    ) {
        Column(
            Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (i in 1..4) {
                    val on = i <= bars
                    Box(
                        Modifier
                            .size(width = 4.dp, height = (6 + i * 3).dp)
                            .align(Alignment.Bottom)
                            .background(
                                if (on) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                    )
                }
            }
            Text(
                if (accuracyM != null) "±${accuracyM.roundToInt()}m" else "no fix",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun pushData(
    holder: MapHolder,
    trails: List<TrailEntity>,
    tasks: List<TaskEntity>,
    structures: List<StructureEntity>,
    tracks: List<TrackEntity>,
    reports: List<TrailReportEntity>,
    projectTrailIds: Set<String>,
    projectStructureIds: Set<String>,
    projectBounds: LatLngBounds?,
    showAllAssets: Boolean,
) {
    val style = holder.style ?: return
    val dimTrails =
        if (showAllAssets) emptySet()
        else trails.map { it.id }.toSet() - projectTrailIds
    val dimStructures =
        if (showAllAssets) emptySet()
        else structures.map { it.id }.toSet() - projectStructureIds

    (style.getSource(TRAIL_SRC) as? GeoJsonSource)
        ?.setGeoJson(MapGeo.trailFeatures(trails, dimTrails))
    (style.getSource(TRACK_SRC) as? GeoJsonSource)?.setGeoJson(MapGeo.trackFeatures(tracks))
    (style.getSource(TASK_SRC) as? GeoJsonSource)?.setGeoJson(MapGeo.taskFeatures(tasks))
    (style.getSource(STRUCTURE_SRC) as? GeoJsonSource)
        ?.setGeoJson(MapGeo.structureFeatures(structures, dimStructures))
    (style.getSource(REPORT_SRC) as? GeoJsonSource)?.setGeoJson(MapGeo.reportFeatures(reports))

    if (!holder.fittedCamera) {
        val bounds = projectBounds ?: MapGeo.bounds(trails, tasks, structures, tracks)
        if (bounds != null) {
            holder.map?.easeCamera(CameraUpdateFactory.newLatLngBounds(bounds, 72), 500)
            holder.fittedCamera = true
        }
    }
}

/** `dim` feature-flag → [dimValue] when set, [fullValue] otherwise. */
private fun dimSwitch(dimValue: Float, fullValue: Float): Expression =
    Expression.switchCase(
        Expression.get("dim"),
        Expression.literal(dimValue),
        Expression.literal(fullValue),
    )

@SuppressLint("MissingPermission")
private fun enableLocation(
    context: android.content.Context,
    map: MapLibreMap,
    style: Style,
    hasPermission: Boolean,
    onTrackingModeChanged: (Int) -> Unit,
) {
    if (!hasPermission) return
    val lc = map.locationComponent
    // The default engine request favours battery over accuracy, which is
    // what made the on-map "blue dot" (and anything marked from it) lag
    // metres behind an actual GPS fix - force a real GPS-backed one instead.
    val request = LocationEngineRequest.Builder(1_000L)
        .setPriority(LocationEngineRequest.PRIORITY_HIGH_ACCURACY)
        .setFastestInterval(500L)
        .build()
    lc.activateLocationComponent(
        LocationComponentActivationOptions.builder(context, style)
            .locationEngineRequest(request)
            .useDefaultLocationEngine(true)
            .build()
    )
    lc.isLocationComponentEnabled = true
    lc.renderMode = RenderMode.COMPASS
    lc.cameraMode = CameraMode.NONE
    lc.addOnCameraTrackingChangedListener(object : OnCameraTrackingChangedListener {
        override fun onCameraTrackingDismissed() {
            onTrackingModeChanged(CameraMode.NONE)
        }

        override fun onCameraTrackingChanged(currentMode: Int) {
            onTrackingModeChanged(currentMode)
        }
    })
}
