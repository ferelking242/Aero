package com.velobrowser.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "velo_settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    object Keys {
        val JAVASCRIPT_ENABLED = booleanPreferencesKey("javascript_enabled")
        val IMAGES_ENABLED = booleanPreferencesKey("images_enabled")
        val AD_BLOCKER_ENABLED = booleanPreferencesKey("ad_blocker_enabled")
        val ULTRA_FAST_MODE = booleanPreferencesKey("ultra_fast_mode")
        val DESKTOP_MODE = booleanPreferencesKey("desktop_mode")
        val SAFE_BROWSING = booleanPreferencesKey("safe_browsing")
        val SEARCH_ENGINE = stringPreferencesKey("search_engine")
        val HOMEPAGE = stringPreferencesKey("homepage")
        val LANGUAGE = stringPreferencesKey("language")
        val ACTIVE_PROFILE_ID = longPreferencesKey("active_profile_id")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
    }

    object Defaults {
        const val JAVASCRIPT_ENABLED = true
        const val IMAGES_ENABLED = true
        const val AD_BLOCKER_ENABLED = true
        const val ULTRA_FAST_MODE = false
        const val DESKTOP_MODE = false
        const val SAFE_BROWSING = true
        const val SEARCH_ENGINE = "https://www.google.com/search?q="
        const val HOMEPAGE = "https://www.google.com"
        const val LANGUAGE = "system"
        const val ACTIVE_PROFILE_ID = 1L
        const val DARK_MODE = false
    }

    val settings: Flow<BrowserSettings> = context.dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences())
            else throw e
        }
        .map { prefs ->
            BrowserSettings(
                javascriptEnabled = prefs[Keys.JAVASCRIPT_ENABLED] ?: Defaults.JAVASCRIPT_ENABLED,
                imagesEnabled = prefs[Keys.IMAGES_ENABLED] ?: Defaults.IMAGES_ENABLED,
                adBlockerEnabled = prefs[Keys.AD_BLOCKER_ENABLED] ?: Defaults.AD_BLOCKER_ENABLED,
                ultraFastMode = prefs[Keys.ULTRA_FAST_MODE] ?: Defaults.ULTRA_FAST_MODE,
                desktopMode = prefs[Keys.DESKTOP_MODE] ?: Defaults.DESKTOP_MODE,
                safeBrowsing = prefs[Keys.SAFE_BROWSING] ?: Defaults.SAFE_BROWSING,
                searchEngine = prefs[Keys.SEARCH_ENGINE] ?: Defaults.SEARCH_ENGINE,
                homepage = prefs[Keys.HOMEPAGE] ?: Defaults.HOMEPAGE,
                language = prefs[Keys.LANGUAGE] ?: Defaults.LANGUAGE,
                activeProfileId = prefs[Keys.ACTIVE_PROFILE_ID] ?: Defaults.ACTIVE_PROFILE_ID,
                darkMode = prefs[Keys.DARK_MODE] ?: Defaults.DARK_MODE
            )
        }

    suspend fun setJavascriptEnabled(enabled: Boolean) = update { it[Keys.JAVASCRIPT_ENABLED] = enabled }
    suspend fun setImagesEnabled(enabled: Boolean) = update { it[Keys.IMAGES_ENABLED] = enabled }
    suspend fun setAdBlockerEnabled(enabled: Boolean) = update { it[Keys.AD_BLOCKER_ENABLED] = enabled }
    suspend fun setUltraFastMode(enabled: Boolean) = update { it[Keys.ULTRA_FAST_MODE] = enabled }
    suspend fun setDesktopMode(enabled: Boolean) = update { it[Keys.DESKTOP_MODE] = enabled }
    suspend fun setSafeBrowsing(enabled: Boolean) = update { it[Keys.SAFE_BROWSING] = enabled }
    suspend fun setSearchEngine(url: String) = update { it[Keys.SEARCH_ENGINE] = url }
    suspend fun setHomepage(url: String) = update { it[Keys.HOMEPAGE] = url }
    suspend fun setLanguage(lang: String) = update { it[Keys.LANGUAGE] = lang }
    suspend fun setActiveProfileId(id: Long) = update { it[Keys.ACTIVE_PROFILE_ID] = id }
    suspend fun setDarkMode(enabled: Boolean) = update { it[Keys.DARK_MODE] = enabled }

    private suspend fun update(block: (MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}

data class BrowserSettings(
    val javascriptEnabled: Boolean = true,
    val imagesEnabled: Boolean = true,
    val adBlockerEnabled: Boolean = true,
    val ultraFastMode: Boolean = false,
    val desktopMode: Boolean = false,
    val safeBrowsing: Boolean = true,
    val searchEngine: String = "https://www.google.com/search?q=",
    val homepage: String = "https://www.google.com",
    val language: String = "system",
    val activeProfileId: Long = 1L,
    val darkMode: Boolean = false
)
