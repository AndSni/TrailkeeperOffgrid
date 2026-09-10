package com.asnidev.trailkeeperoffgrid.model

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

/**
 * Plain value objects for Trailkeeper Offgrid.
 *
 * In the original Trailkeeper these were the JSON DTOs of the FastAPI sync
 * API. Offgrid has no backend: the Room database is the source of truth.
 * These types survive as the shape the repositories, ViewModels and the
 * (future) GeoJSON / GPX export path pass around, and as the input to the
 * `data/local/Mappers.kt` `toEntity()` helpers. The `@SerializedName`
 * snake_case names are kept so the same shapes serialise cleanly to export
 * files.
 */

data class ProjectDto(
    val id: String,
    @SerializedName("organisation_id") val organisationId: String,
    val name: String,
    val description: String,
    val activity: String,
    val status: String,
)

data class TrailDto(
    val id: String,
    @SerializedName("organisation_id") val organisationId: String,
    val name: String,
    val activity: String,
    val difficulty: String,
    val status: String,
    val source: String,
    @SerializedName("length_m") val lengthM: Double,
    val geometry: JsonElement?, // GeoJSON LineString
)

data class TaskPhotoDto(
    val id: String,
    val caption: String,
    val url: String,
)

data class TaskDto(
    val id: String,
    @SerializedName("organisation_id") val organisationId: String,
    @SerializedName("project_id") val projectId: String,
    val title: String,
    val description: String,
    @SerializedName("task_type") val taskType: String,
    val priority: String,
    val status: String,
    val geometry: JsonElement?, // GeoJSON Point, or null
    @SerializedName("nearest_trail_id") val nearestTrailId: String?,
    @SerializedName("estimate_min") val estimateMin: Int?,
    @SerializedName("assignee_ids") val assigneeIds: List<String> = emptyList(),
    val photos: List<TaskPhotoDto> = emptyList(),
    @SerializedName("updated_at") val updatedAt: String,
)

data class WorkLogDto(
    val id: String,
    @SerializedName("project_id") val projectId: String,
    @SerializedName("task_id") val taskId: String?,
    @SerializedName("trail_id") val trailId: String?,
    @SerializedName("user_id") val userId: String,
    val minutes: Int,
    @SerializedName("worked_on") val workedOn: String,
    val note: String,
    @SerializedName("auto_from_task") val autoFromTask: Boolean,
    @SerializedName("updated_at") val updatedAt: String,
)

data class ProjectMemberDto(
    @SerializedName("user_id") val userId: String,
    val email: String,
    val name: String,
    @SerializedName("project_role") val projectRole: String,
)

data class MessageDto(
    val id: String,
    @SerializedName("project_id") val projectId: String,
    @SerializedName("task_id") val taskId: String?,
    @SerializedName("author_id") val authorId: String?,
    val body: String,
    @SerializedName("mentioned_user_ids") val mentionedUserIds: List<String> = emptyList(),
    @SerializedName("created_at") val createdAt: String,
)

data class JobTypeDto(
    val id: String,
    val activity: String,
    val key: String,
    val label: String,
    val unit: String, // hours | km | m2 | count
    @SerializedName("default_crew") val defaultCrew: Int,
    @SerializedName("expected_rate") val expectedRate: Double?,
    val color: String,
    @SerializedName("sort_group") val sortGroup: String,
)

data class SegmentWorkDto(
    val id: String,
    @SerializedName("project_id") val projectId: String,
    @SerializedName("job_type_id") val jobTypeId: String?,
    @SerializedName("trail_id") val trailId: String?,
    val quantity: Double,
    val unit: String,
    @SerializedName("quantity_source") val quantitySource: String,
    @SerializedName("started_at") val startedAt: String,
    @SerializedName("ended_at") val endedAt: String?,
    @SerializedName("active_seconds") val activeSeconds: Int,
    @SerializedName("crew_size") val crewSize: Int,
    val equipment: List<String> = emptyList(),
    val notes: String,
    @SerializedName("created_by_id") val createdById: String?,
    @SerializedName("person_hours") val personHours: Double,
    @SerializedName("rate_min_per_unit") val rateMinPerUnit: Double?,
    @SerializedName("vs_expected_min_per_unit") val vsExpectedMinPerUnit: Double?,
)

data class StructureDto(
    val id: String,
    @SerializedName("organisation_id") val organisationId: String,
    val name: String,
    @SerializedName("structure_type") val structureType: String,
    val status: String,
    val geometry: JsonElement?, // GeoJSON Point, or null
    @SerializedName("nearest_trail_id") val nearestTrailId: String?,
    val material: String,
    val color: String = "",
    @SerializedName("installed_on") val installedOn: String?,
    @SerializedName("inspection_interval_days") val inspectionIntervalDays: Int?,
    val notes: String,
)

