package me.rerere.rikkahub.data.cihai

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.utils.JsonInstant

private val Context.cihaiDataStore: DataStore<Preferences> by preferencesDataStore(name = "cihai")

class CihaiStore(
    private val context: Context,
    scope: AppScope,
    private val json: Json = JsonInstant,
) {
    private val stateKey = stringPreferencesKey("state")

    val state: StateFlow<CihaiState> = context.cihaiDataStore.data
        .map { prefs ->
            prefs[stateKey]?.let { raw ->
                runCatching { json.decodeFromString<CihaiState>(raw) }.getOrDefault(CihaiState())
            }?.normalizedCihaiState() ?: CihaiState()
        }
        .catch { emit(CihaiState()) }
        .stateIn(scope, SharingStarted.Eagerly, CihaiState())

    suspend fun update(transform: (CihaiState) -> CihaiState) {
        context.cihaiDataStore.edit { prefs ->
            val current = prefs[stateKey]?.let { raw ->
                runCatching { json.decodeFromString<CihaiState>(raw) }.getOrDefault(CihaiState())
            }?.normalizedCihaiState() ?: CihaiState()
            prefs[stateKey] = json.encodeToString(transform(current).normalizedCihaiState())
        }
    }

    suspend fun snapshot(): CihaiState = context.cihaiDataStore.data.first()[stateKey]?.let { raw ->
        runCatching { json.decodeFromString<CihaiState>(raw) }.getOrDefault(CihaiState())
    }?.normalizedCihaiState() ?: CihaiState()

    suspend fun selectAssistant(assistantId: String) {
        update { it.copy(selectedAssistantId = assistantId) }
    }

    suspend fun addEntry(entry: CihaiEntry) {
        update { state -> state.addDiaryEntry(entry) }
    }

    suspend fun deleteEntry(entryId: String) {
        update { state -> state.removeCihaiEntry(entryId) }
    }

    suspend fun clearAssistantRecords(assistantId: String) {
        update { state -> state.withoutAssistantRecords(assistantId) }
    }

}

internal fun CihaiState.normalizedCihaiState(): CihaiState {
    val normalizedEntries = entries
        .filter { it.assistantId.isNotBlank() && it.content.isNotBlank() && it.kind == CihaiEntryKind.DIARY }
        .distinctBy { it.id }
        .take(CIHAI_ENTRY_LIMIT)
    return copy(
        entries = normalizedEntries,
    )
}

internal fun CihaiState.addDiaryEntry(entry: CihaiEntry): CihaiState {
    if (entry.kind != CihaiEntryKind.DIARY ||
        entry.id.isBlank() ||
        entry.assistantId.isBlank() ||
        entry.content.isBlank()
    ) {
        return this
    }
    return copy(
        entries = listOf(entry) + entries.filterNot { it.id == entry.id },
    )
}

internal fun CihaiState.removeCihaiEntry(entryId: String): CihaiState = copy(
    entries = entries.filterNot { it.id == entryId },
)

internal fun CihaiState.withoutAssistantRecords(assistantId: String): CihaiState {
    if (assistantId.isBlank()) return this
    return copy(
        entries = entries.filterNot { it.assistantId == assistantId },
    )
}

private const val CIHAI_ENTRY_LIMIT = 300
