package com.asnidev.trailkeeperoffgrid.data.local

import java.sql.Connection
import java.sql.DriverManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies [TrailkeeperDb.MIGRATION_1_2] against a real SQLite engine
 * (`org.xerial:sqlite-jdbc`) rather than Room's `MigrationTestHelper`,
 * which needs Robolectric/instrumentation and was tried first but is far
 * too slow to run in this environment / CI (see `TrailkeeperDb.kt`'s doc
 * comment). This is a plain JVM unit test — no Android runtime, fast.
 *
 * [V1_CREATE_TABLE] is the full, **frozen** v1 schema: one `CREATE TABLE`
 * per entity, copied verbatim from
 * `app/schemas/com.asnidev.trailkeeperoffgrid.data.local.TrailkeeperDb/1.json`
 * (Room's `${'$'}{TABLE_NAME}` placeholder substituted with the real name).
 * v1 never changes again by definition — a schema bump always ships as a
 * new version — so hardcoding it here carries no drift risk. The migration
 * itself is run via the exact same [TrailkeeperDb.CREATE_TRAIL_REPORTS_V2]
 * string the app ships, not a copy.
 */
class MigrationSqlTest {

    private fun freshV1Connection(): Connection {
        val conn = DriverManager.getConnection("jdbc:sqlite::memory:")
        conn.createStatement().use { st -> V1_CREATE_TABLE.values.forEach { st.execute(it) } }
        return conn
    }

    @Test
    fun v1SchemaHasEveryTableTheAppShipped() {
        freshV1Connection().use { conn ->
            assertEquals(V1_CREATE_TABLE.keys, tableNames(conn))
        }
    }

    @Test
    fun migrate1To2_addsTrailReports_withTheExactShipSchema() {
        freshV1Connection().use { conn ->
            conn.createStatement().use { it.execute(TrailkeeperDb.CREATE_TRAIL_REPORTS_V2) }

            assertEquals(V1_CREATE_TABLE.keys + "trail_reports", tableNames(conn))
            assertEquals(EXPECTED_TRAIL_REPORTS_COLUMNS, columnInfo(conn, "trail_reports"))
        }
    }

    @Test
    fun migrate1To2_isIdempotent() {
        // Room's CREATE_TRAIL_REPORTS_V2 is `IF NOT EXISTS` on purpose - a
        // re-run (e.g. a retried migration) must not fail or change anything.
        freshV1Connection().use { conn ->
            conn.createStatement().use { it.execute(TrailkeeperDb.CREATE_TRAIL_REPORTS_V2) }
            conn.createStatement().use { it.execute(TrailkeeperDb.CREATE_TRAIL_REPORTS_V2) }
            assertEquals(EXPECTED_TRAIL_REPORTS_COLUMNS, columnInfo(conn, "trail_reports"))
        }
    }

    @Test
    fun migrate1To2_preservesExistingRows_andAcceptsNewOnes() {
        freshV1Connection().use { conn ->
            conn.createStatement().use { st ->
                st.execute(
                    "INSERT INTO projects (id, organisationId, name, description, activity, status) " +
                        "VALUES ('p1', 'local', 'Blue Trail', '', 'mtb', 'active')"
                )
            }

            conn.createStatement().use { it.execute(TrailkeeperDb.CREATE_TRAIL_REPORTS_V2) }

            conn.createStatement().use { st ->
                val rs = st.executeQuery("SELECT name FROM projects WHERE id = 'p1'")
                assertTrue("the pre-migration project row must survive", rs.next())
                assertEquals("Blue Trail", rs.getString(1))
            }

            conn.prepareStatement(
                "INSERT INTO trail_reports (id, organisationId, projectId, status, kind, severity, " +
                    "note, geometryJson, nearestTrailId, photosJson, reportedById, createdAt, resolvedAt) " +
                    "VALUES (?, 'local', 'p1', 'impassable', 'bridge', 'high', 'washed out', " +
                    "NULL, NULL, '[]', 'me', '2026-09-10T00:00:00Z', NULL)"
            ).use { ps ->
                ps.setString(1, "r1")
                assertEquals(1, ps.executeUpdate())
            }

            conn.createStatement().use { st ->
                val rs = st.executeQuery("SELECT COUNT(*) FROM trail_reports")
                rs.next()
                assertEquals(1, rs.getInt(1))
            }
        }
    }

    // ---- helpers ------------------------------------------------------

    private fun tableNames(conn: Connection): Set<String> {
        val out = mutableSetOf<String>()
        conn.createStatement().use { st ->
            val rs = st.executeQuery(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'"
            )
            while (rs.next()) out.add(rs.getString(1))
        }
        return out
    }

    private data class Col(val name: String, val type: String, val notNull: Boolean, val pk: Int)

    private fun columnInfo(conn: Connection, table: String): List<Col> {
        val out = mutableListOf<Col>()
        conn.createStatement().use { st ->
            val rs = st.executeQuery("PRAGMA table_info($table)")
            while (rs.next()) {
                out.add(
                    Col(
                        name = rs.getString("name"),
                        type = rs.getString("type"),
                        notNull = rs.getInt("notnull") == 1,
                        pk = rs.getInt("pk"),
                    )
                )
            }
        }
        return out
    }

    private companion object {
        val V1_CREATE_TABLE: Map<String, String> = mapOf(
            "projects" to "CREATE TABLE IF NOT EXISTS `projects` (`id` TEXT NOT NULL, `organisationId` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `activity` TEXT NOT NULL, `status` TEXT NOT NULL, PRIMARY KEY(`id`))",
            "trails" to "CREATE TABLE IF NOT EXISTS `trails` (`id` TEXT NOT NULL, `organisationId` TEXT NOT NULL, `name` TEXT NOT NULL, `activity` TEXT NOT NULL, `difficulty` TEXT NOT NULL, `status` TEXT NOT NULL, `source` TEXT NOT NULL, `lengthM` REAL NOT NULL, `geometryJson` TEXT, PRIMARY KEY(`id`))",
            "tasks" to "CREATE TABLE IF NOT EXISTS `tasks` (`id` TEXT NOT NULL, `projectId` TEXT NOT NULL, `organisationId` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `taskType` TEXT NOT NULL, `priority` TEXT NOT NULL, `status` TEXT NOT NULL, `geometryJson` TEXT, `nearestTrailId` TEXT, `estimateMin` INTEGER, `assigneeIdsJson` TEXT NOT NULL, `photosJson` TEXT NOT NULL, `updatedAt` TEXT NOT NULL, PRIMARY KEY(`id`))",
            "work_logs" to "CREATE TABLE IF NOT EXISTS `work_logs` (`id` TEXT NOT NULL, `projectId` TEXT NOT NULL, `taskId` TEXT, `trailId` TEXT, `userId` TEXT NOT NULL, `minutes` INTEGER NOT NULL, `workedOn` TEXT NOT NULL, `note` TEXT NOT NULL, `autoFromTask` INTEGER NOT NULL, `updatedAt` TEXT NOT NULL, PRIMARY KEY(`id`))",
            "project_members" to "CREATE TABLE IF NOT EXISTS `project_members` (`projectId` TEXT NOT NULL, `userId` TEXT NOT NULL, `email` TEXT NOT NULL, `name` TEXT NOT NULL, `projectRole` TEXT NOT NULL, PRIMARY KEY(`projectId`, `userId`))",
            "messages" to "CREATE TABLE IF NOT EXISTS `messages` (`id` TEXT NOT NULL, `projectId` TEXT NOT NULL, `taskId` TEXT, `authorId` TEXT, `body` TEXT NOT NULL, `mentionedUserIdsJson` TEXT NOT NULL, `createdAt` TEXT NOT NULL, PRIMARY KEY(`id`))",
            "notifications" to "CREATE TABLE IF NOT EXISTS `notifications` (`id` TEXT NOT NULL, `type` TEXT NOT NULL, `subjectType` TEXT NOT NULL, `subjectId` TEXT NOT NULL, `projectId` TEXT, `actorId` TEXT, `body` TEXT NOT NULL, `createdAt` TEXT NOT NULL, `readAt` TEXT, PRIMARY KEY(`id`))",
            "job_types" to "CREATE TABLE IF NOT EXISTS `job_types` (`id` TEXT NOT NULL, `activity` TEXT NOT NULL, `key` TEXT NOT NULL, `label` TEXT NOT NULL, `unit` TEXT NOT NULL, `defaultCrew` INTEGER NOT NULL, `expectedRate` REAL, `color` TEXT NOT NULL, `sortGroup` TEXT NOT NULL, PRIMARY KEY(`id`))",
            "segment_work" to "CREATE TABLE IF NOT EXISTS `segment_work` (`id` TEXT NOT NULL, `projectId` TEXT NOT NULL, `jobTypeId` TEXT, `trailId` TEXT, `quantity` REAL NOT NULL, `unit` TEXT NOT NULL, `quantitySource` TEXT NOT NULL, `startedAt` TEXT NOT NULL, `endedAt` TEXT, `activeSeconds` INTEGER NOT NULL, `crewSize` INTEGER NOT NULL, `equipmentJson` TEXT NOT NULL, `notes` TEXT NOT NULL, `createdById` TEXT, `personHours` REAL NOT NULL, `rateMinPerUnit` REAL, `vsExpectedMinPerUnit` REAL, PRIMARY KEY(`id`))",
            "structures" to "CREATE TABLE IF NOT EXISTS `structures` (`id` TEXT NOT NULL, `organisationId` TEXT NOT NULL, `name` TEXT NOT NULL, `structureType` TEXT NOT NULL, `status` TEXT NOT NULL, `geometryJson` TEXT, `nearestTrailId` TEXT, `material` TEXT NOT NULL, `color` TEXT NOT NULL, `installedOn` TEXT, `inspectionIntervalDays` INTEGER, `notes` TEXT NOT NULL, PRIMARY KEY(`id`))",
            "inspection_forms" to "CREATE TABLE IF NOT EXISTS `inspection_forms` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `targetType` TEXT NOT NULL, `fieldsJson` TEXT NOT NULL, `version` INTEGER NOT NULL, `isActive` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "inspections" to "CREATE TABLE IF NOT EXISTS `inspections` (`id` TEXT NOT NULL, `projectId` TEXT NOT NULL, `structureId` TEXT NOT NULL, `formId` TEXT, `formVersion` INTEGER, `inspectorId` TEXT, `inspectedOn` TEXT NOT NULL, `answersJson` TEXT NOT NULL, `risk` TEXT, `condition` TEXT, `notes` TEXT NOT NULL, PRIMARY KEY(`id`))",
            "tracks" to "CREATE TABLE IF NOT EXISTS `tracks` (`id` TEXT NOT NULL, `projectId` TEXT NOT NULL, `name` TEXT NOT NULL, `activity` TEXT NOT NULL, `source` TEXT NOT NULL, `startedAt` TEXT, `endedAt` TEXT, `movingSeconds` INTEGER NOT NULL, `lengthM` REAL NOT NULL, `pointCount` INTEGER NOT NULL, `geometryJson` TEXT, `recordedById` TEXT, PRIMARY KEY(`id`))",
        )

        // Column order + notNull/pk, straight from
        // app/schemas/.../2.json's `trail_reports` entity.
        val EXPECTED_TRAIL_REPORTS_COLUMNS = listOf(
            Col("id", "TEXT", notNull = true, pk = 1),
            Col("organisationId", "TEXT", notNull = true, pk = 0),
            Col("projectId", "TEXT", notNull = false, pk = 0),
            Col("status", "TEXT", notNull = true, pk = 0),
            Col("kind", "TEXT", notNull = true, pk = 0),
            Col("severity", "TEXT", notNull = true, pk = 0),
            Col("note", "TEXT", notNull = true, pk = 0),
            Col("geometryJson", "TEXT", notNull = false, pk = 0),
            Col("nearestTrailId", "TEXT", notNull = false, pk = 0),
            Col("photosJson", "TEXT", notNull = true, pk = 0),
            Col("reportedById", "TEXT", notNull = false, pk = 0),
            Col("createdAt", "TEXT", notNull = true, pk = 0),
            Col("resolvedAt", "TEXT", notNull = false, pk = 0),
        )
    }
}
