package me.rerere.rikkahub.data.datastore.migration

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.utils.JsonInstant

class PreferenceStoreV4Migration : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean {
        val version = currentData[SettingsStore.VERSION]
        return version == null || version < 4
    }

    override suspend fun migrate(currentData: Preferences): Preferences {
        val prefs = currentData.toMutablePreferences()

        prefs[SettingsStore.PROVIDERS] = prefs[SettingsStore.PROVIDERS]?.let { json ->
            enableClaudePromptCaching(json)
        } ?: "[]"

        prefs[SettingsStore.VERSION] = 4
        return prefs.toPreferences()
    }

    override suspend fun cleanUp() {}
}

internal fun enableClaudePromptCaching(providersJson: String): String {
    return runCatching {
        val root = JsonInstant.parseToJsonElement(providersJson).jsonArray
        val migrated = JsonArray(
            root.map { provider ->
                val providerObj = provider as? JsonObject ?: return@map provider
                val type = providerObj["type"]?.jsonPrimitive?.content ?: return@map provider
                if (type != "claude") {
                    return@map provider
                }
                JsonObject(
                    providerObj.toMutableMap().apply {
                        put("promptCaching", JsonPrimitive(true))
                    }
                )
            }
        )
        if (migrated == root) providersJson else JsonInstant.encodeToString(migrated)
    }.getOrElse { providersJson }
}
