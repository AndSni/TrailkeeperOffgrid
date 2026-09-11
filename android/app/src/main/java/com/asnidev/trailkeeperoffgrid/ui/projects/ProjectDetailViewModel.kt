package com.asnidev.trailkeeperoffgrid.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asnidev.trailkeeperoffgrid.data.Identity
import com.asnidev.trailkeeperoffgrid.data.LocalStore
import com.asnidev.trailkeeperoffgrid.data.NotificationRepository
import com.asnidev.trailkeeperoffgrid.record.TrackRecorder
import com.asnidev.trailkeeperoffgrid.data.local.ProjectEntity
import com.asnidev.trailkeeperoffgrid.data.local.TaskEntity
import com.asnidev.trailkeeperoffgrid.data.local.TrailEntity
import com.asnidev.trailkeeperoffgrid.data.local.MessageEntity
import com.asnidev.trailkeeperoffgrid.data.local.ProjectMemberEntity
import com.asnidev.trailkeeperoffgrid.data.local.TrailkeeperDb
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProjectDetailUiState(
    val project: ProjectEntity? = null,
    val trails: List<TrailEntity> = emptyList(),
    val tasks: List<TaskEntity> = emptyList(),
    val syncing: Boolean = false,
    val error: String? = null,
    val loaded: Boolean = false,
)

private data class SyncStatus(val syncing: Boolean = false, val error: String? = null)

data class MessageRow(
    val id: String,
    val authorName: String,
    val body: String,
    val createdAt: String,
    val mine: Boolean,
)

/**
 * Everything the project screen shows comes straight from Room, which is the
 * source of truth. There is nothing to "sync"; [refresh] only exists so the
 * ported UI's pull-to-refresh affordance has a handler, and [SyncStatus]
 * survives purely to surface a photo-write error.
 */
class ProjectDetailViewModel(private val projectId: String) : ViewModel() {
    private val db = TrailkeeperDb.db
    private val orgId = Identity.currentOrgId()
    private val myId = Identity.currentUserId()
    private val sync = MutableStateFlow(SyncStatus())

    private fun toRows(messages: List<MessageEntity>, members: List<ProjectMemberEntity>): List<MessageRow> {
        val names = members.associate { it.userId to it.name }
        return messages.map { m ->
            MessageRow(
                id = m.id,
                authorName = if (m.authorId == myId) Identity.displayName() else names[m.authorId] ?: "Note",
                body = m.body,
                createdAt = m.createdAt,
                mine = m.authorId != null && m.authorId == myId,
            )
        }
    }

