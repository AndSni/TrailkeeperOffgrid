package com.asnidev.trailkeeperoffgrid.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.asnidev.trailkeeperoffgrid.data.local.ProjectEntity
import com.asnidev.trailkeeperoffgrid.data.local.StructureEntity
import com.asnidev.trailkeeperoffgrid.data.local.StructureTypeEntity
import com.asnidev.trailkeeperoffgrid.data.local.TaskEntity
import com.asnidev.trailkeeperoffgrid.data.local.TrackEntity
import com.asnidev.trailkeeperoffgrid.data.local.TrailEntity
import com.asnidev.trailkeeperoffgrid.data.local.TrailkeeperDb
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import java.io.BufferedInputStream
import java.io.File
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Device-to-device sharing of one project, task, route, trail or structure —
 * a smaller, scoped sibling of [Backup]'s whole-workspace zip. Still just a
 * zip of JSON tables (+ referenced photos) written with [ZipOutputStream],
 * but built into this app's own cache dir and handed to the OS share sheet
 * (Bluetooth, Quick Share, messaging apps, ...) instead of a SAF-picked
 * destination — there's no backend and no custom transport here, the
 * phone's existing share targets are the transport.
 *
 * The parent project (when the shared thing has one) always rides along in
 * the bundle so the receiving device gets a complete, valid row, but it's
 * only *applied* automatically when that project already exists locally (a
 * re-share/update of something already linked). Otherwise [import] needs a
 * [ProjectDecision] first — attach to one of this device's existing
 * projects, or create the sender's project fresh — surfaced by the
 * ui.share.ImportScreen built on [peek].
 */
object ShareBundle {
    const val MIME = "application/vnd.trailkeeper.share+zip"
    const val EXTENSION = "tkshare"
    private const val APP_MARKER = "trailkeeper-offgrid"
    private const val BUNDLE_TYPE = "share"
    private const val SCHEMA = 1

    private val pretty: Gson = GsonBuilder().setPrettyPrinting().create()
    private val plain = Gson()

    enum class Scope { PROJECT, TASK, ROUTE, TRAIL, STRUCTURE }

    data class Result(val ok: Boolean, val detail: String, val file: File? = null)

    // ---- export ----------------------------------------------------------

    suspend fun exportProject(context: Context, projectId: String): Result = withContext(Dispatchers.IO) {
        val db = TrailkeeperDb.db
        val project = db.projectDao().getById(projectId)
            ?: return@withContext Result(false, "Project not found")
        val tasks = db.taskDao().listForProject(projectId)
        val trails = db.trailDao().listForProject(projectId)
        val structures = db.structureDao().listForProject(projectId)
        val tracks = db.trackDao().listForProject(projectId)
        val types = structureTypesFor(structures)

        val tables = LinkedHashMap<String, String>()
        tables["projects"] = pretty.toJson(listOf(project))
        if (tasks.isNotEmpty()) tables["tasks"] = pretty.toJson(tasks)
        if (trails.isNotEmpty()) tables["trails"] = pretty.toJson(trails)
        if (structures.isNotEmpty()) tables["structures"] = pretty.toJson(structures)
        if (tracks.isNotEmpty()) tables["tracks"] = pretty.toJson(tracks)
        if (types.isNotEmpty()) tables["structure_types"] = pretty.toJson(types)

        write(
            context, Scope.PROJECT, project.id, project.name, tables,
            photoUrls = tasks.flatMap { photoUrls(it.photosJson) }.toSet(),
        )
    }

    suspend fun exportTask(context: Context, taskId: String): Result = withContext(Dispatchers.IO) {
        val db = TrailkeeperDb.db
        val task = db.taskDao().getById(taskId) ?: return@withContext Result(false, "Task not found")
        val project = db.projectDao().getById(task.projectId)

        val tables = LinkedHashMap<String, String>()
        tables["tasks"] = pretty.toJson(listOf(task))
        if (project != null) tables["projects"] = pretty.toJson(listOf(project))

        write(context, Scope.TASK, task.id, task.title, tables, photoUrls = photoUrls(task.photosJson).toSet())
    }

