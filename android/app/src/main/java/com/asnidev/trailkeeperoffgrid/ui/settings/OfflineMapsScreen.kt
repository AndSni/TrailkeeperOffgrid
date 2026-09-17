package com.asnidev.trailkeeperoffgrid.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Every country, grouped by continent, reachable from Settings → Offline
 * maps → "Browse all countries". Also folds in any hand-picked "quick"
 * region that isn't already a whole country (e.g. "Riga & Vidzeme" - a
 * sub-country test area) into its own group at the top, so removing the
 * old always-visible quick-preset list from the main Settings screen
 * doesn't make that region unreachable. Same [RegionRow] /
 * [OfflineMaps.download] both use; a large country may hit MapLibre's
 * tile-count limit in one go (surfaced as the existing "too large — try a
 * smaller region" message).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineMapsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val countries by vm.countries.collectAsState()
    val quickPresets by vm.quickPresets.collectAsState()
    val downloaded by vm.downloadedRegions.collectAsState()
    val downloads by vm.downloads.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }

    // countries is already continent-then-name sorted (home continent
    // first); groupBy preserves that iteration order for both the groups
    // and the rows within each group. Quick presets whose id duplicates a
    // real country (Latvia/Estonia/Lithuania are quick presets AND full
    // countries) are dropped here - they're already reachable below.
    val grouped = remember(countries, quickPresets, query) {
        val countryIds = countries.map { it.id }.toSet()
        val quick = quickPresets.filter { it.id !in countryIds }
        val all = quick.map { it.copy(continent = "Quick") } + countries
        val filtered =
            if (query.isBlank()) all
            else all.filter { it.name.contains(query, ignoreCase = true) }
        filtered.groupBy { it.continent.ifBlank { "Other" } }
            .toList()
            .sortedBy { (continent, _) -> if (continent == "Quick") 0 else 1 }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("All countries") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search countries") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            if (grouped.isEmpty()) {
                item {
                    Text(
                        "No countries match \"$query\".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }
            grouped.forEach { (continent, list) ->
                item(key = "header_$continent") {
                    Text(
                        continent,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    )
                }
                items(list, key = { it.id }) { p ->
                    RegionRow(
                        preset = p,
                        downloading = downloads[p.id],
                        alreadySaved = downloaded.any { it.name == p.name && it.complete },
                        onDownload = { vm.downloadPreset(p) },
                    )
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                }
            }
            item { Spacer(Modifier.padding(bottom = 16.dp)) }
        }
    }
}
