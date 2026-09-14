package com.asnidev.trailkeeperoffgrid.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.asnidev.trailkeeperoffgrid.location.DevGpsState
import com.asnidev.trailkeeperoffgrid.location.DeveloperGpsMonitor

/**
 * Raw GPS/GNSS diagnostics: current fix, and per-satellite constellation +
 * signal (CN0) + used-in-fix, for debugging field reports of poor
 * positioning ("blue circle way off", etc.) without needing a separate app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperOptionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    var state by remember { mutableStateOf(DevGpsState()) }
    DisposableEffect(hasPermission) {
        val monitor = if (hasPermission) {
            DeveloperGpsMonitor(context) { state = it }.also { it.start() }
        } else null
        onDispose { monitor?.stop() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Developer options") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (!hasPermission) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Location permission is needed to read live GPS/GNSS status.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = { requestPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION) }) {
                    Text("Grant location permission")
                }
            }
            return@Scaffold
        }

        val loc = state.location
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Position", style = MaterialTheme.typography.titleSmall)
                        if (loc == null) {
                            Text(
                                "Waiting for a fix…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            DevRow("Lat, lon", "%.6f, %.6f".format(loc.latitude, loc.longitude))
                            DevRow("Accuracy", if (loc.hasAccuracy()) "±%.1f m".format(loc.accuracy) else "—")
                            DevRow(
                                "Altitude",
                                if (loc.hasAltitude()) "%.0f m".format(loc.altitude) else "—",
                            )
                            DevRow(
                                "Speed",
                                if (loc.hasSpeed()) "%.1f m/s".format(loc.speed) else "—",
                            )
                            DevRow(
                                "Bearing",
                                if (loc.hasBearing()) "%.0f°".format(loc.bearing) else "—",
                            )
                            DevRow("Provider", loc.provider ?: "—")
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("GNSS status", style = MaterialTheme.typography.titleSmall)
                        DevRow("Satellites used in fix", "${state.satellitesUsed}")
                        DevRow("Satellites visible", "${state.satellitesVisible}")
                    }
                }
            }
            if (state.satellites.isNotEmpty()) {
                item {
                    Text(
                        "Per-satellite signal (sorted strongest first)",
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                items(state.satellites, key = { "${it.constellation}-${it.svid}" }) { s ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${s.constellation} #${s.svid}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                "%.0f dB-Hz%s".format(s.cn0DbHz, if (s.usedInFix) " · in fix" else ""),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (s.usedInFix) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DevRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
