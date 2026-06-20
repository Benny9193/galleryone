package com.example.myapplication.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class StartTab { PHOTOS, ALBUMS }

/** User-configurable app settings. */
data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val startTab: StartTab = StartTab.PHOTOS,
    val autoPlayVideos: Boolean = true,
    val dynamicColor: Boolean = true,
)

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Persists [AppSettings] with Jetpack DataStore. */
class SettingsRepository(context: Context) {

    private val dataStore = context.applicationContext.dataStore

    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            theme = prefs[KEY_THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            startTab = prefs[KEY_START_TAB]?.let { runCatching { StartTab.valueOf(it) }.getOrNull() }
                ?: StartTab.PHOTOS,
            autoPlayVideos = prefs[KEY_AUTOPLAY] ?: true,
            dynamicColor = prefs[KEY_DYNAMIC_COLOR] ?: true,
        )
    }

    suspend fun setTheme(theme: ThemeMode) {
        dataStore.edit { it[KEY_THEME] = theme.name }
    }

    suspend fun setStartTab(tab: StartTab) {
        dataStore.edit { it[KEY_START_TAB] = tab.name }
    }

    suspend fun setAutoPlayVideos(enabled: Boolean) {
        dataStore.edit { it[KEY_AUTOPLAY] = enabled }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[KEY_DYNAMIC_COLOR] = enabled }
    }

    private companion object {
        val KEY_THEME = stringPreferencesKey("theme")
        val KEY_START_TAB = stringPreferencesKey("start_tab")
        val KEY_AUTOPLAY = booleanPreferencesKey("autoplay_videos")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    }
}
