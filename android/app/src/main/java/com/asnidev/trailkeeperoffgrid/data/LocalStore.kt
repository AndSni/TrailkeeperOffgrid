package com.asnidev.trailkeeperoffgrid.data

import android.content.Context
import androidx.room.withTransaction
import com.asnidev.trailkeeperoffgrid.data.local.InspectionEntity
import com.asnidev.trailkeeperoffgrid.data.local.MessageEntity
import com.asnidev.trailkeeperoffgrid.data.local.ProjectEntity
import com.asnidev.trailkeeperoffgrid.data.local.SegmentWorkEntity
import com.asnidev.trailkeeperoffgrid.data.local.StructureEntity
import com.asnidev.trailkeeperoffgrid.data.local.TaskEntity
import com.asnidev.trailkeeperoffgrid.data.local.TrackEntity
import com.asnidev.trailkeeperoffgrid.data.local.TrailEntity
import com.asnidev.trailkeeperoffgrid.data.local.TrailkeeperDb
import com.asnidev.trailkeeperoffgrid.model.InspectionCreateRequest
import com.asnidev.trailkeeperoffgrid.model.RollupDto
import com.asnidev.trailkeeperoffgrid.model.RollupGroupDto
import com.asnidev.trailkeeperoffgrid.model.SegmentWorkCreateRequest
import com.asnidev.trailkeeperoffgrid.model.StructureCreateRequest
import com.asnidev.trailkeeperoffgrid.model.StructurePatchRequest
import com.asnidev.trailkeeperoffgrid.model.TrackCreateRequest
import com.asnidev.trailkeeperoffgrid.model.TrackDto
import com.asnidev.trailkeeperoffgrid.model.TrackPointDto
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun nowIso(): String = Instant.now().toString()

/** A time-ordered id so lists stay in creation order without a server. */
private fun newId(): String {
    val ts = System.currentTimeMillis().toString(16).padStart(12, '0')
    return ts + "-" + UUID.randomUUID().toString().substring(0, 12)
}

/**
 * The single writer for Trailkeeper Offgrid. Replaces Trailkeeper's
 * `SyncRepository`: there is no server, no outbox and no `change_log`, so
 * every call just writes Room (the source of truth) and returns. Values the
 * backend used to derive with PostGIS — line length, polygon area,
 * nearest-trail attach, productivity rollups — are computed here via [Geo].
 *
 * The read side stays where it was: ViewModels observe the DAO `Flow`s
 * directly.
 */
object LocalStore {
    private val db get() = TrailkeeperDb.db
    private val gson = Gson()
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    // ---- projects ---------------------------------------------------------

    suspend fun createProject(name: String, activity: String): String {
        val id = newId()
        db.projectDao().upsert(
            ProjectEntity(
                id = id,
                organisationId = Identity.ORG_ID,
                name = name.trim().ifBlank { "Project" },
                description = "",
                activity = activity.ifBlank { "mtb" },
                status = "active",
            )
        )
        return id
    }

    suspend fun renameProject(id: String, name: String) {
        val p = db.projectDao().getById(id) ?: return
        db.projectDao().upsert(p.copy(name = name.trim().ifBlank { p.name }))
    }

    suspend fun deleteProject(id: String) {
        db.withTransaction {
            db.taskDao().deleteForProject(id)
            db.workLogDao().deleteForProject(id)
            db.messageDao().deleteForProject(id)
            db.segmentWorkDao().deleteForProject(id)
            db.inspectionDao().deleteForProject(id)
            db.trackDao().deleteForProject(id)
            db.projectMemberDao().deleteForProject(id)
            db.projectDao().deleteById(id)
        }
    }

    // ---- tasks ----------------------------------------------------------