    suspend fun exportRoute(context: Context, trackId: String): Result = withContext(Dispatchers.IO) {
        val db = TrailkeeperDb.db
        val track = db.trackDao().getById(trackId) ?: return@withContext Result(false, "Route not found")
        val project = db.projectDao().getById(track.projectId)

        val tables = LinkedHashMap<String, String>()
        tables["tracks"] = pretty.toJson(listOf(track))
        if (project != null) tables["projects"] = pretty.toJson(listOf(project))

        write(context, Scope.ROUTE, track.id, track.name, tables, photoUrls = emptySet())
    }

    suspend fun exportTrail(context: Context, trailId: String): Result = withContext(Dispatchers.IO) {
        val db = TrailkeeperDb.db
        val trail = db.trailDao().getById(trailId) ?: return@withContext Result(false, "Trail not found")
        val project = trail.projectId?.let { db.projectDao().getById(it) }

        val tables = LinkedHashMap<String, String>()
        tables["trails"] = pretty.toJson(listOf(trail))
        if (project != null) tables["projects"] = pretty.toJson(listOf(project))

        write(context, Scope.TRAIL, trail.id, trail.name, tables, photoUrls = emptySet())
    }

    suspend fun exportStructure(context: Context, structureId: String): Result = withContext(Dispatchers.IO) {
        val db = TrailkeeperDb.db
        val structure = db.structureDao().getById(structureId)
            ?: return@withContext Result(false, "Structure not found")
        val project = structure.projectId?.let { db.projectDao().getById(it) }
        val type = db.structureTypeDao().getByKey(structure.structureType)

        val tables = LinkedHashMap<String, String>()
        tables["structures"] = pretty.toJson(listOf(structure))
        if (project != null) tables["projects"] = pretty.toJson(listOf(project))
        if (type != null) tables["structure_types"] = pretty.toJson(listOf(type))

        write(context, Scope.STRUCTURE, structure.id, structure.name, tables, photoUrls = emptySet())
    }

    private suspend fun structureTypesFor(structures: List<StructureEntity>): List<StructureTypeEntity> {
        val dao = TrailkeeperDb.db.structureTypeDao()
        val found = ArrayList<StructureTypeEntity>()
        for (key in structures.map { it.structureType }.distinct()) dao.getByKey(key)?.let { found.add(it) }
        return found
    }

    /** Extracts the `url` of every photo entry in a `photosJson` column
     * (`[{id, caption, url}]`) that actually lives under our own photos/
     * dir — mirrors [LocalStore.isOwnedPhotoPath]'s trust boundary. */
    private fun photoUrls(photosJson: String): List<String> =
        runCatching {
            JsonParser.parseString(photosJson).asJsonArray
                .mapNotNull { it.asJsonObject.get("url")?.asString }
                .filter { LocalStore.isOwnedPhotoPath(it) }
        }.getOrDefault(emptyList())

    private fun safeFileName(scope: Scope, name: String): String {
        val base = name.trim().ifBlank { "share" }
            .replace(Regex("[^A-Za-z0-9 _-]"), "").trim().replace(' ', '_').ifBlank { "share" }
        return "${scope.name.lowercase()}-$base.$EXTENSION"
    }

