package com.asnidev.trailkeeperoffgrid.ui.settings

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.asnidev.trailkeeperoffgrid.data.local.StructureTypeEntity
import kotlinx.coroutines.withTimeoutOrNull

private const val LOCKED_KEY = "other"

/**
 * The editable structure-type taxonomy (culvert, bridge, boardwalk, …) -
 * distinct from editing individual structures, which already happens
 * inline on the Structures tab (hold a card). "other" is a permanent
 * fallback: deleting a type reassigns its structures to it, so it can't
 * itself be renamed or deleted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StructureTypesScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val types by vm.structureTypes.collectAsState()
    var editing by remember { mutableStateOf<StructureTypeEntity?>(null) }
    var adding by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("Structure types") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add type")
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "Used when adding or editing a structure. Hold a type to rename " +
                        "or delete it - deleting one moves its structures to \"other\".",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(types, key = { it.key }) { t ->
                val locked = t.key == LOCKED_KEY
                Card(
                    modifier = Modifier.fillMaxWidth().pointerInput(t.key, locked) {
                        if (locked) return@pointerInput
                        awaitEachGesture {
                            awaitFirstDown()
                            val heldFull =
                                withTimeoutOrNull(1_000L) {
                                    waitForUpOrCancellation()
                                    true
                                } == null
                            if (heldFull) {
                                editing = t
                                waitForUpOrCancellation()
                            }
                        }
                    }
                ) {
                    Row(
                        Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(t.key.replace('_', ' '), style = MaterialTheme.typography.titleMedium)
                        if (locked) {
                            Icon(
                                Icons.Filled.Lock,
                                contentDescription = "Permanent default",
                                modifier = Modifier.padding(start = 8.dp).size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    if (adding) {
        AddTypeDialog(
            onDismiss = { adding = false },
            onAdd = { label -> vm.addStructureType(label); adding = false },
        )
    }

    editing?.let { t ->
        EditTypeDialog(
            type = t,
            onDismiss = { editing = null },
            onSave = { label -> vm.renameStructureType(t.key, label); editing = null },
            onDelete = { vm.deleteStructureType(t.key); editing = null },
        )
    }
}

@Composable
private fun AddTypeDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var label by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add structure type") },
        text = {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = label.isNotBlank(), onClick = { onAdd(label) }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun EditTypeDialog(
    type: StructureTypeEntity,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var label by remember(type.key) { mutableStateOf(type.key.replace('_', ' ')) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit structure type") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = { confirmDelete = true },
                ) { Text("Delete type") }
            }
        },
        confirmButton = {
            TextButton(enabled = label.isNotBlank(), onClick = { onSave(label) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete \"${type.key.replace('_', ' ')}\"?") },
            text = { Text("Structures using it move to \"other\" instead of losing their type.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
