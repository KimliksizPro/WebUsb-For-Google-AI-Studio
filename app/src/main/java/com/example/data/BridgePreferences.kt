package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "webusb_bridge_prefs")

enum class ThemePreference {
    DARK,
    LIGHT,
    SYSTEM
}

data class UserBridgeConfig(
    val adbPort: Int = 5555,
    val wsPort: Int = 8765,
    val autoPollAdbPort: Boolean = true,
    val lastBrowserUrl: String = "https://app.webadb.com",
    val themePreference: ThemePreference = ThemePreference.DARK
)

class BridgePreferences(private val context: Context) {

    companion object {
        private val KEY_ADB_PORT = intPreferencesKey("adb_port")
        private val KEY_WS_PORT = intPreferencesKey("ws_port")
        private val KEY_AUTO_POLL = booleanPreferencesKey("auto_poll_adb")
        private val KEY_LAST_URL = stringPreferencesKey("last_browser_url")
        private val KEY_THEME = stringPreferencesKey("theme_preference")
    }

    val configFlow: Flow<UserBridgeConfig> = context.dataStore.data.map { prefs ->
        val themeStr = prefs[KEY_THEME] ?: ThemePreference.DARK.name
        val theme = try {
            ThemePreference.valueOf(themeStr)
        } catch (_: Exception) {
            ThemePreference.DARK
        }
        UserBridgeConfig(
            adbPort = prefs[KEY_ADB_PORT] ?: 5555,
            wsPort = prefs[KEY_WS_PORT] ?: 8765,
            autoPollAdbPort = prefs[KEY_AUTO_POLL] ?: true,
            lastBrowserUrl = prefs[KEY_LAST_URL] ?: "https://app.webadb.com",
            themePreference = theme
        )
    }

    suspend fun setAdbPort(port: Int) {
        context.dataStore.edit { it[KEY_ADB_PORT] = port }
    }

    suspend fun setWsPort(port: Int) {
        context.dataStore.edit { it[KEY_WS_PORT] = port }
    }

    suspend fun setAutoPoll(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_POLL] = enabled }
    }

    suspend fun setLastBrowserUrl(url: String) {
        context.dataStore.edit { it[KEY_LAST_URL] = url }
    }

    suspend fun setThemePreference(theme: ThemePreference) {
        context.dataStore.edit { it[KEY_THEME] = theme.name }
    }
}