    private fun write(
        context: Context,
        scope: Scope,
        primaryId: String,
        primaryName: String,
        tables: Map<String, String>,
        photoUrls: Set<String>,
    ): Result {
        val dir = File(context.cacheDir, "shares").apply { mkdirs() }
        val file = File(dir, safeFileName(scope, primaryName))
        return try {
            ZipOutputStream(file.outputStream().buffered()).use { zip ->
                fun put(name: String, body: String) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(body.toByteArray())
                    zip.closeEntry()
                }
                put(
                    "manifest.json",
                    pretty.toJson(
                        mapOf(
                            "app" to APP_MARKER,
                            "bundleType" to BUNDLE_TYPE,
                            "scope" to scope.name.lowercase(),
                            "schema" to SCHEMA,
                            "exportedAt" to Instant.now().toString(),
                            "exportedBy" to Identity.rawDisplayName(),
                            "primaryId" to primaryId,
                            "primaryName" to primaryName,
                        )
                    ),
                )
                for ((table, json) in tables) put("data/$table.json", json)

                val photoRoot = File(context.getExternalFilesDir(null), "photos")
                for (url in photoUrls) {
                    val f = File(url)
                    if (!f.isFile) continue
                    val rel = runCatching { f.relativeTo(photoRoot) }.getOrNull() ?: continue
                    zip.putNextEntry(ZipEntry("photos/${rel.path}"))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            Result(true, "Bundle written", file)
        } catch (e: Exception) {
            Result(false, e.message ?: "Share export failed")
        }
    }

    // ---- import ------------------------------------------------------------

    sealed class ProjectDecision {
        data class UseExisting(val projectId: String) : ProjectDecision()
        data object CreateNew : ProjectDecision()
    }

    data class Preview(
        val scope: Scope,
        val primaryName: String,
        val exportedBy: String,
        val project: ProjectEntity?,
        val projectExistsLocally: Boolean,
    ) {
        val needsProjectDecision: Boolean
            get() = scope != Scope.PROJECT && project != null && !projectExistsLocally
    }

    private data class Parsed(
        val manifest: Map<String, Any?>,
        val scope: Scope,
        val tables: Map<String, String>,
        val photos: List<Pair<String, ByteArray>>,
    )

    private suspend fun read(context: Context, src: Uri): Parsed? = withContext(Dispatchers.IO) {
        val tables = HashMap<String, String>()
        val photos = ArrayList<Pair<String, ByteArray>>()
        var manifestJson: String? = null
        context.contentResolver.openInputStream(src)?.use { raw ->
            ZipInputStream(BufferedInputStream(raw)).use { zip ->
                var e = zip.nextEntry
                while (e != null) {
                    val name = e.name
                    when {
                        name == "manifest.json" -> manifestJson = zip.readBytes().toString(Charsets.UTF_8)
                        name.startsWith("data/") && name.endsWith(".json") ->
                            tables[name.removePrefix("data/").removeSuffix(".json")] =
                                zip.readBytes().toString(Charsets.UTF_8)
                        name.startsWith("photos/") && !e.isDirectory ->
                            photos.add(name.removePrefix("photos/") to zip.readBytes())
                    }
                    zip.closeEntry()
                    e = zip.nextEntry
                }
            }
        } ?: return@withContext null

        val mj = manifestJson ?: return@withContext null
        @Suppress("UNCHECKED_CAST")
        val manifest = runCatching { plain.fromJson(mj, Map::class.java) as Map<String, Any?> }.getOrNull()
            ?: return@withContext null
        if (manifest["app"] != APP_MARKER || manifest["bundleType"] != BUNDLE_TYPE) return@withContext null
        val scope = (manifest["scope"] as? String)?.let {
            runCatching { Scope.valueOf(it.uppercase()) }.getOrNull()
        } ?: return@withContext null

        Parsed(manifest, scope, tables, photos)
    }

    /** Reads just enough to show the "Import share" screen — never touches
     * the database except to check whether an embedded parent project id
     * is already known locally. Returns null for anything that isn't one
     * of our own bundles (wrong app marker, corrupt zip, random file that
     * matched the fallback `.tkshare` intent-filter). */
    suspend fun peek(context: Context, src: Uri): Preview? = runCatching {
        val p = read(context, src) ?: return null
        val project = p.tables["projects"]?.let { list<ProjectEntity>(it).firstOrNull() }
        val existsLocally = project?.let { TrailkeeperDb.db.projectDao().getById(it.id) != null } ?: true
        Preview(
            scope = p.scope,
            primaryName = p.manifest["primaryName"] as? String ?: "",
            exportedBy = p.manifest["exportedBy"] as? String ?: "",
            project = project,
            projectExistsLocally = existsLocally,
        )
    }.getOrNull()

    /** Applies the bundle. [decision] is required exactly when a prior
     * [peek] reported [Preview.needsProjectDecision] — ignored otherwise. */
    suspend fun import(context: Context, src: Uri, decision: ProjectDecision?): Result =
        withContext(Dispatchers.IO) {
            val p = read(context, src) ?: return@withContext Result(false, "Not a Trailkeeper Offgrid share file")
            val db = TrailkeeperDb.db
            try {
                val embeddedProject = p.tables["projects"]?.let { list<ProjectEntity>(it).firstOrNull() }
                val localProject = embeddedProject?.let { db.projectDao().getById(it.id) }

                val targetProjectId: String?
                val importProjectRow: Boolean
                when {
                    p.scope == Scope.PROJECT -> {
                        targetProjectId = embeddedProject?.id
                        importProjectRow = true
                    }
                    embeddedProject == null -> {
                        targetProjectId = null
                        importProjectRow = false
                    }
                    localProject != null -> {
                        targetProjectId = embeddedProject.id
                        importProjectRow = true
                    }
                    decision is ProjectDecision.UseExisting -> {
                        targetProjectId = decision.projectId
                        importProjectRow = false
                    }
                    decision is ProjectDecision.CreateNew -> {
                        targetProjectId = embeddedProject.id
                        importProjectRow = true
                    }
                    else -> return@withContext Result(false, "Choose a project first")
                }

                db.withTransaction {
                    if (importProjectRow && embeddedProject != null) db.projectDao().upsert(embeddedProject)

                    p.tables["tasks"]?.let { json ->
                        val rows = list<TaskEntity>(json)
                        val fixed = targetProjectId?.let { pid -> rows.map { it.copy(projectId = pid) } } ?: rows
                        db.taskDao().upsertAll(fixed)
                    }
                    p.tables["tracks"]?.let { json ->
                        val rows = list<TrackEntity>(json)
                        val fixed = targetProjectId?.let { pid -> rows.map { it.copy(projectId = pid) } } ?: rows
                        db.trackDao().upsertAll(fixed)
                    }
                    p.tables["trails"]?.let { json ->
                        val rows = list<TrailEntity>(json)
                        val fixed = targetProjectId?.let { pid -> rows.map { it.copy(projectId = pid) } } ?: rows
                        db.trailDao().upsertAll(fixed)
                    }
                    p.tables["structures"]?.let { json ->
                        val rows = list<StructureEntity>(json)
                        val fixed = targetProjectId?.let { pid -> rows.map { it.copy(projectId = pid) } } ?: rows
                        db.structureDao().upsertAll(fixed)
                    }
                    p.tables["structure_types"]?.let { json ->
                        list<StructureTypeEntity>(json).forEach { db.structureTypeDao().upsert(it) }
                    }
                }

                val photoRoot = File(context.getExternalFilesDir(null), "photos").canonicalFile
                for ((rel, bytes) in p.photos) {
                    val out = File(photoRoot, rel).canonicalFile
                    if (!out.path.startsWith(photoRoot.path + File.separator)) {
                        continue // zip entry tries to write outside photos/ - reject it
                    }
                    out.parentFile?.mkdirs()
                    out.writeBytes(bytes)
                }

                Result(true, describeImport(p.scope, p.tables))
            } catch (e: Exception) {
                Result(false, e.message ?: "Import failed")
            }
        }

    private fun describeImport(scope: Scope, tables: Map<String, String>): String {
        fun count(key: String) = tables[key]?.let { list<Any>(it).size } ?: 0
        return when (scope) {
            Scope.PROJECT -> "Project imported: ${count("tasks")} task(s), ${count("trails")} trail(s), " +
                "${count("structures")} structure(s), ${count("tracks")} route(s)."
            Scope.TASK -> "Task imported."
            Scope.ROUTE -> "Route imported."
            Scope.TRAIL -> "Trail imported."
            Scope.STRUCTURE -> "Structure imported."
        }
    }

    // Array<T>::class.java for a reified T (the same trick Backup.kt uses)
    // was observed on-device to hand Gson a type it resolves to raw
    // LinkedTreeMap elements instead of T - TypeToken.getParameterized is
    // the documented, reliable way to deserialize a generic List<T>.
    private inline fun <reified T> list(json: String): List<T> {
        val type = com.google.gson.reflect.TypeToken.getParameterized(List::class.java, T::class.java).type
        return plain.fromJson(json, type)
    }
}
