package com.asnidev.trailkeeperoffgrid.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Trailkeeper Offgrid's local database — the **only** copy of the user's
 * data. There is no server to re-sync from, so:
 *
 *  - `exportSchema = true`: the schema JSON is committed under
 *    `app/schemas/` and every version bump must ship a tested [androidx.room.migration.Migration].
 *  - **No** `fallbackToDestructiveMigration()`. A missing migration throws
 *    loudly at open instead of silently wiping the field data.
 *
 * (A `MigrationTestHelper` harness is the next hardening slice.)
 */
@Database(
    entities = [
        ProjectEntity::class,
        TrailEntity::class,
        TaskEntity::class,
        WorkLogEntity::class,
        ProjectMemberEntity::class,
        MessageEntity::class,
        NotificationEntity::class,
        JobTypeEntity::class,
        SegmentWorkEntity::class,
        StructureEntity::class,
        InspectionFormEntity::class,
        InspectionEntity::class,
        TrackEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class TrailkeeperDb : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun trailDao(): TrailDao
    abstract fun taskDao(): TaskDao
    abstract fun workLogDao(): WorkLogDao
    abstract fun projectMemberDao(): ProjectMemberDao
    abstract fun messageDao(): MessageDao
    abstract fun notificationDao(): NotificationDao
    abstract fun jobTypeDao(): JobTypeDao
    abstract fun segmentWorkDao(): SegmentWorkDao
    abstract fun structureDao(): StructureDao
    abstract fun inspectionFormDao(): InspectionFormDao
    abstract fun inspectionDao(): InspectionDao
    abstract fun trackDao(): TrackDao

    companion object {
        @Volatile private var built: TrailkeeperDb? = null

        /** Call once from MainActivity with the application context. */
        fun init(context: Context) {
            get(context)
        }

        /** The singleton, after [init]. */
        val db: TrailkeeperDb
            get() = built ?: error("TrailkeeperDb.init() was not called")

        fun get(context: Context): TrailkeeperDb =
            built
                ?: synchronized(this) {
                    built
                        ?: Room.databaseBuilder(
                                context.applicationContext,
                                TrailkeeperDb::class.java,
                                "trailkeeper_offgrid.db",
                            )
                            .build()
                            .also { built = it }
                }
    }
}
