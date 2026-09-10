package com.asnidev.trailkeeperoffgrid.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Trailkeeper Offgrid's local database — the **only** copy of the user's
 * data. There is no server to re-sync from, so:
 *
 *  - `exportSchema = true`: the schema JSON for every version is committed
 *    under `app/schemas/`. Each [Migration]'s DDL is checked byte-for-byte
 *    against the exported `createSql` of the target version before release
 *    (a Robolectric `MigrationTestHelper` harness was tried but is far too
 *    slow to run in this environment / CI).
 *  - **No** `fallbackToDestructiveMigration()`. A missing migration throws
 *    loudly at open instead of silently wiping the field data.
 *
 * ## Migration history
 * - v1 → v2: add `trail_reports` (P5 trail-condition reports).
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
        TrailReportEntity::class,
        TrackEntity::class,
    ],
    version = 2,
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
    abstract fun trailReportDao(): TrailReportDao
    abstract fun trackDao(): TrackDao
    abstract fun backupDao(): BackupDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `trail_reports` (" +
                        "`id` TEXT NOT NULL, " +
                        "`organisationId` TEXT NOT NULL, " +
                        "`projectId` TEXT, " +
                        "`status` TEXT NOT NULL, " +
                        "`kind` TEXT NOT NULL, " +
                        "`severity` TEXT NOT NULL, " +
                        "`note` TEXT NOT NULL, " +
                        "`geometryJson` TEXT, " +
                        "`nearestTrailId` TEXT, " +
                        "`photosJson` TEXT NOT NULL, " +
                        "`reportedById` TEXT, " +
                        "`createdAt` TEXT NOT NULL, " +
                        "`resolvedAt` TEXT, " +
                        "PRIMARY KEY(`id`))"
                )
            }
        }

        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2)

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
                            .addMigrations(*ALL_MIGRATIONS)
                            .build()
                            .also { built = it }
                }
    }
}