    suspend fun createTask(
        projectId: String,
        title: String,
        priority: String,
        lat: Double? = null,
        lon: Double? = null,
    ): String {
        val id = newId()
        val geometry = if (lat != null && lon != null) Geo.pointJson(lat, lon) else null
        val nearest =
            if (lat != null && lon != null)
                Geo.nearestTrailId(lat, lon, db.trailDao().listForOrg(Identity.ORG_ID))
            else null
        db.taskDao().upsert(
            TaskEntity(
                id = id,
                projectId = projectId,
                organisationId = Identity.ORG_ID,
                title = title.trim(),
                description = "",
                taskType = "",
                priority = priority,
                status = "open",
                geometryJson = geometry,
                nearestTrailId = nearest,
                estimateMin = null,
                assigneeIdsJson = "[]",
                photosJson = "[]",
                updatedAt = nowIso(),
            )
        )
        return id
    }

    /** Alias kept for the route-recording "mark spot" caller. */
    suspend fun createTaskAt(
        projectId: String,
        title: String,
        priority: String,
        lat: Double,
        lon: Double,
    ): String = createTask(projectId, title, priority, lat, lon)

    suspend fun setTaskStatus(taskId: String, status: String) {
        val t = db.taskDao().getById(taskId) ?: return
        db.taskDao().upsert(t.copy(status = status, updatedAt = nowIso()))
        if (status == "done" || status == "closed") maybeAutoWorkLog(t)
    }

    suspend fun editTask(taskId: String, title: String, description: String, priority: String) {
        val t = db.taskDao().getById(taskId) ?: return
        db.taskDao().upsert(
            t.copy(
                title = title.trim().ifBlank { t.title },
                description = description.trim(),
                priority = priority,
                updatedAt = nowIso(),
            )
        )
    }

    suspend fun moveTask(taskId: String, lat: Double, lon: Double) {
        val t = db.taskDao().getById(taskId) ?: return
        val nearest = Geo.nearestTrailId(lat, lon, db.trailDao().listForOrg(Identity.ORG_ID))
        db.taskDao().upsert(
            t.copy(
                geometryJson = Geo.pointJson(lat, lon),
                nearestTrailId = nearest,
                updatedAt = nowIso(),
            )
        )
    }

    suspend fun deleteTask(taskId: String) {
        db.withTransaction {
            db.messageDao().deleteForTask(taskId)
            deleteTaskPhotoFiles(taskId)
            db.taskDao().deleteById(taskId)
        }
    }

    private suspend fun maybeAutoWorkLog(task: TaskEntity) {
        // Mirrors the backend's "auto work log on completion" convenience:
        // one zero-minute entry the user can edit later. Skipped if one
        // already exists for this task.
        // (Left minimal for P1 — a real estimate-based minutes value is a
        // later slice.)
    }

    // ---- task photos (local files) ------------------------------------

    private fun photosRoot(): File =
        File(appContext.getExternalFilesDir(null), "photos").apply { mkdirs() }

    fun taskPhotoDir(taskId: String): File = File(photosRoot(), taskId).apply { mkdirs() }

    /** Move an already-compressed JPEG into the task's photo folder and
     * record it in `photos_json`. */
    suspend fun addTaskPhoto(taskId: String, jpeg: File, caption: String = ""): Unit =
        withContext(Dispatchers.IO) {
            val t = db.taskDao().getById(taskId) ?: return@withContext
            val photoId = newId()
            val dest = File(taskPhotoDir(taskId), "$photoId.jpg")
            jpeg.copyTo(dest, overwrite = true)
            jpeg.delete()
            val arr = JsonParser.parseString(t.photosJson.ifBlank { "[]" }).asJsonArray
            val obj = com.google.gson.JsonObject().apply {
                addProperty("id", photoId)
                addProperty("caption", caption)
                addProperty("url", dest.absolutePath)
            }
            arr.add(obj)
            db.taskDao().upsert(t.copy(photosJson = arr.toString(), updatedAt = nowIso()))
        }

