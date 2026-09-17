package com.asnidev.trailkeeperoffgrid.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.asnidev.trailkeeperoffgrid.BuildConfig
import com.asnidev.trailkeeperoffgrid.data.OfflineMaps
import com.asnidev.trailkeeperoffgrid.data.ThemePrefs
import com.asnidev.trailkeeperoffgrid.ui.theme.ACCENT_SWATCHES

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = viewModel()) {
    var browsingCountries by rememberSaveable { mutableStateOf(false) }
    if (browsingCountries) {
        OfflineMapsScreen(vm = vm, onBack = { browsingCountries = false })
        return
    }
    var editingTypes by rememberSaveable { mutableStateOf(false) }
    if (editingTypes) {
        StructureTypesScreen(vm = vm, onBack = { editingTypes = false })
        return
    }
    var showingDevOptions by rememberSaveable { mutableStateOf(false) }
    if (showingDevOptions) {
        DeveloperOptionsScreen(onBack = { showingDevOptions = false })
        return
    }

    val snackbar = remember { SnackbarHostState() }
    val message by vm.message.collectAsState()

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { ProfileCard(vm) }
            item { AppearanceCard(vm) }
            item { OfflineMapsCard(vm, onBrowseAll = { browsingCountries = true }) }
            item { MapScopeCard(vm) }
            item { StructureTypesCard(onManage = { editingTypes = true }) }
            item { RemindersCard(vm) }
            item { BackupCard(vm) }
            item { StorageCard(vm) }
            item { DeveloperOptionsCard(onOpen = { showingDevOptions = true }) }
            item { AboutCard() }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun ProfileCard(vm: SettingsViewModel) {
    val stored by vm.displayName.collectAsState()
    var name by rememberSaveable(stored) { mutableStateOf(stored) }
    SectionCard("Your name") {
        Text(
            "Optional. Used as the author on exported files (GPX, reports) and " +
                "when you hand data to someone else. Leave blank to be “Me”.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Display name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        val dirty = name.trim() != stored.trim()
        Button(onClick = { vm.saveDisplayName(name) }, enabled = dirty) { Text("Save") }
    }
}

@Composable
private fun AppearanceCard(vm: SettingsViewModel) {
    val mode by vm.themeMode.collectAsState()
    val accent by vm.themeAccent.collectAsState()

    SectionCard("Appearance") {
        Text("Theme", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemePrefs.Mode.entries.forEach { m ->
                FilterChip(
                    selected = mode == m,
                    onClick = { vm.setThemeMode(m) },
                    label = { Text(m.name.lowercase().replaceFirstChar { it.uppercase() }) },
                )
            }
        }
        Text("Accent color", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ACCENT_SWATCHES.forEach { a ->
                val selected = a.key == accent
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(a.swatch)
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.outline,
                            shape = CircleShape,
                        )
                        .clickable { vm.setThemeAccent(a.key) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = a.label,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OfflineMapsCard(vm: SettingsViewModel, onBrowseAll: () -> Unit) {
    val downloaded by vm.downloadedRegions.collectAsState()

    SectionCard("Offline maps") {
        Text(
            "Download an area while you have signal so the map still works in " +
                "the field. Zoom 4–14; a country is a few hundred MB — a large " +
                "one may be too big for one region, see below.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (downloaded.isEmpty()) {
            Text(
                "No maps downloaded yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text("Downloaded", style = MaterialTheme.typography.labelLarge)
            downloaded.forEach { r ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(r.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            humanBytes(r.sizeBytes) + if (!r.complete) " · incomplete" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { vm.deleteRegion(r.id, r.name) }) { Text("Delete") }
                }
            }
        }

        OutlinedButton(onClick = onBrowseAll, modifier = Modifier.fillMaxWidth()) {
            Text("Browse all countries →")
        }
    }
}

/** One downloadable region — a quick-preset or a country — reused by
 * [OfflineMapsCard] and [OfflineMapsScreen]. */
@Composable
fun RegionRow(
    preset: OfflineMaps.Preset,
    downloading: RegionDownloadUi?,
    alreadySaved: Boolean,
    onDownload: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(preset.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            when {
                alreadySaved -> Text("saved", color = MaterialTheme.colorScheme.primary)
                downloading?.running == true ->
                    Text("${(downloading.fraction * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                else -> OutlinedButton(onClick = onDownload) { Text("Download") }
            }
        }
        if (downloading?.running == true) {
            LinearProgressIndicator(
                progress = { downloading.fraction },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "${downloading.completed} / " +
                    "${if (downloading.required > 0) downloading.required.toString() else "…"} tiles · " +
                    humanBytes(downloading.bytes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        downloading?.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun MapScopeCard(vm: SettingsViewModel) {
    val storedRadius by vm.mapScopeRadiusM.collectAsState()
    var radiusText by rememberSaveable(storedRadius) { mutableStateOf(storedRadius.toInt().toString()) }
    val parsedRadius = radiusText.toDoubleOrNull()
    val radiusValid =
        parsedRadius != null && parsedRadius >= com.asnidev.trailkeeperoffgrid.data.MapScopePrefs.MIN_RADIUS_M &&
            parsedRadius <= com.asnidev.trailkeeperoffgrid.data.MapScopePrefs.MAX_RADIUS_M
    val radiusDirty = radiusValid && parsedRadius != storedRadius

    val storedMarker by vm.markerRadiusDp.collectAsState()
    var markerText by rememberSaveable(storedMarker) { mutableStateOf(storedMarker.toInt().toString()) }
    val parsedMarker = markerText.toDoubleOrNull()
    val markerValid =
        parsedMarker != null && parsedMarker >= com.asnidev.trailkeeperoffgrid.data.MapDisplayPrefs.MIN_MARKER_RADIUS_DP &&
            parsedMarker <= com.asnidev.trailkeeperoffgrid.data.MapDisplayPrefs.MAX_MARKER_RADIUS_DP
    val markerDirty = markerValid && parsedMarker != storedMarker

    SectionCard("Map") {
        Text(
            "Marker size — how big task/structure/report dots are on the map. " +
                "Bigger is easier to tap.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = markerText,
            onValueChange = { markerText = it.filter { c -> c.isDigit() } },
            label = { Text("Marker size (dp)") },
            singleLine = true,
            isError = markerText.isNotBlank() && !markerValid,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { parsedMarker?.let(vm::setMarkerRadiusDp) }, enabled = markerDirty) { Text("Save") }

        Text(
            "This-project scope — on a project's map, \"This project\" dims " +
                "trails/structures that weren't created here and aren't within " +
                "this distance of one of this project's own trails or recorded " +
                "routes. Tune it to how dense the trail network is where you work.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = radiusText,
            onValueChange = { radiusText = it.filter { c -> c.isDigit() } },
            label = { Text("Radius (metres)") },
            singleLine = true,
            isError = radiusText.isNotBlank() && !radiusValid,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { parsedRadius?.let(vm::setMapScopeRadiusM) }, enabled = radiusDirty) { Text("Save") }
    }
}

@Composable
private fun StorageCard(vm: SettingsViewModel) {
    val s by vm.storage.collectAsState()
    val busy by vm.busy.collectAsState()
    SectionCard("Storage & cleanup") {
        StorageRow("Database", s.dbBytes)
        StorageRow("Task photos", s.photoBytes)
        StorageRow("Recorded tracks", s.trackBytes)
        StorageRow("Offline map tiles", s.tileBytes)
        StorageRow("Total", s.total, bold = true)
        Spacer(Modifier.width(0.dp))
        Text(
            "Clearing photos of completed tasks removes the images only — the " +
                "tasks, notes, work logs and history stay.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = { vm.purgeCompletedTaskPhotos() }, enabled = !busy) {
            if (busy) CircularProgressIndicator(Modifier.width(16.dp))
            else Text("Clear photos of completed tasks")
        }
        OutlinedButton(onClick = { vm.purgeResolvedReportPhotos() }, enabled = !busy) {
            if (busy) CircularProgressIndicator(Modifier.width(16.dp))
            else Text("Clear photos of resolved reports")
        }
    }
}

@Composable
private fun StorageRow(label: String, bytes: Long, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        )
        Text(
            humanBytes(bytes),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun BackupCard(vm: SettingsViewModel) {
    val last by vm.lastBackup.collectAsState()
    val busy by vm.busy.collectAsState()
    var pendingRestore by remember { mutableStateOf<android.net.Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> uri?.let(vm::exportBackup) }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> pendingRestore = uri }

    val projects by vm.projects.collectAsState()
    var pendingGpx by remember { mutableStateOf<android.net.Uri?>(null) }
    val gpxExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> uri?.let(vm::exportGpx) }
    val gpxImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            if (projects.size == 1) vm.importGpx(uri, projects.first().id)
            else pendingGpx = uri
        }
    }

    SectionCard("Backup & restore") {
        Text(
            "Everything is on this phone only. Export a backup zip regularly " +
                "(data + photos) and keep it somewhere safe — it is also how you " +
                "move to a new phone.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Last backup: " + (last?.take(10) ?: "never"),
            style = MaterialTheme.typography.labelMedium,
            color = if (last == null) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { exportLauncher.launch("trailkeeper-offgrid-backup.zip") },
                enabled = !busy,
            ) { Text("Export backup") }
            OutlinedButton(
                onClick = { restoreLauncher.launch(arrayOf("application/zip", "*/*")) },
                enabled = !busy,
            ) { Text("Restore") }
        }

        Text(
            "GPX — hand routes and trails to another app or phone. This import " +
                "brings in both routes and trails from the file; to import only " +
                "one kind, use the Import GPX button on that project's Route or " +
                "Trails tab instead.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { gpxExportLauncher.launch("trailkeeper-offgrid-gpx.zip") },
                enabled = !busy,
            ) { Text("Export GPX") }
            OutlinedButton(
                onClick = { gpxImportLauncher.launch(arrayOf("application/gpx+xml", "application/xml", "text/xml", "*/*")) },
                enabled = !busy && projects.isNotEmpty(),
            ) { Text("Import GPX (routes & trails)") }
        }
        if (projects.isEmpty()) {
            Text(
                "Create a project first to import GPX into.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    }

    pendingGpx?.let { uri ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingGpx = null },
            title = { Text("Import GPX into which project?") },
            text = {
                Column {
                    projects.forEach { p ->
                        TextButton(onClick = {
                            vm.importGpx(uri, p.id); pendingGpx = null
                        }) { Text(p.name) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { pendingGpx = null }) { Text("Cancel") } },
        )
    }

    pendingRestore?.let { uri ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Restore backup") },
            text = {
                Text(
                    "“Merge” adds/updates rows from the backup and keeps what you " +
                        "have. “Replace” wipes this app's data first. Photos are " +
                        "restored either way.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.restoreBackup(uri, replace = false); pendingRestore = null
                }) { Text("Merge") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        vm.restoreBackup(uri, replace = true); pendingRestore = null
                    }) { Text("Replace") }
                    TextButton(onClick = { pendingRestore = null }) { Text("Cancel") }
                }
            },
        )
    }
}

@Composable
private fun StructureTypesCard(onManage: () -> Unit) {
    SectionCard("Structure types") {
        Text(
            "Culvert, bridge, boardwalk and the rest of the picklist shown when " +
                "adding or editing a structure. Add, rename or remove types here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onManage) {
            Text("Manage structure types")
        }
    }
}

@Composable
private fun DeveloperOptionsCard(onOpen: () -> Unit) {
    SectionCard("Developer options") {
        Text(
            "Live GPS position, fix accuracy, and per-satellite signal - for " +
                "debugging poor-connection reports in the field.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onOpen) {
            Text("Open developer options")
        }
    }
}

@Composable
private fun RemindersCard(vm: SettingsViewModel) {
    val on by vm.remindersEnabled.collectAsState()
    SectionCard("Reminders") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Inspection due & overdue tasks", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "A daily on-device check. Notifies when a structure passes its " +
                        "inspection interval, or a high/urgent task stays open too long.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            androidx.compose.material3.Switch(checked = on, onCheckedChange = { vm.setReminders(it) })
        }
        OutlinedButton(onClick = { vm.checkRemindersNow() }, enabled = on) { Text("Check now") }
    }
}

@Composable
private fun AboutCard() {
    val context = LocalContext.current
    SectionCard("About") {
        Text("Trailkeeper Offgrid ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        Text(
            "Single-user and fully offline. All your data lives on this phone — " +
                "back it up (export) regularly. No account, no server.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "A note from the developer",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            "Trailkeeper Offgrid exists to help volunteer trail crews, non-profit " +
                "land trusts, and everyone else keeping trails open without a " +
                "budget for expensive software. It's free, it's open source, and " +
                "it always will be — no accounts, no subscriptions, no ads, and " +
                "your data never leaves your phone.\n\n" +
                "It's built and maintained by a single independent developer in " +
                "their spare time, not a company. If it saves you time out on the " +
                "trail and you'd like to help keep it going, a small donation " +
                "goes a long way — but there's never any obligation. Thank you " +
                "for using it, and for the work you do maintaining trails.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ko-fi.com/andrissni"))
                context.startActivity(intent)
            },
        ) { Text("Support on Ko-fi") }
    }
}