data class InspectionFieldDto(
    val key: String,
    val label: String?,
    val type: String, // bool | text | number | choice | section
    val required: Boolean = false,
    val choices: List<String> = emptyList(),
)

data class InspectionFormDto(
    val id: String,
    val name: String,
    @SerializedName("target_type") val targetType: String,
    val fields: List<InspectionFieldDto> = emptyList(),
    val version: Int,
    @SerializedName("is_active") val isActive: Boolean,
)

data class InspectionDto(
    val id: String,
    @SerializedName("project_id") val projectId: String,
    @SerializedName("structure_id") val structureId: String,
    @SerializedName("form_id") val formId: String?,
    @SerializedName("form_version") val formVersion: Int?,
    @SerializedName("inspector_id") val inspectorId: String?,
    @SerializedName("inspected_on") val inspectedOn: String,
    val answers: JsonElement?,
    val risk: String?,
    val condition: String?,
    val notes: String,
)

data class TrackDto(
    val id: String,
    @SerializedName("project_id") val projectId: String,
    val name: String,
    val activity: String,
    val source: String,
    @SerializedName("started_at") val startedAt: String?,
    @SerializedName("ended_at") val endedAt: String?,
    @SerializedName("moving_seconds") val movingSeconds: Int,
    @SerializedName("length_m") val lengthM: Double,
    @SerializedName("point_count") val pointCount: Int,
    val geometry: JsonElement?, // GeoJSON LineString
    @SerializedName("recorded_by_id") val recordedById: String?,
)

data class TrackPointDto(
    val lat: Double,
    val lon: Double,
    val ele: Double? = null,
    val t: String? = null,
)

// --- input value objects the repositories accept -------------------------

data class TaskCreateRequest(
    val title: String,
    val priority: String = "medium",
    val lat: Double? = null,
    val lon: Double? = null,
)

data class TrailCreateRequest(
    val name: String,
    val activity: String = "mtb",
    val difficulty: String = "",
    val status: String = "open",
    // [lat, lon] pairs in order along the line.
    val points: List<List<Double>>,
)

data class TrackCreateRequest(
    val projectId: String,
    val name: String,
    val activity: String = "mtb",
    val startedAt: String?,
    val endedAt: String?,
    val movingSeconds: Int,
    val points: List<TrackPointDto>,
)

data class StructureCreateRequest(
    val name: String,
    val structureType: String,
    val status: String = "good",
    val lat: Double? = null,
    val lon: Double? = null,
    val material: String = "",
    val color: String = "",
    val notes: String = "",
)

data class StructurePatchRequest(
    val name: String? = null,
    val structureType: String? = null,
    val status: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val material: String? = null,
    val color: String? = null,
    val notes: String? = null,
)

data class InspectionCreateRequest(
    val projectId: String,
    val structureId: String,
    val formId: String? = null,
    val answers: Map<String, Any?> = emptyMap(),
    val risk: String? = null,
    val condition: String? = null,
    val notes: String = "",
)

data class SegmentWorkCreateRequest(
    val projectId: String,
    val jobTypeId: String,
    val trailId: String? = null,
    val geometry: JsonElement? = null, // GeoJSON LineString / Polygon when measured
    val quantity: Double?,
    val quantitySource: String = "manual",
    val startedAt: String,
    val endedAt: String?,
    val activeSeconds: Int,
    val pauses: List<Map<String, String>> = emptyList(),
    val crewSize: Int = 1,
    val equipment: List<String> = emptyList(),
    val notes: String = "",
)

// --- productivity rollup (computed on-device from Room) -----------------

data class RollupGroupDto(
    @SerializedName("group_key") val groupKey: String,
    @SerializedName("group_label") val groupLabel: String,
    @SerializedName("record_count") val recordCount: Int,
    val unit: String,
    @SerializedName("total_quantity") val totalQuantity: Double,
    @SerializedName("total_person_hours") val totalPersonHours: Double,
    @SerializedName("mean_rate_min_per_unit") val meanRateMinPerUnit: Double?,
    @SerializedName("expected_rate") val expectedRate: Double?,
    @SerializedName("delta_min_per_unit") val deltaMinPerUnit: Double?,
)

data class RollupDto(
    @SerializedName("group_by") val groupBy: String,
    val groups: List<RollupGroupDto> = emptyList(),
)

data class NotificationDto(
    val id: String,
    val type: String,
    @SerializedName("subject_type") val subjectType: String,
    @SerializedName("subject_id") val subjectId: String,
    @SerializedName("project_id") val projectId: String?,
    @SerializedName("actor_id") val actorId: String?,
    val body: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("read_at") val readAt: String?,
)
