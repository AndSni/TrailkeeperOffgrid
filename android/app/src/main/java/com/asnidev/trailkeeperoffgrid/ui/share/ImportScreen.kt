package com.asnidev.trailkeeperoffgrid.ui.share

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.asnidev.trailkeeperoffgrid.data.ShareBundle
import com.asnidev.trailkeeperoffgrid.data.local.TrailkeeperDb
import kotlinx.coroutines.launch

private fun ShareBundle.Scope.label(): String = when (this) {
    ShareBundle.Scope.PROJECT -> "Project"
    ShareBundle.Scope.TASK -> "Task"
    ShareBundle.Scope.ROUTE -> "Route"
    ShareBundle.Scope.TRAIL -> "Trail"
    ShareBundle.Scope.STRUCTURE -> "Structure"
}

/** Reached when the app is opened with a `.tkshare` file — another
 * Trailkeeper Offgrid device's export, arriving via [com.asnidev.trailkeeperoffgrid.MainActivity]'s
 * intent-filters (Bluetooth, Quick Share, a messaging app attachment, ...).
 * Shows what's in the bundle before touching the database; a shared task,
 * route, trail or structure whose parent project isn't on this device yet
 * asks where to put it rather than silently inventing one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(uri: Uri, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var preview by remember { mutableStateOf<ShareBundle.Preview?>(null) }
    var loading by remember { mutableStateOf(true) }
    var invalid by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var resultMsg by remember { mutableStateOf<String?>(null) }
    val projects by remember {
        TrailkeeperDb.db.projectDao().observeAll()
    }.collectAsState(initial = emptyList())

    LaunchedEffect(uri) {
        loading = true
        val p = ShareBundle.peek(context, uri)
        preview = p
        invalid = p == null
        loading = false
    }

    fun runImport(decision: ShareBundle.ProjectDecision?) {
        if (importing) return
        importing = true
        scope.launch {
            val r = ShareBundle.import(context, uri, decision)
            importing = false
            resultMsg = if (r.ok) r.detail else "Import failed: ${r.detail}"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import share") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                loading -> CircularProgressIndicator()
                invalid -> Text(
                    "This isn't a Trailkeeper Offgrid share file.",
                    color = MaterialTheme.colorScheme.error,
                )
                resultMsg != null -> {
                    Text(resultMsg!!)
                    Button(onClick = onDone) { Text("Done") }
                }
                preview != null -> {
                    val p = preview!!
                    Text("${p.scope.label()}: ${p.primaryName}", style = MaterialTheme.typography.titleLarge)
                    if (p.exportedBy.isNotBlank()) {
                        Text(
                            "Shared by ${p.exportedBy}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (p.needsProjectDecision) {
                        val projectName = p.project?.name ?: ""
                        Text(
                            "This ${p.scope.label().lowercase()} belongs to project \"$projectName\", " +
                                "which isn't on this device yet.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (projects.isNotEmpty()) {
                            Text("Add to an existing project:", style = MaterialTheme.typography.labelLarge)
                            projects.forEach { proj ->
                                OutlinedButton(
                                    onClick = { runImport(ShareBundle.ProjectDecision.UseExisting(proj.id)) },
                                    enabled = !importing,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(proj.name) }
                            }
                        }
                        Button(
                            onClick = { runImport(ShareBundle.ProjectDecision.CreateNew) },
                            enabled = !importing,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Create new project \"$projectName\"") }
                    } else {
                        Button(
                            onClick = { runImport(null) },
                            enabled = !importing,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Import") }
                    }
                    if (importing) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }
    }
}
