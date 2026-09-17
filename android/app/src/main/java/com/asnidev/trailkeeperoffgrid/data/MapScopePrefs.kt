package com.asnidev.trailkeeperoffgrid.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.mapScopeStore: DataStore<Preferences> by
    preferencesDataStore(name = "trailkeeper_offgrid_map_scope")

/**
 * How close (in metres) an org-wide trail/structure must be to one of a
 * project's own lines (its recorded/imported tracks, plus trails created in
 * that project) to count as "this project's" on the map's This-project /
 * All-assets toggle — see [com.asnidev.trailkeeperoffgrid.ui.map.MapGeo.projectScope].
 * Different terrain needs different tolerances (a tight singletrack network
 * vs. sparse backcountry), so this is user-configurable rather than a fixed
 * constant. Same cached-value + Flow + suspend-setter shape as [Identity].
 */
object MapScopePrefs {
    const val DEFAULT_RADIUS_M = 10.0
    const val MIN_RADIUS_M = 1.0
    const val MAX_RADIUS_M = 500.0

    private val RADIUS_M = doublePreferencesKey("scope_radius_m")

    @Volatile private var cachedRadiusM: Double = DEFAULT_RADIUS_M
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
        runBlocking {
            cachedRadiusM = appContext.mapScopeStore.data.first()[RADIUS_M] ?: DEFAULT_RADIUS_M
        }
    }

    fun radiusM(): Double = cachedRadiusM

    fun radiusMFlow(): Flow<Double> =
        appContext.mapScopeStore.data.map { it[RADIUS_M] ?: DEFAULT_RADIUS_M }

    suspend fun setRadiusM(meters: Double) {
        val clamped = meters.coerceIn(MIN_RADIUS_M, MAX_RADIUS_M)
        appContext.mapScopeStore.edit { it[RADIUS_M] = clamped }
        cachedRadiusM = clamped
    }
}
