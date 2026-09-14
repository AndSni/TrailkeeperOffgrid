package com.asnidev.trailkeeperoffgrid.ui.settings

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.asnidev.trailkeeperoffgrid.data.local.StructureEntity
import com.asnidev.trailkeeperoffgrid.ui.structures.EditStructureDialog
import kotlinx.coroutines.withTimeoutOrNull

/**
 * All structures, org-wide - not scoped to one project (structures aren't
 * project-owned; the same culvert/bridge shows up in every project whose
 * working area covers it). Same hold-to-edit gesture as the Structures tab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageStructuresScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val structures by vm.structures.collectAsState()
    var editing by remember { mutableStateOf<StructureEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage structures") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (structures.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                Text(
                    "No structures yet. Add culverts, bridges, signs and more from a " +
                        "project's Structures tab.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(structures, key = { it.id }) { s ->
                // Hold for 1s -> edit/delete, same gesture as the Structures tab.
                Card(
                    modifier = Modifier.fillMaxWidth().pointerInput(s.id) {
                        awaitEachGesture {
                            awaitFirstDown()
                            val heldFull =
                                withTimeoutOrNull(1_000L) {
                                    waitForUpOrCancellation()
                                    true
                                } == null
                            if (heldFull) {
                                editing = s
                                waitForUpOrCancellation()
                            }
                        }
                    }
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(s.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            buildString {
                                append(s.structureType.replace('_', ' '))
                                append(" · ${s.status.replace('_', ' ')}")
                                if (s.material.isNotBlank()) append(" · ${s.material}")
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    editing?.let { s ->
        EditStructureDialog(
            structure = s,
            onDismiss = { editing = null },
            onSave = { req -> vm.patchStructure(s.id, req); editing = null },
            onDelete = { vm.deleteStructure(s.id); editing = null },
        )
    }
}