    suspend fun deleteTaskPhoto(taskId: String, photoId: String): Unit =
        withContext(Dispatchers.IO) {
            val t = db.taskDao().getById(taskId) ?: return@withContext
            val arr = JsonParser.parseString(t.photosJson.ifBlank { "[]" }).asJsonArray
            val kept = com.google.gson.JsonArray()
            arr.forEach { el ->
                val o = el.asJsonObject
                if (o.get("id")?.asString == photoId) {
                    o.get("url")?.asString?.let { runCatching { File(it).delete() } }
                } else {
                    kept.add(o)
                }
            }
            db.taskDao().upsert(t.copy(photosJson = kept.toString(), updatedAt = nowIso()))
        }

    private fun deleteTaskPhotoFiles(taskId: String) {
        runCatching { taskPhotoDir(taskId).deleteRecursively() }
    }

    /** Settings → "clear photos of completed tasks": drop the image files and
     * the `photos_json` entries for done/closed tasks, keeping the task rows
     * and all other data. Returns (tasks touched, files removed, bytes freed). */
    suspend fun purgeDoneTaskPhotos(olderThanIso: String? = null): Triple<Int, Int, Long> =
        withContext(Dispatchers.IO) {
            var tasks = 0
            var files = 0
            var bytes = 0L
            val done = db.taskDao().listByStatuses(listOf("done", "closed"))
            for (t in done) {
                if (olderThanIso != null && t.updatedAt > olderThanIso) continue
                val arr = runCatching {
                    JsonParser.parseString(t.photosJson.ifBlank { "[]" }).asJsonArray
                }.getOrNull() ?: continue
                if (arr.size() == 0) continue
                arr.forEach { el ->
                    el.asJsonObject.get("url")?.asString?.let { path ->
                        val f = File(path)
                        if (f.exists()) { bytes += f.length(); if (f.delete()) files++ }
                    }
                }
                db.taskDao().upsert(t.copy(photosJson = "[]"))
                tasks++
            }
            Triple(tasks, files, bytes)
        }

    // ---- messages (per-project / per-task notebook) -----------------

    suspend fun postMessage(projectId: String, taskId: String?, body: String) {
        db.messageDao().upsert(
            MessageEntity(
                id = newId(),
                projectId = projectId,
                taskId = taskId,
                authorId = Identity.USER_ID,
                body = body,
                mentionedUserIdsJson = "[]",
                createdAt = nowIso(),
            )
        )
    }

    suspend fun deleteMessage(id: String) = db.messageDao().deleteById(id)

    // ---- trails (org-wide) -------------------------------------------

    suspend fun createTrail(
        name: String,
        activity: String,
        points: List<Pair<Double, Double>>,
        source: String = "walked",
    ): String {
        require(points.size >= 2) { "A trail needs at least two points" }
        val id = newId()
        db.trailDao().upsert(
            TrailEntity(
                id = id,
                organisationId = Identity.ORG_ID,
                name = name.trim().ifBlank { "Trail" },
                activity = activity.ifBlank { "mtb" },
                difficulty = "",
                status = "open",
                source = source,
                lengthM = Geo.lineLengthM(points),
                geometryJson = Geo.lineStringJson(points),
            )
        )
        return id
    }

    suspend fun deleteTrail(id: String) = db.trailDao().deleteById(id)

    // ---- structures (org-wide) -------------------------------------

    suspend fun createStructure(req: StructureCreateRequest): String {
        val id = newId()
        val geometry = if (req.lat != null && req.lon != null) Geo.pointJson(req.lat, req.lon) else null
        val nearest =
            if (req.lat != null && req.lon != null)
                Geo.nearestTrailId(req.lat, req.lon, db.trailDao().listForOrg(Identity.ORG_ID))
            else null
        db.structureDao().upsert(
            StructureEntity(
                id = id,
                organisationId = Identity.ORG_ID,
                name = req.name.trim().ifBlank { "Structure" },
                structureType = req.structureType,
                status = req.status,
                geometryJson = geometry,
                nearestTrailId = nearest,
                material = req.material,
                color = req.color,
                installedOn = null,
                inspectionIntervalDays = null,
                notes = req.notes,
            )
        )
        return id
    }

