package com.asnidev.trailkeeperoffgrid.data

import com.asnidev.trailkeeperoffgrid.data.local.JobTypeEntity

/**
 * In Trailkeeper the backend seeded a job-type taxonomy on account
 * registration. Offgrid has no registration, so [LocalStore] inserts this
 * list the first time it finds the `job_types` table empty. Units are
 * `hours | km | m2 | count`; `expectedRate` is minutes per unit (null = no
 * target). The user can't edit these yet — a job-type admin screen is a
 * later slice.
 */
object DefaultJobTypes {
    fun seed(): List<JobTypeEntity> = listOf(
        row("brushcutting", "Brushcutting", "km", 2, 22.0, "#4C6B3C", "clearing"),
        row("blowdown", "Blowdown / deadfall", "count", 2, 10.0, "#6B4C3C", "clearing"),
        row("tread_repair", "Tread repair", "m2", 2, null, "#B7791F", "surface"),
        row("drainage", "Drainage (dips / bars)", "count", 1, 6.0, "#2F6D7A", "water"),
        row("erosion_control", "Erosion control", "m2", 3, null, "#8A6A4A", "water"),
        row("structure_repair", "Structure repair", "hours", 2, null, "#B23B3B", "structures"),
        row("signage", "Signage / blazing", "count", 1, 15.0, "#5C6450", "markings"),
        row("general", "General trail work", "hours", 1, null, "#607D8B", "other"),
    )

    private fun row(
        key: String,
        label: String,
        unit: String,
        crew: Int,
        rate: Double?,
        color: String,
        group: String,
    ) = JobTypeEntity(
        id = key,
        activity = "",
        key = key,
        label = label,
        unit = unit,
        defaultCrew = crew,
        expectedRate = rate,
        color = color,
        sortGroup = group,
    )
}
