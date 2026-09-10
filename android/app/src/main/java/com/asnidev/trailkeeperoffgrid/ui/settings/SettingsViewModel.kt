package com.asnidev.trailkeeperoffgrid.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.asnidev.trailkeeperoffgrid.data.Identity
import com.asnidev.trailkeeperoffgrid.data.LocalStore
import com.asnidev.trailkeeperoffgrid.data.OfflineMaps
import com.asnidev.trailkeeperoffgrid.data.Reminders
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RegionDownloadUi(
    val completed: Long = 0,
    val required: Long = 0,
    val bytes: Long = 0,
    val running: Boolean = true,
    val error: String? = null,
) {
    val fraction: Float
        get() = if (required > 0) (completed.toFloat() / required).coerceIn(0f, 1f) else 0f
}

data class StorageUi(
    val dbBytes: Long = 0,
    val photoBytes: Long = 0,
    val trackBytes: Long = 0,
    val tileBytes: Long = 0,
) {
    val total get() = dbBytes + photoBytes + trackBytes + tileBytes
}

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx get() = getApplication<Application>()

    val displayName: StateFlow<String> =
        Identity.displayNameFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Identity.rawDisplayName())

    val presets = MutableStateFlow<List<OfflineMaps.Preset>>(emptyList())
    val downloadedRegions = MutableStateFlow<List<OfflineMaps.DownloadedRegion>>(emptyList())

    /** preset id -> live download state */
    val downloads = MutableStateFlow<Map<String, RegionDownloadUi>>(emptyMap())

    val storage = MutableStateFlow(StorageUi())

    val remindersEnabled: StateFlow<Boolean> =
        Reminders.enabledFlow(ctx)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    init {
        viewModelScope.launch { presets.value = OfflineMaps.presets(ctx) }
        refreshRegions()
        refreshStorage()
    }

    fun saveDisplayName(name: String) {
        viewModelScope.launch { Identity.setDisplayName(name) }
    }

    fun refreshRegions() {
        viewModelScope.launch {
            downloadedRegions.value = runCatching { OfflineMaps.downloaded(ctx) }.getOrDefault(emptyList())
        }
    }

    fun refreshStorage() {
        viewModelScope.launch {
            val s = LocalStore.storage()
            val tiles = runCatching { OfflineMaps.totalBytes(ctx) }.getOrDefault(0L)
            storage.value = StorageUi(s.dbBytes, s.photoBytes, s.trackBytes, tiles)
        }
    }

    fun downloadPreset(preset: OfflineMaps.Preset) {
        if (downloads.value[preset.id]?.running == true) return
        downloads.update { it + (preset.id to RegionDownloadUi()) }
        viewModelScope.launch {
            OfflineMaps.download(ctx, preset.name, preset.bounds).collect { ev ->
                when (ev) {
                    is OfflineMaps.Event.Progress ->
                        downloads.update {
                            it + (preset.id to RegionDownloadUi(ev.completed, ev.required, ev.bytes, running = true))
                        }
                    OfflineMaps.Event.Complete -> {
                        downloads.update { it - preset.id }
                        _message.value = "\"${preset.name}\" saved for offline use."
                        refreshRegions(); refreshStorage()
                    }
                    is OfflineMaps.Event.Failed -> {
                        downloads.update { it + (preset.id to RegionDownloadUi(running = false, error = ev.message)) }
                        _message.value = "Download failed: ${ev.message}"
                    }
                    is OfflineMaps.Event.TileLimit -> {
                        downloads.update {
                            it + (preset.id to RegionDownloadUi(running = false, error = "area too large"))
                        }
                        _message.value = "That area is too large to download in one go — try a smaller region."
                    }
                }
            }
        }
    }

    fun deleteRegion(id: Long, name: String) {
        viewModelScope.launch {
            OfflineMaps.delete(ctx, id)
            _message.value = "Deleted \"$name\"."
            refreshRegions(); refreshStorage()
        }
    }

    fun purgeCompletedTaskPhotos() {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val (tasks, files, bytes) = LocalStore.purgeDoneTaskPhotos()
            _busy.value = false
            _message.value =
                if (files == 0) "No photos on completed tasks to clear."
                else "Cleared $files photo(s) from $tasks completed task(s), freed ${humanBytes(bytes)}. Task data kept."
            refreshStorage()
        }
    }

    fun setReminders(on: Boolean) {
        viewModelScope.launch { Reminders.setEnabled(ctx, on) }
    }

    fun checkRemindersNow() {
        Reminders.runNow(ctx)
        _message.value = "Checking for inspection-due and overdue tasks…"
    }

    fun clearMessage() { _message.value = null }
}

fun humanBytes(b: Long): String {
    if (b < 1024) return "$b B"
    val kb = b / 1024.0
    if (kb < 1024) return "%.0f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.2f GB".format(mb / 1024.0)
}
