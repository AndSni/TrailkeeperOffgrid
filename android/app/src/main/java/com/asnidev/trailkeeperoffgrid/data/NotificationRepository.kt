package com.asnidev.trailkeeperoffgrid.data

import com.asnidev.trailkeeperoffgrid.data.local.NotificationEntity
import com.asnidev.trailkeeperoffgrid.data.local.TrailkeeperDb
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * The local reminder inbox. In Trailkeeper this polled `GET /notifications`;
 * Offgrid has no server, so rows are only ever written locally — by a future
 * `WorkManager` job that raises "inspection due" / "task overdue" reminders
 * (P4). For now it just backs the bell badge and the list, both empty until
 * that job lands.
 */
object NotificationRepository {
    private val db get() = TrailkeeperDb.db

    fun unreadCount(): Flow<Int> = db.notificationDao().observeUnreadCount()

    fun all(): Flow<List<NotificationEntity>> = db.notificationDao().observeAll()

    /** Task ids that have at least one unread reminder. */
    fun unreadTaskIds(): Flow<List<String>> = db.notificationDao().observeUnreadTaskIds()

    suspend fun markTaskRead(taskId: String) {
        markRead(db.notificationDao().unreadIdsForTask(taskId))
    }

    /** No-op placeholder — kept so callers that "refresh" don't need to change. */
    suspend fun refresh() {}

    suspend fun add(row: NotificationEntity) = db.notificationDao().upsert(row)

    suspend fun markRead(ids: List<String>) {
        if (ids.isEmpty()) return
        db.notificationDao().markRead(ids, Instant.now().toString())
    }

    suspend fun markAllRead() {
        db.notificationDao().markAllRead(Instant.now().toString())
    }
}