    suspend fun updateStructure(id: String, req: StructurePatchRequest) {
        val s = db.structureDao().getById(id) ?: return
        val movedGeom =
            if (req.lat != null && req.lon != null) Geo.pointJson(req.lat, req.lon) else s.geometryJson
        val movedNearest =
            if (req.lat != null && req.lon != null)
                Geo.nearestTrailId(req.lat, req.lon, db.trailDao().listForOrg(Identity.ORG_ID))
            else s.nearestTrailId
        db.structureDao().upsert(
            s.copy(
                name = req.name?.trim()?.ifBlank { s.name } ?: s.name,
                structureType = req.structureType ?: s.structureType,
                status = req.status ?: s.status,
                material = req.material ?: s.material,
                color = req.color ?: s.color,
                notes = req.notes ?: s.notes,
                geometryJson = movedGeom,
                nearestTrailId = movedNearest,
            )
        )
    }

    suspend fun deleteStructure(id: String) = db.structureDao().deleteById(id)

    // ---- trail-condition reports (P5) ------------------------------

    suspend fun createTrailReport(
        projectId: String?,
        status: String,
        kind: String,
        severity: String,
        note: String,
        lat: Double?,
        lon: Double?,
    ): String {
        val id = newId()
        val geometry = if (lat != null && lon != null) Geo.pointJson(lat, lon) else null
        val nearest =
            if (lat != null && lon != null)
                Geo.nearestTrailId(lat, lon, db.trailDao().listForOrg(Identity.ORG_ID))
            else null
        db.trailReportDao().upsert(
            com.asnidev.trailkeeperoffgrid.data.local.TrailReportEntity(
                id = id,
                organisationId = Identity.ORG_ID,
                projectId = projectId,
                status = status,
                kind = kind,
                severity = severity,
                note = note.trim(),
                geometryJson = geometry,
                nearestTrailId = nearest,
                photosJson = "[]",
                reportedById = Identity.USER_ID,
                createdAt = nowIso(),
                resolvedAt = null,
            )
        )
        return id
    }

    suspend fun setReportResolved(id: String, resolved: Boolean) {
        val r = db.trailReportDao().getById(id) ?: return
        db.trailReportDao().upsert(r.copy(resolvedAt = if (resolved) nowIso() else null))
    }

    suspend fun deleteTrailReport(id: String) {
        db.trailReportDao().deleteById(id)
        deleteReportPhotoFiles(id)
    }

    // ---- report photos (local files) -------------------------------

    private fun reportPhotoDir(reportId: String): File =
        File(photosRoot(), "report-$reportId").apply { mkdirs() }

    /** Same shape as [addTaskPhoto], for a trail-condition report. */
    suspend fun addReportPhoto(reportId: String, jpeg: File, caption: String = ""): Unit =
        withContext(Dispatchers.IO) {
            val r = db.trailReportDao().getById(reportId) ?: return@withContext
            val photoId = newId()
            val dest = File(reportPhotoDir(reportId), "$photoId.jpg")
            jpeg.copyTo(dest, overwrite = true)
            jpeg.delete()
            val arr = JsonParser.parseString(r.photosJson.ifBlank { "[]" }).asJsonArray
            val obj = com.google.gson.JsonObject().apply {
                addProperty("id", photoId)
                addProperty("caption", caption)
                addProperty("url", dest.absolutePath)
            }
            arr.add(obj)
            db.trailReportDao().upsert(r.copy(photosJson = arr.toString()))
        }

