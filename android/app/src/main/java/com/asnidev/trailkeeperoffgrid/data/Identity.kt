package com.asnidev.trailkeeperoffgrid.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.settingsStore: DataStore<Preferences> by
    preferencesDataStore(name = "trailkeeper_offgrid_settings")

/**
 * Replaces Trailkeeper's account `Session`. Offgrid is single-user with no
 * login: there is exactly one implicit local workspace and one implicit
 * local author. The only real preference here is an **optional** display
 * name the user can set in Settings; it is a label for exported files and
 * for handing a GPX to someone else, nothing more.
 *
 * [ORG_ID] / [USER_ID] are stable constants kept so the ported entities,
 * mappers and queries (which still carry `organisation_id` / `created_by`
 * columns) don't need to change.
 */
object Identity {
    const val ORG_ID = "local"
    const val USER_ID = "me"

    private val DISPLAY_NAME = stringPreferencesKey("display_name")

    @Volatile private var cachedName: String = ""
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
        runBlocking {
            cachedName = appContext.settingsStore.data.first()[DISPLAY_NAME].orEmpty()
        }
    }

    /** The single local org id. */
    fun currentOrgId(): String = ORG_ID

    /** The single local author id. */
    fun currentUserId(): String = USER_ID

    /** What to show as the author of things this device records. Falls back
     * to "Me" when the user hasn't set a name. */
    fun displayName(): String = cachedName.ifBlank { "Me" }

    /** The raw stored name (may be blank). */
    fun rawDisplayName(): String = cachedName

    /** Live stream of the display name for Settings. */
    fun displayNameFlow(): Flow<String> =
        appContext.settingsStore.data.map { it[DISPLAY_NAME].orEmpty() }

    suspend fun setDisplayName(name: String) {
        val trimmed = name.trim()
        appContext.settingsStore.edit {
            if (trimmed.isEmpty()) it.remove(DISPLAY_NAME) else it[DISPLAY_NAME] = trimmed
        }
        cachedName = trimmed
    }
}
