package com.asnidev.trailkeeperoffgrid.ui.projects

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.asnidev.trailkeeperoffgrid.data.NotificationRepository
import com.asnidev.trailkeeperoffgrid.data.local.ProjectEntity
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectListScreen(
    onOpenProject: (id: String, name: String) -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSettings: () -> Unit,
    vm: ProjectListViewModel = viewModel(),
) {
    val s by vm.state.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    var editingProject by remember { mutableStateOf<ProjectEntity?>(null) }
    val unread by NotificationRepository.unreadCount().collectAsState(initial = 0)

    LaunchedEffect(Unit) { runCatching { NotificationRepository.refresh() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Projects") },
                actions = {
                    IconButton(onClick = onOpenNotifications) {
                        BadgedBox(badge = { if (unread > 0) Badge { Text("$unread") } }) {
                            Icon(Icons.Default.Notifications, contentDescription = "Notifications")
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) {
                Icon(Icons.Default.Add, contentDescription = "New project")
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                s.loading ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                s.projects.isEmpty() ->
                    Column(
                        Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("No projects yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            s.error ?: "Tap + to create the first one.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                else ->
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(s.projects, key = { it.id }) { p ->
                            // Hold for 1s -> edit/delete; a short tap opens it.
                            // Same shape as the photo-delete gesture in
                            // ui/projects/TaskPhotos.kt.
                            Card(
                                Modifier.fillMaxWidth().pointerInput(p.id) {
                                    awaitEachGesture {
                                        awaitFirstDown()
                                        var released = false
                                        val heldFull =
                                            withTimeoutOrNull(1_000L) {
                                                released = waitForUpOrCancellation() != null
                                                true
                                            } == null
                                        when {
                                            heldFull -> {
                                                editingProject = p
                                                waitForUpOrCancellation()
                                            }
                                            released -> onOpenProject(p.id, p.name)
                                        }
                                    }
                                }
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(p.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "${p.activity} · ${p.status}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (p.description.isNotBlank()) {
                                        Text(
                                            p.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(top = 4.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
            }
        }
    }

    if (showCreate) {
        CreateProjectDialog(
            busy = s.creating,
            onDismiss = { showCreate = false },
            onCreate = { name, activity ->
                vm.create(name, activity)
                showCreate = false
            },
        )
    }

    editingProject?.let { p ->
        EditProjectDialog(
            project = p,
            onDismiss = { editingProject = null },
            onSave = { name, activity ->
                vm.editProject(p.id, name, activity)
                editingProject = null
            },
            onDelete = {
                vm.deleteProject(p.id)
                editingProject = null
            },
        )
    }
}

@Composable
private fun CreateProjectDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var activity by remember { mutableStateOf("mtb") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New project") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = activity,
                    onValueChange = { activity = it },
                    label = { Text("Activity") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name, activity.ifBlank { "mtb" }) },
                enabled = name.isNotBlank() && !busy,
            ) {
                Text("Create")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Hold a project card for 1s to reach this: rename, change activity, or
 * delete (two-step confirm, matching the task/structure/report convention). */
@Composable
private fun EditProjectDialog(
    project: ProjectEntity,
    onDismiss: () -> Unit,
    onSave: (name: String, activity: String) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember(project.id) { mutableStateOf(project.name) }
    var activity by remember(project.id) { mutableStateOf(project.activity) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit project") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = activity,
                    onValueChange = { activity = it },
                    label = { Text("Activity") },
                    singleLine = true,
                )
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = { confirmDelete = true },
                ) { Text("Delete project") }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, activity.ifBlank { project.activity }) },
                enabled = name.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete project?") },
            text = {
                Text(
                    "\"${project.name}\" and its tasks, work logs, routes and messages will " +
                        "be removed. Trails, structures and trail reports are shared and stay."
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
