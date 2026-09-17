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

private val Context.mapDisplayStore: DataStore<Preferences> by
    preferencesDataStore(name = "trailkeeper_offgrid_map_display")

/**
 * How big task/structure/report dots render on the map, in dp - the default
 * was too small to comfortably tap, and different people/screens want
 * different sizes, so it's user-configurable rather than a fixed constant.
 * Same cached-value + Flow + suspend-setter shape as [Identity]/[ThemePrefs].
 */
object MapDisplayPrefs {
    const val DEFAULT_MARKER_RADIUS_DP = 10.0
    const val MIN_MARKER_RADIUS_DP = 4.0
    const val MAX_MARKER_RADIUS_DP = 24.0

    private val MARKER_RADIUS_DP = doublePreferencesKey("marker_radius_dp")

    @Volatile private var cachedRadiusDp: Double = DEFAULT_MARKER_RADIUS_DP
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
        runBlocking {
            cachedRadiusDp = appContext.mapDisplayStore.data.first()[MARKER_RADIUS_DP] ?: DEFAULT_MARKER_RADIUS_DP
        }
    }

    fun markerRadiusDp(): Double = cachedRadiusDp

    fun markerRadiusDpFlow(): Flow<Double> =
        appContext.mapDisplayStore.data.map { it[MARKER_RADIUS_DP] ?: DEFAULT_MARKER_RADIUS_DP }

    suspend fun setMarkerRadiusDp(dp: Double) {
        val clamped = dp.coerceIn(MIN_MARKER_RADIUS_DP, MAX_MARKER_RADIUS_DP)
        appContext.mapDisplayStore.edit { it[MARKER_RADIUS_DP] = clamped }
        cachedRadiusDp = clamped
    }
}
