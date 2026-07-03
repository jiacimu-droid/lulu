package me.rerere.rikkahub.data.desktop

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.utils.JsonInstant

private val Context.desktopDataStore: DataStore<Preferences> by preferencesDataStore(name = "desktop_layout")

class DesktopStore(
    private val context: Context,
    scope: AppScope,
    private val json: Json = JsonInstant,
) {
    private val appOrderKey = stringPreferencesKey("app_order")

    val appOrder: StateFlow<List<String>> = context.desktopDataStore.data
        .map { prefs ->
            prefs[appOrderKey]?.let { raw ->
                runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
            }.orEmpty()
        }
        .catch { emit(emptyList()) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    suspend fun setAppOrder(keys: List<String>) {
        context.desktopDataStore.edit { prefs ->
            prefs[appOrderKey] = json.encodeToString(keys.distinct())
        }
    }
}
