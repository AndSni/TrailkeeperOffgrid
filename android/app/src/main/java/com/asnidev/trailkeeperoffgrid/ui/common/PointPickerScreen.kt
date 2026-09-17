package com.asnidev.trailkeeperoffgrid.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.asnidev.trailkeeperoffgrid.data.local.TrackEntity
import com.asnidev.trailkeeperoffgrid.data.local.TrailEntity
import com.asnidev.trailkeeperoffgrid.location.freshLocation
import com.asnidev.trailkeeperoffgrid.ui.map.MapGeo
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

private val STYLE_URL get() = com.asnidev.trailkeeperoffgrid.ui.map.MapStyle.URL
private const val SRC = "pick-pt"
private const val TRAIL_SRC = "pick-trails"
private const val TRACK_SRC = "pick-tracks"

/**
 * Full-screen "drop a pin" picker. Tap the map to place the marker (tap
 * again to move it); "Use my location" snaps it to the current GPS fix.
 * Returns the chosen (lat, lon) on Done. [trails]/[tracks], when given, are
 * drawn as read-only context (same colours as the main map) so the user can
 * place/move a point relative to the actual trail network instead of a
 * blank map.
 */
@Composable
fun PointPickerScreen(
    title: String,
    hasLocation: Boolean,
    initial: Pair<Double, Double>? = null,
    trails: List<TrailEntity> = emptyList(),
    tracks: List<TrackEntity> = emptyList(),
    onDone: (Double, Double) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var point by remember { mutableStateOf(initial) }
    val styleHolder = remember { arrayOfNulls<Style>(1) }
    val pointHolder = remember { arrayOfNulls<Pair<Double, Double>>(1) }
    pointHolder[0] = point
    // See ui/map/ProjectMapView.kt's `mapUnavailable` for the semantics.
    var mapUnavailable by remember { mutableStateOf(false) }

    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
            addOnDidFailLoadingMapListener { if (styleHolder[0] == null) mapUnavailable = true }
            getMapAsync { map ->
                val start = initial ?: (56.95 to 24.6)
                map.cameraPosition =
                    CameraPosition.Builder()
                        .target(LatLng(start.first, start.second))
                        .zoom(if (initial != null) 15.0 else 6.0)
                        .build()
                map.setStyle(Style.Builder().fromUri(STYLE_URL)) { style ->
                    styleHolder[0] = style
                    mapUnavailable = false
                    style.addSource(GeoJsonSource(TRAIL_SRC))
                    style.addSource(GeoJsonSource(TRACK_SRC))
                    style.addLayer(
                        LineLayer("$TRAIL_SRC-line", TRAIL_SRC).withProperties(
                            PropertyFactory.lineColor("#3C5A31"),
                            PropertyFactory.lineWidth(3f),
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
                    style.addSource(GeoJsonSource(SRC))
                    style.addLayer(
                        CircleLayer("$SRC-c", SRC).withProperties(
                            PropertyFactory.circleRadius(8f),
                            PropertyFactory.circleColor("#3C5A31"),
                            PropertyFactory.circleStrokeWidth(3f),
                            PropertyFactory.circleStrokeColor("#FFFFFF"),
                        )
                    )
                    pushPoint(style, pointHolder[0])
                    (style.getSource(TRAIL_SRC) as? GeoJsonSource)?.setGeoJson(MapGeo.trailFeatures(trails))
                    (style.getSource(TRACK_SRC) as? GeoJsonSource)?.setGeoJson(MapGeo.trackFeatures(tracks))
                }
                map.addOnMapClickListener { p ->
                    pointHolder[0] = p.latitude to p.longitude
                    styleHolder[0]?.let { st -> pushPoint(st, pointHolder[0]) }
                    true
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

    Column(Modifier.fillMaxSize()) {
        Surface(tonalElevation = 2.dp) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    point?.let { "%.5f, %.5f".format(it.first, it.second) } ?: "Tap the map to place the pin",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        enabled = hasLocation,
                        onClick = {
                            scope.launch {
                                freshLocation(context)?.let { loc ->
                                    val p = loc.latitude to loc.longitude
                                    pointHolder[0] = p
                                    point = p
                                    styleHolder[0]?.let { st -> pushPoint(st, p) }
                                    mapView.getMapAsync {
                                        it.easeCamera(
                                            CameraUpdateFactory.newLatLngZoom(
                                                LatLng(p.first, p.second), 16.0
                                            ),
                                            500,
                                        )
                                    }
                                }
                            }
                        },
                    ) { Text("Use my location") }
                    Button(
                        enabled = pointHolder[0] != null,
                        onClick = { pointHolder[0]?.let { onDone(it.first, it.second) } },
                    ) { Text("Done") }
                    OutlinedButton(onClick = onCancel) { Text("Cancel") }
                }
            }
        }
        Box(Modifier.fillMaxSize()) {
            AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize(), update = {
                styleHolder[0]?.let { style ->
                    pushPoint(style, pointHolder[0])
                    (style.getSource(TRAIL_SRC) as? GeoJsonSource)?.setGeoJson(MapGeo.trailFeatures(trails))
                    (style.getSource(TRACK_SRC) as? GeoJsonSource)?.setGeoJson(MapGeo.trackFeatures(tracks))
                }
            })
            if (mapUnavailable) {
                com.asnidev.trailkeeperoffgrid.ui.map.WorldOutlineBackdrop(Modifier.fillMaxSize())
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
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

private fun pushPoint(style: Style, p: Pair<Double, Double>?) {
    val json =
        if (p == null) """{"type":"FeatureCollection","features":[]}"""
        else """{"type":"Point","coordinates":[${p.second},${p.first}]}"""
    (style.getSource(SRC) as? GeoJsonSource)?.setGeoJson(json)
}
