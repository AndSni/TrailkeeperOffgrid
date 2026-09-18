package com.asnidev.trailkeeperoffgrid

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.asnidev.trailkeeperoffgrid.data.Identity
import com.asnidev.trailkeeperoffgrid.data.LocalStore
import com.asnidev.trailkeeperoffgrid.data.MapDisplayPrefs
import com.asnidev.trailkeeperoffgrid.data.MapScopePrefs
import com.asnidev.trailkeeperoffgrid.data.Reminders
import com.asnidev.trailkeeperoffgrid.data.ThemePrefs
import com.asnidev.trailkeeperoffgrid.data.local.TrailkeeperDb
import com.asnidev.trailkeeperoffgrid.ui.theme.TrailkeeperTheme
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MapLibre.getInstance(applicationContext)
        Identity.init(applicationContext)
        ThemePrefs.init(applicationContext)
        MapScopePrefs.init(applicationContext)
        MapDisplayPrefs.init(applicationContext)
        TrailkeeperDb.init(applicationContext)
        LocalStore.init(applicationContext)
        lifecycleScope.launch { runCatching { LocalStore.seedIfEmpty() } }
        Reminders.schedule(applicationContext)

        setContent {
            TrailkeeperTheme {
                TrailkeeperApp(pendingImportUri = shareUriFrom(intent))
            }
        }
    }

    /** A `.tkshare` bundle (ShareBundle.kt) the app was opened with - either
     * shared directly to us (ACTION_SEND, e.g. from a Bluetooth transfer or
     * a messaging app) or opened from a file manager / Downloads
     * (ACTION_VIEW). Null for a normal launch. */
    private fun shareUriFrom(intent: Intent?): Uri? =
        when (intent?.action) {
            Intent.ACTION_SEND ->
                if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
            Intent.ACTION_VIEW -> intent.data
            else -> null
        }
}
