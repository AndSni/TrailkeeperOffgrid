package com.asnidev.trailkeeperoffgrid.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.asnidev.trailkeeperoffgrid.data.local.NotificationEntity
import com.asnidev.trailkeeperoffgrid.data.local.TrailkeeperDb
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.reminderStore by preferencesDataStore(name = "trailkeeper_offgrid_reminders")
private val ENABLED = booleanPreferencesKey("reminders_enabled")

/**
 * Replaces Trailkeeper's server-side APScheduler `inspection_due` /
 * `task_overdue` jobs. A daily [ReminderWorker] scans the local database and
 * writes rows into the reminder inbox (`notifications`); the bell badge and
 * `NotificationsScreen` already render them. No network, no FCM.
 */
object Reminders {
    private const val WORK = "trailkeeper-offgrid-reminders"

    /** Open high/urgent tasks older than this raise an "overdue" reminder. */
    private const val TASK_OVERDUE_DAYS = 14L

    fun enabledFlow(context: Context) =
        context.reminderStore.data.map { it[ENABLED] ?: true }

    suspend fun isEnabled(context: Context): Boolean =
        context.reminderStore.data.first()[ENABLED] ?: true

    suspend fun setEnabled(context: Context, on: Boolean) {
        context.reminderStore.edit { it[ENABLED] = on }
        if (on) schedule(context) else WorkManager.getInstance(context).cancelUniqueWork(WORK)
    }

    /** Idempotent — call from MainActivity on every start. */
    fun schedule(context: Context) {
        val req = PeriodicWorkRequestBuilder<ReminderWorker>(Duration.ofHours(24))
            .setConstraints(Constraints(requiredNetworkType = NetworkType.NOT_REQUIRED))
            .setInitialDelay(Duration.ofHours(1))
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /** "Check now" from Settings. */
    fun runNow(context: Context) {
        WorkManager.getInstance(context)
            .enqueue(OneTimeWorkRequestBuilder<ReminderWorker>().build())
    }

    internal const val OVERDUE_DAYS = TASK_OVERDUE_DAYS
}

class ReminderWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!Reminders.isEnabled(applicationContext)) return Result.success()
        val db = TrailkeeperDb.db
        val now = Instant.now()
        val fresh = ArrayList<NotificationEntity>()

        // --- inspection due -------------------------------------------
        val inspections = db.inspectionDao().listAll()
        val lastByStructure = inspections
            .groupBy { it.structureId }
            .mapValues { (_, list) -> list.maxOf { it.inspectedOn } }
        for (s in db.structureDao().listForOrg(Identity.ORG_ID)) {
            val interval = s.inspectionIntervalDays ?: continue
            if (interval <= 0) continue
            val last = lastByStructure[s.id]
            val overdue = last == null ||
                daysBetween(last, now) >= interval
            if (overdue) {
                fresh.add(
                    notif(
                        id = "insp-${s.id}",
                        type = "inspection_due",
                        subjectType = "structure",
                        subjectId = s.id,
                        body = "Inspection due: ${s.name}" +
                            (last?.let { " (last ${it.take(10)})" } ?: " (never inspected)"),
                        now = now,
                    )
                )
            }
        }

        // --- task overdue -------------------------------------------
        val cutoff = now.minus(Reminders.OVERDUE_DAYS, ChronoUnit.DAYS).toString()
        for (t in db.taskDao().listByStatuses(listOf("open"))) {
            if (t.priority != "high" && t.priority != "urgent") continue
            if (t.updatedAt.isBlank() || t.updatedAt > cutoff) continue
            fresh.add(
                notif(
                    id = "overdue-${t.id}",
                    type = "task_overdue",
                    subjectType = "task",
                    subjectId = t.id,
                    body = "Still open (${t.priority}): ${t.title}",
                    now = now,
                )
            )
        }

        if (fresh.isNotEmpty()) {
            // Deterministic ids -> re-runs replace the same row rather than
            // stacking. A row the user already read (readAt set) keeps its
            // read state only if we don't re-insert; so only add ids that
            // aren't already present.
            val existing = db.notificationDao().observeAll().first().map { it.id }.toSet()
            val toAdd = fresh.filter { it.id !in existing }
            if (toAdd.isNotEmpty()) db.notificationDao().upsertAll(toAdd)
        }
        return Result.success()
    }

    private fun notif(
        id: String,
        type: String,
        subjectType: String,
        subjectId: String,
        body: String,
        now: Instant,
    ) = NotificationEntity(
        id = id,
        type = type,
        subjectType = subjectType,
        subjectId = subjectId,
        projectId = null,
        actorId = null,
        body = body,
        createdAt = now.toString(),
        readAt = null,
    )

    private fun daysBetween(iso: String, now: Instant): Long =
        runCatching {
            ChronoUnit.DAYS.between(Instant.parse(iso), now)
        }.getOrDefault(Long.MAX_VALUE)
}
