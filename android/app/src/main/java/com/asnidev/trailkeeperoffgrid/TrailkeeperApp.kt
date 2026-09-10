package com.asnidev.trailkeeperoffgrid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.asnidev.trailkeeperoffgrid.ui.notifications.NotificationsScreen
import com.asnidev.trailkeeperoffgrid.ui.projects.ProjectDetailScreen
import com.asnidev.trailkeeperoffgrid.ui.projects.ProjectListScreen
import com.asnidev.trailkeeperoffgrid.ui.settings.SettingsScreen

private data class OpenProject(val id: String, val name: String)

private val openProjectSaver: Saver<OpenProject?, List<String>> =
    Saver(
        save = { it?.let { p -> listOf(p.id, p.name) } ?: emptyList() },
        restore = { if (it.size == 2) OpenProject(it[0], it[1]) else null },
    )

@Composable
fun TrailkeeperApp() {
    var open by rememberSaveable(stateSaver = openProjectSaver) { mutableStateOf<OpenProject?>(null) }
    var showNotifications by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    val current = open
    when {
        showSettings -> SettingsScreen(onBack = { showSettings = false })
        showNotifications -> NotificationsScreen(onBack = { showNotifications = false })
        current != null ->
            ProjectDetailScreen(
                projectId = current.id,
                projectName = current.name,
                onBack = { open = null },
            )
        else ->
            ProjectListScreen(
                onOpenProject = { id, name -> open = OpenProject(id, name) },
                onOpenNotifications = { showNotifications = true },
                onOpenSettings = { showSettings = true },
            )
    }
}
