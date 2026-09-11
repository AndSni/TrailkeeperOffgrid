package com.asnidev.trailkeeperoffgrid.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asnidev.trailkeeperoffgrid.data.LocalStore
import com.asnidev.trailkeeperoffgrid.data.local.ProjectEntity
import com.asnidev.trailkeeperoffgrid.data.local.TrailkeeperDb
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProjectsUiState(
    val loading: Boolean = true,
    val projects: List<ProjectEntity> = emptyList(),
    val creating: Boolean = false,
    val error: String? = null,
)

class ProjectListViewModel : ViewModel() {
    private val creating = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    val state: StateFlow<ProjectsUiState> =
        combine(
            TrailkeeperDb.db.projectDao().observeAll(),
            creating,
            error,
        ) { projects, isCreating, err ->
            ProjectsUiState(
                loading = false,
                projects = projects,
                creating = isCreating,
                error = err,
            )
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProjectsUiState())

    /** Kept so the toolbar refresh button still has something to call. */
    fun refresh() {}

    fun create(name: String, activity: String) {
        if (name.isBlank() || creating.value) return
        creating.value = true
        error.value = null
        viewModelScope.launch {
            runCatching { LocalStore.createProject(name, activity) }
                .onFailure { e -> error.value = e.message ?: "Couldn't create project" }
            creating.value = false
        }
    }

    fun editProject(id: String, name: String, activity: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            runCatching { LocalStore.editProject(id, name, activity) }
                .onFailure { e -> error.value = e.message ?: "Couldn't save the project" }
        }
    }

    fun deleteProject(id: String) {
        viewModelScope.launch {
            runCatching { LocalStore.deleteProject(id) }
                .onFailure { e -> error.value = e.message ?: "Couldn't delete the project" }
        }
    }
}
