package com.asnidev.trailkeeperoffgrid.data

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.withTransaction
import com.asnidev.trailkeeperoffgrid.data.local.InspectionEntity
import com.asnidev.trailkeeperoffgrid.data.local.InspectionFormEntity
import com.asnidev.trailkeeperoffgrid.data.local.JobTypeEntity
import com.asnidev.trailkeeperoffgrid.data.local.MessageEntity
import com.asnidev.trailkeeperoffgrid.data.local.NotificationEntity
import com.asnidev.trailkeeperoffgrid.data.local.ProjectEntity
import com.asnidev.trailkeeperoffgrid.data.local.ProjectMemberEntity
import com.asnidev.trailkeeperoffgrid.data.local.SegmentWorkEntity
import com.asnidev.trailkeeperoffgrid.data.local.StructureEntity
import com.asnidev.trailkeeperoffgrid.data.local.TaskEntity
import com.asnidev.trailkeeperoffgrid.data.local.TrackEntity
import com.asnidev.trailkeeperoffgrid.data.local.TrailEntity
import com.asnidev.trailkeeperoffgrid.data.local.TrailkeeperDb
import com.asnidev.trailkeeperoffgrid.data.local.WorkLogEntity
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.BufferedInputStream
import java.io.File
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.backupStore by preferencesDataStore(name = "trailkeeper_offgrid_backup")
private val LAST_BACKUP = stringPreferencesKey("last_backup_iso")

/**
 * The backup story for an app with no server. [exportWorkspace] writes a
 * single `.zip` (to a user-picked SAF location) holding every Room table as
 * a JSON array, GeoJSON copies of the map layers, and the task-photo files.
 * [importWorkspace] reads one back — merge (upsert) or replace (wipe first).
 *
 * This zip is also the only way to move data between phones.
 */
object Backup {
    private val pretty: Gson = GsonBuilder().setPrettyPrinting().create()
    private val plain = Gson()
    private const val SCHEMA = 1

    data class Result(val ok: Boolean, val detail: String)

    fun lastBackupFlow(context: Context) = context.backupStore.data.map { it[LAST_BACKUP] }

    suspend fun lastBackup(context: Context): String? =
        context.backupStore.data.first()[LAST_BACKUP]

    private suspend fun markBackedUp(context: Context) {
        context.backupStore.edit { it[LAST_BACKUP] = Instant.now().toString() }
    }

