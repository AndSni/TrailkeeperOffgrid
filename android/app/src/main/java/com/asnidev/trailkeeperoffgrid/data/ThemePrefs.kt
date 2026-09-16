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

private val Context.themeStore: DataStore<Preferences> by
    preferencesDataStore(name = "trailkeeper_offgrid_theme")

/**
 * Appearance preferences: dark/light mode and an accent-color swatch key.
 * Mirrors [Identity]'s cached-value + Flow + suspend-setter shape so both
 * Settings (writes) and [com.asnidev.trailkeeperoffgrid.ui.theme.TrailkeeperTheme]
 * (reads, reactively) can use it the same way. The actual color values for
 * each accent key live in `ui/theme/Theme.kt` — this object only stores the
 * choice, it has no Compose dependency.
 */
object ThemePrefs {
    enum class Mode { SYSTEM, LIGHT, DARK }

    const val DEFAULT_ACCENT = "moss"

    private val MODE = stringPreferencesKey("theme_mode")
    private val ACCENT = stringPreferencesKey("theme_accent")

    @Volatile private var cachedMode: Mode = Mode.SYSTEM
    @Volatile private var cachedAccent: String = DEFAULT_ACCENT
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
        runBlocking {
            val prefs = appContext.themeStore.data.first()
            cachedMode = prefs[MODE].toMode()
            cachedAccent = prefs[ACCENT] ?: DEFAULT_ACCENT
        }
    }

    fun mode(): Mode = cachedMode

    fun accent(): String = cachedAccent

    fun modeFlow(): Flow<Mode> = appContext.themeStore.data.map { it[MODE].toMode() }

    fun accentFlow(): Flow<String> = appContext.themeStore.data.map { it[ACCENT] ?: DEFAULT_ACCENT }

    suspend fun setMode(mode: Mode) {
        appContext.themeStore.edit { it[MODE] = mode.name }
        cachedMode = mode
    }

    suspend fun setAccent(key: String) {
        appContext.themeStore.edit { it[ACCENT] = key }
        cachedAccent = key
    }

    private fun String?.toMode(): Mode = this?.let { runCatching { Mode.valueOf(it) }.getOrNull() } ?: Mode.SYSTEM
}