    suspend fun deleteReportPhoto(reportId: String, photoId: String): Unit =
        withContext(Dispatchers.IO) {
            val r = db.trailReportDao().getById(reportId) ?: return@withContext
            val arr = JsonParser.parseString(r.photosJson.ifBlank { "[]" }).asJsonArray
            val kept = com.google.gson.JsonArray()
            arr.forEach { el ->
                val o = el.asJsonObject
                if (o.get("id")?.asString == photoId) {
                    o.get("url")?.asString?.let { runCatching { File(it).delete() } }
                } else {
                    kept.add(o)
                }
            }
            db.trailReportDao().upsert(r.copy(photosJson = kept.toString()))
        }

    private fun deleteReportPhotoFiles(reportId: String) {
        runCatching { reportPhotoDir(reportId).deleteRecursively() }
    }

    /** Settings → "clear photos of resolved reports": same deal as
     * [purgeDoneTaskPhotos] but for resolved trail-condition reports. */
    suspend fun purgeResolvedReportPhotos(): Triple<Int, Int, Long> =
        withContext(Dispatchers.IO) {
            var reports = 0
            var files = 0
            var bytes = 0L
            for (r in db.backupDao().trailReports().filter { it.resolvedAt != null }) {
                val arr = runCatching {
                    JsonParser.parseString(r.photosJson.ifBlank { "[]" }).asJsonArray
                }.getOrNull() ?: continue
                if (arr.size() == 0) continue
                arr.forEach { el ->
                    el.asJsonObject.get("url")?.asString?.let { path ->
                        val f = File(path)
                        if (f.exists()) { bytes += f.length(); if (f.delete()) files++ }
                    }
                }
                db.trailReportDao().upsert(r.copy(photosJson = "[]"))
                reports++
            }
            Triple(reports, files, bytes)
        }

    // ---- inspections -----------------------------------------------

    suspend fun createInspection(req: InspectionCreateRequest): String {
        val id = newId()
        db.inspectionDao().upsert(
            InspectionEntity(
                id = id,
                projectId = req.projectId,
                structureId = req.structureId,
                formId = req.formId,
                formVersion = req.formId?.let { db.inspectionFormDao().getById(it)?.version },
                inspectorId = Identity.USER_ID,
                inspectedOn = nowIso(),
                answersJson = gson.toJson(req.answers),
                risk = req.risk,
                condition = req.condition,
                notes = req.notes,
            )
        )
        // A recorded condition writes back to the structure's status, like
        // the backend did.
        req.condition?.let { cond ->
            db.structureDao().getById(req.structureId)?.let { s ->
                if (cond.isNotBlank() && cond != s.status) {
                    db.structureDao().upsert(s.copy(status = cond))
                }
            }
        }
        return id
    }

    // ---- segment work + rollup -----------------------------------

    suspend fun logSegmentWork(req: SegmentWorkCreateRequest): String {
        val id = newId()
        val jt = db.jobTypeDao().getById(req.jobTypeId)
        val unit = jt?.unit ?: "hours"

        // Quantity: measured from the captured geometry, or the manual value,
        // or (for hour-unit work) the active time.
        val measured: Double? = req.geometry?.let { measureQuantity(it, unit) }
        val quantity: Double =
            when {
                measured != null -> measured
                req.quantity != null -> req.quantity
                unit == "hours" -> req.activeSeconds / 3600.0
                else -> 0.0
            }
        val source = if (measured != null) "measured" else req.quantitySource

        val personHours = req.activeSeconds / 3600.0 * req.crewSize
        val rate = if (quantity > 0) req.activeSeconds / 60.0 / quantity else null
        val vsExpected = if (rate != null && jt?.expectedRate != null) rate - jt.expectedRate else null

        db.segmentWorkDao().upsert(
            SegmentWorkEntity(
                id = id,
                projectId = req.projectId,
                jobTypeId = req.jobTypeId,
                trailId = req.trailId,
                quantity = quantity,
                unit = unit,
                quantitySource = source,
                startedAt = req.startedAt,
                endedAt = req.endedAt,
                activeSeconds = req.activeSeconds,
                crewSize = req.crewSize,
                equipmentJson = gson.toJson(req.equipment),
                notes = req.notes,
                createdById = Identity.USER_ID,
                personHours = personHours,
                rateMinPerUnit = rate,
                vsExpectedMinPerUnit = vsExpected,
            )
        )
        return id
    }