    /** The project's own notebook thread. */
    val discussion: StateFlow<List<MessageRow>> =
        combine(
            db.messageDao().observeThread(projectId, null),
            db.projectMemberDao().observeForProject(projectId),
        ) { messages, members -> toRows(messages, members) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** taskId -> number of notes on that task's thread. */
    val taskCommentCounts: StateFlow<Map<String, Int>> =
        db.messageDao()
            .observeTaskCommentCounts(projectId)
            .map { list -> list.associate { it.taskId to it.count } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Task ids with an unread reminder - drives the accent tint on the tile. */
    val unreadCommentTasks: StateFlow<Set<String>> =
        NotificationRepository.unreadTaskIds()
            .map { it.toSet() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** One task's own note thread. */
    fun taskThread(taskId: String): Flow<List<MessageRow>> =
        combine(
            db.messageDao().observeThread(projectId, taskId),
            db.projectMemberDao().observeForProject(projectId),
        ) { messages, members -> toRows(messages, members) }

    val state: StateFlow<ProjectDetailUiState> =
        combine(
            db.projectDao().observe(projectId),
            db.trailDao().observeForOrg(orgId),
            db.taskDao().observeForProject(projectId),
            sync,
        ) { project, trails, tasks, s ->
            ProjectDetailUiState(
                project = project,
                trails = trails,
                tasks = tasks,
                syncing = s.syncing,
                error = s.error,
                loaded = true,
            )
        }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                ProjectDetailUiState(),
            )

    /** Called when a task's note thread is opened - drops its unread accent. */
    fun markTaskCommentsRead(taskId: String) {
        viewModelScope.launch { runCatching { NotificationRepository.markTaskRead(taskId) } }
    }

    fun uploadTaskPhoto(taskId: String, jpeg: java.io.File) {
        viewModelScope.launch {
            runCatching { LocalStore.addTaskPhoto(taskId, jpeg) }
                .onFailure { e ->
                    sync.update { it.copy(error = e.message ?: "Couldn't save the photo") }
                }
        }
    }

    fun deleteTaskPhoto(taskId: String, photoId: String) {
        viewModelScope.launch {
            runCatching { LocalStore.deleteTaskPhoto(taskId, photoId) }
                .onFailure { e ->
                    sync.update { it.copy(error = e.message ?: "Couldn't delete the photo") }
                }
        }
    }

    fun setReportResolved(reportId: String, resolved: Boolean) {
        viewModelScope.launch { runCatching { LocalStore.setReportResolved(reportId, resolved) } }
    }

    fun deleteTrailReport(reportId: String) {
        viewModelScope.launch { runCatching { LocalStore.deleteTrailReport(reportId) } }
    }

    fun uploadReportPhoto(reportId: String, jpeg: java.io.File) {
        viewModelScope.launch {
            runCatching { LocalStore.addReportPhoto(reportId, jpeg) }
                .onFailure { e -> sync.update { it.copy(error = e.message ?: "Couldn't save the photo") } }
        }
    }

    fun deleteReportPhoto(reportId: String, photoId: String) {
        viewModelScope.launch {
            runCatching { LocalStore.deleteReportPhoto(reportId, photoId) }
                .onFailure { e -> sync.update { it.copy(error = e.message ?: "Couldn't delete the photo") } }
        }
    }

    /** No server to reconcile with; the Room flows already push every change. */
    fun refresh() {
        sync.update { it.copy(syncing = false, error = null) }
    }

    fun addTask(title: String, priority: String, lat: Double? = null, lon: Double? = null) {
        if (title.isBlank()) return
        viewModelScope.launch {
            runCatching { LocalStore.createTask(projectId, title.trim(), priority, lat, lon) }
                .onFailure { e -> sync.update { it.copy(error = e.message ?: "Couldn't add the task") } }
        }
    }

    fun setStatus(taskId: String, status: String) {
        viewModelScope.launch { runCatching { LocalStore.setTaskStatus(taskId, status) } }
    }

    fun editTask(taskId: String, title: String, description: String, priority: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            runCatching {
                LocalStore.editTask(taskId, title.trim(), description.trim(), priority)
            }
                .onFailure { e -> sync.update { it.copy(error = e.message ?: "Couldn't edit the task") } }
        }
    }

    fun moveTask(taskId: String, lat: Double, lon: Double) {
        viewModelScope.launch {
            runCatching { LocalStore.moveTask(taskId, lat, lon) }
                .onFailure { e -> sync.update { it.copy(error = e.message ?: "Couldn't move the task") } }
        }
    }

    fun postMessage(body: String, taskId: String? = null) {
        val text = body.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            runCatching { LocalStore.postMessage(projectId, taskId = taskId, body = text) }
                .onFailure { e -> sync.update { it.copy(error = e.message ?: "Couldn't save the note") } }
        }
    }

    fun deleteMessage(id: String) {
        viewModelScope.launch {
            runCatching { LocalStore.deleteMessage(id) }
                .onFailure { e -> sync.update { it.copy(error = e.message ?: "Couldn't delete") } }
        }
    }

    /** Save the current [TrackRecorder] path as a Trail (walk-to-map). */
    fun saveWalkedTrail(name: String, activity: String) {
        val pts = TrackRecorder.state.value.points.map { it.lat to it.lon }
        if (pts.size < 2) {
            sync.update { it.copy(error = "Not enough GPS points yet") }
            return
        }
        viewModelScope.launch {
            runCatching { LocalStore.createTrail(name, activity, pts) }
                .onSuccess { TrackRecorder.reset() }
                .onFailure { e -> sync.update { it.copy(error = e.message ?: "Couldn't save the trail") } }
        }
    }
}
