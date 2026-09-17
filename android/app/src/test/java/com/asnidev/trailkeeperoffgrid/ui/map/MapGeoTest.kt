package com.asnidev.trailkeeperoffgrid.ui.map

import com.asnidev.trailkeeperoffgrid.data.local.StructureEntity
import com.asnidev.trailkeeperoffgrid.data.local.TaskEntity
import com.asnidev.trailkeeperoffgrid.data.local.TrackEntity
import com.asnidev.trailkeeperoffgrid.data.local.TrailEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
            projectId = "p1",
            trails = emptyList(),
            tasks = listOf(pointTask(56.95, 24.10)),
            structures = emptyList(),
            tracks = emptyList(),
            radiusM = 10.0,
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

    private fun trail(id: String, projectId: String?, geometryJson: String?) = TrailEntity(
        id = id,
        organisationId = "o1",
        projectId = projectId,
        name = id,
        activity = "mtb",
        difficulty = "",
        status = "open",
        source = "walked",
        lengthM = 100.0,
        geometryJson = geometryJson,
    )

    private fun structure(id: String, projectId: String?, geometryJson: String?) = StructureEntity(
        id = id,
        organisationId = "o1",
        projectId = projectId,
        name = id,
        structureType = "culvert",
        status = "good",
        geometryJson = geometryJson,
        nearestTrailId = null,
        material = "",
        color = "",
        installedOn = null,
        inspectionIntervalDays = null,
        notes = "",
    )

    private fun track(id: String, projectId: String, geometryJson: String) = TrackEntity(
        id = id,
        projectId = projectId,
        name = id,
        activity = "mtb",
        source = "recorded",
        startedAt = null,
        endedAt = null,
        movingSeconds = 0,
        lengthM = 111.0,
        pointCount = 2,
        geometryJson = geometryJson,
        recordedById = null,
    )

    // A short roughly-N/S line, ~111m long, in project p1's own recorded track.
    private val ownTrackLine =
        """{"type":"LineString","coordinates":[[24.0000,56.9000],[24.0000,56.9010]]}"""

    @Test
    fun `a trail created in this project is in scope regardless of distance`() {
        val farAwayGeometry = """{"type":"LineString","coordinates":[[24.0,57.5],[24.01,57.5]]}"""
        val scope = MapGeo.projectScope(
            projectId = "p1",
            trails = listOf(trail("own", projectId = "p1", geometryJson = farAwayGeometry)),
            tasks = emptyList(),
            structures = emptyList(),
            tracks = listOf(track("t1", "p1", ownTrackLine)),
            radiusM = 10.0,
        )
        assertTrue(scope.trailIds.contains("own"))
    }

    @Test
    fun `an unrelated trail far from this project's lines is out of scope`() {
        val farAwayGeometry = """{"type":"LineString","coordinates":[[24.0,57.5],[24.01,57.5]]}"""
        val scope = MapGeo.projectScope(
            projectId = "p1",
            trails = listOf(trail("other", projectId = "p2", geometryJson = farAwayGeometry)),
            tasks = emptyList(),
            structures = emptyList(),
            tracks = listOf(track("t1", "p1", ownTrackLine)),
            radiusM = 10.0,
        )
        assertFalse(scope.trailIds.contains("other"))
    }

    @Test
    fun `an unassigned trail within the radius of this project's track is in scope`() {
        // ~5m east of the track's midpoint at this latitude.
        val nearGeometry =
            """{"type":"LineString","coordinates":[[24.0000822,56.9005],[24.001,56.9005]]}"""
        val scope = MapGeo.projectScope(
            projectId = "p1",
            trails = listOf(trail("near", projectId = null, geometryJson = nearGeometry)),
            tasks = emptyList(),
            structures = emptyList(),
            tracks = listOf(track("t1", "p1", ownTrackLine)),
            radiusM = 10.0,
        )
        assertTrue(scope.trailIds.contains("near"))
    }

    @Test
    fun `a structure created in this project is in scope regardless of distance`() {
        val scope = MapGeo.projectScope(
            projectId = "p1",
            trails = emptyList(),
            tasks = emptyList(),
            structures = listOf(
                structure("own", projectId = "p1", geometryJson = """{"type":"Point","coordinates":[24.01,57.5]}"""),
            ),
            tracks = listOf(track("t1", "p1", ownTrackLine)),
            radiusM = 10.0,
        )
        assertTrue(scope.structureIds.contains("own"))
    }

    @Test
    fun `an unassigned structure beyond the radius of this project's lines is out of scope`() {
        val scope = MapGeo.projectScope(
            projectId = "p1",
            trails = emptyList(),
            tasks = emptyList(),
            structures = listOf(
                structure("far", projectId = null, geometryJson = """{"type":"Point","coordinates":[24.01,57.5]}"""),
            ),
            tracks = listOf(track("t1", "p1", ownTrackLine)),
            radiusM = 10.0,
        )
        assertFalse(scope.structureIds.contains("far"))
    }

    @Test
    fun `an unassigned structure within the radius of this project's track is in scope`() {
        val scope = MapGeo.projectScope(
            projectId = "p1",
            trails = emptyList(),
            tasks = emptyList(),
            structures = listOf(
                structure("near", projectId = null, geometryJson = """{"type":"Point","coordinates":[24.0000822,56.9005]}"""),
            ),
            tracks = listOf(track("t1", "p1", ownTrackLine)),
            radiusM = 10.0,
        )
        assertTrue(scope.structureIds.contains("near"))
    }
}
