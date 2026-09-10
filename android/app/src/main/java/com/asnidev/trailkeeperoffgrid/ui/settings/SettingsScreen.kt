package com.asnidev.trailkeeperoffgrid.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.asnidev.trailkeeperoffgrid.BuildConfig
import com.asnidev.trailkeeperoffgrid.data.OfflineMaps

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = viewModel()) {
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
            item { OfflineMapsCard(vm) }
            item { RemindersCard(vm) }
            item { BackupCard(vm) }
            item { StorageCard(vm) }
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
private fun OfflineMapsCard(vm: SettingsViewModel) {
    val presets by vm.presets.collectAsState()
    val downloaded by vm.downloadedRegions.collectAsState()
    val downloads by vm.downloads.collectAsState()

    SectionCard("Offline maps") {
        Text(
            "Download an area while you have signal so the map still works in " +
                "the field. Zoom 4–14; a country is a few hundred MB.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (presets.isEmpty()) {
            Text("No regions defined.", style = MaterialTheme.typography.bodySmall)
        }
        presets.forEach { p ->
            val dl = downloads[p.id]
            val already = downloaded.any { it.name == p.name && it.complete }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    when {
                        already -> Text("saved", color = MaterialTheme.colorScheme.primary)
                        dl?.running == true ->
                            Text("${(dl.fraction * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                        else ->
                            OutlinedButton(onClick = { vm.downloadPreset(p) }) { Text("Download") }
                    }
                }
                if (dl?.running == true) {
                    LinearProgressIndicator(
                        progress = { dl.fraction },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "${dl.completed} / ${if (dl.required > 0) dl.required.toString() else "…"} tiles · ${humanBytes(dl.bytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                dl?.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        if (downloaded.isNotEmpty()) {
            Text("Downloaded", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
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
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
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
    SectionCard("About") {
        Text("Trailkeeper Offgrid ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        Text(
            "Single-user and fully offline. All your data lives on this phone — " +
                "back it up (export) regularly. No account, no server.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
