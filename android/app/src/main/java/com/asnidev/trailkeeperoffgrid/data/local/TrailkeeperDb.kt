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
 * - v2 → v3: add `structure_types` (editable structure-type taxonomy,
 *   seeded with the app's original hardcoded list; `"other"` is the
 *   permanent fallback a deleted type's structures get reassigned to).
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
        StructureTypeEntity::class,
    ],
    version = 3,
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
    abstract fun structureTypeDao(): StructureTypeDao
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

        /** The exact DDL [MIGRATION_2_3] runs to create the table itself -
         * the seed rows are separate ([SEED_STRUCTURE_TYPES_V3]) since they're
         * DML, not part of the schema `MigrationSqlTest` checks against
         * `3.json`. */
        const val CREATE_STRUCTURE_TYPES_V3: String =
            "CREATE TABLE IF NOT EXISTS `structure_types` (" +
                "`key` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`key`))"

        /** The original hardcoded `STRUCTURE_TYPES` list, in its original
         * order, becoming the seed data for the new editable table. */
        val SEED_STRUCTURE_TYPES_V3: List<String> = listOf(
            "culvert", "bridge", "boardwalk", "ford", "steps", "retaining_wall",
            "drain", "waterbar", "sign", "gate", "bench", "kiosk", "other",
        )

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(CREATE_STRUCTURE_TYPES_V3)
                SEED_STRUCTURE_TYPES_V3.forEachIndexed { i, key ->
                    db.execSQL(
                        "INSERT OR IGNORE INTO structure_types (`key`, sortOrder) VALUES (?, ?)",
                        arrayOf<Any>(key, i),
                    )
                }
            }
        }

        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

        /** A brand-new install creates `structure_types` straight from the
         * entity (no migration runs), so it still needs seeding here. */
        private val SEED_ON_CREATE = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                SEED_STRUCTURE_TYPES_V3.forEachIndexed { i, key ->
                    db.execSQL(
                        "INSERT OR IGNORE INTO structure_types (`key`, sortOrder) VALUES (?, ?)",
                        arrayOf<Any>(key, i),
                    )
                }
            }
        }

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
                            .addCallback(SEED_ON_CREATE)
                            .build()
                            .also { built = it }
                }
    }
}