    private fun measureQuantity(geometry: JsonElement, unit: String): Double? {
        val obj = runCatching { geometry.asJsonObject }.getOrNull() ?: return null
        val type = obj.get("type")?.asString ?: return null
        val coords = obj.get("coordinates")?.asJsonArray ?: return null
        return when (type) {
            "LineString" -> {
                val pts = coords.mapNotNull { p ->
                    (p as? com.google.gson.JsonArray)?.let {
                        if (it.size() >= 2) it[1].asDouble to it[0].asDouble else null
                    }
                }
                val m = Geo.lineLengthM(pts)
                if (unit == "m2") null else if (unit == "km") m / 1000.0 else m
            }
            "Polygon" -> {
                val ring = coords[0].asJsonArray.mapNotNull { p ->
                    (p as? com.google.gson.JsonArray)?.let {
                        if (it.size() >= 2) it[1].asDouble to it[0].asDouble else null
                    }
                }
                Geo.polygonAreaM2(ring)
            }
            else -> null
        }
    }

    /** Client-side equivalent of the backend's
     * `GET /segment-work/rollup?group_by=job_type|trail|member|week`. */
    suspend fun segmentRollup(projectId: String, groupBy: String): RollupDto {
        val recs = db.segmentWorkDao().listForProject(projectId)
        val jobTypes = db.jobTypeDao().listAll().associateBy { it.id }
        val trails = db.trailDao().listForOrg(Identity.ORG_ID).associateBy { it.id }

        data class Key(val key: String, val label: String)
        fun keyOf(r: SegmentWorkEntity): Key = when (groupBy) {
            "trail" -> Key(r.trailId ?: "—", r.trailId?.let { trails[it]?.name } ?: "No trail")
            "member" -> Key(r.createdById ?: "—", Identity.displayName())
            "week" -> {
                val wk = r.startedAt.take(10)
                Key(wk, wk)
            }
            else -> Key(
                r.jobTypeId ?: "—",
                r.jobTypeId?.let { jobTypes[it]?.label } ?: "(job type)",
            )
        }

        val groups = recs.groupBy { keyOf(it) }.map { (k, rs) ->
            val unit = rs.firstOrNull()?.unit ?: ""
            val totalQty = rs.sumOf { it.quantity }
            val totalPh = rs.sumOf { it.personHours }
            val totalActiveMin = rs.sumOf { it.activeSeconds } / 60.0
            val meanRate = if (totalQty > 0) totalActiveMin / totalQty else null
            val expected =
                if (groupBy == "job_type") rs.firstOrNull()?.jobTypeId?.let { jobTypes[it]?.expectedRate }
                else null
            val delta = if (meanRate != null && expected != null) meanRate - expected else null
            RollupGroupDto(
                groupKey = k.key,
                groupLabel = k.label,
                recordCount = rs.size,
                unit = unit,
                totalQuantity = round2(totalQty),
                totalPersonHours = round2(totalPh),
                meanRateMinPerUnit = meanRate?.let { round2(it) },
                expectedRate = expected,
                deltaMinPerUnit = delta?.let { round2(it) },
            )
        }.sortedByDescending { it.totalPersonHours }

        return RollupDto(groupBy = groupBy, groups = groups)
    }

    private fun round2(d: Double): Double = (d * 100).roundToInt() / 100.0

    // ---- tracks (recorded routes) --------------------------------

