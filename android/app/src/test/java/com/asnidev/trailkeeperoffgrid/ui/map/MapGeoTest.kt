package com.asnidev.trailkeeperoffgrid.ui.map

import com.asnidev.trailkeeperoffgrid.data.local.TaskEntity
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Regression test for the crash reported by a user: a project with exactly
 * one geo-tagged task threw InvalidLatLngBoundsException from
 * LatLngBounds.Builder().build(), which only accepts 2+ points. Every
 * ProjectDetailScreen recomposition re-ran projectScope(), so the project
 * became permanently unopenable once it had a single task.
 */
class MapGeoTest {

    private fun pointTask(lat: Double, lng: Double) = TaskEntity(
        id = "t1",
        projectId = "p1",
        organisationId = "o1",
        title = "Task",
        description = "",
        taskType = "maintenance",
        priority = "normal",
        status = "open",
        geometryJson = """{"type":"Point","coordinates":[$lng,$lat]}""",
        nearestTrailId = null,
        estimateMin = null,
        assigneeIdsJson = "[]",
        photosJson = "[]",
        updatedAt = "",
    )

    @Test
    fun `projectScope with a single geo-tagged task does not throw`() {
        val scope = MapGeo.projectScope(
            trails = emptyList(),
            tasks = listOf(pointTask(56.95, 24.10)),
            structures = emptyList(),
            tracks = emptyList(),
        )
        assertNotNull(scope.bounds)
    }

    @Test
    fun `bounds with a single geo-tagged task does not throw`() {
        val result = MapGeo.bounds(
            trails = emptyList(),
            tasks = listOf(pointTask(56.95, 24.10)),
        )
        assertNotNull(result)
    }
}
