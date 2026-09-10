package com.asnidev.trailkeeperoffgrid.data

/** Vocabulary for [LocalStore.createTrailReport]. */
object TrailReports {
    val STATUSES = listOf("passable", "caution", "impassable")
    val KINDS = listOf(
        "blowdown", "washout", "bridge", "overgrown", "erosion", "drainage", "sign", "other",
    )
    val SEVERITIES = listOf("low", "medium", "high")

    fun label(s: String): String = s.replace('_', ' ').replaceFirstChar { it.uppercase() }
}
