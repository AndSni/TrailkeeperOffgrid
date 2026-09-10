package com.asnidev.trailkeeperoffgrid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.asnidev.trailkeeperoffgrid.data.Identity
import com.asnidev.trailkeeperoffgrid.data.LocalStore
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
        TrailkeeperDb.init(applicationContext)
        LocalStore.init(applicationContext)
        lifecycleScope.launch { runCatching { LocalStore.seedIfEmpty() } }

        setContent {
            TrailkeeperTheme {
                TrailkeeperApp()
            }
        }
    }
}
