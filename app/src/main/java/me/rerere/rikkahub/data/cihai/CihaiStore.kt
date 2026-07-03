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
            } ?: CihaiState()
        }
        .catch { emit(CihaiState()) }
        .stateIn(scope, SharingStarted.Eagerly, CihaiState())

    suspend fun update(transform: (CihaiState) -> CihaiState) {
        context.cihaiDataStore.edit { prefs ->
            val current = prefs[stateKey]?.let { raw ->
                runCatching { json.decodeFromString<CihaiState>(raw) }.getOrDefault(CihaiState())
            } ?: CihaiState()
            prefs[stateKey] = json.encodeToString(transform(current).normalized())
        }
    }

    suspend fun selectAssistant(assistantId: String) {
        update { it.copy(selectedAssistantId = assistantId) }
    }

    suspend fun addEntry(entry: CihaiEntry) {
        update { state ->
            state.copy(entries = (listOf(entry) + state.entries).take(CIHAI_ENTRY_LIMIT))
        }
    }

    suspend fun markEntryMemorySaved(entryId: String) {
        update { state ->
            state.copy(entries = state.entries.map { entry ->
                if (entry.id == entryId) entry.copy(memorySaved = true) else entry
            })
        }
    }

    suspend fun addBook(book: CihaiBook) {
        update { state ->
            state.copy(books = (listOf(book) + state.books).take(CIHAI_BOOK_LIMIT))
        }
    }

    suspend fun updateBook(book: CihaiBook) {
        update { state ->
            state.copy(books = state.books.map { current ->
                if (current.id == book.id) book else current
            })
        }
    }

    private fun CihaiState.normalized(): CihaiState =
        copy(
            entries = entries
                .filter { it.assistantId.isNotBlank() && it.content.isNotBlank() }
                .distinctBy { it.id }
                .take(CIHAI_ENTRY_LIMIT),
            books = books
                .filter { it.assistantId.isNotBlank() && it.title.isNotBlank() && it.content.isNotBlank() }
                .distinctBy { it.id }
                .take(CIHAI_BOOK_LIMIT),
        )

    private companion object {
        const val CIHAI_ENTRY_LIMIT = 300
        const val CIHAI_BOOK_LIMIT = 60
    }
}
