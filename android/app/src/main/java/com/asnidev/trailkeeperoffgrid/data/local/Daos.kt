package com.asnidev.trailkeeperoffgrid.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Upsert suspend fun upsert(row: ProjectEntity)

    @Upsert suspend fun upsertAll(rows: List<ProjectEntity>)

    @Query("SELECT * FROM projects WHERE id = :id") fun observe(id: String): Flow<ProjectEntity?>

    @Query("SELECT * FROM projects WHERE id = :id") suspend fun getById(id: String): ProjectEntity?

    @Query("SELECT * FROM projects ORDER BY name") fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE organisationId = :orgId ORDER BY name")
    suspend fun listForOrg(orgId: String): List<ProjectEntity>

    @Query("DELETE FROM projects WHERE id = :id") suspend fun deleteById(id: String)
}

@Dao
interface TrailDao {
    @Upsert suspend fun upsertAll(rows: List<TrailEntity>)

    @Upsert suspend fun upsert(row: TrailEntity)

    @Query("SELECT * FROM trails WHERE organisationId = :orgId ORDER BY name")
    fun observeForOrg(orgId: String): Flow<List<TrailEntity>>

    @Query("SELECT * FROM trails WHERE organisationId = :orgId ORDER BY name")
    suspend fun listForOrg(orgId: String): List<TrailEntity>

    @Query("DELETE FROM trails WHERE id = :id") suspend fun deleteById(id: String)
}

@Dao
interface TaskDao {
    @Upsert suspend fun upsertAll(rows: List<TaskEntity>)

    @Upsert suspend fun upsert(row: TaskEntity)

    @Query("SELECT * FROM tasks WHERE projectId = :projectId ORDER BY title")
    fun observeForProject(projectId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id") suspend fun getById(id: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE status IN (:statuses)")
    suspend fun listByStatuses(statuses: List<String>): List<TaskEntity>

    @Query("DELETE FROM tasks WHERE id = :id") suspend fun deleteById(id: String)

    @Query("DELETE FROM tasks WHERE projectId = :projectId") suspend fun deleteForProject(projectId: String)
}

@Dao
interface WorkLogDao {
    @Upsert suspend fun upsertAll(rows: List<WorkLogEntity>)

    @Upsert suspend fun upsert(row: WorkLogEntity)

    @Query("SELECT * FROM work_logs WHERE projectId = :projectId ORDER BY workedOn DESC")
    fun observeForProject(projectId: String): Flow<List<WorkLogEntity>>

    @Query("DELETE FROM work_logs WHERE id = :id") suspend fun deleteById(id: String)

    @Query("DELETE FROM work_logs WHERE projectId = :projectId") suspend fun deleteForProject(projectId: String)
}

@Dao
interface JobTypeDao {
    @Upsert suspend fun upsertAll(rows: List<JobTypeEntity>)

    @Upsert suspend fun upsert(row: JobTypeEntity)

    @Query("SELECT * FROM job_types ORDER BY sortGroup, label")
    fun observeAll(): Flow<List<JobTypeEntity>>

    @Query("SELECT * FROM job_types ORDER BY sortGroup, label")
    suspend fun listAll(): List<JobTypeEntity>

    @Query("SELECT * FROM job_types WHERE id = :id") suspend fun getById(id: String): JobTypeEntity?

    @Query("SELECT COUNT(*) FROM job_types") suspend fun count(): Int

    @Query("DELETE FROM job_types WHERE id = :id") suspend fun deleteById(id: String)
}

@Dao
interface SegmentWorkDao {
    @Upsert suspend fun upsertAll(rows: List<SegmentWorkEntity>)

    @Upsert suspend fun upsert(row: SegmentWorkEntity)

    @Query("SELECT * FROM segment_work WHERE projectId = :projectId ORDER BY startedAt DESC")
    fun observeForProject(projectId: String): Flow<List<SegmentWorkEntity>>

