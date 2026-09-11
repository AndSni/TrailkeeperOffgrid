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
 *    under `app/schemas/`. Each [Migration]'s DDL lives in a named
 *    `..._SQL` constant so `data/local/MigrationSqlTest.kt` (a plain-JVM
 *    test against real SQLite via `org.xerial:sqlite-jdbc` — a Robolectric
 *    `MigrationTestHelper` harness was tried first but is far too slow to
 *    run in this environment / CI) can build the real v1 schema, run the
 *    exact same SQL Room runs, and assert the result against the exported
 *    schema JSON.
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
        /** The exact DDL [MIGRATION_1_2] runs — pulled out so
         * `MigrationSqlTest` can execute this same string against a real
         * SQLite engine and assert it produces the schema `2.json` declares. */
        const val CREATE_TRAIL_REPORTS_V2: String =
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

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(CREATE_TRAIL_REPORTS_V2)
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