    suspend fun exportWorkspace(context: Context, dest: Uri): Result = withContext(Dispatchers.IO) {
        val b = TrailkeeperDb.db.backupDao()
        try {
            context.contentResolver.openOutputStream(dest)?.use { raw ->
                ZipOutputStream(raw.buffered()).use { zip ->
                    fun put(name: String, body: String) {
                        zip.putNextEntry(ZipEntry(name))
                        zip.write(body.toByteArray())
                        zip.closeEntry()
                    }

                    val projects = b.projects()
                    val trails = b.trails()
                    val tasks = b.tasks()
                    val structures = b.structures()
                    val tracks = b.tracks()

                    put(
                        "manifest.json",
                        pretty.toJson(
                            mapOf(
                                "app" to "trailkeeper-offgrid",
                                "schema" to SCHEMA,
                                "exportedAt" to Instant.now().toString(),
                                "displayName" to Identity.rawDisplayName(),
                                "counts" to mapOf(
                                    "projects" to projects.size,
                                    "trails" to trails.size,
                                    "tasks" to tasks.size,
                                    "structures" to structures.size,
                                    "tracks" to tracks.size,
                                ),
                            )
                        ),
                    )

                    put("data/projects.json", pretty.toJson(projects))
                    put("data/trails.json", pretty.toJson(trails))
                    put("data/tasks.json", pretty.toJson(tasks))
                    put("data/work_logs.json", pretty.toJson(b.workLogs()))
                    put("data/project_members.json", pretty.toJson(b.members()))
                    put("data/messages.json", pretty.toJson(b.messages()))
                    put("data/notifications.json", pretty.toJson(b.notifications()))
                    put("data/job_types.json", pretty.toJson(b.jobTypes()))
                    put("data/segment_work.json", pretty.toJson(b.segmentWork()))
                    put("data/structures.json", pretty.toJson(structures))
                    put("data/inspection_forms.json", pretty.toJson(b.inspectionForms()))
                    put("data/inspections.json", pretty.toJson(b.inspections()))
                    put("data/tracks.json", pretty.toJson(tracks))

                    put("geojson/trails.geojson", featureCollection(trails.mapNotNull { it.geometryJson }))
                    put("geojson/tasks.geojson", featureCollection(tasks.mapNotNull { it.geometryJson }))
                    put("geojson/structures.geojson", featureCollection(structures.mapNotNull { it.geometryJson }))
                    put("geojson/tracks.geojson", featureCollection(tracks.mapNotNull { it.geometryJson }))

                    val photoRoot = File(context.getExternalFilesDir(null), "photos")
                    if (photoRoot.isDirectory) {
                        photoRoot.walkTopDown().filter { it.isFile }.forEach { f ->
                            zip.putNextEntry(ZipEntry("photos/" + f.relativeTo(photoRoot).path))
                            f.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                }
            } ?: return@withContext Result(false, "Couldn't open the destination file")
            markBackedUp(context)
            Result(true, "Backup written")
        } catch (e: Exception) {
            Result(false, e.message ?: "Export failed")
        }
    }

    suspend fun importWorkspace(context: Context, src: Uri, replace: Boolean): Result =
        withContext(Dispatchers.IO) {
            val db = TrailkeeperDb.db
            try {
                val json = HashMap<String, String>()
                val photos = ArrayList<Pair<String, ByteArray>>()
                context.contentResolver.openInputStream(src)?.use { raw ->
                    ZipInputStream(BufferedInputStream(raw)).use { zip ->
                        var e: ZipEntry? = zip.nextEntry
                        while (e != null) {
                            val name = e.name
                            when {
                                name.startsWith("data/") && name.endsWith(".json") ->
                                    json[name.removePrefix("data/").removeSuffix(".json")] =
                                        zip.readBytes().toString(Charsets.UTF_8)
                                name.startsWith("photos/") && !e.isDirectory ->
                                    photos.add(name.removePrefix("photos/") to zip.readBytes())
                            }
                            zip.closeEntry()
                            e = zip.nextEntry
                        }
                    }
                } ?: return@withContext Result(false, "Couldn't open the backup file")

                if (json.isEmpty()) return@withContext Result(false, "Not a Trailkeeper Offgrid backup")

                db.withTransaction {
                    if (replace) db.clearAllTables()
                    json["projects"]?.let { db.projectDao().upsertAll(list<ProjectEntity>(it)) }
                    json["trails"]?.let { db.trailDao().upsertAll(list<TrailEntity>(it)) }
                    json["tasks"]?.let { db.taskDao().upsertAll(list<TaskEntity>(it)) }
                    json["work_logs"]?.let { db.workLogDao().upsertAll(list<WorkLogEntity>(it)) }
                    json["project_members"]?.let { db.projectMemberDao().upsertAll(list<ProjectMemberEntity>(it)) }
                    json["messages"]?.let { db.messageDao().upsertAll(list<MessageEntity>(it)) }
                    json["notifications"]?.let { db.notificationDao().upsertAll(list<NotificationEntity>(it)) }
                    json["job_types"]?.let { db.jobTypeDao().upsertAll(list<JobTypeEntity>(it)) }
                    json["segment_work"]?.let { db.segmentWorkDao().upsertAll(list<SegmentWorkEntity>(it)) }
                    json["structures"]?.let { db.structureDao().upsertAll(list<StructureEntity>(it)) }
                    json["inspection_forms"]?.let { db.inspectionFormDao().upsertAll(list<InspectionFormEntity>(it)) }
                    json["inspections"]?.let { db.inspectionDao().upsertAll(list<InspectionEntity>(it)) }
                    json["tracks"]?.let { db.trackDao().upsertAll(list<TrackEntity>(it)) }
                }

                val photoRoot = File(context.getExternalFilesDir(null), "photos")
                if (replace) runCatching { photoRoot.deleteRecursively() }
                for ((rel, bytes) in photos) {
                    val out = File(photoRoot, rel)
                    out.parentFile?.mkdirs()
                    out.writeBytes(bytes)
                }
                Result(true, "Restored ${json.size} data files")
            } catch (e: Exception) {
                Result(false, e.message ?: "Restore failed")
            }
        }

    private inline fun <reified T> list(json: String): List<T> =
        plain.fromJson(json, Array<T>::class.java).toList()

    private fun featureCollection(geoms: List<String>): String =
        """{"type":"FeatureCollection","features":[""" +
            geoms.joinToString(",") { """{"type":"Feature","geometry":$it,"properties":{}}""" } +
            "]}"
}