    @Query("SELECT * FROM segment_work WHERE projectId = :projectId ORDER BY startedAt DESC")
    suspend fun listForProject(projectId: String): List<SegmentWorkEntity>

    @Query("DELETE FROM segment_work WHERE id = :id") suspend fun deleteById(id: String)

    @Query("DELETE FROM segment_work WHERE projectId = :projectId")
    suspend fun deleteForProject(projectId: String)
}

@Dao
interface StructureDao {
    @Upsert suspend fun upsertAll(rows: List<StructureEntity>)

    @Upsert suspend fun upsert(row: StructureEntity)

    @Query("SELECT * FROM structures WHERE organisationId = :orgId ORDER BY name")
    fun observeForOrg(orgId: String): Flow<List<StructureEntity>>

    @Query("SELECT * FROM structures WHERE id = :id") suspend fun getById(id: String): StructureEntity?

    @Query("SELECT * FROM structures WHERE organisationId = :orgId")
    suspend fun listForOrg(orgId: String): List<StructureEntity>

    @Query("DELETE FROM structures WHERE id = :id") suspend fun deleteById(id: String)
}

@Dao
interface InspectionFormDao {
    @Upsert suspend fun upsertAll(rows: List<InspectionFormEntity>)

    @Upsert suspend fun upsert(row: InspectionFormEntity)

    @Query("SELECT * FROM inspection_forms ORDER BY name")
    fun observeAll(): Flow<List<InspectionFormEntity>>

    @Query("SELECT * FROM inspection_forms WHERE id = :id")
    suspend fun getById(id: String): InspectionFormEntity?

    @Query("DELETE FROM inspection_forms WHERE id = :id") suspend fun deleteById(id: String)
}

@Dao
interface InspectionDao {
    @Upsert suspend fun upsertAll(rows: List<InspectionEntity>)

    @Upsert suspend fun upsert(row: InspectionEntity)

    @Query("SELECT * FROM inspections WHERE projectId = :projectId ORDER BY inspectedOn DESC")
    fun observeForProject(projectId: String): Flow<List<InspectionEntity>>

    @Query("SELECT * FROM inspections") suspend fun listAll(): List<InspectionEntity>

    @Query("DELETE FROM inspections WHERE id = :id") suspend fun deleteById(id: String)

    @Query("DELETE FROM inspections WHERE projectId = :projectId")
    suspend fun deleteForProject(projectId: String)
}

@Dao
interface TrailReportDao {
    @Upsert suspend fun upsert(row: TrailReportEntity)

    @Upsert suspend fun upsertAll(rows: List<TrailReportEntity>)

    @Query("SELECT * FROM trail_reports WHERE organisationId = :orgId ORDER BY createdAt DESC")
    fun observeForOrg(orgId: String): Flow<List<TrailReportEntity>>

    @Query("SELECT * FROM trail_reports WHERE id = :id")
    suspend fun getById(id: String): TrailReportEntity?

    @Query("DELETE FROM trail_reports WHERE id = :id") suspend fun deleteById(id: String)
}

@Dao
interface TrackDao {
    @Upsert suspend fun upsertAll(rows: List<TrackEntity>)

    @Upsert suspend fun upsert(row: TrackEntity)

    @Query("SELECT * FROM tracks WHERE projectId = :projectId ORDER BY startedAt DESC")
    fun observeForProject(projectId: String): Flow<List<TrackEntity>>

    @Query("DELETE FROM tracks WHERE id = :id") suspend fun deleteById(id: String)

    @Query("DELETE FROM tracks WHERE projectId = :projectId")
    suspend fun deleteForProject(projectId: String)
}

@Dao
interface ProjectMemberDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(rows: List<ProjectMemberEntity>)

    @Query("SELECT * FROM project_members WHERE projectId = :projectId ORDER BY name")
    fun observeForProject(projectId: String): Flow<List<ProjectMemberEntity>>

    @Query("DELETE FROM project_members WHERE projectId = :projectId")
    suspend fun deleteForProject(projectId: String)
}

