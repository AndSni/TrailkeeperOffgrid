package com.asnidev.trailkeeperoffgrid

import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.asnidev.trailkeeperoffgrid.ui.notifications.NotificationsScreen
import com.asnidev.trailkeeperoffgrid.ui.projects.ProjectDetailScreen
import com.asnidev.trailkeeperoffgrid.ui.projects.ProjectListScreen
import com.asnidev.trailkeeperoffgrid.ui.settings.SettingsScreen
import com.asnidev.trailkeeperoffgrid.ui.share.ImportScreen
import kotlinx.coroutines.delay

private data class OpenProject(val id: String, val name: String)

private val openProjectSaver: Saver<OpenProject?, List<String>> =
    Saver(
        save = { it?.let { p -> listOf(p.id, p.name) } ?: emptyList() },
        restore = { if (it.size == 2) OpenProject(it[0], it[1]) else null },
    )

private val SplashBackground = Color(0xFF141712) // matches @color/splash_background
private const val SPLASH_MILLIS = 2000L

/**
 * A branded splash (the full skull/trail emblem + wordmark, not just a
 * small centered icon) shown for [SPLASH_MILLIS] on launch, then crossfaded
 * into the real app. `android:windowBackground` is already this same dark
 * colour (see themes.xml), so there's no flash before this draws.
 */
@Composable
fun TrailkeeperApp(pendingImportUri: Uri? = null) {
    var showSplash by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(SPLASH_MILLIS)
        showSplash = false
    }

    Crossfade(targetState = showSplash, label = "splash") { splash ->
        if (splash) SplashScreen() else MainNav(pendingImportUri)
    }
}

@Composable
private fun SplashScreen() {
    Box(Modifier.fillMaxSize().background(SplashBackground), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.splash_logo),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(32.dp),
        )
    }
}

private val uriSaver: Saver<Uri?, String> =
    Saver(save = { it?.toString() ?: "" }, restore = { if (it.isEmpty()) null else Uri.parse(it) })

@Composable
private fun MainNav(initialImportUri: Uri? = null) {
    var open by rememberSaveable(stateSaver = openProjectSaver) { mutableStateOf<OpenProject?>(null) }
    var showNotifications by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var importUri by rememberSaveable(stateSaver = uriSaver) { mutableStateOf(initialImportUri) }

    val current = open
    when {
        importUri != null -> ImportScreen(uri = importUri!!, onDone = { importUri = null })
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