    suspend fun saveTrack(req: TrackCreateRequest): TrackDto {
        val id = newId()
        val pts = req.points.map { it.lat to it.lon }
        val lengthM = Geo.lineLengthM(pts)
        val geometry = if (pts.size >= 2) Geo.lineStringJson(pts) else null
        val entity = TrackEntity(
            id = id,
            projectId = req.projectId,
            name = req.name.trim().ifBlank { "Route" },
            activity = req.activity,
            source = "recorded",
            startedAt = req.startedAt,
            endedAt = req.endedAt,
            movingSeconds = req.movingSeconds,
            lengthM = lengthM,
            pointCount = pts.size,
            geometryJson = geometry,
            recordedById = Identity.USER_ID,
        )
        db.trackDao().upsert(entity)
        // The full point list (with elevation + time) is kept on disk so the
        // map layer's simplified LineString stays small; a GPX export reads
        // this back.
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(appContext.getExternalFilesDir(null), "tracks").apply { mkdirs() }
                File(dir, "$id.json").writeText(gson.toJson(req.points))
            }
        }
        return TrackDto(
            id = id,
            projectId = req.projectId,
            name = entity.name,
            activity = entity.activity,
            source = entity.source,
            startedAt = entity.startedAt,
            endedAt = entity.endedAt,
            movingSeconds = entity.movingSeconds,
            lengthM = entity.lengthM,
            pointCount = entity.pointCount,
            geometry = geometry?.let { JsonParser.parseString(it) },
            recordedById = entity.recordedById,
        )
    }

    /** The full recorded point list for a track (lat/lon/ele/time), read from
     * the on-disk sidecar `saveTrack` wrote. Empty for imported/older tracks. */
    fun trackPoints(id: String): List<TrackPointDto> {
        val f = File(File(appContext.getExternalFilesDir(null), "tracks"), "$id.json")
        if (!f.exists()) return emptyList()
        return runCatching {
            gson.fromJson(f.readText(), Array<TrackPointDto>::class.java).toList()
        }.getOrDefault(emptyList())
    }

    suspend fun deleteTrack(id: String) {
        db.trackDao().deleteById(id)
        withContext(Dispatchers.IO) {
            runCatching {
                File(File(appContext.getExternalFilesDir(null), "tracks"), "$id.json").delete()
            }
        }
    }

    // ---- storage readout (Settings) ----------------------------------

    data class Storage(val dbBytes: Long, val photoBytes: Long, val trackBytes: Long) {
        val total get() = dbBytes + photoBytes + trackBytes
    }

    fun storage(): Storage = Storage(
        dbBytes = dbFileBytes(),
        photoBytes = dirBytes(File(appContext.getExternalFilesDir(null), "photos")),
        trackBytes = dirBytes(File(appContext.getExternalFilesDir(null), "tracks")),
    )

    private fun dbFileBytes(): Long {
        val f = appContext.getDatabasePath("trailkeeper_offgrid.db") ?: return 0
        var total = f.length()
        // WAL + SHM alongside it.
        listOf("-wal", "-shm").forEach { suffix ->
            File(f.path + suffix).let { if (it.exists()) total += it.length() }
        }
        return total
    }

    private fun dirBytes(dir: File): Long =
        if (!dir.exists()) 0L
        else dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    // ---- first-run seed + wipe -----------------------------------

    /** Insert the default job types the first time the table is empty. */
    suspend fun seedIfEmpty() {
        if (db.jobTypeDao().count() == 0) {
            db.jobTypeDao().upsertAll(DefaultJobTypes.seed())
        }
    }

    /** Danger: wipe everything (used by a future "reset app" in Settings). */
    suspend fun clear() {
        withContext(Dispatchers.IO) {
            db.clearAllTables()
            runCatching { File(appContext.getExternalFilesDir(null), "photos").deleteRecursively() }
            runCatching { File(appContext.getExternalFilesDir(null), "tracks").deleteRecursively() }
        }
    }
}