data class TaskCommentCount(val taskId: String, val count: Int)

@Dao
interface MessageDao {
    @Upsert suspend fun upsertAll(rows: List<MessageEntity>)

    @Upsert suspend fun upsert(row: MessageEntity)

    @Query(
        "SELECT * FROM messages WHERE projectId = :projectId AND taskId IS :taskId ORDER BY createdAt"
    )
    fun observeThread(projectId: String, taskId: String?): Flow<List<MessageEntity>>

    @Query(
        "SELECT taskId AS taskId, COUNT(*) AS count FROM messages " +
            "WHERE projectId = :projectId AND taskId IS NOT NULL GROUP BY taskId"
    )
    fun observeTaskCommentCounts(projectId: String): Flow<List<TaskCommentCount>>

    @Query("DELETE FROM messages WHERE id = :id") suspend fun deleteById(id: String)

    @Query("DELETE FROM messages WHERE taskId = :taskId") suspend fun deleteForTask(taskId: String)

    @Query("DELETE FROM messages WHERE projectId = :projectId")
    suspend fun deleteForProject(projectId: String)
}

@Dao
interface NotificationDao {
    @Upsert suspend fun upsertAll(rows: List<NotificationEntity>)

    @Upsert suspend fun upsert(row: NotificationEntity)

    @Query("SELECT * FROM notifications ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<NotificationEntity>>

    @Query("SELECT COUNT(*) FROM notifications WHERE readAt IS NULL")
    fun observeUnreadCount(): Flow<Int>

    @Query(
        "SELECT DISTINCT subjectId FROM notifications " +
            "WHERE subjectType = 'task' AND readAt IS NULL"
    )
    fun observeUnreadTaskIds(): Flow<List<String>>

    @Query(
        "SELECT id FROM notifications " +
            "WHERE subjectType = 'task' AND subjectId = :taskId AND readAt IS NULL"
    )
    suspend fun unreadIdsForTask(taskId: String): List<String>

    @Query("UPDATE notifications SET readAt = :ts WHERE id IN (:ids)")
    suspend fun markRead(ids: List<String>, ts: String)

    @Query("UPDATE notifications SET readAt = :ts WHERE readAt IS NULL")
    suspend fun markAllRead(ts: String)

    @Query("DELETE FROM notifications") suspend fun clear()
}

/** Whole-table reads for the Settings → Backup export. Import reuses each
 * entity DAO's `upsertAll`. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM projects") suspend fun projects(): List<ProjectEntity>
    @Query("SELECT * FROM trails") suspend fun trails(): List<TrailEntity>
    @Query("SELECT * FROM tasks") suspend fun tasks(): List<TaskEntity>
    @Query("SELECT * FROM work_logs") suspend fun workLogs(): List<WorkLogEntity>
    @Query("SELECT * FROM project_members") suspend fun members(): List<ProjectMemberEntity>
    @Query("SELECT * FROM messages") suspend fun messages(): List<MessageEntity>
    @Query("SELECT * FROM notifications") suspend fun notifications(): List<NotificationEntity>
    @Query("SELECT * FROM job_types") suspend fun jobTypes(): List<JobTypeEntity>
    @Query("SELECT * FROM segment_work") suspend fun segmentWork(): List<SegmentWorkEntity>
    @Query("SELECT * FROM structures") suspend fun structures(): List<StructureEntity>
    @Query("SELECT * FROM inspection_forms") suspend fun inspectionForms(): List<InspectionFormEntity>
    @Query("SELECT * FROM inspections") suspend fun inspections(): List<InspectionEntity>
    @Query("SELECT * FROM tracks") suspend fun tracks(): List<TrackEntity>
    @Query("SELECT * FROM trail_reports") suspend fun trailReports(): List<TrailReportEntity>
}
